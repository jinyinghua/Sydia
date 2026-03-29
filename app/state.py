"""
Sydia - 全局状态池 (Task Pool + Agent State)
两个 LLM（中间人脑 & 执行脑）通过此模块共享数据
"""
import asyncio
import time
import uuid
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class Task:
    id: str = field(default_factory=lambda: str(uuid.uuid4())[:8])
    title: str = ""
    detail: str = ""
    status: str = "pending"  # pending / running / done / failed / waiting_human
    created_at: float = field(default_factory=time.time)
    result: str = ""


class AgentState:
    """全局单例状态, 被 middleman_agent 和 execution_agent 共同读写"""

    def __init__(self):
        # ── 任务池 ──
        self.task_pool: list[Task] = []
        self.task_lock = asyncio.Lock()

        # ── 执行脑状态 ──
        self.execution_status: str = "IDLE"
        self.current_task_id: Optional[str] = None
        self.current_url: str = ""
        self.last_screenshot_b64: str = ""  # 最新一帧截图 (base64 jpeg)

        # ── 对话历史 (中间人脑) ──
        self.chat_history: list[dict] = []

        # ── Workflow 日志 ──
        self.workflow_logs: list[dict] = []

    # ── 任务池操作 ──
    async def add_task(self, title: str, detail: str = "") -> Task:
        async with self.task_lock:
            t = Task(title=title, detail=detail or title)
            self.task_pool.append(t)
            self._log("task_added", f"新任务入池: {t.title}")
            return t

    async def edit_task(self, task_id: str, title: str = "", detail: str = "") -> Optional[Task]:
        async with self.task_lock:
            for t in self.task_pool:
                if t.id == task_id:
                    if title:
                        t.title = title
                    if detail:
                        t.detail = detail
                    self._log("task_edited", f"任务已修改: {t.id}")
                    return t
        return None

    async def delete_task(self, task_id: str) -> bool:
        async with self.task_lock:
            for i, t in enumerate(self.task_pool):
                if t.id == task_id:
                    self.task_pool.pop(i)
                    self._log("task_deleted", f"任务已删除: {task_id}")
                    return True
        return False

    async def pop_next_task(self) -> Optional[Task]:
        """取出下一个待执行任务"""
        async with self.task_lock:
            for t in self.task_pool:
                if t.status == "pending":
                    t.status = "running"
                    self.current_task_id = t.id
                    return t
        return None

    async def finish_task(self, task_id: str, result: str = "", failed: bool = False):
        async with self.task_lock:
            for t in self.task_pool:
                if t.id == task_id:
                    t.status = "failed" if failed else "done"
                    t.result = result
                    break
            self.current_task_id = None
            self.execution_status = "IDLE"

    def get_pool_summary(self) -> list[dict]:
        return [
            {"id": t.id, "title": t.title, "status": t.status, "result": t.result}
            for t in self.task_pool
        ]

    # ── 日志 ──
    def _log(self, event: str, msg: str):
        entry = {"time": time.time(), "event": event, "msg": msg}
        self.workflow_logs.append(entry)
        # 只保留最近 200 条
        if len(self.workflow_logs) > 200:
            self.workflow_logs = self.workflow_logs[-200:]
        print(f"[Sydia:{event}] {msg}")


# 全局单例
agent_state = AgentState()
