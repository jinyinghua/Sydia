import asyncio
import os
from app.state import state

# 浏览器用户数据目录，挂载持久卷后 Cookie/Session 重启不丢
DATA_DIR = os.getenv("DATA_DIR", "/app/data")
BROWSER_SESSION_DIR = os.path.join(DATA_DIR, "browser_session")


async def run_playwright_loop():
    """
    执行脑核心循环：
    1. 使用 persistent_context 保持登录态
    2. 不断轮询任务池，逐条执行
    """
    from playwright.async_api import async_playwright

    os.makedirs(BROWSER_SESSION_DIR, exist_ok=True)
    print(f"[Execution] Browser session dir: {BROWSER_SESSION_DIR}")

    async with async_playwright() as pw:
        # ---- 关键：持久化浏览器上下文 ----
        context = await pw.chromium.launch_persistent_context(
            user_data_dir=BROWSER_SESSION_DIR,
            headless=True,
            viewport={"width": 1280, "height": 720},
            locale="zh-CN",
        )
        page = context.pages[0] if context.pages else await context.new_page()
        print("[Execution] Browser ready, entering main loop...")

        while True:
            # 修复：同时检查 idle 和 error 状态，error 后自动恢复继续消费任务
            if state.task_queue and state.status in ("idle", "error"):
                task = state.pop_task()
                state.set_current(task)
                state.set_status("running")
                print(f"[Execution] ▶ Start task: {task}")

                try:
                    # ---------- 任务执行占位逻辑 ----------
                    # 实际部署时替换为多模态截图 + LLM 决策循环
                    await page.goto("https://www.bing.com", timeout=15000)
                    await asyncio.sleep(3)

                    # 截图存入 state，供前端 Viewer 展示
                    screenshot_bytes = await page.screenshot()
                    import base64
                    state.last_screenshot_b64 = base64.b64encode(screenshot_bytes).decode()

                    # 标记完成
                    state.set_status("idle")
                    state.set_current(None)
                    state.add_history(f"✅ 任务完成: {task}")
                    print(f"[Execution] ✅ Done: {task}")

                except Exception as e:
                    state.set_status("idle")  # 修复：出错后恢复为 idle，继续消费后续任务
                    state.set_current(None)
                    state.add_history(f"❌ 任务出错: {task} | {str(e)}")
                    print(f"[Execution] ❌ Error: {e}")

            await asyncio.sleep(1)
