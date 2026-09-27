FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY runner.py .

ENTRYPOINT ["python", "runner.py"]
CMD ["--help"]
