"""Sends results to the backend's internal endpoint."""

import httpx

from app.config import Settings


class BackendRejected(Exception):
    def __init__(self, status: int, body: dict | str):
        super().__init__(f"Backend returned {status}")
        self.status = status
        self.body = body


def post_results(settings: Settings, result: dict, transport: httpx.BaseTransport | None = None) -> dict:
    return _send(settings, "POST", "/api/v1/internal/ml/results", transport, json=result)


def get_thresholds(settings: Settings, transport: httpx.BaseTransport | None = None) -> dict[str, float | None]:
    """Decision values live in the backend's thresholds table, so each run reads them from there."""
    return _send(settings, "GET", "/api/v1/internal/ml/thresholds", transport)


def _send(settings: Settings, method: str, path: str, transport: httpx.BaseTransport | None, **kwargs) -> dict:
    with httpx.Client(base_url=settings.backend_url, timeout=30, transport=transport) as client:
        try:
            response = client.request(method, path, headers={"X-Internal-Api-Key": settings.internal_api_key},
                                      **kwargs)
        except httpx.TransportError as ex:
            raise BackendRejected(502, f"Could not reach the backend: {ex}") from ex
    if response.is_success:
        return response.json()
    try:
        body = response.json()
    except ValueError:
        body = response.text
    raise BackendRejected(response.status_code, body)
