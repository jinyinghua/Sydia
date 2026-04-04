"""
Sydia - FastAPI 主入口
"""
import asyncio
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.responses import StreamingResponse, JSONResponse
from pydantic import BaseModel

from app.state import agent_state
from app.middleman_agent import chat_stream
from app.execution_agent import execution_loop
from app.memory import get_stats as memory_stats, is_available as memory_available

@asynccontextmanager
async def lifespan(app: FastAPI):
    exec_task = asyncio.create_task(execution_loop())
    agent_state._log("startup", "Sydia 系统启动完成")
    yield
    exec_task.cancel()

app = FastAPI(title="Sydia", lifespan=lifespan)

class ChatRequest(BaseModel): message: str

@app.post("/v1/chat")
async def api_chat(req: ChatRequest):
    async def event_stream():
        async for chunk in chat_stream(req.message):
            yield f"data: {chunk}\n\n"
        yield "data: [DONE]\n\n"
    return StreamingResponse(event_stream(), media_type="text/event-stream")

@app.get("/v1/state")
async def api_state():
    return {
        "execution_status": agent_state.execution_status,
        "current_url": agent_state.current_url,
        "task_pool": agent_state.get_pool_summary(),
        "memory": await memory_stats(),
        "recent_logs": [{"event": l["event"], "msg": l["msg"]} for l in agent_state.workflow_logs[-30:]]
    }

@app.get("/v1/screenshot")
async def api_screenshot():
    b64 = agent_state.last_screenshot_b64
    return {"screenshot": b64} if b64 else {"screenshot": None}

STATIC_DIR = os.path.join(os.path.dirname(__file__), "static")
app.mount("/", StaticFiles(directory=STATIC_DIR, html=True), name="static")
