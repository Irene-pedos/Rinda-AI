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
  "id": 9001,
  "farmerId": 9001,
  "name": "Demo plot 1",
  "crop": "MAIZE",
  "boundary": {
    "type": "Polygon",
    "coordinates": [[[30.1, -1.95], [30.102, -1.95], [30.102, -1.948], [30.1, -1.948], [30.1, -1.95]]]
  },
  "areaHa": 4.9209,
  "plantingDate": null,
  "status": "ACTIVE",
  "dataStatus": "TO_CONFIRM",
  "isDemo": true,
  "createdAt": "2026-11-02T13:34:07.608495Z",
  "updatedAt": "2026-11-02T13:34:07.608495Z"
}
```

- `farmerId` is the farmer's user `id`.
- `plantingDate` is null only on seeded placeholder plots, so handle null.
- `status` is `PENDING_VERIFICATION`, `ACTIVE` or `REJECTED`. `ARCHIVED` plots are never returned.
- This `status` is the verification state. It isn't crop health, which will come from `GET /plots/{id}/status`.

A plot that doesn't exist and a plot this user can't see both return 404 `PLOT_NOT_FOUND`.

### `POST /plots`

`FARMER` only. Registers a plot, which starts as `PENDING_VERIFICATION`.

```json
{
  "name": "Hillside",
  "plantingDate": "2026-09-20",
  "boundary": {
    "type": "Polygon",
    "coordinates": [[[30.12, -1.95], [30.122, -1.95], [30.122, -1.948], [30.12, -1.948], [30.12, -1.95]]]
  }
}
```

- `name` is required, up to 80 characters.
- `plantingDate` is required, and it can't be in the future.
- `boundary` has one ring of 4 to 500 `[longitude, latitude]` points, and the last point must repeat the first. Holes aren't allowed.
- `crop` is optional. Only `MAIZE` is accepted, so don't send it and don't build a crop picker.

The server calculates `areaHa`. Don't send it. Returns **201** with the plot.

| Status | Code | When |
|---|---|---|
| 400 | `PLOT_GEOMETRY_INVALID` | Not a polygon, not closed, crosses itself, has holes, or has too few or too many points |
| 400 | `PLOT_OUTSIDE_RWANDA` | A point is outside Rwanda's bounding box |
| 400 | `PLOT_TOO_SMALL` | Below the minimum area |
| 400 | `PLOT_TOO_LARGE` | Above the maximum area |
| 400 | `PLANTING_DATE_INVALID` | In the future, or outside a confirmed season |
| 400 | `CROP_NOT_SUPPORTED` | `crop` is anything other than `MAIZE` |

The area limits and season dates are `TO_CONFIRM` and can change, so **don't copy them into the app**. Let the server reject the plot and use `detail` to show the limit. Today the minimum is 0.1 ha, there's no maximum, and season dates aren't enforced because none are confirmed. Overlap checks (§5.2) aren't built yet.

### `PUT /plots/{id}`

`FARMER`, own plots only. Always send the full body, as for `POST /plots`. Returns **200** with the plot.

| Plot status | What can change |
|---|---|
| `ACTIVE` | `name` only. Changing the boundary or date returns 409 `PLOT_LOCKED`. Send them unchanged. |
| `PENDING_VERIFICATION`, `REJECTED` | Everything. Changing the boundary or date sets the plot to `PENDING_VERIFICATION`. |

Validation errors are the same as `POST /plots`, plus `PLOT_NOT_FOUND`.

**Known limit:** seeded demo plots can't be edited. Their `plantingDate` is null, but `PUT` requires one, so the request fails with `VALIDATION_FAILED` or `PLOT_LOCKED`. Hide the edit action when `plantingDate` is null. This goes away when real plot details replace the placeholders.

### `DELETE /plots/{id}`

`FARMER`, own plots only. Returns **204** with an empty body. The plot disappears from the API, but the record is kept. A second delete returns 404 `PLOT_NOT_FOUND`.

| Status | Code | When |
|---|---|---|
| 409 | `PLOT_DELETE_DEFERRED` | The plot has active cover or an open claim. Explain that it can be deleted after the season or claim closes (§2.2 #11). |

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

`AGENT` of the farmer's cooperative (question 8: admins may get this later). Body: `decision` (`ACTIVE` or `REJECTED`), `confirmedPlantingDate` (optional; corrects the farmer's date, §2.2 #10), `rejectionReason` (required to reject, max 500). Returns **200** with the plot. The farmer gets `PLOT_VERIFIED` or `PLOT_REJECTED` (with the reason), and `audit_log` records the change.

| Status | Code | When |
|---|---|---|
| 400 | `PLANTING_DATE_REQUIRED` | Verifying a plot with no planting date and none sent |
| 400 | `PLANTING_DATE_INVALID` | The date is in the future, or outside a confirmed season |
| 400 | `REJECTION_REASON_REQUIRED` | Rejecting without a reason |
| 404 | `PLOT_NOT_FOUND` | Not a plot of your cooperative |
| 409 | `PLOT_NOT_PENDING` | Already checked |

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
