FROM mcr.microsoft.com/playwright/python:v1.65.2-noble

ENV PYTHONDONTWRITEBYTECODE=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /tests

COPY requirements.txt .
RUN pip install -r requirements.txt

COPY conftest.py pytest.ini support.py test_smoke.py test_walkthrough.py ./

ENTRYPOINT ["python", "-m", "pytest"]
CMD ["-q"]
