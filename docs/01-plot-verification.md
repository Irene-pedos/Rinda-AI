# 01 — Plot Registration & Verification

## Goal
Let a farmer register the plot they planted so that the system can trust it to an honest, stated degree, without connecting to NLA databases.

## Verification levels (stored on every plot)

| Level | How the plot reaches it | Allowed use |
|---|---|---|
| `SELF_DECLARED` | Farmer typed a UPI only | Demo only |
| `DOCUMENT_SUPPORTED` | UPI plus uploaded e-title / portal screenshot, GPS walk done, automatic checks passed | Learning version, onboarding |
| `FIELD_VERIFIED` | Above plus human verification at the plot (agent / cooperative) with geotagged photo | Pilot; **minimum level for payouts** |
| `NLA_VERIFIED` | Matched against NLA data (future) | Production |

Rules:
- A plot can only move up a level when its checks pass; flagged or disputed plots stay put until resolved.
- The UI and the insurer dashboard must display the level next to every plot.

## Farmer flow (replaces free drawing)
1. Farmer enters **UPI** (and NID if needed for the account; see data handling below).
2. Farmer uploads their **e-title or a screenshot** of the plot details, obtained through their own access and with their consent. The system never contacts NLA systems.
3. System extracts from the document: UPI, owner name, plot size, land use, location text. Validate the UPI format. Check that the owner name on the document matches the farmer's account name.
4. Farmer goes to the plot and **walks its corners**; the app records GPS points and builds the polygon. **No free drawing anywhere.** The farmer may only describe the planted area inside the walked boundary.
5. Farmer declares: crop (maize), planting date, relationship to the plot (owner / family member / tenant).
6. Automatic checks run (below). Result: `DOCUMENT_SUPPORTED`, or flagged for review.
7. Human verification can raise the plot to `FIELD_VERIFIED`.

## GPS walk rules
- Record points with accuracy values; reject or ask to repeat when accuracy is poor (threshold: configurable, suggestion to tune with real tests).
- Detect and flag mock-location apps.
- Compute polygon area; keep the raw points.
- The planted area is a sub-polygon inside the walked boundary. Planted area, not parcel area, is what insurance needs, and one crop can cover part of a plot or several UPIs.

## Automatic checks and what they catch

| Check | Catches | Limits |
|---|---|---|
| Walked location lies inside the admin area encoded in the UPI (province / district / sector / cell) | Claims far from the real plot | Only as precise as the boundary files; needs cell-level boundaries (open question) |
| Walked area vs area on the document (flag if the difference exceeds about 10–15%, a suggestion to tune) | Plots of clearly different size | A neighbour with a similar size passes |
| Plot shape/orientation vs a sketch on the document | Different-shaped plots | Only if the document has a sketch (open question) |
| Overlap with polygons already in Rinda | Two accounts claiming the same plot | Only works if the real owner registered first |
| One active claim per UPI per season | Duplicate claims | None significant |
| Satellite view of the polygon vs declared crop (see file 02) | Empty or different-crop plots | A weak signal for small plots |
| OTP to the phone linked to the NID, if NIDA allows that check | Using someone else's identity | Availability unconfirmed |

## What cannot be solved without NLA (be honest in UI and pitch)
A farmer who walks the **neighbour's** plot in the same cell, with similar size, can pass the automatic checks. This is why:
- payouts need `FIELD_VERIFIED` (a human confirms the plot is the farmer's, with a geotagged photo taken at the plot);
- overlapping claims go to a dispute flow;
- the final fix is NLA's parcel polygon (point-in-polygon check of the walked points). Until then, the system must not claim to have proven ownership.

## Dispute flow
- When two claims overlap: mark both `DISPUTED`, pause any payout, request each side's e-title, and send to the verifier (agent). Do not auto-resolve.

## Tenants and relatives
- Many farmers are not the registered owner. Store `relationship_to_plot` = `OWNER` | `FAMILY_MEMBER` | `TENANT`.
- Non-owners need the owner's consent or a lease document. The exact mechanism is **undecided** (open question 6): make it a pluggable step.

## Declaration
- The farmer confirms the plot is theirs / they have the right to farm it, and that a false claim can cancel cover. This is a legal/terms control, not a technical one.

## Data handling
- Do not store the full NID if avoidable. If it must be stored, encrypt it. Keep only what is needed.
- Treat GPS tracks and documents as personal data. Restrict access, do not log them, and never put real data in tests or the repo.
- Get the test farmer's explicit consent before using their real plot.
- Verify the applicable data protection law (believed to be Law No. 058/2021) before launch.

## Backend abstraction (so NLA can be added later)
Define an interface and keep the rest of the system independent of its implementation:

```
LandRegistryProvider
  verifyPlot(upi, ownerIdentity) -> PlotRecord { upi, ownerName, areaM2, landUse, locationAdmin, polygon? , sourceLevel }
```
- `DocumentProvider` (now): builds the record from the uploaded document and the walked polygon. Max level: `DOCUMENT_SUPPORTED`.
- `NlaProvider` (later): real NLA data. Level: `NLA_VERIFIED`.
- Optional `MockProvider` for development, using **fake** data and clearly labelled as such.

## Testing with the real maize farmer (with consent)
1. Farmer gets their own e-title through their own access and uploads it.
2. Walk the plot boundary with the app.
3. Compare the computed area with the document area to see whether the suggested tolerance is realistic.
4. Never paste their NID or UPI into chats, issues, commits, or screenshots.
