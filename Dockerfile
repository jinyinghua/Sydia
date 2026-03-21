# 使用包含浏览器内核的微软官方镜像
FROM mcr.microsoft.com/playwright/python:v1.42.0-jammy

WORKDIR /app

# 1. 拷贝并安装依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 2. 拷贝代码，包含 app/static 前端文件
COPY app/ ./app/

# 环境变量设置
ENV PYTHONUNBUFFERED=1
ENV PORT=8080

# 暴露端口，供 Zeabur / Cloud Run 映射
EXPOSE 8080

# 启动命令：使用 uvicorn 运行 main 里的 FastAPI
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port $PORT"]
