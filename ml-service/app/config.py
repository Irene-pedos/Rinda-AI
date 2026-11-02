"""Settings from the repo's .env file. Real environment variables take priority."""

import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

from dotenv import load_dotenv

REPO_ROOT = Path(__file__).resolve().parents[2]


@dataclass(frozen=True)
class Settings:
    backend_url: str
    internal_api_key: str
    gee_project_id: str


@lru_cache
def get_settings() -> Settings:
    load_dotenv(REPO_ROOT / ".env")
    key = os.environ.get("ML_INTERNAL_API_KEY", "")
    if not key:
        raise RuntimeError("ML_INTERNAL_API_KEY is not set. Run scripts\\new-env.ps1.")
    return Settings(
        backend_url=os.environ.get("BACKEND_URL", "http://localhost:8080").rstrip("/"),
        internal_api_key=key,
        gee_project_id=os.environ.get("GEE_PROJECT_ID", ""),
    )
