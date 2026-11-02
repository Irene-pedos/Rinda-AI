# Frontend API Needs (Contract Alignment)

This document tracks endpoints needed by the Android frontend that are not yet implemented in `backend/src` (returning 404). Per the ZERO-MISMATCH rule, the frontend will never fake endpoints or fabricate fields; this document serves as the formal interface request to the backend.

---

## 1. Plot Status & Crop Health

### `GET /api/v1/plots/{id}/status`
- **Why**: Displayed on Home tab summary, Plots list chip, Plot Detail, and Crop Stress Detail.
- **Role**: `FARMER` (own plots), `AGENT` (cooperative plots), `OFFICER`, `ADMIN`.
- **Expected Response (200 OK)**:
  ```json
  {
    "date": "2026-10-25",
    "status": "HEALTHY",
    "anomaly": -0.15,
    "rainPct": 85.5,
    "cause": null,
    "confidence": "HIGH",
    "explanation": "NDVI is within the normal band for 35 DAP.",
    "modelVersion": "stress-rules-v1"
  }
  ```
- **Enum values**:
  - `status`: `HEALTHY`, `WATCH`, `AT_RISK`, `NOT_ENOUGH_DATA`
  - `confidence`: `HIGH`, `MEDIUM`, `LOW`
  - `cause`: nullable; e.g. `WATER_DEFICIT`, `UNEXPLAINED` (never specific pests/diseases)

---

## 2. Harvest Forecast

### `GET /api/v1/plots/{id}/forecast`
- **Why**: Displayed on Home tab forecast tile, Plot Detail, and Harvest Forecast screen.
- **Role**: `FARMER` (own plots), `AGENT`, `OFFICER`, `ADMIN`.
- **Expected Response (200 OK)**:
  ```json
  {
    "date": "2026-10-25",
    "lowPct": 75.0,
    "highPct": 90.0,
    "confidence": "MEDIUM",
    "modelVersion": "harvest-range-v1"
  }
  ```
- **Note**: Null / empty before ~42 DAP. UI labels this "Prototype estimate".

---

## 3. Enrollment / Coverage

### `GET /api/v1/plots/{id}/enrollment`
- **Why**: Displayed on Plot Detail and Coverage screen.
- **Expected Response (200 OK)**:
  ```json
  {
    "status": "COVERED",
    "seasonCode": "2026A",
    "startDate": "2026-09-15",
    "endDate": "2027-02-15",
    "sumInsured": 250000.00,
    "premium": 15000.00,
    "isDemo": true
  }
  ```
- **Enum values**: `status`: `PENDING`, `COVERED`, `CANCELLED`

---

## 4. Notifications / Alerts

### `GET /api/v1/notifications`
- **Parameters**: `page` (default 0), `size` (default 20).
- **Expected Response (200 OK)**:
  ```json
  {
    "items": [
      {
        "id": 1,
        "type": "HEALTH",
        "title": "Crop Stress Detected",
        "body": "Plot Demo plot 1 is showing signs of water deficit.",
        "link": "/plots/9001",
        "isRead": false,
        "createdAt": "2026-10-25T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalItems": 1,
    "totalPages": 1
  }
  ```
- **Enum values**: `type`: `HEALTH` (navigates to Plot Detail), `CLAIM` (navigates to Claim Detail), `SYSTEM`.

### `POST /api/v1/notifications/{id}/read`
- **Expected Response**: `204 No Content` or `200 OK`.

---

## 5. Claims & Review Requests

### `GET /api/v1/claims`
- **Parameters**: `page`, `size`. Farmers see only their own claims.
- **Expected Response (200 OK)**: `PageResponse<ClaimSummaryResponse>`

### `GET /api/v1/claims/{id}`
- **Expected Response (200 OK)**:
  ```json
  {
    "id": 101,
    "plotId": 9001,
    "plotName": "Demo plot 1",
    "seasonId": 9001,
    "triggerDate": "2026-10-20",
    "status": "UNDER_REVIEW",
    "payoutRate": 0.40,
    "amount": 100000.00,
    "evidenceSnapshot": {
      "triggerReason": "AT_RISK for 12 days during flowering stage",
      "ndviAnomaly": -1.2,
      "rainDeficitPct": 45.0
    },
    "decision": {
      "decision": "APPROVED",
      "reason": "Satellite evidence and agent visit confirmed severe drought loss.",
      "decidedAt": "2026-10-22T14:30:00Z"
    },
    "modelVersion": "trigger-v1",
    "isDemo": true,
    "createdAt": "2026-10-20T08:00:00Z"
  }
  ```
- **Enum values**: `status`: `UNDER_REVIEW`, `FIELD_VISIT_REQUESTED`, `APPROVED`, `DECLINED`, `PAID`, `REVIEW_REQUESTED`, `FINAL`.

### `POST /api/v1/claims/{id}/review-request`
- **Expected Request (JSON)**:
  ```json
  {
    "message": "I noticed the drought affected the west slope more severely."
  }
  ```
- **Expected Response (200/201)**: `{ "id": 1, "claimId": 101, "status": "OPEN", "createdAt": "..." }`

---

## 6. Problem Reports (Evidence Submission)

### `POST /api/v1/problem-reports`
- **Content-Type**: `multipart/form-data`
- **Form parts**:
  - `plotId`: Long
  - `reason`: String (e.g. `WATER_DEFICIT`, `PEST_OR_DISEASE`, `EROSION`, `OTHER`)
  - `note`: String
  - `agentVisitRequested`: Boolean
  - `photo`: File (optional image upload)
- **Expected Response (201 Created)**: Problem report response with generated ID.

---

## 7. Payout Account (Simulated)

### `GET /api/v1/payout-account`
- **Expected Response (200 OK)**:
  ```json
  {
    "provider": "MTN_MOMO",
    "msisdn": "+250780000001",
    "verified": true,
    "nameMatch": true
  }
  ```
- (Returns 404 or empty object if not configured yet).

### `POST /api/v1/payout-account`
- **Expected Request (JSON)**:
  ```json
  {
    "provider": "MTN_MOMO",
    "msisdn": "0780000001"
  }
  ```
- Triggers simulated SMS OTP. Returns 202 Accepted.

### `POST /api/v1/payout-account/verify`
- **Expected Request (JSON)**:
  ```json
  {
    "code": "123456"
  }
  ```
- Returns 200 OK with verified account details.

---

## 8. Agent Operations

### `GET /api/v1/agent/farmers`
- **Expected Response (200 OK)**:
  ```json
  [
    {
      "farmerId": 9001,
      "fullName": "Demo Farmer One",
      "phone": "+250780000001",
      "plotsCount": 3,
      "pendingVerificationCount": 0,
      "atRiskCount": 1
    }
  ]
  ```

### `POST /api/v1/agent/plots/{id}/verify`
- **Expected Request (JSON)**:
  ```json
  {
    "decision": "ACTIVE",
    "confirmedPlantingDate": "2026-09-20",
    "rejectionReason": null
  }
  ```
- **Expected Response (200 OK)**: Updated `PlotResponse`.

### `POST /api/v1/agent/field-visits`
- **Content-Type**: `multipart/form-data`
- **Form parts**:
  - `plotId`: Long
  - `checklist`: JSON string
  - `plantCondition`: String
  - `observedCause`: String
  - `growthStage`: String
  - `lat`: Double
  - `lng`: Double
  - `visitedAt`: ISO-8601 Instant
  - `photo`: File (optional)
- **Expected Response (201 Created)**: Created field visit record.

---

## 9. Account & Consent Management

### `PUT /api/v1/me`
- **Request (JSON)**: `{ "fullName": "...", "language": "rw"|"en" }`
- **Response**: Updated `UserResponse`.

### `POST /api/v1/me/consent`
- **Request (JSON)**: `{ "version": "v1.0" }`
- **Response**: `200 OK` / `201 Created`.

### `DELETE /api/v1/me`
- **Response**: `204 No Content` or `409 PLOT_DELETE_DEFERRED` if active cover / open claims exist.
