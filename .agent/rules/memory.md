---
trigger: always_on
---

这份方案的核心逻辑是：**"像人类大脑一样"**

### 🧠 核心架构：Sydia 记忆管理系统

#### 1. 数据结构 
*   **Table: `memories`(本地)**
    *   `id` (INT, Primary Key): 唯一标识，方便 LLM 操作(ME)
    *   `body` (TEXT): 记忆的具体内容。(ME)
    *   `weight` (FLOAT): 初始权重（根据交互频率动态增减）(ME)
    *   `date` (DATETIME): 存入/最后更新时间(ME)
    *   `embedding` (VECTOR): (可选) 用于语义关联搜索(ME)
    *   `category` (TEXT): 由 embedding LLM 自动分类（如：学业、代码、人际、Agent开发）(ME)
    *   `is_pinned` (BOOLEAN): 重要事项固定不衰减(ME)

#### 2. 核心逻辑算法
*   **权重衰减**：
    *   逻辑：非重要信息随时间自动"淡化"。
    *   公式：`New_Weight = Old_Weight * 0.95` (每次打开应用执行，is_pinned=True 的记录不受影响)
*   **强化记忆**：
    *   逻辑：当用户在对话中重复提到某个关键词，相关 ID 的 `weight` 增加
    *   幅度：每次 +0.1，上限 1.0
    *   执行：由记忆模块自主检测，执行重新赋值操作(ME)
    
*   **记忆巩固(ME)**：
    *   **触发**：每天打开应用时自动运行(注重性能优化)
    *   **阈值**：由 LLM 自行判断哪些是高权重、哪些是低权重
    *   **操作**：调用 LLM 完善高权重记忆，缩减低权重的记忆
    *   **结果**：权重高内容详细完善，权重低内容简略
    
#### 3.交互层(ME)
*   **获取记忆**：每次发起对话时，查询所有还存在的记忆，作为 system prompt 发送给 LLM
*   **记忆数量限制**：用户可自行配置每次发送给 LLM 的记忆数量上限（默认 20 条，按权重排序）

---

### 🛠️ 实施步骤建议

1.  **第一步：SQLite 数据库定义**
    *   在本地设备上创建 `sydia_brain.db`。
    *   定义 `memories` 表。
    *   数据在本地存储，并进行加密保护隐私。
2.  **第二步：Python MCP Server 开发**
    *   使用 `fastapi` 或 `mcp-python-sdk`。
    *   编写 `add_memory` 和 `search_memory` 两个核心 Tool。
3.  **第三步：定时任务 (The "Cyber Sleep" Script)**
    *   写一个 Python 脚本，每天凌晨 3:00 运行。
    *   逻辑：提取 `weight` < 0.3 的碎碎念 -> 调用 LLM 总结 -> 更新数据库。
4.  **第四步：Sydia Agent 接入**
    *   让Sydia访问本地 Skills。
    *   **感知层**：Android 识屏。
    *   **行动层**：根据记忆权重决定是否提醒你。