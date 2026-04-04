"""
Sydia - 执行脑 (Execution Agent)
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
from app.memory import memory_search_text, memory_save

client = AsyncOpenAI(api_key=os.getenv("OPENAI_API_KEY", ""), base_url=os.getenv("OPENAI_BASE_URL", None))
MODEL = os.getenv("LLM_MODEL", "gpt-4o")

SYSTEM_PROMPT = """你是 Sydia, 一个高级 Web 自动化智能体。

## 工作方式
1. 观察截图、当前 URL 和 相关记忆
2. 每次调用一个工具来执行操作
3. 任务完成后调用 task_done

## 关于记忆
- 相关记忆是系统根据当前任务从向量数据库检索出的历史经验, 包含类似任务的路径或用户偏好
"""

_browser, _page = None, None

async def get_page() -> Page:
    global _browser, _page
    if _browser is None:
        pw = await async_playwright().start()
        _browser = await pw.chromium.launch(headless=True)
    if _page is None or _page.is_closed():
        _page = await _browser.new_page(viewport={"width": 1280, "height": 720})
    return _page

async def capture_screenshot() -> tuple[str, str]:
    page = await get_page()
    url = page.url
    agent_state.current_url = url
    try:
        screenshot_bytes = await page.screenshot(type="jpeg", quality=50)
        b64 = base64.b64encode(screenshot_bytes).decode("utf-8")
        agent_state.last_screenshot_b64 = b64
        return url, b64
    except Exception: return url, ""

async def run_tool(name: str, args: dict) -> str:
    page = await get_page()
    try:
        if name == "navigate":
            await page.goto(args["url"], timeout=15000)
            return f"已导航到 {args['url']}"
        elif name == "click":
            locator = page.get_by_text(args["target_text"]).first
            await locator.click(timeout=5000)
            return f"已点击 '{args['target_text']}'"
        elif name == "type_text":
            locator = page.get_by_placeholder(args["target_text"]).first
            try: await locator.fill(args["text"], timeout=3000)
            except Exception: await page.get_by_text(args["target_text"]).first.fill(args["text"], timeout=3000)
            return f"在 '{args['target_text']}' 输入了文字"
        elif name == "scroll":
            delta = -500 if args.get("direction") == "up" else 500
            await page.mouse.wheel(0, delta)
            return f"已向{args.get('direction')}滚动"
        elif name == "wait":
            await asyncio.sleep(float(args.get("seconds", 2)))
            return f"已等待 {args.get('seconds')} 秒"
        elif name == "task_done": return f"__TASK_DONE__:{args.get('summary', '任务完成')}"
        elif name == "task_failed": return f"__TASK_FAILED__:{args.get('reason', '未知原因')}"
        elif name == "send_email":
            await send_email(args.get("to", os.getenv("TRUSTED_SENDER", "")), args.get("subject", "Sydia 通知"), args.get("content", ""))
            return f"邮件已发送给 {args.get('to')}"
        return f"未知工具: {name}"
    except Exception as e: return f"工具执行失败: {e}"

async def ask_execution_llm(task: Task, url: str, b64_img: str, history: list[dict]) -> dict:
    memory_context = await memory_search_text(f"任务: {task.title}\n详情: {task.detail}\n当前URL: {url}")
    user_content = [
        {"type": "text", "text": f"## 当前任务\n{task.detail}\n\n## 当前 URL\n{url}\n\n## 相关记忆\n{memory_context or '无相关历史记忆'}\n\n请观察截图并决策。"},
        {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64_img}", "detail": "low"}}
    ]
    messages = [{"role": "system", "content": SYSTEM_PROMPT}] + history[-20:] + [{"role": "user", "content": user_content}]
    return await client.chat.completions.create(model=MODEL, messages=messages, tools=tool_registry.get_openai_tools(), tool_choice="required", temperature=0.1)

async def execution_loop():
    while True:
        task = await agent_state.pop_next_task()
        if task is None:
            await asyncio.sleep(2)
            continue
        agent_state.execution_status = f"执行中: {task.title}"
        await memory_save(f"开始执行任务: {task.title}", metadata={"task_id": task.id})
        page = await get_page()
        if page.url == "about:blank": await page.goto("https://www.google.com")
        exec_history = []
        for step in range(30):
            url, b64 = await capture_screenshot()
            try: response = await ask_execution_llm(task, url, b64, exec_history)
            except Exception as e:
                agent_state._log("llm_error", str(e))
                await asyncio.sleep(5); continue
            msg = response.choices[0].message
            exec_history.append({"role": "assistant", "content": msg.content or "", "tool_calls": [
                {"id": tc.id, "type": "function", "function": {"name": tc.function.name, "arguments": tc.function.arguments}}
                for tc in (msg.tool_calls or [])
            ] if msg.tool_calls else None})
            if not msg.tool_calls: continue
            tool_call = msg.tool_calls[0]
            result = await run_tool(tool_call.function.name, json.loads(tool_call.function.arguments))
            exec_history.append({"role": "tool", "tool_call_id": tool_call.id, "content": result})
            if result.startswith("__TASK_DONE__"):
                summary = result.split(":", 1)[1]
                await agent_state.finish_task(task.id, result=summary)
                await memory_save(f"任务完成: {task.title} | 总结: {summary}", metadata={"task_id": task.id})
                break
            elif result.startswith("__TASK_FAILED__"):
                reason = result.split(":", 1)[1]
                await agent_state.finish_task(task.id, result=reason, failed=True)
                await memory_save(f"任务失败: {task.title} | 原因: {reason}", metadata={"task_id": task.id})
                break
        else:
            await agent_state.finish_task(task.id, result="超时", failed=True)
