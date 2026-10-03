FROM python:3.11-slim
RUN apt-get update && apt-get install -y --no-install-recommends \
    libreoffice-writer libreoffice-impress poppler-utils fonts-noto-cjk fonts-liberation \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
RUN useradd --uid 10001 --create-home preview && mkdir /cache && chown preview:preview /cache
USER preview
ENV PREVIEW_CACHE=/cache PYTHONUNBUFFERED=1
EXPOSE 8090
HEALTHCHECK --interval=10s --timeout=3s --retries=10 CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8090/health', timeout=2)"
CMD ["python", "server.py"]
