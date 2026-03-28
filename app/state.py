import json
import os
from pydantic import BaseModel, Field
from typing import List, Optional

DATA_DIR = os.getenv("DATA_DIR", "/app/data")
STATE_FILE = os.path.join(DATA_DIR, "state.json")


class State(BaseModel):
    status: str = "idle"  # idle, running, error, waiting_user
    task_queue: List[str] = Field(default_factory=list)
    current_task: Optional[str] = None
    last_screenshot_b64: Optional[str] = None
    history: List[str] = Field(default_factory=list)

    def save(self):
        """将状态持久化到磁盘 JSON 文件"""
        os.makedirs(DATA_DIR, exist_ok=True)
        # screenshot 体积大，不写入磁盘，仅保留元数据
        dump = self.model_dump(exclude={"last_screenshot_b64"})
        with open(STATE_FILE, "w", encoding="utf-8") as f:
            json.dump(dump, f, ensure_ascii=False, indent=2)

    @classmethod
    def load(cls) -> "State":
        """从磁盘恢复状态，若文件不存在则返回全新状态"""
        if os.path.exists(STATE_FILE):
            try:
                with open(STATE_FILE, "r", encoding="utf-8") as f:
                    data = json.load(f)
                return cls(**data)
            except Exception:
                return cls()
        return cls()

    # ---------- 便捷写入方法（自动触发持久化） ----------

    def add_task(self, task: str):
        self.task_queue.append(task)
        self.save()

    def pop_task(self) -> Optional[str]:
        if self.task_queue:
            task = self.task_queue.pop(0)
            self.save()
            return task
        return None

    def set_current(self, task: Optional[str]):
        self.current_task = task
        self.save()

    def set_status(self, s: str):
        self.status = s
        self.save()

    def add_history(self, msg: str):
        self.history.append(msg)
        # 只保留最近 200 条，防止文件膨胀
        if len(self.history) > 200:
            self.history = self.history[-200:]
        self.save()


# 全局单例：启动时自动从磁盘恢复
state = State.load()
