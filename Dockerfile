# Sydia - Dockerfile
# 基于微软 Playwright 官方镜像 (含 Chromium + 所有系统依赖)
FROM mcr.microsoft.com/playwright/python:v1.49.1-noble

WORKDIR /sydia

# 安装 Python 依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制项目代码
COPY app/ ./app/

# 环境变量 (运行时通过云平台注入覆盖)
ENV PYTHONUNBUFFERED=1
ENV PORT=8080
ENV HEADLESS=true

# 暴露端口
EXPOSE 8080

# 启动命令
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
