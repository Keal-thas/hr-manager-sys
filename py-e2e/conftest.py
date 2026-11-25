import os
import time
from urllib.request import urlopen

import pytest


@pytest.fixture(scope="session")
def app_url():
    return os.environ.get("E2E_BASE_URL", "http://127.0.0.1:18080").rstrip("/")


@pytest.fixture(scope="session")
def browser_context_args(browser_context_args):
    return {
        **browser_context_args,
        "viewport": {"width": 1440, "height": 900},
        "record_video_size": {"width": 1440, "height": 900},
    }


@pytest.fixture(scope="session", autouse=True)
def require_backend(app_url):
    deadline = time.time() + 300
    last_error = None
    while time.time() < deadline:
        try:
            with urlopen(f"{app_url}/api/health", timeout=3) as response:
                if response.status == 200:
                    return
        except OSError as error:
            last_error = error
        time.sleep(2)
    pytest.exit(
        f"backend not ready: {app_url}/api/health ({last_error})\n"
        "start it first: bash scripts/e2e-server.sh",
        returncode=1,
    )
