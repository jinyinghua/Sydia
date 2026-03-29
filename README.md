# Sydia

一个 24/7 运行的"双脑"AI Agent 系统。

## 架构

- **执行脑 (Execution Agent)**: 后台常驻，通过 Playwright 浏览器自动执行 Web 任务
- **中间人脑 (Middleman Agent)**: 对外接待用户，支持 MCP 工具调用，可动态管理任务池
- **双向邮件通信**: 通过 SMTP/IMAP 像真人一样收发邮件

## 路由

| 路径 | 用途 |
|------|------|
| `/` | Web 控制台界面 |
| `/v1/chat` | 与中间人脑对话 (SSE 流式) |
| `/v1/state` | 系统状态 |
| `/v1/screenshot` | 浏览器截图 |
| `/v1/tasks` | 任务池 CRUD |
| `/v1/tools` | 工具注册/管理 (MCP) |
| `/v1/logs` | Workflow 日志 |

## 环境变量

| 变量 | 必填 | 说明 |
|------|------|------|
| `OPENAI_API_KEY` | ✅ | OpenAI API 密钥 |
| `OPENAI_BASE_URL` | ❌ | 自定义 API 地址 (兼容第三方) |
| `LLM_MODEL` | ❌ | 模型名称 (默认 `gpt-4o`) |
| `EMAIL_ACCOUNT` | ❌ | Agent 邮箱账号 (163/QQ) |
| `EMAIL_PASSWORD` | ❌ | 邮箱授权码 |
| `SMTP_HOST` | ❌ | SMTP 服务器 (默认 `smtp.qq.com`) |
| `SMTP_PORT` | ❌ | SMTP 端口 (默认 `465`) |
| `IMAP_HOST` | ❌ | IMAP 服务器 (默认 `imap.qq.com`) |
| `IMAP_PORT` | ❌ | IMAP 端口 (默认 `993`) |
| `TRUSTED_SENDER` | ❌ | 信任的发件人邮箱 |

## 构建与部署

### 本地 Docker 构建

```bash
# 构建镜像
docker build -t sydia .

# 运行 (替换为你的真实密钥)
docker run -d \
  --name sydia \
  -p 8080:8080 \
  -e OPENAI_API_KEY="sk-xxx" \
  -e EMAIL_ACCOUNT="your@qq.com" \
  -e EMAIL_PASSWORD="授权码" \
  -e SMTP_HOST="smtp.qq.com" \
  -e SMTP_PORT="465" \
  -e IMAP_HOST="imap.qq.com" \
  -e IMAP_PORT="993" \
  -e TRUSTED_SENDER="boss@example.com" \
  sydia

# 访问 http://localhost:8080
```

### 云端部署 (Zeabur / ClawCloud Run)

1. 将代码推送到 GitHub
2. 在平台上绑定仓库，平台会自动识别 Dockerfile
3. 在平台控制台中添加上述环境变量
4. 部署完成后通过分配的域名访问

### 邮箱配置参考

| 邮箱 | SMTP Host | SMTP Port | IMAP Host | IMAP Port |
|------|-----------|-----------|-----------|-----------|
| QQ邮箱 | smtp.qq.com | 465 | imap.qq.com | 993 |
| 163邮箱 | smtp.163.com | 465 | imap.163.com | 993 |
