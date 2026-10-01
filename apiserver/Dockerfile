FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# 歌曲文档缓存目录（生成后落盘于此，挂卷持久化）。
ENV LYRICS_DATA_DIR=/app/data
VOLUME /app/data

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=4).status==200 else 1)"

# GLM key 用 `docker run -e GLM_API_KEY=...` 注入（代码里的默认值只是本地兜底）。
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
