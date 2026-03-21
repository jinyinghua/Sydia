import os
import json
import httpx
from typing import List, Dict, Any, Tuple, Optional

async def llm_call_with_tools(prompt: str, tools: List[Dict[str, Any]]) -> Tuple[str, Optional[List[Any]]]:
    """
    调用大模型接口（支持工具调用）。
    这里是一个占位实现，实际使用时请替换为 OpenAI 或其他提供商的 SDK 调用。
    """
    api_key = os.getenv("OPENAI_API_KEY")
    api_base = os.getenv("OPENAI_API_BASE", "https://api.openai.com/v1")
    model = os.getenv("OPENAI_MODEL", "gpt-4o")

    if not api_key:
        return "Error: OPENAI_API_KEY not set", None

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }

    payload = {
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
        "tools": tools,
        "tool_choice": "auto"
    }

    async with httpx.AsyncClient() as client:
        try:
            response = await client.post(
                f"{api_base}/chat/completions",
                headers=headers,
                json=payload,
                timeout=60.0
            )
            response.raise_for_status()
            data = response.json()
            
            message = data["choices"][0]["message"]
            content = message.get("content") or ""
            tool_calls = message.get("tool_calls")
            
            return content, tool_calls
        except Exception as e:
            return f"LLM Call Error: {str(e)}", None
