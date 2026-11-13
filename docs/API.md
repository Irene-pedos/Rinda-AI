# Rinda API contract

For Agy, who builds the Android app and dashboard against this API. The product rules are in `RINDA_PROJECT.md`; if this file and the spec disagree, the spec wins, so please raise it.

- **Built:** section 3 lists what works today. Those examples were captured from the running backend, and tests cover the behaviour.
- **Planned:** section 5 lists what the backend will add. Those paths come from spec §8, but the fields are drafts taken from the database schema and may change. Nothing there exists yet, so don't treat it as final.

`ApiContractTest` fails if a built endpoint is missing from this file.

## 1. Basics

| Item | Value |
|---|---|
| Base URL | `http://localhost:8080/api/v1` on the backend computer |
| From the Android emulator | `http://10.0.2.2:8080/api/v1` |
| Format | JSON, UTF-8 |
| Dates | `yyyy-MM-dd`, for example `2026-09-20` |
| Timestamps | UTC ISO-8601, with 0 to 6 fractional digits, for example `2026-11-02T13:34:07.608495Z` |
| IDs | 64-bit integers, so use `long` in Java |
| Login | `Authorization: Bearer <accessToken>` on everything except the `/auth` endpoints |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html`, `dev` profile only |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs`, `dev` profile only |

To run the backend, follow steps 3 to 5 of the root `README.md`.

### Android notes

- **Plain HTTP:** the dev backend has no HTTPS, and Android blocks plain HTTP by default. Allow it in a debug-only network security config, and not in release builds.
- **Physical phone:** use the backend computer's Wi-Fi address instead of `10.0.2.2`. This hasn't been tested on the lab computer, and its firewall may block it. Where the demo runs is still open (`OPEN_QUESTIONS.md` question 11).
- **Coordinates:** the API uses GeoJSON `[longitude, latitude]`, but osmdroid's `GeoPoint` takes `(latitude, longitude)`. Swapping them usually fails with `PLOT_OUTSIDE_RWANDA`.
- **Timestamps:** `minSdk` is 24, and `java.time` needs API 26 unless core library desugaring is on.
- **Tokens:** keep them in EncryptedSharedPreferences, not SQLite.

### Dashboard note

The backend sends no CORS headers, so browser calls from another origin fail. A preflight request gets a 401. Call the API through Next.js rewrites or server routes, which need no backend change.

## 2. Rules that apply everywhere

### Errors

Every error is `application/problem+json` with a stable `code`. Branch on `code`, never on `detail` or `status`.

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "The boundary must be inside Rwanda.",
  "instance": "/api/v1/plots",
  "code": "PLOT_OUTSIDE_RWANDA"
}
```

`detail` is English and written for people, but it isn't translated, so map each `code` to a string in `strings.xml`. `instance` is missing on `UNAUTHORIZED` and on some `FORBIDDEN` responses.

`VALIDATION_FAILED` adds a `fields` object, with one message per field:

```json
{
  "status": 400,
  "detail": "One or more fields are invalid.",
  "code": "VALIDATION_FAILED",
  "fields": {
    "plantingDate": "must not be null",
    "boundary": "must not be null",
    "name": "must not be blank"
  }
}
```

These codes can come from any endpoint:

| Status | Code | Meaning |
|---|---|---|
| 400 | `VALIDATION_FAILED` | A field is missing or invalid. See `fields`. |
| 400 | `MALFORMED_REQUEST` | The body isn't valid JSON, or a value has the wrong format, such as a bad date. |
| 400 | `REQUEST_INVALID` | A path or query value has the wrong type, such as `/plots/abc`. |
| 401 | `UNAUTHORIZED` | The access token is missing, invalid or expired. |
| 403 | `FORBIDDEN` | This role can't do this. |
| 404 | `NOT_FOUND` | No such route. |
| 405 | `METHOD_NOT_ALLOWED` | Wrong HTTP method. |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Send `Content-Type: application/json`. |
| 500 | `INTERNAL_ERROR` | Backend failure. Try again later. |

Body validation runs before the role check, so an invalid body from the wrong role gets a 400, not a 403.

### Pages

List endpoints take `page` (from 0, default 0) and `size` (1 to 100, default 20). Anything else returns `PAGE_INVALID`.

```json
{ "items": [], "page": 0, "size": 20, "totalItems": 0, "totalPages": 0 }
```

### Demo data

Whenever `isDemo` is `true`, the UI must show a "Demo" label. Never mix demo and real data.

`dataStatus` is on plots. `TO_CONFIRM` means a placeholder whose details aren't real, so show it as unconfirmed. Plots created through the API are always `CONFIRMED`. The demo cooperative's `district` is the literal text `TO_CONFIRM`.

## 3. Built endpoints

### Login flow

```
POST /auth/otp/request      →  202, code sent
POST /auth/otp/verify       →  200, tokens         existing account
                            →  409 SIGNUP_DETAILS_REQUIRED   new phone number
GET  /auth/cooperatives     →  list for the sign-up screen
POST /auth/otp/verify       →  200, tokens         same code, with fullName and cooperativeId
```

Open the screens by `user.role` from the login response. Sign-up only ever creates farmers.

### `POST /auth/otp/request`

No login needed. Sends a 6-digit code. In dev mode it appears in the backend log as `[DEV OTP] phone=... code=...`.

```json
{ "phone": "0781234567" }
```

Accepts `07XXXXXXXX` or `+2507XXXXXXXX` with an MTN or Airtel prefix (72, 73, 78, 79). Spaces and dashes are ignored. Both forms are the same account.

Returns **202** with an empty body.

| Status | Code | When |
|---|---|---|
| 400 | `PHONE_INVALID` | Not a Rwandan mobile number |
| 429 | `OTP_RATE_LIMITED` | Less than 60 seconds since the last code for this phone |

Codes last 5 minutes and allow 5 wrong attempts. Only the newest code works.

### `POST /auth/otp/verify`

No login needed. Logs in, or signs up a new farmer.

```json
{ "phone": "+250781234567", "code": "123456" }
```

For a new phone number, add `fullName` (up to 120 characters) and `cooperativeId`:

```json
{ "phone": "+250781234567", "code": "123456", "fullName": "Aline", "cooperativeId": 9001 }
```

Returns **200**:

```json
{
  "accessToken": "eyJ...",
  "accessTokenExpiresIn": 900,
  "refreshToken": "kX2...",
  "user": {
    "id": 9001,
    "phone": "+250780000001",
    "fullName": "Demo Farmer One",
    "role": "FARMER",
    "language": "en",
    "isDemo": true
  }
}
```

`role` is `FARMER`, `AGENT`, `OFFICER` or `ADMIN`. `language` is `rw` or `en`. New accounts start on `rw`.

| Status | Code | When |
|---|---|---|
| 400 | `OTP_INVALID` | Wrong, expired, used or too many attempts. Unknown phones get this too. |
| 400 | `PHONE_INVALID` | Not a Rwandan mobile number |
| 400 | `COOPERATIVE_NOT_FOUND` | New account with an unknown `cooperativeId` |
| 403 | `ACCOUNT_SUSPENDED` | The account is suspended |
| 409 | `SIGNUP_DETAILS_REQUIRED` | New phone number. Ask for the details and resend with the **same code**. |

### `POST /auth/refresh`

No login needed. Swaps a refresh token for a new pair. Access tokens last 15 minutes and refresh tokens 30 days.

```json
{ "refreshToken": "kX2..." }
```

Returns **200** with the same body as `/auth/otp/verify`. Always store the new refresh token.

| Status | Code | When |
|---|---|---|
| 401 | `REFRESH_INVALID` | Unknown, expired or already used, or the account is suspended. Go to login. |

**Never refresh twice at once.** Each refresh token works once. If a used one comes back, the backend treats it as stolen and ends every session for that user, including the newest token. When several requests get a 401 together, only one may refresh and the others wait for it. With OkHttp, use an `Authenticator` with a lock, and check whether another thread already stored a new token. If a refresh gets no response, the token may already be used, so sending it again can log the user out.

### `GET /auth/cooperatives`

No login needed. Returns a plain array, sorted by name, not a page.

```json
[{ "id": 9001, "name": "Demo Cooperative", "district": "TO_CONFIRM", "sector": null, "isDemo": true }]
```

`sector` can be null.

### `GET /me`

Any role. Returns the same object as `user` in the login response.

### `GET /plots`

Any role. Takes `page` and `size`. Newest first, without deleted plots.

| Role | Sees |
|---|---|
| `FARMER` | Their own plots |
| `AGENT` | Plots of farmers in their cooperative. Returns 403 `AGENT_NOT_LINKED` if they have no cooperative. |
| `OFFICER`, `ADMIN` | All plots |

Returns **200** with a page of plots.

### `GET /plots/{id}`

Any role, with the same visibility as the list. Returns **200**:

```json
{
  "id": 42,
  "farmerId": 7,
  "upi": "0/00/00/00/42",
  "name": "Hillside",
  "crop": "MAIZE",
  "boundary": { "type": "Polygon", "coordinates": [[[30.1, -1.95], [30.102, -1.95], [30.102, -1.948], [30.1, -1.948], [30.1, -1.95]]] },
  "areaHa": 4.9209,
  "walkedAreaM2": 49209.0,
  "documentAreaM2": 49000.0,
  "areaDiffPct": 0.43,
  "plantedArea": null,
  "plantingDate": "2026-09-20",
  "relationshipToPlot": "OWNER",
  "status": "PENDING_VERIFICATION",
  "verificationLevel": "DOCUMENT_SUPPORTED",
  "financialActionsAllowed": false,
  "flags": [],
  "checks": [
    { "check": "UPI_FORMAT", "result": "PASSED", "note": "Five numeric parts. The exact format is not confirmed yet." },
    { "check": "ADMIN_AREA", "result": "NOT_RUN", "note": "Switched off until a cell boundary file with a confirmed licence is available." },
    { "check": "FIELD_VERIFICATION", "result": "PENDING", "note": "Needed before any payout. Ownership is not proven until then." }
  ],
  "monitoring": { "state": "DATA_AVAILABLE", "latestImageDate": "2026-10-28", "seasonFlag": "CONSISTENT" },
  "dataStatus": "CONFIRMED",
  "isDemo": false,
  "createdAt": "2026-11-11T08:00:00.000000Z",
  "updatedAt": "2026-11-11T08:05:00.000000Z"
}
```

- `boundary`, `areaHa` and `walkedAreaM2` are null until the plot is walked. **There is no way to draw or send a boundary.**
- `documentAreaM2` is the area the farmer **typed** from their document. Nothing is read from the image, so the agent compares it with the image at the field check.
- `plantedArea` is null: for now the whole walked boundary counts as planted, so the insured area can overstate what is really planted.
- `upi` and `relationshipToPlot` are null only on plots registered before UPIs were collected. `plantingDate` is null only on seeded placeholder plots.
- `checks` lists every registration check with `result` `PASSED`, `FLAGGED`, `REVIEWED` (a person resolved a flag), `PENDING` or `NOT_RUN`. Show `NOT_RUN` checks; don't hide them.
- `flags` holds the review flags, open and resolved (`resolvedAt` null means open). `type` is one of `AREA_MISMATCH`, `AREA_UNCHECKED`, `GPS_ACCURACY_UNCHECKED`, `MOCK_LOCATION`, `OVERLAP`, `DUPLICATE_UPI`, `NON_OWNER`, `LEGACY_DRAWN_BOUNDARY`, `OUTSIDE_ADMIN_AREA` (not raised while that check is off), `SATELLITE_INCONSISTENT`, `OTHER`. `relatedPlotId` is the other plot of an overlap or shared UPI; never show the other farmer.
- `status` is the registration state, not crop health (that is `GET /plots/{id}/status`):

| `status` | Meaning |
|---|---|
| `DRAFT` | Registration not finished: walk the plot and upload the e-title or portal screenshot |
| `PENDING_VERIFICATION` | Everything passed; waiting for the agent's field check |
| `FLAGGED` | A check needs a person's review (open flag) |
| `DISPUTED` | Overlaps another plot or shares its UPI. Every financial action is paused until a verifier resolves it. |
| `ACTIVE` | Checked in the field (`FIELD_VERIFIED` or higher) |
| `REJECTED` | The agent did not accept the plot. Walking it again starts over. |

- `verificationLevel` must be shown next to every plot, in the app and on the dashboard:

| `verificationLevel` | Reached by | Allowed use |
|---|---|---|
| `SELF_DECLARED` | UPI typed only | Demo only |
| `DOCUMENT_SUPPORTED` | UPI + e-title or portal screenshot + GPS walk + automatic checks passed, no open flag | Onboarding. **Not proof of ownership.** |
| `FIELD_VERIFIED` | The above + the agent's check at the plot with a geotagged photo | Minimum for payouts and cover |
| `NLA_VERIFIED` | Matched against NLA data. Not reachable yet. | Production |

- `financialActionsAllowed` is true only at `FIELD_VERIFIED` or higher with `status` `ACTIVE`. Otherwise payouts are refused (409 `PLOT_NOT_FIELD_VERIFIED` or `PLOT_NOT_ACTIVE`), and so is cover (409 `PLOT_NOT_ACTIVE`).

A plot that doesn't exist and a plot this user can't see both return 404 `PLOT_NOT_FOUND`.

### Registering a plot

Free drawing is gone. The steps are: **`GET /land-registry/parcels`** (optional: look up the UPI and show the parcel), **`POST /plots`** (details), **`POST /plots/{id}/walk`** (the GPS boundary walk at the plot) and **`POST /plots/{id}/documents`** (the farmer's own e-title or portal screenshot). The last two can happen in either order. Rinda never contacts NLA, Irembo, Amakuru or the Land Dashboard: the farmer supplies the document with their own access.

The automatic checks raise review flags; none of them rejects a plot. While a threshold is not supplied, its check sends the plot to a person instead of being skipped (`AREA_UNCHECKED`, `GPS_ACCURACY_UNCHECKED`). The thresholds are `TO_CONFIRM`, so **don't copy them into the app**: show `detail`.

### `GET /land-registry/parcels?upi=`

Any logged-in role. The first screen of "Add a plot": the farmer types the UPI and sees what the registry provider knows about that parcel (shape, area, land use, location), before the walk. Returns **200**:

```json
{
  "upi": "0/00/00/00/1002",
  "found": true,
  "source": "MOCK",
  "isMock": true,
  "notice": "Demo data, not official land records. Your plot's boundary still comes from walking its edge.",
  "parcel": {
    "boundary": { "type": "Polygon", "coordinates": [[[29.742, -2.596], [29.7432, -2.5961], [29.7435, -2.595], [29.7426, -2.5944], [29.7419, -2.595], [29.742, -2.596]]] },
    "areaM2": 24039.4,
    "areaHa": 2.4039,
    "landUse": "Agriculture (mock)",
    "location": "Demo province, Demo district, Demo sector, Demo cell"
  }
}
```

- **The parcel's shape is a reference, never the plot's boundary.** Show it as "this is roughly your parcel", then send the farmer to walk the edge (`POST /plots/{id}/walk`). Nothing here can be sent back as a boundary.
- `found: false` is normal, not an error: register as usual with the walk and the e-title. `notice` says what to tell the farmer; `parcel` is null.
- **`isMock: true` is invented demo data: always label it "Demo, not official land records".** The `MOCK` provider (dev profile only) knows only the fake zero-code UPIs in `backend/src/main/resources/mock-registry/parcels.json`: `0/00/00/00/9001` to `9004` (the demo plots) and `0/00/00/00/1001` to `1003`. A real UPI is never matched and never given an invented shape.
- On the `DOCUMENT` provider (production for now) `found` is always false: Rinda has no parcel data until an official NLA agreement.
- The owner's name is never returned, so a UPI can't be used to find out who owns a parcel. A lookup never changes the plot's verification level.

| Status | Code | When |
|---|---|---|
| 400 | `UPI_INVALID` | Not five numeric parts, for example `1/01/01/01/0001` |

### `POST /plots`

`FARMER` only. Starts a registration (`DRAFT`, `SELF_DECLARED`):

```json
{
  "name": "Hillside",
  "upi": "1/01/01/01/0001",
  "plantingDate": "2026-09-20",
  "relationshipToPlot": "OWNER",
  "declarationAccepted": true
}
```

- `name` is required, up to 80 characters. `plantingDate` is required and can't be in the future.
- `upi` is typed from the farmer's e-title. It is checked loosely (five numeric parts separated by `/`) because the exact format is not confirmed.
- `relationshipToPlot` is `OWNER`, `FAMILY_MEMBER` or `TENANT`. Anything but `OWNER` raises a `NON_OWNER` flag: the farmer uploads a `LEASE` or `OWNER_CONSENT` document and a person reviews it.
- `declarationAccepted` must be `true`: the farmer confirms they have the right to farm the plot and that a false claim can cancel cover.
- `crop` is optional. Only `MAIZE` is accepted, so don't send it and don't build a crop picker.
- **Don't send `boundary`.** It is refused.

Returns **201** with the plot. A second plot with the same UPI makes both `DISPUTED` (`DUPLICATE_UPI`), and both farmers get `PLOT_DISPUTED`.

| Status | Code | When |
|---|---|---|
| 400 | `BOUNDARY_NOT_ACCEPTED` | The body has a `boundary` |
| 400 | `UPI_INVALID` | Not five numeric parts |
| 400 | `DECLARATION_REQUIRED` | `declarationAccepted` is not `true` |
| 400 | `PLANTING_DATE_INVALID` | In the future, or outside a confirmed season |
| 400 | `CROP_NOT_SUPPORTED` | `crop` is anything other than `MAIZE` |

### `POST /plots/{id}/walk`

`FARMER`, own plot. The farmer walks the edge of the plot and the app records a GPS fix at each corner, or along the edge. The server builds the polygon from the points, in order, and closes it.

```json
{
  "points": [
    { "lat": -1.95, "lng": 30.1, "accuracyM": 4.0, "recordedAt": "2026-11-11T07:00:00Z", "mockLocation": false },
    { "lat": -1.95, "lng": 30.102, "accuracyM": 3.5, "recordedAt": "2026-11-11T07:02:10Z", "mockLocation": false },
    { "lat": -1.948, "lng": 30.102, "accuracyM": 5.0, "recordedAt": "2026-11-11T07:04:30Z", "mockLocation": false }
  ]
}
```

- 3 to 499 points, each later than the one before. Don't repeat the first point at the end.
- `accuracyM` is the fix's horizontal accuracy from Android. `mockLocation` is `Location.isMock()` (`isFromMockProvider()` before API 31). **Never send a default or remembered location.**
- Walking again replaces the boundary, and every earlier walk is kept as a record. A walk is refused after the field check and while the plot is disputed.

Returns **200** with the plot. A walk checks the area limits, overlap with other plots in Rinda, the walked area against the document area, GPS accuracy and mock location. Above the overlap limit, both plots become `DISPUTED`; shared edges don't count as overlap. Walk points are personal data: don't log them on the phone.

| Status | Code | When |
|---|---|---|
| 400 | `WALK_INVALID` | Points out of order, or fewer than 3 different corners |
| 400 | `WALK_ACCURACY_POOR` | A point is less accurate than the limit. Ask the farmer to wait for a better signal and walk again. |
| 400 | `PLOT_GEOMETRY_INVALID` | The walk crosses itself |
| 400 | `PLOT_OUTSIDE_RWANDA` | A point is outside Rwanda's bounding box |
| 400 | `PLOT_TOO_SMALL`, `PLOT_TOO_LARGE` | Outside the area limits |
| 409 | `PLOT_LOCKED` | Already checked in the field |
| 409 | `PLOT_DISPUTED` | A verifier must resolve the dispute first |

**Honest limit:** the server checks the points but can't prove they were walked, and a walk proves presence, not ownership. That is why payouts need the field check.

### `POST /plots/{id}/documents`

`FARMER`, own plot. Multipart form with these fields:

- `type`: `E_TITLE`, `PORTAL_SCREENSHOT`, `LEASE` or `OWNER_CONSENT`.
- `file`: JPEG, PNG or PDF, up to 5 MB.
- `documentAreaM2`: required for `E_TITLE` and `PORTAL_SCREENSHOT`. The area shown on the document, in m², typed by the farmer.

Returns **201**:

```json
{ "id": 3, "plotId": 42, "type": "E_TITLE", "contentType": "application/pdf", "typedAreaM2": 49000.0,
  "uploadedAt": "2026-11-11T08:00:00Z", "isDemo": false }
```

| Status | Code | When |
|---|---|---|
| 400 | `DOCUMENT_REQUIRED` | No file |
| 400 | `DOCUMENT_INVALID` | Not a JPEG, PNG or PDF (checked by content, not by file name) |
| 400 | `DOCUMENT_AREA_REQUIRED` | E-title or screenshot without a positive `documentAreaM2` |
| 409 | `PLOT_LOCKED` | E-title or screenshot after the field check |

### `GET /plots/{id}/documents`

Same visibility as the plot. Returns **200** with a list of documents (details only).

### `GET /plots/{id}/documents/{documentId}/file`

Same visibility as the plot. Returns the file, marked not to be cached, or 404 `DOCUMENT_NOT_FOUND` if it isn't this plot's.

### `POST /plots/{id}/flags/{flagId}/resolve`

`AGENT` (the farmer's cooperative), `OFFICER` or `ADMIN`, never the plot's farmer. Body: `{ "note": "Checked the e-title with the farmer." }` (required, max 1000). A person resolves a flag after reviewing it; nothing resolves one automatically. Returns **200** with the plot, with its status and level recomputed. `audit_log` records `PLOT_FLAG_RESOLVED`.

Each side of an overlap has its own flag, so resolve both if both plots are legitimate. To refuse a plot instead, reject it with `POST /agent/plots/{id}/verify`.

| Status | Code | When |
|---|---|---|
| 404 | `FLAG_NOT_FOUND` | Not a flag of this plot |
| 409 | `FLAG_ALREADY_RESOLVED` | Already resolved |
| 409 | `FLAG_NEEDS_WALK` | `LEGACY_DRAWN_BOUNDARY`: only walking the plot replaces a drawn boundary |
| 409 | `TENURE_PROOF_MISSING` | `NON_OWNER` with no `LEASE` or `OWNER_CONSENT` uploaded |

### `PUT /plots/{id}`

`FARMER`, own plots only. Body: `name` and `plantingDate`, both required. Returns **200** with the plot. After the field check only `name` can change: a new date returns 409 `PLOT_LOCKED`. A `boundary` is refused with 400 `BOUNDARY_NOT_ACCEPTED`; walk the plot again instead.

### `DELETE /plots/{id}`

`FARMER`, own plots only. Returns **204** with an empty body. The plot disappears from the API, but the record is kept. A second delete returns 404 `PLOT_NOT_FOUND`.

| Status | Code | When |
|---|---|---|
| 409 | `PLOT_DELETE_DEFERRED` | The plot has active cover or an open claim. Explain that it can be deleted after the season or claim closes (§2.2 #11). |

### Satellite monitoring

Monitoring starts only once a plot has a walked boundary (`docs/02-satellite-monitoring.md`). Saving the walk queues a **backfill** from the planting date to today, so a new plot shows its season so far. A **weekly refresh** (Mondays 04:00 Kigali time) then fetches whatever new images exist. A new walk starts a fresh series; an earlier planting date starts a new backfill.

Satellite data is **a review flag, never proof of crop**, and it is **never live**: always show `latestImageDate`. Real plots get Earth Engine data; demo plots only the simulated sample, labelled Demo. Nothing falls back to the sample.

### `GET /plots/{id}/monitoring`

Any role, with the same visibility as `GET /plots/{id}` (404 `PLOT_NOT_FOUND` otherwise). Returns **200**:

```json
{
  "plotId": 42,
  "state": "DATA_AVAILABLE",
  "message": "Satellite images up to the date shown. Not live: updated weekly.",
  "verificationLevel": "DOCUMENT_SUPPORTED",
  "verificationNote": "This plot is not verified yet (DOCUMENT_SUPPORTED). The satellite curve checks what was declared; crop health and claims start after the agent's field check.",
  "healthStatusAvailable": false,
  "isDemo": false,
  "dataOrigin": "GEE",
  "latestImageDate": "2026-10-28",
  "monitoredSince": "2026-09-20",
  "lastFetchedAt": "2026-11-09T02:04:11.000000Z",
  "refreshing": false,
  "updateFrequency": "WEEKLY",
  "minClearPixels": 20,
  "seasonCheck": {
    "flag": "CONSISTENT", "reason": "CONSISTENT_SO_FAR", "confidence": "LOW", "checkDate": "2026-11-09",
    "plantingDate": "2026-09-20", "signals": ["BARE_START_SEEN", "GROWTH_SEEN"],
    "dataQualityNote": "6 clear image(s) since planting, latest 2026-10-28, median 31 clear pixels each (minimum 20). A review flag, not proof of crop: ...",
    "imagesUsed": 6, "pixelsUsed": 29, "latestImageDate": "2026-10-28", "modelVersion": "0.6.0", "dataOrigin": "GEE"
  },
  "observations": [
    { "date": "2026-09-25", "source": "S2", "ndviMedian": 0.2104, "pixelCount": 31, "cloudFraction": 0.0313, "usable": true },
    { "date": "2026-09-30", "source": "S2", "ndviMedian": 0.4012, "pixelCount": 4, "cloudFraction": 0.875, "usable": false }
  ],
  "rainfall": [ { "date": "2026-09-20", "rainMm": 3.21, "source": "CHIRPS" } ],
  "latestJob": { "id": 7, "kind": "REFRESH", "status": "DONE", "fetchFrom": "2026-10-30", "fetchTo": "2026-11-09",
                 "errorCode": null, "createdAt": "2026-11-09T02:00:00.000000Z", "finishedAt": "2026-11-09T02:04:11.000000Z" },
  "notes": ["Satellite data is a review flag, never proof of crop: satellites can't tell maize from similar crops.",
            "Not live: Rinda checks for new images once a week, and cloudy weeks have none. The date of the latest image is always shown."]
}
```

`state` drives the screen (`docs/02-satellite-monitoring.md`, "UI states"). The app shows "Register your plot to start monitoring" itself when the farmer has no plot.

| `state` | Show |
|---|---|
| `NO_BOUNDARY` | "Walk the edge of your plot to start satellite monitoring", with a button to the walk. No chart. |
| `FETCHING` | "Fetching satellite data". The first fetch is queued or running. |
| `FETCH_FAILED` | The first fetch failed (for example Earth Engine down). Rinda tries again at the next weekly update. |
| `NO_CLEAR_IMAGE` | "No clear image since [`monitoredSince`]; showing rainfall only". Expected in cloudy weeks. |
| `DATA_AVAILABLE` | NDVI curve, rainfall, `latestImageDate` and the season flag |

- `observations` are the **raw** values, one per image date: the median NDVI of the clear pixels inside the plot (shrunk by one pixel so edge pixels are left out), how many clear pixels it used, and the share of the plot's pixels masked as cloud or shadow. `usable` is false when `pixelCount` is below the `min_clear_pixels` threshold; plot only usable points, and show `pixelCount` so weak signals are visible. With `min_clear_pixels` empty (`minClearPixels` null), no image is usable.
- `seasonCheck.flag` answers "consistent with a maize season?": `CONSISTENT`, `INCONSISTENT` or `NOT_ENOUGH_DATA`. It compares the curve since planting with the expected pattern: low (bare soil), a rise, a peak, a decline. `reason` is one of `SEASON_PATTERN_SEEN`, `CONSISTENT_SO_FAR`, `GREEN_AT_PLANTING`, `NO_GROWTH_SEEN`, `STILL_GREEN_AFTER_SEASON`, `FEW_CLEAR_IMAGES`, `NO_CLEAR_IMAGE_AFTER_PLANTING`, `TOO_EARLY`, `PATTERN_UNCLEAR`, `MIN_CLEAR_PIXELS_NOT_SET`. `confidence` is never `HIGH`: the rules aren't checked against field results yet. `seasonCheck` is null before the first fetch.
- `INCONSISTENT` opens a `SATELLITE_INCONSISTENT` flag on the plot for a person to review (`POST /plots/{id}/flags/{flagId}/resolve`). It never rejects a plot. On a plot below `FIELD_VERIFIED` the open flag makes it `FLAGGED` until reviewed; on a `FIELD_VERIFIED` plot it is shown and the field check stands.
- `verificationNote` is null at `FIELD_VERIFIED` or higher. Otherwise show it: the curve and flag start at the boundary, but crop health (`GET /plots/{id}/status`) and the claim trigger run only on `FIELD_VERIFIED` plots (`healthStatusAvailable`).
- `dataOrigin` `SAMPLE_FILE` is simulated data on a demo plot: label it Demo. Never call anything "live" or "real-time".

`GET /plots` and `GET /plots/{id}` carry a short `monitoring` summary for lists and the dashboard: `{ "state": "DATA_AVAILABLE", "latestImageDate": "2026-10-28", "seasonFlag": "CONSISTENT" }` (`latestImageDate` and `seasonFlag` can be null).

### Plot data for the charts

Any role, with the same visibility as `GET /plots/{id}`. A plot that can't be seen returns 404 `PLOT_NOT_FOUND`. A plot with no analysis yet returns **200** with an empty list, which isn't an error, so show an empty state.

`dataOrigin` is `GEE` for real satellite data and `SAMPLE_FILE` for simulated data. **Always label `SAMPLE_FILE` data as Demo.**

`dap` is days after planting. It's null if the plot has no known planting date. For a seeded plot, it uses the planting date of the latest analysis.

### `GET /plots/{id}/observations`

Takes optional `from` and `to` (`yyyy-MM-dd`, both included). Returns **200** with a plain array, oldest first:

```json
[
  {
    "date": "2026-10-05",
    "dap": 15,
    "source": "S2",
    "ndvi": 0.41,
    "smoothedNdvi": 0.4,
    "clearPixels": 240,
    "cloudPct": 12.5,
    "dataOrigin": "GEE"
  },
  {
    "date": "2026-10-10",
    "dap": 20,
    "source": "S2",
    "ndvi": null,
    "smoothedNdvi": null,
    "clearPixels": 3,
    "cloudPct": null,
    "dataOrigin": "GEE"
  }
]
```

`ndvi` is null when clouds left too few clear pixels. **Show a gap, and never join across it as if a reading existed.** Plot `smoothedNdvi` for the line, and compare it with the band from `GET /plots/{id}/baseline`.

| Status | Code | When |
|---|---|---|
| 400 | `DATE_RANGE_INVALID` | `from` is after `to` |
| 400 | `REQUEST_INVALID` | A date isn't `yyyy-MM-dd` |

### `GET /plots/{id}/weather`

Takes the same `from` and `to`, with the same errors. Returns **200** with a plain array of daily rain, oldest first:

```json
[{ "date": "2026-10-01", "dap": 11, "rainMm": 4.2, "dataOrigin": "GEE" }]
```

`rainMm` of `0` means a dry day. A missing date means no data, which isn't the same thing.

### `GET /plots/{id}/status`

Returns **200** with the latest crop health:

```json
{
  "date": "2025-12-10",
  "status": "AT_RISK",
  "anomaly": -1.84,
  "rainPct": 42.5,
  "cause": "WATER_DEFICIT",
  "confidence": "MEDIUM",
  "needsFieldVisit": false,
  "signals": [
    { "code": "NDVI_BELOW_NORMAL", "readings": 3 },
    { "code": "NDVI_WELL_BELOW_NORMAL", "readings": 3, "days": 15 },
    { "code": "RAIN_BELOW_NORMAL", "rainPct": 42.5 }
  ],
  "dataQualityNote": "3 clear reading(s) in the 20 days to 2025-12-10. Latest: 2025-12-09. These rules have not been checked against field results yet.",
  "modelVersion": "0.3.0",
  "dataOrigin": "GEE",
  "lastKnownStatus": null,
  "lastKnownDate": null,
  "thresholdsUsed": { "at_risk_anomaly": -1.5, "watch_rain_pct": 60.0 }
}
```

- `status` is `HEALTHY`, `WATCH`, `AT_RISK` or `NOT_ENOUGH_DATA`. Show it as icon plus word, never colour alone.
- **This is crop health, not a claim, and not the plot's `status` from `GET /plots/{id}`.** `AT_RISK` must not open a claim screen (§2.2 #7).
- `NOT_ENOUGH_DATA` shows "Not enough satellite data yet". A plot with no analysis yet also returns this, with `date` null and the other result fields null or empty.
- **With `NOT_ENOUGH_DATA`, `lastKnownStatus` and `lastKnownDate` give the newest real status.** Show "Last update: <date>", and never present that status as current. Both are null with any other status, or if the plot has never had one.
- `thresholdsUsed` lists the values this result used. Show it to officers if useful, but don't copy these values into the app, because they can change. Older results may have it as null.
- `cause` is `WATER_DEFICIT`, `UNEXPLAINED` or null. Null means no low NDVI to explain, so don't show a cause. Word them as "likely", and never name a pest or disease.
- `needsFieldVisit` is true for `UNEXPLAINED`. Show that an agent visit is recommended.
- `confidence` is `HIGH`, `MEDIUM` or `LOW`, and always show it. The rules aren't back-tested yet, so today it stops at `MEDIUM`.
- `rainPct` is rain over the 30 days to the latest available rain data, as a percent of normal. Null means it couldn't be compared, which doesn't mean no rain.
- Always show `dataQualityNote`. It's English only for now.

Each item in `signals` has a `code`, so map codes to `strings.xml`:

| Code | Meaning | Extra field |
|---|---|---|
| `NDVI_BELOW_NORMAL` | Latest readings are below the normal band | `readings` in a row |
| `NDVI_WELL_BELOW_NORMAL` | Latest readings are far below the normal band | `readings` in a row, and the `days` they span |
| `RAIN_BELOW_NORMAL` | Rain is below the limit for this plot | `rainPct` |
| `RAIN_NOT_COMPARED` | Not enough rain data to compare | |
| `NORMAL_CURVE_ESTIMATED` | The normal band here was estimated, so confidence is `LOW` | |
| `NO_RECENT_READINGS` | Too few clear readings recently | |
| `NO_NORMAL_CURVE` | No normal band for this growth stage | |

### `GET /plots/{id}/forecast`

Returns **200** with the newest forecast:

```json
{
  "available": true,
  "date": "2025-12-10",
  "lowPct": 62.4,
  "highPct": 81.9,
  "confidence": "LOW",
  "reason": null,
  "dataQualityNote": "Based on 7 of 9 10-day periods since planting. This compares crop greenness with this plot's past seasons. It isn't a measured yield, and it hasn't been checked against real harvests.",
  "modelVersion": "0.5.0",
  "dataOrigin": "GEE",
  "thresholdsUsed": { "forecast_min_dap": 42.0, "forecast_min_bins": 3.0 }
}
```

- **Show the range, labelled "Prototype estimate", as a percent of normal.** Never show one number, a midpoint or a tonnage.
- Always show `dataQualityNote`. `confidence` stays `LOW`, because this hasn't been compared with real harvests.
- When `available` is false, `lowPct` and `highPct` are null, and `reason` says why:

| `reason` | Meaning |
|---|---|
| `NOT_ANALYSED` | The plot hasn't been analysed yet |
| `TOO_EARLY` | It's too soon after planting. Don't hardcode the number of days, because it can change. |
| `NOT_ENOUGH_DATA` | Too few clear readings could be compared |

A forecast is about the crop, and isn't a claim or a payout.

### `GET /plots/{id}/enrollment`

Returns **200** with the plot's newest enrollment:

```json
{
  "status": "COVERED",
  "seasonCode": "DEMO",
  "startDate": null,
  "endDate": null,
  "sumInsured": null,
  "premium": null,
  "isDemo": true
}
```

- `status` is `NOT_ENROLLED`, `PENDING`, `COVERED` or `CANCELLED`. `NOT_ENROLLED` has null in the other fields.
- **`sumInsured` and `premium` are null until the insurer confirms them.** Show "Not confirmed yet", never 0.
- The dates can be null too, because season dates aren't confirmed.
- Farmers can't request enrollment in the MVP (§10.1).

### `GET /plots/{id}/baseline`

Returns **200** with the newest normal band saved for the plot:

```json
{
  "seasonType": "A",
  "version": "plot-history-v1",
  "binDays": 10,
  "dataOrigin": "GEE",
  "bins": [
    { "dapBin": 10, "p10": 0.2, "p50": 0.3, "p90": 0.4, "filled": false },
    { "dapBin": 20, "p10": 0.3, "p50": 0.4, "p90": 0.5, "filled": true }
  ]
}
```

- A bin covers `dapBin` up to, but not including, `dapBin + binDays`. Use `binDays` from the response, not a fixed 10.
- `filled` is true when a bin had too few past readings and was estimated from the bins on each side. Show those bins differently, for example with a dashed band.
- Longer gaps and the ends of the curve aren't estimated, so the list can still have gaps. Don't draw the band across a gap.
- With no baseline yet, `bins` is empty and the other fields are null.

### Alerts

### `GET /notifications`

Any role. Takes `page` and `size`. Returns **200** with a page of your own notifications, newest first:

```json
{
  "items": [
    {
      "id": 12,
      "type": "HEALTH",
      "code": "HEALTH_AT_RISK",
      "title": "Your plot needs attention",
      "body": "Demo plot 1 has been growing well below its normal range. This is a crop health alert, not a claim.",
      "plotId": 9001,
      "plotName": "Demo plot 1",
      "link": "/plots/9001",
      "isRead": false,
      "isDemo": true,
      "createdAt": "2026-11-03T08:15:02.114Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

- **`HEALTH` opens plot detail, using `plotId`. `CLAIM` will open claim detail** (§2.2 #7). A health alert is never a claim.
- `title` and `body` are English. Show your own `strings.xml` text for each `code`, with `plotName` filled in.
- Show a Demo label when `isDemo` is true.

| `code` | When |
|---|---|
| `HEALTH_WATCH` | Crop health became `WATCH` |
| `HEALTH_AT_RISK` | Crop health became `AT_RISK` |
| `CLAIM_TRIGGERED` | The trigger started a claim. `type` is `CLAIM` and `link` is `/claims/{id}`. |
| `CLAIM_APPROVED`, `CLAIM_DECLINED`, `CLAIM_FINAL`, `CLAIM_FIELD_VISIT`, `CLAIM_PAID`, `PAYOUT_ACCOUNT_NEEDED` | Claim changes for the farmer (`type` `CLAIM`) |
| `PLOT_VERIFIED`, `PLOT_REJECTED` | The agent checked the plot (`type` `SYSTEM`, opens the plot) |
| `PLOT_DISPUTED` | Another registration overlaps this plot or shares its UPI (`type` `SYSTEM`, opens the plot). Payouts wait. |
| `AGENT_VISIT_REQUESTED`, `FIELD_VISIT_NEEDED` | For agents: a farmer or an officer asked for a field visit |
| `FIELD_VISIT_RECORDED` | For officers: the visit a claim was waiting for was recorded |

A health alert is only sent when a plot gets worse than its last known status. Recovering, moving into or out of `NOT_ENOUGH_DATA`, and re-checking the same date don't send one. The status can change often, and this keeps alerts to the changes that matter (`ML_VALIDATION.md`). More `CLAIM` codes come with decisions and payouts.

### `POST /notifications/{id}/read`

Any role, your own notifications only. Returns **204**. Marking one twice is fine.

| Status | Code | When |
|---|---|---|
| 404 | `NOTIFICATION_NOT_FOUND` | It doesn't exist, or it isn't yours |

### Claims

**Claims have no create endpoint, and never will.** Only the parametric trigger creates them (§3.7). It runs on the backend every time an ML result is saved:

- The plot must be `ACTIVE` with a `COVERED` enrollment on the trigger date.
- It must have been `AT_RISK` for at least **X** days without a break inside the sensitive stage (days `sensitive_stage_start_dap` to `sensitive_stage_end_dap` after planting). Any other status, including `NOT_ENOUGH_DATA`, breaks the run: missing data never counts as loss.
- The run needs at least `trigger_min_valid_obs` clear Sentinel-2 readings.
- And either the **whole** harvest forecast range is below **Y**% of normal, or the rain deficit (100 − rain % of normal) is at least **Z**%.
- X, Y and Z are `trigger_at_risk_days`, `trigger_forecast_pct` and `trigger_rain_deficit_pct` in the `thresholds` table. **While any of them is empty, no claim is ever created** (question 3).
- At most one automatic claim per plot and season. Re-running the analysis never makes a second one.
- A new claim is `UNDER_REVIEW`, the farmer gets a `CLAIM_TRIGGERED` notification, and `audit_log` records `CLAIM_TRIGGERED`. A person decides every claim.

### `GET /claims`

Any role. Takes `page` and `size`. Returns **200** with a page of claims, newest trigger date first. Farmers see claims on their own plots, agents those in their cooperative, and officers and admins all of them. `evidenceSnapshot` is `null` in the list, to keep it small.

### `GET /claims/{id}`

Same visibility. Returns **200**:

```json
{
  "id": 41,
  "plotId": 9001,
  "plotName": "Demo plot 1",
  "seasonId": 9001,
  "seasonCode": "DEMO",
  "triggerDate": "2026-11-29",
  "status": "UNDER_REVIEW",
  "payoutRate": null,
  "amount": null,
  "evidenceSnapshot": {
    "rule": "AT_RISK for at least 10 days without a break between days 50 and 100 after planting, and …",
    "triggerReason": "The crop was well below its normal growth for 14 days in the flowering and grain-filling stage, and rain was 55% below normal.",
    "thresholds": { "trigger_at_risk_days": { "value": 10.000, "status": "CONFIRMED_FOR_DEMO" } },
    "sensitiveStage": { "startDap": 50, "endDap": 100 },
    "atRiskRun": { "from": "2026-11-15", "to": "2026-11-29", "days": 14, "fromDap": 56, "toDap": 70, "validObservations": 2 },
    "statuses": [ { "date": "2026-11-15", "dap": 56, "status": "AT_RISK", "anomaly": -1.800, "rainPct": 45.0,
                    "cause": "WATER_DEFICIT", "confidence": "MEDIUM", "modelVersion": "0.3.0", "dataOrigin": "GEE", "signals": [] } ],
    "forecast": { "met": true, "date": "2026-11-29", "lowPct": 55.00, "highPct": 70.00, "confidence": "LOW" },
    "rain": { "met": true, "rainPct": 45.0, "deficitPct": 55.0 },
    "rainDeficitPct": "55",
    "lowestAnomaly": -1.800,
    "modelVersions": ["0.3.0"],
    "reviewFlags": ["LOW_CONFIDENCE_FORECAST"],
    "chart": { "observations": [ … ], "baseline": [ … ], "baselineVersion": "plot-history-v1", "rainDaily": [ … ] }
  },
  "decision": null,
  "modelVersion": "0.3.0",
  "isDemo": true,
  "createdAt": "2026-11-29T06:00:12.402Z"
}
```

- `status` is `UNDER_REVIEW`, `FIELD_VISIT_REQUESTED`, `APPROVED`, `DECLINED`, `PAID`, `REVIEW_REQUESTED` or `FINAL`. **Always take it from the server, never from local data.**
- `amount` and `payoutRate` stay `null` until the insurer confirms the rates (question 4). Show "not confirmed", never 0.
- `evidenceSnapshot` is frozen when the claim starts and never changes. `triggerReason` is plain English for the farmer. `chart` has the NDVI readings, the normal band and daily rain up to the trigger date.
- `reviewFlags` tells the officer what to check: `LOW_CONFIDENCE_STATUS`, `LOW_CONFIDENCE_FORECAST`, `NORMAL_CURVE_ESTIMATED` (question 19), `FIELD_VISIT_SUGGESTED` and `SAMPLE_DATA`.
- `decision` is the latest officer decision (`decision`, `reason`, `decidedAt`), or `null`.
- `payout` is the simulated payout (`amount`, `reference`, `status`, `isSimulated` always `true`, `createdAt`), or `null`.
- `review` is the farmer's latest review request (`message`, `status` `OPEN` or `ANSWERED`, `answer`, `createdAt`), or `null`.
- **`canRequestReview`** is `true` only when the farmer may ask for a review now. Show the "Ask for a review" button only then.
- Show a Demo label when `isDemo` is true.

| Status | Code | When |
|---|---|---|
| 404 | `CLAIM_NOT_FOUND` | It doesn't exist, or you can't see it |

### Claim actions

Each action returns **200** with the claim as it now is (same shape as `GET /claims/{id}`). Never change a status on the phone; show what comes back.

| Endpoint | Who | Body | What happens |
|---|---|---|---|
| `POST /claims/{id}/decision` | `OFFICER` | `decision` (`APPROVED`, `DECLINED`), `reason` (required, max 2000) | From `UNDER_REVIEW`, `FIELD_VISIT_REQUESTED` or `REVIEW_REQUESTED`. Approve → `APPROVED`, then the payout is tried at once. Decline → `DECLINED`, or `FINAL` when it answers a review. |
| `POST /claims/{id}/field-visit-request` | `OFFICER` | none | `UNDER_REVIEW` → `FIELD_VISIT_REQUESTED`. The farmer and the cooperative's agents are told. |
| `POST /claims/{id}/payout` | `OFFICER` | none | Sends the simulated payout of an `APPROVED` claim → `PAID`. Use it after the rates are confirmed or the farmer verified their account. |
| `POST /claims/{id}/review-request` | `FARMER` (own claim) | `message` (required, max 500) | From `APPROVED` or `DECLINED`, once per decision → `REVIEW_REQUESTED`. |

- **The amount is never guessed.** It is the claim's `amount`, or the enrollment's `sum_insured` × the claim's `payoutRate`. Both come from the insurer (question 4) and are set in the database for now. Without them the claim stays `APPROVED`.
- A payout also needs a verified payout account. If there is none, the farmer gets a `PAYOUT_ACCOUNT_NEEDED` notification.
- Every action writes to `audit_log` and notifies the farmer: `CLAIM_APPROVED`, `CLAIM_DECLINED`, `CLAIM_FINAL`, `CLAIM_FIELD_VISIT`, `CLAIM_PAID`, `PAYOUT_ACCOUNT_NEEDED`. Agents get `FIELD_VISIT_NEEDED`.

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Missing or empty `reason` or `message` |
| 403 | `FORBIDDEN` | Wrong role (only officers decide; only farmers ask for reviews) |
| 404 | `CLAIM_NOT_FOUND` | It doesn't exist, or it isn't the farmer's |
| 409 | `CLAIM_NOT_DECIDABLE` | Already decided; it needs a review request first |
| 409 | `CLAIM_NOT_UNDER_REVIEW` | Field visit asked for after a decision |
| 409 | `CLAIM_NOT_REVIEWABLE` | No decision yet, already paid, already under review, or final |
| 409 | `REVIEW_ALREADY_REQUESTED` | This decision was already reviewed |
| 409 | `CLAIM_NOT_APPROVED` | Payout of a claim that isn't `APPROVED` (including one already `PAID`) |
| 409 | `PAYOUT_AMOUNT_NOT_CONFIRMED` | The insurer's sum insured or payout rate is missing |
| 409 | `PAYOUT_ACCOUNT_NOT_VERIFIED` | The farmer has no verified payout account |

### Payout account

`FARMER` only. One mobile-money number per farmer, for simulated payouts (§2.2 #12).

| Endpoint | Body | Returns |
|---|---|---|
| `GET /payout-account` | none | **200** with the account, or **404** `PAYOUT_ACCOUNT_NOT_SET` |
| `POST /payout-account` | `provider` (`MTN_MOMO`, `AIRTEL`), `msisdn` (`078…` or `+25078…`) | **202** with the account. A 6-digit code is sent to the number unless it is already verified (in dev, it's in the backend log). |
| `POST /payout-account/verify` | `code` | **200** with `verified: true` |

```json
{ "provider": "MTN_MOMO", "msisdn": "+250783333333", "verified": true, "nameMatch": true,
  "nameCheck": "SIMULATED", "verifiedAt": "2026-11-09T10:12:00Z" }
```

- **Any change of number or provider makes the account unverified** until the new code is entered. Saving the same verified number again keeps it verified.
- MTN numbers start with 078 or 079 and Airtel numbers with 072 or 073; anything else gets **400** `PAYOUT_PROVIDER_MISMATCH`.
- `nameCheck` is always `SIMULATED`: Rinda can't see mobile-money accounts. Show it that way.
- Payout codes and login codes are separate: a login code can't verify a payout number. Wrong or old codes get **400** `OTP_INVALID`, and asking again within 60 s gets **429** `OTP_RATE_LIMITED`.

### Field evidence

Problem reports and field visits are **evidence only**. Neither ever creates a claim, a decision or a payout (§2.2 #1). Each is attached to the plot's open claim (`claimId`), if there is one, so the officer sees it.

**Offline outbox:** send a `clientId` (any string up to 40 characters, such as a UUID made on the phone). Sending the same `clientId` again returns the first record with **200** instead of **201**, so a retry never makes a duplicate. Send times as ISO-8601 (`2026-11-05T09:00:00Z`), not milliseconds.

**Photos** are optional, JPEG or PNG only (checked from the file itself, max 5 MB; compress first). They are kept privately: `photoUrl` is an API path that needs your token, and only people who can see the record get the photo.

#### `POST /problem-reports`

`FARMER`, own plots only. Multipart form:

| Field | Required | Notes |
|---|---|---|
| `plotId` | yes | Your plot |
| `reason` | yes | `DRY_LEAVES`, `YELLOW_LEAVES`, `FLOODING` or `OTHER`. There are no pest or disease reasons: satellites can't identify them, and these go to advice and an agent visit. |
| `note` | no | Up to 2000 characters |
| `agentVisitRequested` | no | `true` tells the cooperative's agents (`AGENT_VISIT_REQUESTED` notification) |
| `clientId`, `reportedAt` | no | Outbox id, and when the farmer made the report |
| `photo` | no | File part |

Returns **201** (or **200** for a repeated `clientId`):

```json
{ "id": 7, "plotId": 9001, "plotName": "Demo plot 1", "farmerId": 9001, "reason": "DRY_LEAVES",
  "note": "Leaves curling since Monday", "agentVisitRequested": true, "claimId": 41,
  "photoUrl": "/api/v1/problem-reports/7/photo", "reportedAt": "2026-11-05T07:15:00Z",
  "createdAt": "2026-11-05T07:20:11Z", "isDemo": true }
```

#### `GET /problem-reports`

Any role: farmers see their own, agents their cooperative's, officers and admins all. Takes `plotId`, `claimId`, `page` and `size`. `GET /problem-reports/{id}/photo` returns the image.

#### `GET /agent/farmers`

`AGENT`. Returns **200** with a list (not a page) of the farmers in your cooperative, those needing attention first:

```json
[ { "farmerId": 9001, "fullName": "Demo Farmer One", "phone": "+250780000001", "plotsCount": 3,
    "pendingVerificationCount": 0, "atRiskCount": 1, "openClaimsCount": 1, "isDemo": true } ]
```

#### `POST /agent/plots/{id}/verify`

`AGENT` of the farmer's cooperative (question 8: admins may get this later), never the plot's own farmer. **Multipart form** (it was JSON before 2026-11-11):

| Field | Required | Notes |
|---|---|---|
| `decision` | yes | `ACTIVE` (verify) or `REJECTED` |
| `confirmedPlantingDate` | no | Corrects the farmer's date (§2.2 #10) |
| `rejectionReason` | to reject | Max 500 |
| `photo` | to verify | Taken at the plot **in the app** (JPEG or PNG) |
| `photoLat`, `photoLng`, `photoAccuracyM`, `photoTakenAt` | to verify | Recorded by the app when the photo was taken. Never typed in or defaulted. |

Verifying needs the plot at `PENDING_VERIFICATION`: registration complete and no open flag. The photo must be taken inside the walked boundary, or within the `verification_photo_max_distance_m` limit once that is set. The plot becomes `FIELD_VERIFIED` and `ACTIVE`. Rejecting works from any status before the check.

Returns **200** with the plot. The farmer gets `PLOT_VERIFIED` or `PLOT_REJECTED`, `plot_verifications` keeps the check, and `audit_log` records it.

| Status | Code | When |
|---|---|---|
| 400 | `PLANTING_DATE_REQUIRED` | Verifying a plot with no planting date and none sent |
| 400 | `PLANTING_DATE_INVALID` | The date is in the future, or outside a confirmed season |
| 400 | `REJECTION_REASON_REQUIRED` | Rejecting without a reason |
| 400 | `VERIFICATION_PHOTO_REQUIRED` | Verifying without a photo |
| 400 | `VERIFICATION_LOCATION_REQUIRED` | Verifying without the photo's location and time, or with a time in the future |
| 400 | `VERIFICATION_PHOTO_TOO_FAR` | The photo was taken too far from the walked boundary |
| 403 | `VERIFIER_IS_FARMER` | The agent is the plot's farmer |
| 404 | `PLOT_NOT_FOUND` | Not a plot of your cooperative |
| 409 | `PLOT_NOT_PENDING` | Already checked |
| 409 | `PLOT_NOT_READY` | Verifying a plot that is `DRAFT`, `FLAGGED` or `DISPUTED` |

#### `POST /agent/field-visits`

`AGENT`, plots of your cooperative. Multipart form: `plotId`, `plantCondition` (`GOOD`, `STRESSED`, `SEVERE_LOSS`, `FAILED`), `observedCause` (`WATER_DEFICIT`, `EXCESS_WATER`, `NUTRIENT`, `OTHER`), `growthStage` (`EMERGENCE`, `VEGETATIVE`, `FLOWERING`, `GRAIN`, `MATURITY`), `visitedAt` (required, not in the future), and optionally `moistureChecked`, `standDensityChecked`, `farmerPresent`, `notes`, `lat` + `lng` (both or neither), `clientId`, `photo`.

**Send `lat` and `lng` only from a real GPS fix.** Never send a default location when the phone has none: leave them out.

Returns **201** with the visit (`checklist` holds the three ticks). If the claim was waiting for this visit (`FIELD_VISIT_REQUESTED`), officers get `FIELD_VISIT_RECORDED`. The claim status itself only changes when an officer decides.

#### `GET /field-visits`

`AGENT` (your cooperative), `OFFICER` and `ADMIN`; farmers get **403**. Takes `plotId`, `claimId`, `page` and `size`. `GET /field-visits/{id}/photo` returns the image.

### Run analysis (admin)

`ADMIN` only. The "Run analysis" button (§10.1): the backend asks the ML service to analyse plots one at a time. The ML service posts each result to `/internal/ml/results`, where it is saved and the claim trigger runs. Running it again is safe: the same result changes nothing, and no second claim is made.

#### `POST /admin/analysis-runs`

Body (all optional):

| Field | Notes |
|---|---|
| `source` | `GEE` (real satellite data, the default) or `SAMPLE` (the simulated sample, demo plots only) |
| `plotIds` | Plots to analyse. For `GEE`, leave out to analyse every eligible real plot. Required for `SAMPLE`. |
| `asOf` | Last day of data to include. Defaults to today in Rwanda. Not in the future. |

Returns **202** at once with the run (status `RUNNING`). Plots that can't be analysed with that source are listed as `SKIPPED` with an `errorCode`:

| `errorCode` | Why |
|---|---|
| `PLOT_NOT_ACTIVE` | Not verified yet. Monitoring starts after verification (§2.2 #4), demo plots included. |
| `PLOT_NOT_CONFIRMED` | `GEE` on a plot whose location and planting date aren't confirmed |
| `DEMO_PLOT` | `GEE` on a demo plot: real data is never stored on placeholders |
| `REAL_PLOT` | `SAMPLE` on a real plot: sample data never goes on real plots |
| `NO_PLANTING_DATE` | No planting date |
| `NO_SEASON` | No covered or pending enrollment, so the season type (A or B) is unknown |

There is **no fallback**: if Earth Engine fails, the plot fails; it is never filled with sample data.

| Status | Code | When |
|---|---|---|
| 400 | `PLOT_IDS_REQUIRED` | `SAMPLE` without `plotIds` |
| 404 | `PLOT_NOT_FOUND` | A plot in `plotIds` doesn't exist |
| 409 | `ANALYSIS_ALREADY_RUNNING` | Another run is still going. One run at a time. |

#### `GET /admin/analysis-runs` and `GET /admin/analysis-runs/{id}`

The list is a page of runs, newest first (`items` is `null`). One run includes `items`, one per plot:

```json
{ "id": 3, "source": "SAMPLE", "status": "DONE", "triggerType": "MANUAL", "requestedBy": 9005, "asOf": null,
  "plotsTotal": 2, "plotsDone": 1, "plotsFailed": 0, "plotsSkipped": 1,
  "createdAt": "…", "startedAt": "…", "finishedAt": "…",
  "items": [
    { "plotId": 9001, "plotName": "Demo plot 1", "status": "DONE", "attempts": 1, "errorCode": null,
      "error": null, "mlRunId": 13, "finishedAt": "…" },
    { "plotId": 9004, "plotName": "Demo plot 4", "status": "SKIPPED", "attempts": 0,
      "errorCode": "PLOT_NOT_ACTIVE", "error": "Only verified plots are analysed.", "mlRunId": null, "finishedAt": "…" } ] }
```

- Run `status`: `QUEUED`, `RUNNING`, `DONE` (finished; some plots may have failed), `FAILED` (every plot failed) or `INTERRUPTED` (the backend restarted mid-run).
- A plot is retried once after an ML-service or network failure (`ML_SERVICE_ERROR`). A refusal, such as the backend rejecting the result, is not retried (`REFUSED`). `error` has the detail.
- `triggerType` is `MANUAL`, or `SCHEDULE` when the optional 5-day schedule started it (off unless `RINDA_ANALYSIS_CRON` is set).
- Each run is in `audit_log` as `ANALYSIS_RUN_STARTED`.

### Officer dashboard

`OFFICER` and `ADMIN`. The web dashboard's main screens (§1, §10.1). Decisions use the claim actions above.

#### `GET /officer/claims`

The claims queue, **needs-attention first**: `REVIEW_REQUESTED`, then `UNDER_REVIEW`, `FIELD_VISIT_REQUESTED`, `APPROVED` (unpaid), then closed claims; oldest trigger date first within each. Filters: `status` (comma-separated list), `flag` (one review flag), `demo` (`true` or `false`), `cooperativeId`, `q` (plot name, farmer name or phone), `page`, `size`.

```json
{ "items": [ { "id": 41, "status": "UNDER_REVIEW", "triggerDate": "2026-11-29", "daysOpen": 3,
    "plotId": 9001, "plotName": "Demo plot 1", "areaHa": 4.9209, "farmerId": 9001, "farmerName": "Demo Farmer One",
    "farmerPhone": "+250780000001", "cooperativeName": "Demo Cooperative", "seasonCode": "DEMO",
    "reviewFlags": ["LOW_CONFIDENCE_FORECAST"], "problemReports": 1, "fieldVisits": 0, "openReview": false,
    "payoutRate": null, "amount": null, "modelVersion": "0.5.0", "isDemo": true } ],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1 }
```

`daysOpen` is `null` once a claim is closed (`PAID`, `FINAL`, `DECLINED`). Unknown `status` or `flag` values get **400**.

#### `GET /officer/claims/{id}`

The case file: everything for one decision in one call.

| Field | What |
|---|---|
| `claim` | The claim, as `GET /claims/{id}` (with the frozen evidence) |
| `farmer` | `id`, `fullName`, `phone`, `cooperativeName`, `isDemo` |
| `plot` | `id`, `name`, `areaHa`, `plantingDate`, `status`, `dataStatus`, `verifiedByName`, `isDemo` |
| `cover` | The enrollment the claim was made under: `status`, `seasonCode`, `sumInsured`, `premium`, `startDate`, `endDate`, or `null` |
| `payoutAccount` | `provider`, `msisdnMasked` (last 3 digits only), `verified`, or `null` when the farmer has none |
| `decisions` | Every decision, oldest first: `decision`, `reason`, `officerName`, `decidedAt`, `answeredReview` |
| `reviews` | Every review request: `message`, `status`, `answer`, `createdAt`, `answeredAt` |
| `problemReports`, `fieldVisits` | The plot's reports and visits, newest first (same shape as their own endpoints) |
| `history` | The claim's audit trail, oldest first: `action`, `actorName`, `actorRole`, `before`, `after`, `occurredAt` |

#### `GET /officer/summary`

For the dashboard home: `claimsByStatus` (counts), `awaitingDecision`, `openReviews`, `flaggedOpen` (open claims with a review flag), `approvedUnpaid` (approved but rates or the payout account are missing), `simulatedPaidTotal`, `plotsByStatus`, **`triggerValuesMissing`** (the trigger keys still empty; while any is listed, no claim can start), and `latestAnalysisRun`.

### Satellite refresh (admin)

#### `POST /admin/monitoring/refresh`

`ADMIN` only. Runs the weekly satellite refresh now, for every walked plot that needs a fetch (useful for the demo and for checking Earth Engine). No body. Returns **202** with `{ "jobsQueued": 3 }`; the fetches run in the background, and each plot's `GET /plots/{id}/monitoring` shows its `latestJob`. A plot with a fetch already queued or running is skipped, and so is a plot whose season ended (200 days after planting, TO_CONFIRM). A plot that never had a successful fetch gets a backfill again.

### Admin settings

`ADMIN` only. These are the values that drive decisions and money (§0 rule 2). They come from the team and the insurer and are entered here, never invented. **Every change is written to `audit_log`** with the value before and after.

| Endpoint | Body | Notes |
|---|---|---|
| `GET /admin/thresholds` | | Every decision value: `key`, `value` (null = not supplied yet), `status`, `description`, `updatedByName`, `updatedAt` |
| `PUT /admin/thresholds/{key}` | `value` (number or null), `status` (`TO_CONFIRM`, `CONFIRMED_FOR_DEMO`, `CONFIRMED`) | A null value can only be `TO_CONFIRM`. Only anomaly scores may be negative. The ML service reads the new value on its next run. Audited as `THRESHOLD_CHANGED`. |
| `GET /admin/seasons` | | Seasons |
| `POST /admin/seasons` | `code`, `type` (`A`, `B`), `startDate`, `endDate`, `dataStatus` (`TO_CONFIRM`, `CONFIRMED`) | **201**. A confirmed season needs both dates. A confirmed season turns on the planting-date window check for new plots (question 5). |
| `PUT /admin/seasons/{id}` | same | Audited as `SEASON_CHANGED` |
| `GET /admin/enrollments` | | Page of covers. Filters: `seasonId`, `plotId`. |
| `POST /admin/enrollments` | `plotId`, `seasonId`, `status` (`PENDING`, `COVERED`, `CANCELLED`), `sumInsured`, `premium`, `startDate`, `endDate` | **201**. Cover is set by the cooperative or insurer, not bought in the app (§2.2 #5). Only an `ACTIVE` plot can be `COVERED`. One per plot and season. Leave money fields null until the insurer confirms them. |
| `PUT /admin/enrollments/{id}` | same (same plot and season) | Audited as `ENROLLMENT_CHANGED` |
| `PUT /admin/claims/{id}/payout-rate` | `payoutRate` (above 0, at most 1) | The insurer's rate for this claim's loss level, entered from their table (§3.7). Returns the claim. Not allowed on `PAID`, `FINAL` or `DECLINED` claims. The amount is worked out at payout as sum insured × rate. |
| `GET /admin/audit-log` | | Page, newest first. Filters: `entity`, `entityId`, `action`, `actorId`. |
| `GET /admin/users` | | Page of accounts. Filters: `role`, `q` (name or phone). |
| `POST /admin/users` | `phone`, `fullName`, `role` (`AGENT`, `OFFICER`, `ADMIN`), `cooperativeId` (required for agents), `language` | **201**. Sign-up only ever creates farmers (§2.2 #3); staff are created here and log in with the usual phone code. |
| `PUT /admin/users/{id}/status` | `status` (`ACTIVE`, `SUSPENDED`) | Suspending ends every session at once; the access token in use stops within 15 minutes. You can't suspend yourself. Accounts are never deleted, so decisions keep their author. |
| `POST /admin/cooperatives` | `name`, `district`, `sector` | **201**. It then appears in `GET /auth/cooperatives` for sign-up. |

| Status | Code | When |
|---|---|---|
| 400 | `THRESHOLD_INVALID` | Negative where not allowed, or a confirmed status with no value |
| 400 | `SEASON_DATES_REQUIRED`, `SEASON_DATES_INVALID`, `ENROLLMENT_INVALID` | Missing or reversed dates, or changing a cover's plot or season |
| 400 | `COOPERATIVE_REQUIRED`, `COOPERATIVE_NOT_FOUND`, `PHONE_INVALID` | Creating a user |
| 404 | `THRESHOLD_NOT_FOUND`, `SEASON_NOT_FOUND`, `ENROLLMENT_NOT_FOUND`, `PLOT_NOT_FOUND`, `CLAIM_NOT_FOUND`, `USER_NOT_FOUND` | |
| 409 | `SEASON_EXISTS`, `ENROLLMENT_EXISTS`, `COOPERATIVE_EXISTS`, `PHONE_IN_USE` | Already exists |
| 409 | `PLOT_NOT_ACTIVE` | Covering a plot that isn't verified |
| 409 | `CLAIM_CLOSED_FOR_RATE` | Setting a rate on a closed claim |
| 409 | `CANNOT_CHANGE_SELF` | Suspending your own account |

### ML service health (admin)

Part of "Run analysis" above.

#### `GET /admin/analysis-runs/ml-health`

Whether the ML service is reachable, its model version, and whether Earth Engine is authorised: `{ "reachable": true, "status": "ok", "modelVersion": "0.5.0", "gee": { "projectId": "rinda-ai", "authorised": true } }`. When it's down: `{ "reachable": false, "detail": "…" }`.

## 4. Demo accounts

Available with the `dev` profile. Log in with the code from the backend log.

| Phone | Role | Data |
|---|---|---|
| `+250780000001` | `FARMER` | Plots 9001 to 9003, all `ACTIVE` and covered |
| `+250780000002` | `FARMER` | Plot 9004, `PENDING_VERIFICATION` |
| `+250780000003` | `AGENT` | Sees all four plots |
| `+250780000004` | `OFFICER` | Sees all plots |
| `+250780000005` | `ADMIN` | Sees all plots |

The demo plots are placeholder locations, not farms. Plot 9001 can hold the simulated satellite sample (`README.md` step 9).

## 5. Planned endpoints

**None of these exist yet, so calling them returns 404 `NOT_FOUND`.** The fields are drafts for planning screens and the SQLite tables. Build against them only behind an interface you can adjust. This section will move to section 3 as each endpoint is built.

### Account

| Endpoint | Draft |
|---|---|
| `PUT /me` | Change name or language |
| `POST /me/consent` | Records `version`. The server sets `acceptedAt`. |
| `DELETE /me` | Deletion request, following §2.2 #11 |

The planned onboarding shows language and consent **before** login, but these endpoints need a token. Keep both choices locally, and send them right after login.

### Plot data

Every model result will include `modelVersion`, a confidence and a data quality note, as §0 and §3 require.

| Endpoint | Draft fields |
|---|---|

Display rules for these:

- The chart title is "Crop health (NDVI)", plotted against the normal band at the same days after planting (§2.2 #8).

### Enrollment

| Endpoint | Draft |
|---|---|
| `POST /plots/{id}/enrollment-request` | Not in the MVP (§10.1) |

### Officer and admin

Built: see "Officer dashboard" and "Admin settings" in section 3.
