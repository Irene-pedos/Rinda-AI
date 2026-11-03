# Rinda Android UI and UX guidelines

**For:** Agy (and anyone building Android screens). **Read with:** `RINDA_PROJECT.md` §2.2, §5.3 and §9, and `CLAUDE.md`.

The Android app was redesigned on 2026-11-03. Every new screen must look and behave like the screens that exist now. When this document and the spec disagree on behaviour, the spec wins; on look and feel, this document wins. If something is not covered, copy the closest existing screen and ask the team.

---

## 1. Who we design for

Most Rinda farmers have used a smartphone very little. Some read slowly, some read only Kinyarwanda, many have small, cheap phones with weak internet, and they often use the app outside in bright light. So:

1. **One screen, one job.** Each screen has one main action, and it is the big green button at the bottom.
2. **Show, then tell.** Every important thing has an icon and a short word. Long explanations go behind a "?" button.
3. **Say what it means and what to do.** Never show only a status. Show the status, then one sentence about what it means, then "What you can do".
4. **Guide step by step.** Flows longer than one form are wizards with "Step 1 of 3", a progress bar, and Back and Next buttons in the same place on every step.
5. **Never leave the user stuck.** Every empty list says why it is empty and offers the next action. Every error says what to do ("Check your internet and try again") and offers a retry button.
6. **Confirm what happened.** After sending something, show a success screen or message that says what happens next.
7. **Be honest.** Never show a status, a number or evidence that the server did not send (see §6).
8. **Big and calm.** Body text is 16sp, touch targets are at least 48dp (buttons 56dp), and cards are flat with lots of space.

---

## 2. Design tokens

Everything comes from resources. **Never write a hex colour, a raw `sp` size or a raw `dp` spacing value in a layout or in Java.** The only exceptions are the launcher icon files.

### 2.1 Colours (`res/values/colors.xml`, dark versions in `res/values-night/colors.xml`)

Use the token for its *role*, never because of its hue. Both files have the same names, so dark mode works automatically.

| Token | Role |
|---|---|
| `rinda_primary` / `rinda_on_primary` | Main buttons, selected states, links, the plot line on charts |
| `rinda_primary_container` / `rinda_on_primary_container` | Tinted cards ("Getting started", "Ask for a review"), icon badges, selected cards |
| `rinda_secondary` / `rinda_secondary_container` | Harvest gold: plot area, harvest forecast, tips (lightbulb) |
| `rinda_tertiary` / `rinda_tertiary_container` | Rain blue: rainfall, weather, satellite evidence |
| `rinda_error` / `rinda_error_container` | Destructive actions (delete, log out) and field errors |
| `rinda_bg` | Screen background (warm cream) |
| `rinda_surface` | Cards, bottom navigation |
| `rinda_surface_variant` | Info cards, "What you can do" box, neutral tags |
| `rinda_text_primary` / `_secondary` / `_tertiary` | Titles / body text / captions and meta |
| `rinda_outline` / `rinda_outline_strong` | Card outlines and dividers / outlined buttons and unselected chips |
| `rinda_hero_start`, `rinda_hero_end`, `rinda_on_hero`, `rinda_on_hero_muted`, `rinda_hero_overlay` | Only the green splash and the Home summary card |
| `status_*` and `status_*_bg` | Only through `StatusUi` (see §4.3). Never pick a status colour by hand. |
| `status_demo` / `status_demo_bg` | Only for "Demo" labels and notices about practice data and simulated payouts |
| `chart_*` | Only for charts |

### 2.2 Type scale (`res/values/styles.xml`)

Set `android:textAppearance`, never `textSize` + `textStyle` + `textColor` on each view.

| Style | Size | Use |
|---|---|---|
| `TextAppearance.Rinda.Display` | 30sp bold | One per screen: the greeting, the plot or claim name, big numbers |
| `TextAppearance.Rinda.Headline` | 24sp bold | Screen titles inside content, empty state titles, a status word |
| `TextAppearance.Rinda.Title` | 18sp medium | Card titles, list item titles, toolbar |
| `TextAppearance.Rinda.Subtitle` | 16sp medium | Row titles, small headings |
| `TextAppearance.Rinda.Body` | 16sp | Normal text. **Default for anything a farmer must read.** |
| `TextAppearance.Rinda.BodySmall` | 14sp | Second line of a card, helper sentences |
| `TextAppearance.Rinda.Label` | 14sp medium | Short labels next to icons |
| `TextAppearance.Rinda.Section` | 15sp medium | Section header above a group of cards ("What do you want to do?") |
| `TextAppearance.Rinda.Caption` | 13sp | Dates, data quality notes, "Step 1 of 3". **Nothing smaller than 13sp.** |

Mark screen and section titles with `android:accessibilityHeading="true"`.

### 2.3 Spacing, shape and size (`res/values/dimens.xml`)

- Use the 4dp grid: `spacing_xs` 4, `spacing_sm` 8, `spacing_md` 12, `spacing_base` 16, `spacing_lg` 20, `spacing_xl` 24, `spacing_xxl` 32.
- `screen_padding` (20dp) on the left and right of every screen; `card_padding` (16dp) inside cards; `card_gap` (12dp) between cards; `section_gap` (24dp) before a new section.
- Corners: `radius_md` 14dp (buttons, inputs, boxes), `radius_lg` 20dp (cards), `radius_xl` 28dp (dialogs, sheets).
- Touch: `min_touch_target` 48dp, `button_height` 56dp, `row_height` 64dp.
- Icons: `icon_sm` 18 (in pills), `icon_md` 24 (default), `icon_badge` 44 (round badge), `illustration` 160 (onboarding, empty states).
- Cards are **flat** (0dp elevation, 1dp outline). Only floating things over a map, the bottom bar and the FAB have elevation.

### 2.4 Icons

- All icons are 24dp white vectors in `res/drawable/ic_*.xml`, tinted where they are used (`app:tint`, `app:drawableTint`, `app:iconTint`). By default they take the secondary text colour.
- To add one, copy the template of any `ic_*.xml` file and paste a [Material Symbols](https://fonts.google.com/icons) path. Keep the white fill and `android:tint="?attr/colorControlNormal"`.
- `ic_rinda_sprout` (brand colours) and `ic_rinda_mark_light` (white, for green backgrounds) are the logo. Never tint them.
- Icon meanings are fixed, so learn them once and use them the same way everywhere: `ic_leaf` maize or plot, `ic_satellite` satellite data, `ic_weather_rain` rain, `ic_shield` insurance cover or claims, `ic_wallet` money, `ic_visit` field visit or agent, `ic_lightbulb` tip or what to do, `ic_help` "?" help, `ic_info` note, `ic_lock` fixed or private, `ic_calendar` dates, `ic_area` hectares, `ic_map_pin` location, `ic_offline` no internet.

---

## 3. Theme and default widgets

`Theme.Rinda` (`res/values/themes.xml`) sets the default style for buttons, cards, inputs, toolbars, chips, dialogs, bottom sheets, snackbars and the bottom navigation. **A plain `MaterialButton`, `MaterialCardView` or `TextInputLayout` already looks right.** Add a `style=` only for a different variant.

- Use `com.google.android.material.appbar.MaterialToolbar` with `app:navigationIcon="@drawable/ic_back"` (or `ic_close` for a flow the user can cancel). Never use `ic_chevron_right` as a back arrow.
- Never use `Widget.MaterialComponents.*` styles. They are the old library styles and look different.
- Activity transitions (slide and fade) come from the theme. Don't call `overridePendingTransition`.
- The splash uses `Theme.Rinda.Splash`. API-27-only window items go in `values-v27` / `values-night-v27`.

---

## 4. Components

Reuse these. If you need something new, build it once as a `view_*.xml` include or a `ui/common` helper, then reuse it.

### 4.1 Buttons

| Variant | How | When |
|---|---|---|
| Primary | `MaterialButton` (no style) | The one main action of the screen. Full width in the bottom bar. |
| Tonal | `style="@style/Widget.Rinda.Button.Tonal"` | A second, softer action (map Undo / Start again) |
| Outlined | `style="@style/Widget.Rinda.Button.Outlined"` | Back, Rename, secondary actions |
| Danger | `style="@style/Widget.Rinda.Button.Danger"` | Delete, Log out. Never the primary button. Always confirm with a dialog. |
| Text | `style="@style/Widget.Rinda.Button.Text"` | "See all", "Send a new code", "Skip" |
| Icon | `style="@style/Widget.Rinda.IconButton"` + `android:contentDescription` | "?" help buttons, map controls |

Give important buttons an icon (`app:icon`). Use the arrow at the end (`app:iconGravity="textEnd"`) for Next and Continue. Button text is a verb in sentence case: "Send me a code", "Add a plot", "Ask for a review". Never all caps.

### 4.2 Cards

| Variant | Style | Use |
|---|---|---|
| Default | none | Content cards and list items |
| Tinted | `Widget.Rinda.Card.Tinted` | Guidance: "Get started in 3 steps", "What happens next", "Ask for a review" |
| Info | `Widget.Rinda.Card.Info` | Status + meaning box, fixed values (Crop: Maize), explanations |
| Demo | `Widget.Rinda.Card.Demo` | Demo notices and demo accounts |

A tappable card has `android:clickable="true"`, `android:focusable="true"` and a chevron (`ic_chevron_right`) at the end.

Cards with a title use `layout/view_card_header.xml` (icon + title + optional "?"). Fill it the way `PlotDetailActivity.header()` does.

### 4.3 Status: `StatusUi` (the only way to show a status)

`ui/common/StatusUi.java` maps every status to **an icon, a word, its colours and a plain-language meaning**:

- `forHealth(status)`: crop health (`HEALTHY`, `WATCH`, `AT_RISK`, `NOT_ENOUGH_DATA`; `null` means not checked yet). It also has a "What you can do" text (`todoRes`).
- `forPlot(PlotStatus)`: verification (Waiting for check / Verified / Not accepted / Archived).
- `forClaim(status)`: claim status.
- `forEnrollment(status)`: insurance cover.
- `confidenceLabel(context, confidence)`: LOW always says "a person will check".

Show it as a pill: `<TextView style="@style/Widget.Rinda.StatusPill" />`, then `StatusUi.applyPill(textView, spec)` in Java. Or as a round badge: `StatusUi.applyBadge(imageView, spec)`. Show `spec.meaningRes` under the pill wherever there is room.

**Never** write your own `switch` on a status string, and never set a status colour directly. If a new status appears, add it to `StatusUi` (with an icon, a word and a meaning) and use it everywhere.

### 4.4 Small parts

- **Tag** `Widget.Rinda.Tag`: small pill for role, confidence or plot name. `Widget.Rinda.Tag.Demo` is the "Demo" label (text is built in).
- **Icon badge** `Widget.Rinda.IconBadge`: round tinted icon at the start of a card or row.
- **Detail row** `layout/view_detail_row.xml`: icon, label on the left, value on the right (review screens, sum insured).
- **Settings row** `layout/view_settings_row.xml`: badge, title, current value, chevron (Profile).
- **Icon point** `layout/view_icon_point.xml`: badge + bold title + one sentence (consent, "What happens next").
- **Numbered step** `layout/view_step_row.xml`: "1  Add your plot…" (Getting started).
- **Tracker step** `layout/view_tracker_step.xml`: vertical progress (claim progress). Painted by `ClaimDetailActivity.paint()`: done = check on green, current = clock, failed = cross, to do = lock.
- **Action tile** `layout/view_action_tile.xml`: big Home shortcut, 2 per row.
- **Empty state** `layout/view_empty_state.xml`: include it in every list and fill it with `EmptyState.bind(...)`.
- **Help sheet**: `InfoSheet.show(context, titleRes, bodyRes)` or `InfoSheet.bind(button, titleRes, bodyRes)`. Help text lives in `help_*` strings.
- **Bottom bar**: `<LinearLayout style="@style/Rinda.BottomBar">` at the bottom of the screen, holding a `LinearProgressIndicator` (`id progressBar`, hidden) and the primary button.

### 4.5 Inputs

- `TextInputLayout` (default style) + `TextInputEditText` with `style="@style/Rinda.InputText"` (18sp, 56dp).
- Always set a `hint` (the label), and for anything unclear a `helperText` that says what to write. Add a `startIconDrawable` that shows what the field is for.
- Use `placeholderText` for examples ("e.g. Field by the river").
- Dropdowns use `Widget.Rinda.TextInput.Dropdown` + `MaterialAutoCompleteTextView` (see `activity_signup.xml`). Never use a plain `Spinner`.
- Show errors on the field (`til.setError(...)`), never in a Toast. Validate every field at once and show all errors together.
- Dates use a date picker; the field is not typed into. Show dates with `DateTimeUtils.formatDate()` (e.g. "Sep 20, 2026"), never as `2026-09-20`.

### 4.6 Feedback (`ui/common/Ui.java`)

| Need | Use |
|---|---|
| Short confirmation ("Plot name saved") | `Ui.toast(view, R.string.x)` (a Snackbar) |
| Error that can be retried | `Ui.error(view, getString(R.string.error_network_action), this::retry)` |
| A decision, or a consequence to explain | `Ui.dialog(context)` (rounded Material dialog). Title is a question ("Delete this plot?"), the message says what will happen, and the buttons are verbs ("Delete" / "Cancel"). |
| Writing a message (review, report) | A bottom sheet, like `sheet_ask_review.xml` |
| Loading | `LinearProgressIndicator` in the bottom bar, plus a disabled button. For lists, `SwipeRefreshLayout` with `R.color.rinda_primary`. |
| No internet | Nothing: `HomeActivity` already shows the offline banner. Keep showing cached data. |

Don't use `android.widget.Toast` (except right before `finish()`) or `android.app.AlertDialog`.

---

## 5. Words (copy)

All text goes in `res/values/strings.xml`, with a draft in `res/values-rw/strings.xml` when possible (marked "DRAFT – needs native speaker review"). Never type text into a layout or a Java string.

**Rules**
- Short sentences (under 15 words), everyday words, active voice, and say "you".
- Say what happens and what to do next: "Your cooperative agent checks the boundary. When it is verified, satellites start watching your maize."
- No internal codes or jargon on screen: never `PENDING_VERIFICATION`, `DAP`, `p10`, `NDVI anomaly`, `HTTP 409`. If a technical word is needed (NDVI), explain it with a "?" help sheet.
- Never blame the user. Say "This code is wrong or too old. Check the SMS or ask for a new code", not "Invalid input".
- Sentence case everywhere ("Add a plot", not "Add A Plot").
- Numbers: use `getString(R.string.x, value)` with format arguments. Areas are "4.92 ha", money is "15,000 RWF", and percentages are whole numbers.
- **Never name a pest or disease as a cause** (satellites cannot see them). Never promise money ("you will get"), and call payouts "simulated".

**Naming**: `screen_thing` (`register_step_draw`), `btn_*` for buttons, `error_*` for errors, `help_*_title` / `help_*_body` for help sheets, `*_meaning` and `*_todo` for status explanations, `cd_*` for content descriptions.

When you add a string that has no Kinyarwanda draft yet, lint lists it as a `MissingTranslation` warning. That's expected (question 12 in `OPEN_QUESTIONS.md`).

---

## 6. Honesty rules in the UI (from the spec, never cut)

| Rule | In the UI |
|---|---|
| Never guess a status (§3) | If there is no data, show `StatusUi.forHealth(null)` ("Not checked yet") or `NOT_ENOUGH_DATA`. Never default to Healthy. Unknown status values become "not enough data". |
| Every ML result has confidence and a data note (§3) | Show the confidence tag and the `dataQualityNote` next to every health status and forecast. |
| Forecasts are ranges (§3.6) | "70% – 90% of a normal harvest", labelled as a prototype estimate. Never one number, never tonnes. |
| Money values come only from the server (§0 rule 2) | `null` amount, rate or sum insured shows "Not decided yet" / "Not confirmed yet", never 0 and never an example number. Never make up season codes or dates. |
| Claim status comes from the server | Never change a claim status on the phone. After an action, reload from the server. If a request fails, say it was not sent. |
| Health ≠ claims (§2.2 #7) | Health alerts open Plot detail; claim updates open Claim detail (`AlertRouter`). "At risk" is never shown as a claim. |
| Claims start only by trigger (§2.2 #1) | No "Make a claim" button anywhere. "Report a problem" is evidence and says so (`report_disclaimer`). |
| Demo data is labelled (§0 rule 4) | Every screen showing `isDemo` data shows the Demo tag or `Card.Demo` notice. |
| No real money (§0 rule 5) | Any payout screen shows `claim_simulated_notice`. |
| Maize only | Crop is a fixed "Maize" with a lock icon. Never a picker. |
| Roles from the account (§2.2 #3) | Never show agent screens or a role switch to farmers. Choose screens by `user.getRole()`. |
| Never invent evidence | If the evidence snapshot is missing, show `evidence_unavailable`. |

---

## 7. Screen templates

### 7.1 Detail screen (Plot detail, Claim detail)

```
LinearLayout (vertical)
├─ MaterialToolbar (ic_back, title, Demo tag at the end if isDemo)
└─ SwipeRefreshLayout (weight 1)
   └─ NestedScrollView
      └─ LinearLayout (paddingStart/End = screen_padding, animateLayoutChanges=true)
         ├─ Display title + meta row (icon + caption)
         ├─ Card.Info: StatusPill + meaning sentence
         ├─ Card (view_card_header with "?") …   ← most important first
         ├─ Card …
         ├─ Section header ("Manage plot")
         └─ Outlined button, then Danger button
```

### 7.2 Form or wizard (Register plot, Report a problem, Payout account)

```
LinearLayout (vertical)
├─ MaterialToolbar (ic_close, flow title)
├─ Step header: LinearProgressIndicator (max = steps) + "Step 1 of N" caption + Headline step title
├─ FrameLayout (weight 1) holding one container per step; only one is visible
│    (switch with Ui.reveal / GONE, like RegisterPlotActivity.goTo())
└─ Rinda.BottomBar: progressBar + [Back (Outlined, hidden on step 1)] [Next (Primary, weight 2)]
```

- The system back button goes to the previous step (`OnBackPressedCallback`).
- Next stays disabled until the step is complete, or shows field errors when tapped.
- The last step is a review of everything ("Check and send"), with a tinted "What happens next" card.
- After sending, show a success state in the same screen: a big check badge that pops in, a headline, one sentence, the new status pill and what happens next. The button becomes "Done" or "See my …".

### 7.3 List tab (Plots, Alerts, Claims)

```
SwipeRefreshLayout
└─ FrameLayout
   ├─ RecyclerView (padding = screen_padding, layoutAnimation="@anim/list_rise", clipToPadding=false)
   └─ <include layout="@layout/view_empty_state" />   ← filled with EmptyState.bind(...)
```

- Show cached Room data first (LiveData), then refresh from the API.
- Call `rv.scheduleLayoutAnimation()` the first time items arrive.
- If the list is not the direct child of `SwipeRefreshLayout`, set `setOnChildScrollUpCallback` (see `ClaimsFragment`).
- The empty state gives the reason and, where there is one, the next action ("Add my first plot").

### 7.4 Bottom sheet (help, ask for review)

Handle bar at the top, Headline title, Body text, content, primary button, then a Text "Cancel" button. Open it expanded (`STATE_EXPANDED`, `setSkipCollapsed(true)`).

---

## 8. Motion

Motion explains what happened. Keep it short (200–300 ms) and use the same few patterns:

- Screens: theme slide + fade (automatic).
- Tabs: fade (`HomeActivity.loadFragment`).
- Content arriving from the network: `Ui.reveal(view)` (fade in).
- Lists: `@anim/list_rise` (items rise in one after another).
- Success: the check badge scales up with an overshoot, plus a light haptic (`HapticFeedbackConstants.VIRTUAL_KEY`).
- Taps on the map: haptic feedback for each corner.
- Charts: `animateX(500)` / `animateY(500)` once on load.

No looping animations, no motion that delays the user, and no effects just for decoration.

---

## 9. Accessibility checklist

- [ ] Touch targets ≥ 48dp; main buttons 56dp.
- [ ] Status shown with icon **and** word (`StatusUi`), never colour alone. Unread alerts use a dot **and** bold text.
- [ ] Every meaningful image has `android:contentDescription`; decorative ones have `android:importantForAccessibility="no"`.
- [ ] Icon-only buttons have a `contentDescription` (`@string/cd_help`, `@string/cd_back`).
- [ ] Titles have `accessibilityHeading="true"`; text that changes (status, step title) has `accessibilityLiveRegion="polite"`.
- [ ] Text is at least 13sp, and the screen still works at the largest font size (scrolls, no cut-off text, `maxLines` + `ellipsize` on single-line titles only).
- [ ] Works in dark mode (only tokens used) and in Kinyarwanda (longer words: let text wrap).
- [ ] Inputs have a hint (label), and `autofillHints` for phone, name and SMS code.

---

## 10. Screens still to build (spec §9), and how

Build each one from the templates in §7 and the components in §4. Endpoints marked as drafts in `docs/API.md` may not exist yet: build the screen, show the server's answer honestly, and never fake success.

| Screen | Template | Key UX points |
|---|---|---|
| **Report a problem** (4 steps) | Wizard 7.2 | 1 choose plot (cards), 2 "What do you see?" as big icon cards (dry leaves, yellow leaves, flooding, other; **no pest or disease names as causes**), 3 note + optional photo + "Ask an agent to visit" switch, 4 review. Show `report_disclaimer` on step 1 and the review: it is evidence and never starts a claim. Offline: save to the `outbox` and say "Saved. It will be sent when you are online." Replace the Home "Help" link with a "Report a problem" tile when this exists. |
| **Payout account + OTP** | Wizard 7.2 | Provider as two big cards (MTN MoMo, Airtel Money), number field with +250 prefix, then the OTP screen pattern from `activity_verify_otp.xml` (auto-submit, 60 s resend countdown). Explain that the account name must match the farmer's name. Changing the number during an open claim needs re-verification (§2.2 #12). Always show `payout_simulated_notice`. |
| **Coverage / enrollment** | Detail 7.1 | One card per plot with `StatusUi.forEnrollment` pill, dates and sum insured only from the server. List only covered perils the insurer confirmed (§2.2 #6). Enrollment "request" is not in the MVP. |
| **Crop stress detail** | Detail 7.1 | Status + meaning + likely cause + confidence + "needs field visit". Opened from the health card. |
| **Harvest forecast**, **Weather & rain** | Detail 7.1 | Bigger versions of the plot detail cards, with the help sheets. |
| **Edit plot** | Wizard 7.2 (2 steps) | Only the name can change once verified (`error_plot_locked`). Say this before the user starts. |
| **Notification settings**, **Edit profile** | Settings rows (`view_settings_row`) | Save on change, confirm with `Ui.toast`. |
| **Help & privacy** | Re-use `ConsentActivity` (read-only) and the `help_*` sheets | Already linked from Profile. |
| **Agent: My farmers** | List 7.3 | Only for `Role.AGENT` (role from the account). Each farmer card shows plots waiting for check as a count tag. |
| **Agent: Verify plot** | Detail 7.1 + map | Map with the boundary, area, planting date (agent can correct it, §2.2 #10), "Verify plot" primary and "Reject boundary" danger, each confirmed by a dialog. |
| **Agent: Field visit (+ success)** | Wizard 7.2 | Checklist steps with big choice cards (condition, what you saw, growth stage), photo, location. Offline-first via the `outbox`. |
| **States** | — | Offline banner exists. "Not enough satellite data" = `StatusUi.forHealth("NOT_ENOUGH_DATA")`. Empty = `EmptyState`. Errors = `Ui.error` with retry. Log out and delete = `Ui.dialog` with the consequence explained. |

---

## 11. Before you commit a screen

- [ ] Built from a template in §7, using only components from §4.
- [ ] No hex colours, raw `sp`/`dp` values, or hard-coded text (lint `HardcodedText` is clean).
- [ ] One primary button, at the bottom. Destructive actions are Danger buttons with a confirm dialog.
- [ ] Every status uses `StatusUi`, with its meaning shown.
- [ ] Empty, loading, offline and error states are all handled.
- [ ] Demo data is labelled; money values and statuses come only from the server.
- [ ] Checked in light and dark mode, in English and Kinyarwanda, and at the largest font size.
- [ ] `assembleDebug` and `lintDebug` pass (JDK 17, see `CLAUDE.md`).

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\gradlew.bat assembleDebug lintDebug
```

## 12. Where things are

| Path | What |
|---|---|
| `res/values/colors.xml`, `res/values-night/colors.xml` | Colour tokens (light, dark) |
| `res/values/styles.xml` | Type scale and component styles |
| `res/values/themes.xml` (+ `-night`, `-v27`, `-night-v27`) | App theme and splash theme |
| `res/values/dimens.xml` | Spacing, sizes, radii |
| `res/anim/` | Screen, fade and list animations |
| `res/layout/view_*.xml`, `sheet_*.xml`, `dialog_*.xml` | Reusable parts |
| `ui/common/StatusUi.java` | Status → icon, word, colours, meaning |
| `ui/common/InfoSheet.java` | "?" help sheets |
| `ui/common/EmptyState.java` | Empty states |
| `ui/common/Ui.java` | Snackbars, dialogs, online check, fade-in |
| `ui/alerts/AlertRouter.java` | Where each alert opens, and how it looks |
