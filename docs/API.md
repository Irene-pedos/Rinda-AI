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

### `GET /plots/{id}/baseline`

Returns **200** with the newest normal band saved for the plot:

```json
{
  "seasonType": "A",
  "version": "plot-history-v1",
  "binDays": 10,
  "dataOrigin": "GEE",
  "bins": [
    { "dapBin": 10, "p10": 0.2, "p50": 0.3, "p90": 0.4 },
    { "dapBin": 20, "p10": 0.3, "p50": 0.4, "p90": 0.5 }
  ]
}
```

- A bin covers `dapBin` up to, but not including, `dapBin + binDays`. Use `binDays` from the response, not a fixed 10.
- Bins with too few past readings are left out, so the list can have gaps. Don't draw the band across a gap.
- With no baseline yet, `bins` is empty and the other fields are null.

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
| `GET /plots/{id}/status` | `date`, `status`, `anomaly`, `rainPct`, `cause`, `confidence`, `explanation`, `modelVersion` |
| `GET /plots/{id}/forecast` | `date`, `lowPct`, `highPct`, `confidence`, `modelVersion` |

Display rules for these:

- `status` is `HEALTHY`, `WATCH`, `AT_RISK` or `NOT_ENOUGH_DATA`. `confidence` is `HIGH`, `MEDIUM` or `LOW`. Always show the status as icon plus word, never colour alone.
- `AT_RISK` isn't a claim. Show "Not enough satellite data yet" for `NOT_ENOUGH_DATA`, and never fill in a guess.
- The chart title is "Crop health (NDVI)", plotted against the normal band at the same days after planting (§2.2 #8).
- The forecast is a range compared with the plot's normal, labelled "Prototype estimate". Never show one number or a tonnage.
- Never show a specific pest or disease as a cause.

### Enrollment, alerts, claims and reports

| Endpoint | Draft |
|---|---|
| `POST /plots/{id}/enrollment-request` | Not in the MVP (§10.1) |
| `GET /plots/{id}/enrollment` | `status` (`PENDING`, `COVERED`, `CANCELLED`), `startDate`, `endDate` |
| `GET /notifications` | Page of `id`, `type`, `title`, `body`, `link`, `isRead`, `createdAt` |
| `POST /notifications/{id}/read` | Marks one as read |
| `GET /claims` | Page of claims. Farmers see their own. |
| `GET /claims/{id}` | `status`, `triggerDate`, `plotId`, the evidence snapshot, `modelVersion`, `isDemo` |
| `POST /claims/{id}/review-request` | `message` from the farmer |
| `POST /problem-reports` | Multipart: `plotId`, `reason`, `note`, `agentVisitRequested`, optional photo |
| `GET /payout-account` | `provider` (`MTN_MOMO`, `AIRTEL`), `msisdn`, `verified` |
| `POST /payout-account` | Sets the account, which then needs OTP verification |
| `POST /payout-account/verify` | Confirms it with the OTP |

- Notification `type` is `HEALTH`, `CLAIM` or `SYSTEM`. `HEALTH` opens plot detail and `CLAIM` opens claim detail (§2.2 #7).
- Claim `status` is `UNDER_REVIEW`, `FIELD_VISIT_REQUESTED`, `APPROVED`, `DECLINED`, `PAID`, `REVIEW_REQUESTED` or `FINAL`. Always take it from the server, never from local data.
- Claims have no create endpoint, and never will. The parametric trigger creates them. A problem report is evidence only and never creates a claim or payout.
- Amounts and payout rates can be null until the insurer confirms them. Show that they aren't confirmed, not zero. Payouts are simulated.

### Agent

| Endpoint | Draft |
|---|---|
| `GET /agent/farmers` | Farmers in the agent's cooperative, with their plot status |
| `POST /agent/plots/{id}/verify` | Confirms or rejects a plot, and can correct the planting date |
| `POST /agent/field-visits` | Multipart: `plotId`, checklist, `plantCondition`, `observedCause`, `growthStage`, photo, `lat`, `lng`, `visitedAt` |

### Officer and admin

| Endpoint | Draft |
|---|---|
| `POST /claims/{id}/decision` | `decision` (`APPROVED`, `DECLINED`) and a required `reason`. Only a human can do this. |

The claims queue, review answers, thresholds, seasons and audit log endpoints come later. Their paths aren't set yet (§8).
