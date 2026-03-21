import asyncio
import os
from fastapi import FastAPI, APIRouter, Header, HTTPException, Depends
from fastapi.staticfiles import StaticFiles
from app.middleman_agent import middleman_router
from app.execution_agent import run_playwright_loop
from app.state import state

app = FastAPI(title="Sydia OpenClaw Core")

# --- 0. 身份校验逻辑 ---
WEB_API_KEY = os.getenv("WEB_API_KEY")

async def verify_api_key(x_api_key: str = Header(None)):
    # 如果设置了环境变量 WEB_API_KEY，则进行校验
    if WEB_API_KEY and x_api_key != WEB_API_KEY:
        raise HTTPException(status_code=401, detail="Invalid API Key")
    return x_api_key

# --- 1. 注册 /v1 API 路由 ---
# 所有的 /v1 接口都需要经过 API Key 校验
v1_router = APIRouter(prefix="/v1", dependencies=[Depends(verify_api_key)])

# 将中间人(Ask AI)模块的接口挂载到 /v1
v1_router.include_router(middleman_router)

# 基础状态接口也挂在 /v1
@v1_router.get("/state")
async def get_state():
    return {
        "status": state.status,
        "task_queue": state.task_queue,
        "current_task": state.current_task,
        "screenshot": state.last_screenshot_b64
    }

app.include_router(v1_router)

# --- 2. 注册 / 静态 Web 路由 ---
# 获取当前文件所在目录的 static 路径
static_dir = os.path.join(os.path.dirname(__file__), "static")

# 注意：静态文件挂载通常放在最后，或者确保它不会覆盖 API 路由
# FastAPI 会按顺序匹配路由
app.mount("/", StaticFiles(directory=static_dir, html=True), name="web")

# --- 3. 启动后台执行线程 ---
@app.on_event("startup")
async def startup_event():
    # 启动执行脑的异步循环
    asyncio.create_task(run_playwright_loop())
    print("Sydia background tasks initialized.")

if __name__ == "__main__":
    import uvicorn
    # 本地测试时使用的启动方式
    uvicorn.run(app, host="0.0.0.0", port=8080)
