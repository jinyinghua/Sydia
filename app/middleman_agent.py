"""
Sydia - 中间人脑 (Middleman Agent / Ask AI)
对应手稿图2: 一个完整的支持 MCP 的 LLM 聊天模块
它是人类与系统之间的桥梁, 可以:
  - 回答关于系统状态的问题
  - 通过工具 add_task / edit_task 操控任务池
  - 通过工具 send_email 像真人一样发邮件
  - 流式输出 (stream)
"""
import json
import os
from openai import AsyncOpenAI

from app.state import agent_state
from app.email_service import send_email

client = AsyncOpenAI(
    api_key=os.getenv("OPENAI_API_KEY", ""),
    base_url=os.getenv("OPENAI_BASE_URL", None),
)
MODEL = os.getenv("LLM_MODEL", "gpt-4o")

# ── 中间人专属工具定义 (与执行脑的工具不同) ──

MIDDLEMAN_TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "add_task",
            "description": "向任务池中添加一个新任务, 当用户想让 Agent 做某件事时调用",
            "parameters": {
                "type": "object",
                "properties": {
                    "title": {"type": "string", "description": "任务标题 (简短)"},
                    "detail": {"type": "string", "description": "任务详细描述"},
                },
                "required": ["title", "detail"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "edit_task",
            "description": "修改任务池中已有任务的内容",
            "parameters": {
                "type": "object",
                "properties": {
                    "task_id": {"type": "string", "description": "要修改的任务 ID"},
                    "title": {"type": "string", "description": "新的任务标题"},
                    "detail": {"type": "string", "description": "新的任务详情"},
                },
                "required": ["task_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "delete_task",
            "description": "从任务池中删除一个任务",
            "parameters": {
                "type": "object",
                "properties": {
                    "task_id": {"type": "string", "description": "要删除的任务 ID"},
                },
                "required": ["task_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "send_email",
            "description": "像真人一样发送电子邮件 (回复/汇报/问候等)",
            "parameters": {
                "type": "object",
                "properties": {
                    "to": {"type": "string", "description": "收件人邮箱"},
                    "subject": {"type": "string", "description": "邮件主题"},
                    "content": {"type": "string", "description": "邮件正文"},
                },
                "required": ["to", "subject", "content"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_system_status",
            "description": "获取当前系统的实时状态 (执行脑状态/任务池/最近日志)",
            "parameters": {"type": "object", "properties": {}},
        },
    },
]

# ── 中间人工具执行器 ──

async def run_middleman_tool(name: str, args: dict) -> str:
    if name == "add_task":
        task = await agent_state.add_task(args["title"], args.get("detail", ""))
        return f"✅ 任务已添加 (ID: {task.id}): {task.title}"

    elif name == "edit_task":
        task = await agent_state.edit_task(
            args["task_id"],
            args.get("title", ""),
            args.get("detail", ""),
        )
        if task:
            return f"✅ 任务 {task.id} 已修改: {task.title}"
        return "❌ 未找到该任务 ID"

    elif name == "delete_task":
        ok = await agent_state.delete_task(args["task_id"])
        return "✅ 任务已删除" if ok else "❌ 未找到该任务 ID"

    elif name == "send_email":
        success = await send_email(args["to"], args["subject"], args["content"])
        return "✅ 邮件已发送" if success else "❌ 邮件发送失败 (请检查邮箱配置)"

    elif name == "get_system_status":
        pool = agent_state.get_pool_summary()
        logs = agent_state.workflow_logs[-10:]
        return json.dumps({
            "execution_status": agent_state.execution_status,
            "current_url": agent_state.current_url,
            "task_pool": pool,
            "recent_logs": [l["msg"] for l in logs],
        }, ensure_ascii=False, indent=2)

    return f"未知工具: {name}"


# ── 构建 System Prompt ──

def build_system_prompt() -> str:
    pool = agent_state.get_pool_summary()
    return f"""你是 Sydia 的中间人助手。你是用户与自动化系统之间的桥梁。

## 你的身份
- 你负责接待用户, 理解他们的需求
- 你可以通过工具向任务池添加/修改/删除任务
- 你可以通过工具发送电子邮件
- 你可以查询系统状态

## 当前系统状态
- 执行脑状态: {agent_state.execution_status}
- 当前操作 URL: {agent_state.current_url or '无'}
- 任务池: {json.dumps(pool, ensure_ascii=False) if pool else '空'}

## 重要规则
- 当用户想让系统做某件事时, 使用 add_task 工具添加任务
- 当用户想修改或取消任务时, 使用 edit_task 或 delete_task
- 用户如果只是聊天/问问题, 直接回复即可, 不需要调用工具
- 回复保持简洁友好, 使用中文
"""


# ── 对外接口: 流式聊天 ──

async def chat_stream(user_message: str):
    """
    流式处理用户消息, yield 每个文本片段
    如果 LLM 调用了工具, 会先执行工具再继续生成
    """
    # 维护对话历史
    agent_state.chat_history.append({"role": "user", "content": user_message})

    # 构建消息
    messages = [{"role": "system", "content": build_system_prompt()}]
    # 只保留最近 20 轮
    messages.extend(agent_state.chat_history[-40:])

    # 第一次调用: 可能触发工具
    response = await client.chat.completions.create(
        model=MODEL,
        messages=messages,
        tools=MIDDLEMAN_TOOLS,
        temperature=0.7,
        max_tokens=2048,
        stream=False,  # 工具判断阶段先不流式
    )

    msg = response.choices[0].message

    # 如果触发了工具调用
    if msg.tool_calls:
        # 执行所有工具
        tool_results = []
        tool_messages = []
        for tc in msg.tool_calls:
            fn_name = tc.function.name
            try:
                fn_args = json.loads(tc.function.arguments)
            except json.JSONDecodeError:
                fn_args = {}

            result = await run_middleman_tool(fn_name, fn_args)
            tool_results.append(f"[{fn_name}]: {result}")

            tool_messages.append({
                "role": "tool",
                "tool_call_id": tc.id,
                "content": result,
            })

        # 把工具结果反馈给 LLM, 让它生成最终回复 (流式)
        follow_messages = messages + [
            {
                "role": "assistant",
                "content": msg.content or "",
                "tool_calls": [
                    {
                        "id": tc.id,
                        "type": "function",
                        "function": {"name": tc.function.name, "arguments": tc.function.arguments},
                    }
                    for tc in msg.tool_calls
                ],
            },
            *tool_messages,
        ]

        stream = await client.chat.completions.create(
            model=MODEL,
            messages=follow_messages,
            temperature=0.7,
            max_tokens=2048,
            stream=True,
        )

        full_reply = ""
        async for chunk in stream:
            delta = chunk.choices[0].delta.content
            if delta:
                full_reply += delta
                yield delta

        agent_state.chat_history.append({"role": "assistant", "content": full_reply})

    else:
        # 没有工具调用, 直接流式输出
        stream = await client.chat.completions.create(
            model=MODEL,
            messages=messages,
            temperature=0.7,
            max_tokens=2048,
            stream=True,
        )

        full_reply = ""
        async for chunk in stream:
            delta = chunk.choices[0].delta.content
            if delta:
                full_reply += delta
                yield delta

        agent_state.chat_history.append({"role": "assistant", "content": full_reply})


# ── 对外接口: 处理收到的邮件 ──

async def process_incoming_email(subject: str, body: str, sender: str):
    """将收到的邮件当作用户消息, 交给中间人处理"""
    prompt = f"你收到了一封来自 {sender} 的邮件。\n主题: {subject}\n内容:\n{body}\n\n请理解邮件意图并执行相应操作 (如添加任务等), 然后以邮件回复的口吻生成回复内容。"

    full_reply = ""
    async for chunk in chat_stream(prompt):
        full_reply += chunk

    # 自动回复邮件
    if full_reply and sender:
        await send_email(sender, f"Re: {subject}", full_reply)

    return full_reply
