# Open questions

Items the team must confirm. Until then, values that depend on them stay `TO_CONFIRM`; they must never be guessed. Numbering in the "Spec" column refers to `RINDA_PROJECT.md`.

## Blocking real results

| # | Question | Needed for | Spec | Status |
|---|---|---|---|---|
| 1 | Which district is the pilot, and what are the 3–5 real maize plot locations (boundaries or GPS points) and planting dates? | Demo plots, satellite extraction | §10.3, §12.3 | Open |
| 2 | What is the Google Cloud project ID registered for Earth Engine? | Real satellite data | §3.1, §10.3 | Open |
| 3 | What trigger thresholds should the demo use (X days, Y% yield, Z% rain)? | Claim trigger | §3.7, §10.3 | Open |

## Insurer rules

| # | Question | Needed for | Spec | Status |
|---|---|---|---|---|
| 4 | What are the covered perils, sum insured per hectare, premium or subsidy, and payout rates? | Coverage screen, payout amount | §3.7, §12.1 | Open |

## Season and plot rules

| # | Question | Needed for | Spec | Status |
|---|---|---|---|---|
| 5 | What are the exact Season A and Season B date windows for maize? | Planting date validation | §5.2, §12.2 | Open |
| 6 | What are the minimum and maximum plot area, and the maximum allowed overlap? | Plot validation | §3.2, §5.2, §12.5 | Open |
| 7 | How many clear pixels does a plot need (suggested 20)? | "Not enough data" rule | §3.2 | Open |
| 8 | Do agents only verify plots in the pilot, or admins too? | Plot verification | §12.7 | Open |

## Other

| # | Question | Needed for | Spec | Status |
|---|---|---|---|---|
| 9 | Should Kinyarwanda read-aloud use pre-recorded audio, or be dropped for now? | Android | §2.2, §12.6 | Open |
| 10 | Which confirmed past drought or flood seasons can be used for back-testing? | Validation | §3.8, §12.4 | Open |
| 11 | Will the demo run locally (laptop and phone on the same Wi-Fi) or on a free cloud host? | Deployment | §12.8 | Open |
| 12 | Who reviews and corrects the Kinyarwanda text? | Android | §12.9 | Open |
