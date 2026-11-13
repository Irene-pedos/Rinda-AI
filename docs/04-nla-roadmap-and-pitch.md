# 04 — NLA Roadmap & Pitch Notes

## Constraint
We cannot connect to NLA databases without authorization, and the approval process is long. So Rinda is built to work **without** NLA now, then upgraded after the project is presented.

## Do not
- Scrape or automate NLA, Irembo, Amakuru (LIIP) or the Land Dashboard.
- Attempt to call NLA systems or use credentials that are not the farmer's own for their own lookups.
- Collect NIDs or UPIs for anyone without their consent.

## Roadmap (use in the pitch)
1. **Demo** (`DOCUMENT_SUPPORTED`): farmer-supplied e-title, GPS boundary walk, automatic checks, satellite monitoring.
2. **Pilot** (`FIELD_VERIFIED`): human verification at the plot; payouts allowed only here and above.
3. **Scale** (`NLA_VERIFIED`): NLA parcel data via an official agreement; walked points checked against NLA's polygon.

Key message: the design is already built for NLA. Moving to NLA means filling in the highest verification level, with no redesign. NLA data is the missing piece only for the last step.

## Honest limits to state in the pitch
- A GPS walk proves presence, not ownership. The exact "same cell, similar-size neighbour" case is only closed by NLA's polygon.
- Satellite checks are review flags, not proof of crop. They are near-real-time (days), not live.
- Soil moisture at plot level is not available from satellites; rainfall and vegetation indicators are used instead.

## Approaching NLA (after the presentation)
- NLA general contact (from lands.rw/home): phone 2142; Nyarugenge Pension Plaza, P.O. Box 433, Kigali; Mon–Fri, 9:00–17:00.
- Include: an institutional letter (e.g. from RP-Huye), a clear description of Rinda's purpose, what data is needed (parcel polygon by UPI, owner match), how data is protected, and the data protection basis.
- Known context: other systems already integrate with NLA data (Irembo, NIDA, Ubutaka), so partner access exists; no public API was found.
- Related government bodies likely involved (to confirm): RISA, NIDA.

## Open items before approaching NLA
- Confirm the data protection law and any required approvals (believed Law No. 058/2021; verify).
- Decide who the insurer or institutional partner is, since NLA is more likely to engage with a backed pilot.
- Prepare evidence from the pilot: how many plots registered, flags raised, verification results, and the comparison of walked area to document area.

## Sources
- NLA FAQ (UPI structure): https://www.lands.rw/updates/faqs
- NLA home: https://www.lands.rw/home
- NLA Land Use and Mapping: https://www.lands.rw/land-use-and-mapping
- Land Information Inquiry Portal (Amakuru login): https://amakuru.lands.rw/login
- FAO/AGRIS, Citizen-Centric Land Administration in Rwanda: https://agris.fao.org/search/en/records/675ac06d0ce2cede71d02ebd
- The New Times, technology and fraudulent land sales: https://www.newtimes.co.rw/article/21512/unicef-calls-for-measures-to-avert-child-deaths/amp
