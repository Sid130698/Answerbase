FROM python:3.12-slim
WORKDIR /app
COPY models/reranker/requirements.txt .
RUN pip install --no-cache-dir torch==2.9.1 --index-url https://download.pytorch.org/whl/cpu && pip install --no-cache-dir -r requirements.txt
RUN useradd --create-home --uid 10001 model
RUN mkdir -p /home/model/.cache/huggingface && chown -R model:model /home/model/.cache
USER model
COPY models/reranker/server.py .
ENV BIND_ADDRESS=0.0.0.0 PORT=8092 HF_HOME=/home/model/.cache/huggingface
CMD ["python", "server.py"]
