from pydantic import BaseModel, Field
from typing import List, Optional

class State(BaseModel):
    status: str = "idle"  # idle, running, error, waiting_user
    task_queue: List[str] = Field(default_factory=list)
    current_task: Optional[str] = None
    last_screenshot_b64: Optional[str] = None
    history: List[str] = Field(default_factory=list)

# 全局单例状态
state = State()
