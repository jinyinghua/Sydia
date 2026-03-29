"""
Sydia - FastAPI 主入口
路由规范:
  /           -> Web 前端界面
  /v1/...     -> API 接口
"""
import asyncio
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.staticfiles import StaticFiles
from fastapi.responses import HTMLResponse, StreamingResponse, JSONResponse
from pydantic import BaseModel

from app.state import agent_state
from app.middleman_agent import chat_stream, process_incoming_email
from app.execution_agent import execution_loop
from app.email_service import fetch_new_emails
from app.tools import tool_registry


# ── 后台任务 ──

async def email_polling_loop():
    """每 5 分钟检查信任邮件"""
    while True:
        try:
            emails = await fetch_new_emails()
            for em in emails:
                agent_state._log("email_in", f"收到邮件: {em['subject']} from {em['from']}")
                await process_incoming_email(em["subject"], em["body"], em["from"])
        except Exception as e:
            print(f"[EmailPoll] 错误: {e}")
        await asyncio.sleep(300)  # 5 分钟


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期: 启动后台任务"""
    # 启动执行脑循环
    exec_task = asyncio.create_task(execution_loop())
    # 启动邮件轮询
    email_task = asyncio.create_task(email_polling_loop())

    agent_state._log("startup", "Sydia 系统启动完成")
    yield

    exec_task.cancel()
    email_task.cancel()


app = FastAPI(title="Sydia", lifespan=lifespan)


# ═══════════════════════════════════════
#  /v1/... API 接口
# ═══════════════════════════════════════

class ChatRequest(BaseModel):
    message: str


class TaskRequest(BaseModel):
    title: str
    detail: str = ""


class TaskEditRequest(BaseModel):
    title: str = ""
    detail: str = ""


# ── 聊天 (流式) ──

@app.post("/v1/chat")
async def api_chat(req: ChatRequest):
    """与中间人脑对话 (SSE 流式)"""
    async def event_stream():
        async for chunk in chat_stream(req.message):
            # SSE 格式
            yield f"data: {chunk}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")


# ── 系统状态 ──

@app.get("/v1/state")
async def api_state():
    """获取系统全局状态"""
    return {
        "execution_status": agent_state.execution_status,
        "current_url": agent_state.current_url,
        "task_pool": agent_state.get_pool_summary(),
        "recent_logs": [
            {"event": l["event"], "msg": l["msg"]}
            for l in agent_state.workflow_logs[-30:]
        ],
    }


@app.get("/v1/screenshot")
async def api_screenshot():
    """获取最新浏览器截图"""
    b64 = agent_state.last_screenshot_b64
    if not b64:
        return {"screenshot": None}
    return {"screenshot": b64}


# ── 任务池 CRUD ──

@app.get("/v1/tasks")
async def api_list_tasks():
    return {"tasks": agent_state.get_pool_summary()}


@app.post("/v1/tasks")
async def api_add_task(req: TaskRequest):
    task = await agent_state.add_task(req.title, req.detail)
    return {"ok": True, "task": {"id": task.id, "title": task.title, "status": task.status}}


@app.put("/v1/tasks/{task_id}")
async def api_edit_task(task_id: str, req: TaskEditRequest):
    task = await agent_state.edit_task(task_id, req.title, req.detail)
    if task:
        return {"ok": True, "task": {"id": task.id, "title": task.title}}
    return JSONResponse({"ok": False, "error": "Task not found"}, status_code=404)


@app.delete("/v1/tasks/{task_id}")
async def api_delete_task(task_id: str):
    ok = await agent_state.delete_task(task_id)
    return {"ok": ok}


# ── 工具管理 (MCP) ──

@app.get("/v1/tools")
async def api_list_tools():
    return {"tools": tool_registry.get_tool_names()}


class ToolRegisterRequest(BaseModel):
    name: str
    description: str
    parameters: dict = {}


@app.post("/v1/tools")
async def api_register_tool(req: ToolRegisterRequest):
    tool_registry.register(req.name, req.description, req.parameters)
    return {"ok": True, "tools": tool_registry.get_tool_names()}


@app.delete("/v1/tools/{tool_name}")
async def api_unregister_tool(tool_name: str):
    tool_registry.unregister(tool_name)
    return {"ok": True}


# ── Workflow 日志 ──

@app.get("/v1/logs")
async def api_logs():
    return {"logs": agent_state.workflow_logs[-50:]}


# ═══════════════════════════════════════
#  /  Web 前端 (静态文件)
# ═══════════════════════════════════════

STATIC_DIR = os.path.join(os.path.dirname(__file__), "static")
app.mount("/", StaticFiles(directory=STATIC_DIR, html=True), name="static")
