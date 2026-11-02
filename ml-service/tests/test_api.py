import httpx
import pytest
from fastapi.testclient import TestClient

from app import backend_client, gee_source, main
from app.config import Settings, get_settings

KEY = "test-key"
SETTINGS = Settings(backend_url="http://backend.test", internal_api_key=KEY, gee_project_id="test-project")
HEADERS = {"X-Internal-Api-Key": KEY}
BOUNDARY = {"type": "Polygon", "coordinates": [[[30.1, -1.95], [30.102, -1.95], [30.102, -1.948], [30.1, -1.95]]]}
GEE_BODY = {"plotId": 1, "source": "gee", "boundary": BOUNDARY, "plantingDate": "2026-09-20", "seasonType": "A"}


THRESHOLDS = {
    "not_enough_data_window_days": 15, "not_enough_data_min_obs": 1, "watch_rain_pct": 60,
    "at_risk_consecutive_obs": 2, "at_risk_min_days": 10, "at_risk_anomaly": -1,
}


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(main.backend_client, "get_thresholds", lambda settings: dict(THRESHOLDS))
    main.app.dependency_overrides[get_settings] = lambda: SETTINGS
    yield TestClient(main.app)
    main.app.dependency_overrides.clear()


@pytest.fixture
def posted(monkeypatch):
    calls = []

    def fake_post(settings, result):
        calls.append(result)
        return {"runId": 1, "observations": 2, "weatherDays": 3, "baselineBins": 4}

    monkeypatch.setattr(main.backend_client, "post_results", fake_post)
    return calls


@pytest.fixture
def gee_down(monkeypatch):
    def refuse(project_id):
        raise gee_source.GeeNotAuthorised("no credentials")

    monkeypatch.setattr(gee_source, "initialise", refuse)


def test_health_reports_earth_engine_is_not_authorised(client, gee_down):
    body = client.get("/health").json()
    assert body["status"] == "ok"
    assert body["gee"] == {"projectId": "test-project", "authorised": False, "detail": "no credentials"}


def test_process_needs_the_api_key(client, posted):
    assert client.post("/process/plot", json={"plotId": 1, "source": "sample"}).status_code == 401
    wrong = client.post("/process/plot", json={"plotId": 1, "source": "sample"},
                        headers={"X-Internal-Api-Key": "wrong"})
    assert wrong.status_code == 401
    assert posted == []


def test_sample_run_is_posted_with_its_label(client, posted):
    response = client.post("/process/plot", json={"plotId": 9001, "source": "sample"}, headers=HEADERS)
    assert response.status_code == 200
    assert response.json()["dataOrigin"] == "SAMPLE_FILE"
    assert posted[0]["dataOrigin"] == "SAMPLE_FILE"
    assert posted[0]["plotId"] == 9001


def test_asking_for_real_data_never_falls_back_to_the_sample(client, posted, gee_down):
    response = client.post("/process/plot", json=GEE_BODY, headers=HEADERS)
    assert response.status_code == 503
    assert "no credentials" in response.json()["detail"]
    assert posted == []


def test_gee_request_needs_plot_details(client, posted):
    response = client.post("/process/plot", json={"plotId": 1, "source": "gee"}, headers=HEADERS)
    assert response.status_code == 422
    assert posted == []


def test_backend_refusal_is_passed_back(client, monkeypatch):
    def refuse(settings, result):
        raise backend_client.BackendRejected(409, {"code": "SAMPLE_ON_REAL_PLOT"})

    monkeypatch.setattr(main.backend_client, "post_results", refuse)
    response = client.post("/process/plot", json={"plotId": 1, "source": "sample"}, headers=HEADERS)
    assert response.status_code == 409
    assert response.json()["detail"]["code"] == "SAMPLE_ON_REAL_PLOT"


def test_run_stores_the_thresholds_it_used(client, posted):
    client.post("/process/plot", json={"plotId": 9001, "source": "sample"}, headers=HEADERS)
    assert posted[0]["params"]["thresholds"] == THRESHOLDS
    assert posted[0]["status"]["status"] in {"HEALTHY", "WATCH", "AT_RISK", "NOT_ENOUGH_DATA"}


def test_a_threshold_with_no_value_stops_the_run(client, posted, monkeypatch):
    monkeypatch.setattr(main.backend_client, "get_thresholds",
                        lambda settings: {**THRESHOLDS, "watch_rain_pct": None})
    response = client.post("/process/plot", json={"plotId": 9001, "source": "sample"}, headers=HEADERS)
    assert response.status_code == 400
    assert "watch_rain_pct" in response.json()["detail"]
    assert posted == []


def test_client_reads_thresholds_with_the_key():
    seen = {}

    def handler(request):
        seen["url"] = str(request.url)
        seen["key"] = request.headers["X-Internal-Api-Key"]
        return httpx.Response(200, json={"watch_rain_pct": 60.0})

    assert backend_client.get_thresholds(SETTINGS, httpx.MockTransport(handler)) == {"watch_rain_pct": 60.0}
    assert seen == {"url": "http://backend.test/api/v1/internal/ml/thresholds", "key": KEY}


def test_client_sends_the_key_to_the_internal_endpoint():
    seen = {}

    def handler(request):
        seen["url"] = str(request.url)
        seen["key"] = request.headers["X-Internal-Api-Key"]
        return httpx.Response(200, json={"runId": 5})

    saved = backend_client.post_results(SETTINGS, {"plotId": 1}, httpx.MockTransport(handler))
    assert saved == {"runId": 5}
    assert seen == {"url": "http://backend.test/api/v1/internal/ml/results", "key": KEY}


def test_client_reports_an_unreachable_backend():
    def handler(request):
        raise httpx.ConnectError("refused")

    with pytest.raises(backend_client.BackendRejected) as caught:
        backend_client.post_results(SETTINGS, {}, httpx.MockTransport(handler))
    assert caught.value.status == 502
