"""
Sydia - 执行脑 (Execution Agent)
后台常驻循环: 从 Task Pool 取任务 -> 截图感知 -> LLM 决策 -> 工具执行 -> 循环
对应手稿图1左侧: task+plan+tools+角色设定+Memory+context -> LLM -> tools -> act -> 结果
"""
import asyncio
import base64
import json
import os
from openai import AsyncOpenAI
from playwright.async_api import async_playwright, Browser, Page

from app.state import agent_state, Task
from app.tools import tool_registry
from app.email_service import send_email

client = AsyncOpenAI(
    api_key=os.getenv("OPENAI_API_KEY", ""),
    base_url=os.getenv("OPENAI_BASE_URL", None),
)
MODEL = os.getenv("LLM_MODEL", "gpt-4o")

SYSTEM_PROMPT = """你是 Sydia, 一个高级 Web 自动化智能体。你正在通过浏览器执行用户交给你的任务。

## 工作方式
1. 你会收到当前浏览器页面的截图和当前 URL
2. 你需要观察截图内容, 决定下一步操作
3. 通过调用工具 (function calling) 来执行操作
4. 每次只调用 **一个** 工具

## 重要原则
- 仔细观察截图, 确认页面状态后再行动
- 如果页面还在加载, 调用 wait 等待
- 如果任务已完成, 立刻调用 task_done
- 如果遇到验证码/登录墙等无法绕过的障碍, 调用 task_failed
- 如果需要向用户汇报或求助, 调用 send_email
"""

# ── 外部记忆 (模拟 RAG / Memory) ──
MEMORY_STORE: dict[str, str] = {}


async def get_memory(query: str) -> str:
    """模拟外部记忆检索, 实际项目中接向量数据库"""
    matches = []
    for key, val in MEMORY_STORE.items():
        if key.lower() in query.lower():
            matches.append(val)
    return "; ".join(matches) if matches else ""


async def save_memory(key: str, value: str):
    MEMORY_STORE[key] = value


# ── 浏览器管理 ──

_browser: Browser | None = None
_page: Page | None = None


async def get_page() -> Page:
    global _browser, _page
    if _browser is None:
        pw = await async_playwright().start()
        _browser = await pw.chromium.launch(headless=True)
    if _page is None or _page.is_closed():
        _page = await _browser.new_page(viewport={"width": 1280, "height": 720})
        await _page.goto("about:blank")
    return _page


async def capture_screenshot() -> tuple[str, str]:
    """截图并返回 (url, base64_jpeg)"""
    page = await get_page()
    url = page.url
    agent_state.current_url = url
    try:
        screenshot_bytes = await page.screenshot(type="jpeg", quality=50)
        b64 = base64.b64encode(screenshot_bytes).decode("utf-8")
        agent_state.last_screenshot_b64 = b64
        return url, b64
    except Exception:
        return url, ""


# ── 工具执行器 ──

async def run_tool(name: str, args: dict) -> str:
    """执行一个具体工具并返回结果文本"""
    page = await get_page()

    try:
        if name == "navigate":
            url = args["url"]
            await page.goto(url, timeout=15000, wait_until="domcontentloaded")
            agent_state._log("navigate", f"已导航到 {url}")
            return f"已导航到 {url}"

        elif name == "click":
            target = args["target_text"]
            locator = page.get_by_text(target).first
            await locator.scroll_into_view_if_needed(timeout=3000)
            await locator.click(timeout=5000)
            await page.wait_for_timeout(1500)
            agent_state._log("click", f"已点击 '{target}'")
            return f"已点击 '{target}'"

        elif name == "type_text":
            target = args["target_text"]
            text = args["text"]
            # 尝试通过 placeholder 定位
            locator = page.get_by_placeholder(target).first
            try:
                await locator.fill(text, timeout=3000)
            except Exception:
                locator = page.get_by_text(target).first
                await locator.fill(text, timeout=3000)
            await page.wait_for_timeout(500)
            agent_state._log("type", f"在 '{target}' 输入了文字")
            return f"在 '{target}' 中输入了 '{text}'"

        elif name == "scroll":
            direction = args.get("direction", "down")
            delta = -500 if direction == "up" else 500
            await page.mouse.wheel(0, delta)
            await page.wait_for_timeout(800)
            return f"已向{direction}滚动"

        elif name == "wait":
            seconds = min(max(float(args.get("seconds", 2)), 0.5), 10)
            await page.wait_for_timeout(int(seconds * 1000))
            return f"已等待 {seconds} 秒"

        elif name == "screenshot":
            return "截图已更新, 请观察"

        elif name == "task_done":
            summary = args.get("summary", "任务完成")
            return f"__TASK_DONE__:{summary}"

        elif name == "task_failed":
            reason = args.get("reason", "未知原因")
            return f"__TASK_FAILED__:{reason}"

        elif name == "send_email":
            to = args.get("to", os.getenv("TRUSTED_SENDER", ""))
            subject = args.get("subject", "Sydia 通知")
            content = args.get("content", "")
            await send_email(to, subject, content)
            return f"邮件已发送给 {to}"

        else:
            return f"未知工具: {name}"

    except Exception as e:
        agent_state._log("tool_error", f"{name} 执行失败: {e}")
        return f"工具执行失败: {e}"


# ── LLM 决策 ──

async def ask_execution_llm(task: Task, url: str, b64_img: str, history: list[dict]) -> dict:
    """请求执行脑 LLM 做出下一步决策"""
    memory = await get_memory(task.detail)

    user_content = []
    user_content.append({
        "type": "text",
        "text": (
            f"## 当前任务\n{task.detail}\n\n"
            f"## 当前 URL\n{url}\n\n"
            f"## 相关记忆\n{memory or '无'}\n\n"
            "请观察截图并决定下一步操作。调用一个工具。"
        ),
    })
    if b64_img:
        user_content.append({
            "type": "image_url",
            "image_url": {"url": f"data:image/jpeg;base64,{b64_img}", "detail": "low"},
        })

    messages = [{"role": "system", "content": SYSTEM_PROMPT}]
    # 加入最近的执行历史 (最多 10 轮)
    messages.extend(history[-20:])
    messages.append({"role": "user", "content": user_content})

    response = await client.chat.completions.create(
        model=MODEL,
        messages=messages,
        tools=tool_registry.get_openai_tools(),
        tool_choice="required",
        temperature=0.1,
        max_tokens=1024,
    )
    return response


# ── 主执行循环 ──

async def execution_loop():
    """常驻后台: 不断从 Task Pool 取任务并执行"""
    print("[ExecutionAgent] 🚀 执行脑已启动, 等待任务...")

    while True:
        task = await agent_state.pop_next_task()
        if task is None:
            await asyncio.sleep(2)
            continue

        agent_state.execution_status = f"执行中: {task.title}"
        agent_state._log("exec_start", f"开始执行任务: {task.title}")

        # 初始导航 (如果任务里有 URL 就打开)
        page = await get_page()
        if page.url == "about:blank":
            await page.goto("https://www.google.com", wait_until="domcontentloaded")

        exec_history: list[dict] = []
        max_steps = 30  # 防止死循环

        for step in range(max_steps):
            # 1. 环境感知
            url, b64 = await capture_screenshot()

            # 2. LLM 决策
            try:
                response = await ask_execution_llm(task, url, b64, exec_history)
            except Exception as e:
                agent_state._log("llm_error", str(e))
                await asyncio.sleep(5)
                continue

            msg = response.choices[0].message

            # 记录 assistant 的完整回复到执行历史
            exec_history.append({"role": "assistant", "content": msg.content or "", "tool_calls": [
                {"id": tc.id, "type": "function", "function": {"name": tc.function.name, "arguments": tc.function.arguments}}
                for tc in (msg.tool_calls or [])
            ] if msg.tool_calls else None})

            if not msg.tool_calls:
                # LLM 没有调用工具, 可能在思考, 继续
                await asyncio.sleep(1)
                continue

            # 3. 执行工具
            tool_call = msg.tool_calls[0]
            fn_name = tool_call.function.name
            try:
                fn_args = json.loads(tool_call.function.arguments)
            except json.JSONDecodeError:
                fn_args = {}

            agent_state._log("tool_call", f"Step {step+1}: {fn_name}({fn_args})")
            result = await run_tool(fn_name, fn_args)

            # 记录 tool 结果到执行历史
            exec_history.append({
                "role": "tool",
                "tool_call_id": tool_call.id,
                "content": result,
            })

            # 4. 检查终止条件
            if result.startswith("__TASK_DONE__:"):
                summary = result.split(":", 1)[1]
                await agent_state.finish_task(task.id, result=summary)
                agent_state._log("exec_done", f"✅ 任务完成: {summary}")

                # 完成后主动发邮件汇报
                trusted = os.getenv("TRUSTED_SENDER", "")
                if trusted:
                    await send_email(
                        trusted,
                        f"[Sydia 汇报] {task.title}",
                        f"任务已完成。\n\n结果总结:\n{summary}",
                    )
                break

            elif result.startswith("__TASK_FAILED__:"):
                reason = result.split(":", 1)[1]
                await agent_state.finish_task(task.id, result=reason, failed=True)
                agent_state._log("exec_fail", f"❌ 任务失败: {reason}")

                trusted = os.getenv("TRUSTED_SENDER", "")
                if trusted:
                    await send_email(
                        trusted,
                        f"[Sydia 求助] {task.title}",
                        f"任务执行失败, 需要您的协助。\n\n原因:\n{reason}",
                    )
                break
        else:
            # 超过最大步数
            await agent_state.finish_task(task.id, result="超过最大执行步数", failed=True)
            agent_state._log("exec_timeout", f"⏰ 任务超时: {task.title}")
