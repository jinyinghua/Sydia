import asyncio
from app.state import state
import base64

async def run_playwright_loop():
    """
    执行脑核心循环：监控任务池并执行任务
    """
    print("Execution Agent started...")
    while True:
        if state.task_queue and state.status == "idle":
            # 获取新任务
            task = state.task_queue.pop(0)
            state.current_task = task
            state.status = "running"
            print(f"Executing task: {task}")

            try:
                # 这里模拟 Playwright 操作
                # 在实际实现中，这里会调用 Playwright 进行截图、分析页面、点击等
                await asyncio.sleep(5) # 模拟耗时操作
                
                # 模拟任务完成
                state.status = "idle"
                state.current_task = None
                print(f"Task completed: {task}")
            except Exception as e:
                print(f"Error executing task: {e}")
                state.status = "error"
        
        await asyncio.sleep(1) # 轮询间隔
