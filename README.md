# Sydia

一个 24/7 运行的 "双脑" AI Agent 系统，具备基于 RAG 的长期记忆能力。

## 🧠 核心架构

- **执行脑 (Execution Agent)**: 后台常驻循环，通过 Playwright 浏览器执行任务。每次决策前会自动检索相关记忆。
- **中间人脑 (Middleman Agent)**: 对外窗口，通过 API 或邮件与人类沟通。支持 MCP 工具调用，可手动或自动存储重要记忆。
- **长期记忆 (Memory / RAG)**: 采用 **Gemini Embedding** + **Pinecone**。任务的开始、过程、结论会自动向量化并持久化，实现跨会话的经验积累。

## 🔍 RAG 工作流

1. **感官输入**: 接收用户指令或观察浏览器截图。
2. **检索 (Retrieve)**: 调用 Gemini `text-embedding-004` 将当前上下文转为 768 维向量，在 Pinecone 中进行余弦相似度搜索。
3. **增强 (Augment)**: 将搜到的历史经验（如：用户偏好、特定网站的操作技巧、上次失败的原因）注入 System Prompt。
4. **生成 (Generate)**: LLM 根据增强后的上下文做出最优决策。
5. **存储 (Store)**: 任务结束后的经验教训自动写回 Pinecone，形成闭环。

## ⚙️ 环境变量配置

请在部署环境（如 Zeabur, Docker, 或 .env）中配置以下变量：

| 类别 | 变量名 | 必填 | 说明 |
| :--- | :--- | :---: | :--- |
| **基础** | `OPENAI_API_KEY` | ✅ | 用于两个 LLM 大脑决策 |
| | `LLM_MODEL` | ❌ | 默认 `gpt-4o` |
| **记忆** | `GEMINI_API_KEY` | ✅ | 用于生成 768 维 Embedding |
| | `PINECONE_API_KEY` | ✅ | Pinecone 向量库密钥 |
| | `PINECONE_INDEX_HOST` | ✅ | Pinecone Index 的 Host 地址 |
| **邮件** | `EMAIL_ACCOUNT` | ❌ | Agent 发信账号 (如 QQ/163) |
| | `EMAIL_PASSWORD` | ❌ | 邮箱授权码 |
| | `TRUSTED_SENDER` | ❌ | 信任的控制者邮箱 |

## 🛠️ Pinecone 设置建议

- **Dimensions**: `768`
- **Metric**: `cosine`

## 🚀 快速启动 (Docker)

```bash
docker build -t sydia .

docker run -d --name sydia -p 8080:8080 \
  -e OPENAI_API_KEY="sk-xxx" \
  -e GEMINI_API_KEY="AIza-xxx" \
  -e PINECONE_API_KEY="pc-xxx" \
  -e PINECONE_INDEX_HOST="https://your-index.pinecone.io" \
  sydia
```
访问 `http://localhost:8080` 进入控制台。
