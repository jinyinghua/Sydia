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
                "required": ["task"]
            }
        }
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
                "required": ["new_task"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "send_email",
            "description": "给人类发送邮件",
            "parameters": {
                "type": "object",
                "properties": {
                    "subject": {"type": "string", "description": "邮件主题"},
                    "body": {"type": "string", "description": "邮件正文"}
                },
                "required": ["subject", "body"]
            }
        }
    }
]

@middleman_router.post("/chat")
async def ask_ai_middleman(req: ChatRequest):
    """
    人类与中间人对话逻辑
    """
    context = f"当前任务池: {state.task_queue}\n执行脑状态: {state.status}\n当前任务: {state.current_task}"
    
    prompt = f"你是中间人(Middleman)。你的职责是与人类沟通，并使用工具管理任务池或收发邮件。\n背景: {context}\n人类说: {req.message}"
    if req.file_content:
        prompt += f"\n人类附带文件内容: {req.file_content}"

    # 调用大模型
    response_msg, tool_calls = await llm_call_with_tools(prompt, tools=MIDDLEMAN_TOOLS)

    if tool_calls:
        for tool in tool_calls:
            name = tool.function.name
            args = json.loads(tool.function.arguments)
            
            if name == "add_task_to_pool":
                state.task_queue.append(args['task'])
                state.history.append(f"AI: 已添加任务 - {args['task']}")
                return {"reply": f"已自动为您将任务加入队列: {args['task']}", "raw": response_msg}
                
            elif name == "edit_current_task":
                state.current_task = args['new_task']
                state.history.append(f"AI: 已修改当前任务为 - {args['new_task']}")
                return {"reply": f"已随时修改当前任务为: {args['new_task']}", "raw": response_msg}
                
            elif name == "send_email":
                # 这里可以集成之前的邮件发送逻辑
                state.history.append(f"AI: 已发送邮件 - {args['subject']}")
                return {"reply": f"邮件已发送: {args['subject']}", "raw": response_msg}

    state.history.append(f"User: {req.message}")
    state.history.append(f"AI: {response_msg}")
    return {"reply": response_msg, "raw": response_msg}
