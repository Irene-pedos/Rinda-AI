"""Sends results to the backend's internal endpoint."""

import httpx

from app.config import Settings


class BackendRejected(Exception):
    def __init__(self, status: int, body: dict | str):
        super().__init__(f"Backend returned {status}")
        self.status = status
        self.body = body


def post_results(settings: Settings, result: dict, transport: httpx.BaseTransport | None = None) -> dict:
    with httpx.Client(base_url=settings.backend_url, timeout=30, transport=transport) as client:
        try:
            response = client.post(
                "/api/v1/internal/ml/results",
                json=result,
                headers={"X-Internal-Api-Key": settings.internal_api_key},
            )
        except httpx.TransportError as ex:
            raise BackendRejected(502, f"Could not reach the backend: {ex}") from ex
    if response.is_success:
        return response.json()
    try:
        body = response.json()
    except ValueError:
        body = response.text
    raise BackendRejected(response.status_code, body)
