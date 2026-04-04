"""
Sydia - 中间人脑 (Middleman Agent)
"""
import json
import os
from openai import AsyncOpenAI

from app.state import agent_state
from app.email_service import send_email
from app.memory import memory_search_text, memory_save, is_available as memory_available

client = AsyncOpenAI(api_key=os.getenv("OPENAI_API_KEY", ""), base_url=os.getenv("OPENAI_BASE_URL", None))
MODEL = os.getenv("LLM_MODEL", "gpt-4o")

MIDDLEMAN_TOOLS = [
    {"type": "function", "function": {"name": "add_task", "description": "添加新任务", "parameters": {"type": "object", "properties": {"title": {"type": "string"}, "detail": {"type": "string"}}, "required": ["title", "detail"]}}},
    {"type": "function", "function": {"name": "save_memory", "description": "存入长期记忆", "parameters": {"type": "object", "properties": {"content": {"type": "string"}}, "required": ["content"]}}},
    {"type": "function", "function": {"name": "get_system_status", "description": "查询状态", "parameters": {"type": "object", "properties": {}}}}
]

async def run_middleman_tool(name: str, args: dict) -> str:
    if name == "add_task":
        task = await agent_state.add_task(args["title"], args["detail"])
        return f"✅ 已添加任务: {task.title}"
    elif name == "save_memory":
        await memory_save(args["content"], metadata={"source": "user_preference"})
        return f"✅ 已存入记忆: {args['content'][:50]}"
    elif name == "get_system_status":
        pool = agent_state.get_pool_summary()
        return json.dumps({"status": agent_state.execution_status, "pool": pool}, ensure_ascii=False)
    return f"未知工具: {name}"

async def chat_stream(user_message: str):
    memory_context = await memory_search_text(user_message)
    agent_state.chat_history.append({"role": "user", "content": user_message})
    messages = [{"role": "system", "content": f"你是 Sydia 中间人助手。长期记忆状态: {'已连接' if memory_available() else '未配置'}。\n\n相关记忆:\n{memory_context or '无相关历史记忆'}"}]
    messages.extend(agent_state.chat_history[-40:])
    response = await client.chat.completions.create(model=MODEL, messages=messages, tools=MIDDLEMAN_TOOLS, temperature=0.7, max_tokens=2048, stream=False)
    msg = response.choices[0].message
    if msg.tool_calls:
        tool_messages = []
        for tc in msg.tool_calls:
            result = await run_middleman_tool(tc.function.name, json.loads(tc.function.arguments))
            tool_messages.append({"role": "tool", "tool_call_id": tc.id, "content": result})
        stream = await client.chat.completions.create(model=MODEL, messages=messages + [{"role": "assistant", "content": msg.content or "", "tool_calls": [{"id": tc.id, "type": "function", "function": {"name": tc.function.name, "arguments": tc.function.arguments}} for tc in msg.tool_calls]}, *tool_messages], temperature=0.7, max_tokens=2048, stream=True)
        full_reply = ""
        async for chunk in stream:
            if delta := chunk.choices[0].delta.content:
                full_reply += delta; yield delta
        agent_state.chat_history.append({"role": "assistant", "content": full_reply})
    else:
        stream = await client.chat.completions.create(model=MODEL, messages=messages, temperature=0.7, max_tokens=2048, stream=True)
        full_reply = ""
        async for chunk in stream:
            if delta := chunk.choices[0].delta.content:
                full_reply += delta; yield delta
        agent_state.chat_history.append({"role": "assistant", "content": full_reply})
