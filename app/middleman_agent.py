from fastapi import APIRouter
from pydantic import BaseModel
from app.state import state
from app.llm import llm_call_with_tools
import json

middleman_router = APIRouter()


class ChatRequest(BaseModel):
    message: str
    file_content: str = None


MIDDLEMAN_TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "add_task_to_pool",
            "description": "向任务池(Task Pool)中添加一个新任务",
            "parameters": {
                "type": "object",
                "properties": {
                    "task": {"type": "string", "description": "任务描述"}
                },
                "required": ["task"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "edit_current_task",
            "description": "修改当前正在执行的任务",
            "parameters": {
                "type": "object",
                "properties": {
                    "new_task": {"type": "string", "description": "新的任务描述"}
                },
                "required": ["new_task"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "send_email",
            "description": "给人类发送邮件通知",
            "parameters": {
                "type": "object",
                "properties": {
                    "subject": {"type": "string", "description": "邮件主题"},
                    "body": {"type": "string", "description": "邮件正文"},
                },
                "required": ["subject", "body"],
            },
        },
    },
]


@middleman_router.post("/chat")
async def ask_ai_middleman(req: ChatRequest):
    """人类与中间人对话，中间人通过工具管理任务池"""
    context = (
        f"当前任务池: {state.task_queue}\n"
        f"执行脑状态: {state.status}\n"
        f"当前任务: {state.current_task}"
    )

    # 构建包含历史的 messages 列表
    messages = build_messages_with_history(context, req.message, req.file_content)

    response_msg, tool_calls = await llm_call_with_tools(messages, tools=MIDDLEMAN_TOOLS)

    # ---- 工具调用分发（支持多工具） ----
    results = []
    if tool_calls:
        for tool in tool_calls:
            # 修复：httpx 返回的是 dict，使用 [] 访问
            name = tool["function"]["name"]
            args = json.loads(tool["function"]["arguments"])

            if name == "add_task_to_pool":
                state.add_task(args["task"])
                state.add_history(f"AI: 已添加任务 → {args['task']}")
                results.append(f"已将任务加入队列: {args['task']}")

            elif name == "edit_current_task":
                state.set_current(args["new_task"])
                state.add_history(f"AI: 已修改当前任务 → {args['new_task']}")
                results.append(f"已修改当前任务为: {args['new_task']}")

            elif name == "send_email":
                state.add_history(f"AI: 已发送邮件 → {args['subject']}")
                results.append(f"邮件已发送: {args['subject']}")

        combined_reply = "\n".join(results)
        return {"reply": combined_reply, "raw": response_msg}

    # ---- 普通对话 ----
    state.add_history(f"User: {req.message}")
    state.add_history(f"AI: {response_msg}")
    return {"reply": response_msg, "raw": response_msg}


def build_messages_with_history(context: str, user_message: str, file_content: str = None):
    """构建包含对话历史的 messages 列表，支持多轮对话"""
    system_prompt = (
        "你是中间人(Middleman)。职责：与人类沟通，使用工具管理任务池或发邮件。\n"
        f"当前系统背景:\n{context}"
    )

    messages = [{"role": "system", "content": system_prompt}]

    # 从 state.history 中提取最近的对话记录作为上下文
    recent_history = state.history[-20:]  # 最近 20 条
    for entry in recent_history:
        if entry.startswith("User: "):
            messages.append({"role": "user", "content": entry[6:]})
        elif entry.startswith("AI: "):
            messages.append({"role": "assistant", "content": entry[4:]})

    # 当前用户消息
    content = user_message
    if file_content:
        content += f"\n\n附带文件内容:\n{file_content}"
    messages.append({"role": "user", "content": content})

    return messages
