# Sydia

一个长驻云端的 **双脑架构** AI Agent，通过 Web 界面与邮件双向受控，自动操作浏览器完成任务。

## 架构

```
┌─────────────────────────────────────────────────┐
│                   Web UI (/)                    │
│  ┌──────────┐  ┌──────────┐  ┌───────────────┐ │
│  │ Ask AI   │  │  Viewer  │  │  Task Pool    │ │
│  │ (Chat)   │  │(截图预览) │  │ (任务队列)    │ │
│  └────┬─────┘  └──────────┘  └───────────────┘ │
│       │              ▲               ▲          │
└───────┼──────────────┼───────────────┼──────────┘
        │              │               │
   /v1/chat       /v1/state       /v1/state
        │              │               │
┌───────▼──────────────┴───────────────┴──────────┐
│                  FastAPI (/v1)                   │
│                                                 │
│  ┌─────────────────┐    ┌─────────────────────┐ │
│  │  中间人脑        │    │  执行脑              │ │
│  │  Middleman LLM  │───▶│  Execution Agent    │ │
│  │                 │    │                     │ │
│  │  · 对话理解      │    │  · Playwright 操控   │ │
│  │  · 任务调度      │    │  · 截图 + 多模态     │ │
│  │  · 邮件通知      │    │  · 自动循环执行      │ │
│  └─────────────────┘    └─────────────────────┘ │
│                                                 │
│              state.py (共享状态)                  │
│              /app/data/state.json                │
└─────────────────────────────────────────────────┘
```

**中间人脑**：负责与人类沟通，通过 MCP Tool Calling 动态管理任务池、发送邮件。

**执行脑**：后台静默运行 Playwright，逐条消费任务队列，通过截图 + LLM 实现多模态自动操作。

## 项目结构

```
sydia/
├── app/
│   ├── static/                # Web 前端 (M3 深色主题)
│   │   ├── index.html
│   │   ├── style.css
│   │   └── app.js
│   ├── main.py                # FastAPI 入口，路由划分
│   ├── middleman_agent.py     # 中间人脑 (Chat + Tool Calling)
│   ├── execution_agent.py     # 执行脑 (Playwright 循环)
│   ├── llm.py                 # LLM 调用封装
│   └── state.py               # 全局状态 (JSON 持久化)
├── requirements.txt
├── Dockerfile
└── README.md
```

## 环境变量

| 变量 | 必填 | 说明 | 默认值 |
|------|------|------|--------|
| `OPENAI_API_KEY` | ✅ | OpenAI 或兼容接口的 API Key | — |
| `OPENAI_API_BASE` | — | 自定义 API 地址 (中转) | `https://api.openai.com/v1` |
| `OPENAI_MODEL` | — | 模型名称 | `gpt-4o` |
| `WEB_API_KEY` | — | 设置后所有 `/v1` 接口需携带 `X-API-Key` Header | — |
| `DATA_DIR` | — | 持久化数据目录 | `/app/data` |
| `PORT` | — | 服务监听端口 | `8080` |

## 本地运行

```bash
cd sydia

# 构建镜像
docker build -t sydia .

# 启动容器
docker run -d -p 8080:8080 \
  -v $(pwd)/data:/app/data \
  -e OPENAI_API_KEY="sk-xxx" \
  --name sydia sydia

# 访问
# Web UI  →  http://localhost:8080
# API 文档 →  http://localhost:8080/docs
```

## 云端部署 (Zeabur / ClawCloud / Railway)

1. 将代码推送至 GitHub 仓库
2. 在云平台创建服务，关联该仓库（平台自动识别 Dockerfile）
3. 在平台 **Variables** 面板添加环境变量（至少 `OPENAI_API_KEY`）
4. 在平台 **Storage / Volumes** 面板创建持久卷，挂载路径填 `/app/data`
5. 部署完成后通过平台分配的域名访问

> **⚠️ 持久卷 `/app/data` 的作用：**
> - `state.json`：保存任务队列和对话历史，容器重启后自动恢复
> - `browser_session/`：保存 Playwright 浏览器的 Cookie 和 Session，登录过一次后重启无需重新登录

## API

所有接口前缀为 `/v1`，若设置了 `WEB_API_KEY` 则需在 Header 中传入 `X-API-Key`。

### `GET /v1/state`

获取当前系统状态。

```json
{
  "status": "idle",
  "task_queue": ["打开邮箱检查新邮件"],
  "current_task": null,
  "screenshot": null
}
```

### `POST /v1/chat`

与中间人对话。中间人可能直接回复，也可能调用工具操作任务池。

```json
// Request
{ "message": "帮我查一下今天的邮件" }

// Response
{ "reply": "已将任务加入队列: 打开邮箱检查今日邮件", "raw": "..." }
```

## 技术栈

- **后端**：FastAPI + asyncio + Playwright
- **前端**：原生 HTML/CSS/JS（Material Design 3 深色主题）
- **LLM**：GPT-4o（支持自定义中转地址）
- **容器**：基于 `mcr.microsoft.com/playwright/python` 官方镜像
- **部署**：单 Dockerfile，云原生 PaaS 友好

## License

MIT
