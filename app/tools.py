"""
Sydia - 工具注册表 (MCP / Tool Registry)
支持动态注册新能力, 为 LLM 提供 function calling schema
"""


class ToolRegistry:
    """所有 Agent 可使用的工具在此注册"""

    def __init__(self):
        self._tools: dict[str, dict] = {}
        self._register_builtins()

    def _register_builtins(self):
        """注册内置工具"""

        # ── 浏览器操作类 ──
        self.register(
            name="navigate",
            description="导航到指定的 URL 地址",
            parameters={
                "type": "object",
                "properties": {
                    "url": {"type": "string", "description": "要访问的完整 URL"}
                },
                "required": ["url"],
            },
        )
        self.register(
            name="click",
            description="点击页面上的元素 (通过可见文字定位)",
            parameters={
                "type": "object",
                "properties": {
                    "target_text": {"type": "string", "description": "元素上可见的文字内容"}
                },
                "required": ["target_text"],
            },
        )
        self.register(
            name="type_text",
            description="在输入框中输入文字 (先定位再输入)",
            parameters={
                "type": "object",
                "properties": {
                    "target_text": {"type": "string", "description": "输入框附近的可见文字或 placeholder"},
                    "text": {"type": "string", "description": "要输入的内容"},
                },
                "required": ["target_text", "text"],
            },
        )
        self.register(
            name="scroll",
            description="滚动页面",
            parameters={
                "type": "object",
                "properties": {
                    "direction": {"type": "string", "enum": ["up", "down"], "description": "滚动方向"}
                },
                "required": ["direction"],
            },
        )
        self.register(
            name="wait",
            description="等待一段时间让页面加载完成",
            parameters={
                "type": "object",
                "properties": {
                    "seconds": {"type": "number", "description": "等待秒数 (1-10)"}
                },
                "required": ["seconds"],
            },
        )
        self.register(
            name="screenshot",
            description="对当前页面截图用于观察 (不做任何操作)",
            parameters={"type": "object", "properties": {}},
        )

        # ── 任务控制类 ──
        self.register(
            name="task_done",
            description="当前任务已完成, 提供执行结果的总结",
            parameters={
                "type": "object",
                "properties": {
                    "summary": {"type": "string", "description": "任务完成的结果总结"}
                },
                "required": ["summary"],
            },
        )
        self.register(
            name="task_failed",
            description="当前任务失败 (遇到验证码/登录墙等无法绕过的障碍)",
            parameters={
                "type": "object",
                "properties": {
                    "reason": {"type": "string", "description": "失败原因"}
                },
                "required": ["reason"],
            },
        )

        # ── 邮件通信类 ──
        self.register(
            name="send_email",
            description="像真人一样发送电子邮件 (汇报工作/求助/回复他人)",
            parameters={
                "type": "object",
                "properties": {
                    "to": {"type": "string", "description": "收件人邮箱地址"},
                    "subject": {"type": "string", "description": "邮件主题"},
                    "content": {"type": "string", "description": "邮件正文"},
                },
                "required": ["to", "subject", "content"],
            },
        )

    # ── 公开 API ──

    def register(self, name: str, description: str, parameters: dict):
        self._tools[name] = {
            "type": "function",
            "function": {
                "name": name,
                "description": description,
                "parameters": parameters,
            },
        }

    def unregister(self, name: str):
        self._tools.pop(name, None)

    def get_openai_tools(self) -> list[dict]:
        """返回 OpenAI function calling 格式的工具列表"""
        return list(self._tools.values())

    def get_tool_names(self) -> list[str]:
        return list(self._tools.keys())

    def get_description_text(self) -> str:
        """纯文本描述 (用于 system prompt)"""
        lines = []
        for name, spec in self._tools.items():
            func = spec["function"]
            props = func["parameters"].get("properties", {})
            params_str = ", ".join(
                f'{k}: {v.get("description", v.get("type", ""))}'
                for k, v in props.items()
            )
            lines.append(f"  • {name}({params_str}) — {func['description']}")
        return "\n".join(lines)


# 全局单例
tool_registry = ToolRegistry()
