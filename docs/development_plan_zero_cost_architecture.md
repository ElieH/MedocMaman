# Medication & Medical Follow-up System: Zero-Cost Technical Specification

**Target Platform:** Android (Kotlin + Jetpack Compose) — Sideloaded APK  
**Target Device:** Xiaomi Redmi (HyperOS / MIUI)  
**Backend & Watchdog:** Firebase Free Tier (Firestore, FCM) + Google Apps Script (Free Cron/Watchdog, Sheet Sync & Email Alerts)  
**Operating Cost:** $0.00 / month  

---

## 1. System Overview & Zero-Cost Architecture

This project builds a fail-safe medication reminder and medical follow-up system for a French-speaking elderly user on a Xiaomi Redmi smartphone, managed remotely by her son and daughter.

To achieve a **$0/month recurring cost** while preventing **silent failures** (e.g., Xiaomi's aggressive battery management killing the app, dead battery, or lost internet), the architecture replaces paid cloud telephony (Twilio), paid LLMs (Claude), and paid notification services (Pushover) with a native Android + Firebase Spark + Google Apps Script stack:

### Architecture Components
1. **Patient Android App (Kotlin / Compose):**
   - Schedules exact local alarms (`AlarmManager.setAlarmClock`).
   - Triggers a full-screen incoming call UI (`CallStyle` notification + `USE_FULL_SCREEN_INTENT`).
   - Speaks in French using on-device `TextToSpeech` (`Locale.FRENCH`) or pre-recorded family audio files.
   - Captures responses via **giant touch buttons** (Level 0) and on-device **Android `SpeechRecognizer` / offline keyword matcher** (Level 1) scanning for French keywords and emergency signs (**VITE**).
   - Sends a `RINGED` and `COMPLETED` heartbeat to **Firebase Firestore**.
   - **Offline Fallback:** If offline when an alarm triggers or an emergency occurs, sends a direct SMS to the children using Android's `SmsManager` (uses the phone's existing mobile plan).

2. **Caregiver Companion Mode (Same APK, Role Switch in Settings):**
   - Installed on the son's and daughter's phones.
   - Subscribes to Firebase Cloud Messaging (FCM) topic `caregiver_alerts`.
   - When an **IMMEDIATE** alert arrives, triggers a high-priority local alarm/siren on the children's phones that rings continuously until one child taps **"Acknowledge"** (replicating Pushover's $5 group-acknowledge feature for free via Firestore state sync).

3. **Zero-Cost Cloud Watchdog & Admin Backend (Google Apps Script + Google Sheets + Firebase Firestore):**
   - **Admin UI:** A shared Google Sheet used by the children to manage medications, schedules, Meuhedet prescription dates, and medical exams.
   - **The Watchdog (Apps Script Time-Driven Trigger running every 5 minutes):**
     - Syncs changes from the Google Sheet to Firestore.
     - Checks Firestore for any scheduled medication call that is `> 10 minutes` past due with no `RINGED` report (indicating the phone is off or Xiaomi HyperOS killed the app), or `> 35 minutes` with no `COMPLETED` status.
     - Sends high-priority FCM push alerts to the children's apps AND sends free emails via `MailApp.sendEmail()` with a 1-tap `tel:` link so the children can immediately call their mother.

---

## 2. Hard Safety & Medical Boundaries

The app is a reminder assistant and **must never act as a doctor**:
1. **No treatment modification:** Never suggest skipping, doubling, stopping, or rescheduling a dose.
2. **Missed dose rules:** Each medication has an `on_missed_instruction_fr` field populated strictly from doctor/pharmacist instructions. If empty, the app instructs the user *not* to take an extra dose and alerts the children.
3. **No diagnosing or reassuring:** If the user reports feeling unwell, the app never says "it's okay." It immediately triggers the safety check and alerts the children.
4. **Emergency Signs (VITE Protocol - Carotid Artery / Stroke Risk):**
   - **V**isage (Facial weakness/numbness)
   - **I**nertie d'un membre (Arm/leg weakness, especially on one side)
   - **T**rouble de la parole (Difficulty speaking or understanding, or sudden vision loss/blurriness, severe sudden headache, unexplained dizziness/fall)
   - **E**n urgence (Immediate action: halt normal script, instruct in French to call `101`, display giant red `101` button using `Intent.ACTION_DIAL` pre-filled with `tel:101`, and fire a **CRITICAL** alert to the children with her address).

---

## 3. Data Models (Firestore & Local Room Cache)

The Android app must maintain a local **Room SQLite database** mirroring Firestore so reminders work 100% offline.

### 3.1 `medications` Collection
```json
{
  "id": "med_morning_statin",
  "name_fr": "Atorvastatine",
  "dosage_fr": "Un comprimé",
  "meal_timing_fr": "après le petit-déjeuner",
  "visual_description_fr": "le petit comprimé rond et blanc",
  "image_url": "https://...",
  "local_image_res": "pill_white_round",
  "scheduled_times": ["08:00", "20:00"],
  "on_missed_instruction_fr": "Ne prenez pas de double dose. Attendez la prochaine prise.",
  "prescription_expiry_date": "2026-12-15",
  "stock_empty_date": "2026-12-10",
  "prescription_status": "ACTIVE", 
  "active": true,
  "updated_at": "2026-11-09T10:00:00Z"
}
```
*Note on `prescription_status`:* Enum of `ACTIVE`, `REMINDER_SENT_14D`, `REQUEST_OPENED_MEUHEDET`, `ESCALATED_7D`.

### 3.2 `call_logs` Collection (Daily Schedule & Heartbeats)
Document ID format: `YYYY-MM-DD_HH-mm_medId`
```json
{
  "id": "2026-11-11_08-00_med_morning_statin",
  "med_id": "med_morning_statin",
  "scheduled_timestamp": 1794376800000,
  "status": "PENDING", 
  "ringed_at": null,
  "completed_at": null,
  "attempt_count": 0,
  "outcome": null,
  "user_transcript": "",
  "alert_level": "NONE",
  "acknowledged_by": null
}
```
*Note on `outcome`:* Enum matching Section 4 of the spec:
- `TAKEN` (Weekly summary)
- `SNOOZED_15M` (Re-rings in 15m; if unconfirmed after 3 tries -> `TODAY` alert)
- `FORGOT` (Reads `on_missed_instruction_fr`; `WEEKLY` alert, or `TODAY` if >= 3 times/week)
- `OUT_OF_STOCK` (`TODAY` alert + checks prescription status)
- `REFUSED_OR_CONCERNED` (`TODAY` alert)
- `SIDE_EFFECTS_UNWELL` (Triggers VITE safety screen -> `IMMEDIATE` alert)
- `EMERGENCY_VITE` (Opens 101 dialer -> `CRITICAL` alert)
- `CONFUSED_OR_UNCLEAR` (`IMMEDIATE` alert)
- `NO_ANSWER_3_TRIES` (`IMMEDIATE` alert)
- `WATCHDOG_SILENT_FAILURE` (Phone didn't report `RINGED` within 10m -> `IMMEDIATE` alert)

### 3.3 `medical_exams` Collection
```json
{
  "id": "exam_carotid_duplex",
  "name_fr": "Échographie-Doppler des carotides",
  "name_he": "אולטרסאונד דופלקס של עורקי הצוואר",
  "state": "NEEDS_REFERRAL",
  "last_exam_date": "2026-01-15",
  "next_target_date": "2027-01-15",
  "state_updated_at": "2026-11-01"
}
```
*Note on `state`:* State machine: `NEEDS_REFERRAL` -> `HAS_REFERRAL` -> `APPOINTMENT_BOOKED` -> `EXAM_COMPLETED` -> `RESULTS_AT_DOCTOR` -> `NEXT_DATE_SET`.

---

## 4. Step-by-Step Implementation Plan for Antigravity

### Phase 1: Android Project Setup & Xiaomi Survival Core
1. **Initialize Project:**
   - Create a single-activity Android project using **Kotlin** and **Jetpack Compose** (`minSdk = 29`, `targetSdk = 34`).
   - Add dependencies: Firebase BOM (`firestore`, `messaging`), `androidx.room`, `androidx.core:core-telecom`, `coil-compose` (for pill images).
2. **Declare Permissions in `AndroidManifest.xml`:**
   - `USE_EXACT_ALARM` and `SCHEDULE_EXACT_ALARM`
   - `USE_FULL_SCREEN_INTENT`
   - `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MICROPHONE` (only started *after* user taps "Answer")
   - `POST_NOTIFICATIONS`
   - `RECEIVE_BOOT_COMPLETED`
   - `WAKE_LOCK` and `DISABLE_KEYGUARD`
   - `RECORD_AUDIO`
   - `SEND_SMS` (for offline fallback alerts)
   - `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
3. **Build the "Xiaomi System Health & Permissions Screen":**
   - Create a diagnostic screen accessible via a hidden long-press on the home screen title.
   - Programmatically check and display green/red status for:
     - `AlarmManager.canScheduleExactAlarms()`
     - `NotificationManager.canUseFullScreenIntent()`
     - `PowerManager.isIgnoringBatteryOptimizations()`
     - Notification channel importance (`IMPORTANCE_HIGH`).
   - Include a deep-link button to open Xiaomi's proprietary **Autostart** and **Other Permissions** settings (`miui.intent.action.APP_PERM_EDITOR`) and display a checklist for the family setup:
     1. Autostart = ON
     2. Battery Saver = No restrictions
     3. Show on Lock Screen = ON
     4. Display pop-up windows while running in background = ON
     5. Lock app in Recents = ON

### Phase 2: Alarm Engine & Simulated Incoming Call
1. **Exact Alarm Scheduler (`MedicationAlarmScheduler.kt`):**
   - Use `AlarmManager.setAlarmClock(AlarmClockInfo(triggerTime, showIntent), pendingIntent)`. `setAlarmClock` is mandatory because Android exempts it from Doze mode restrictions.
   - Register a `BootCompletedReceiver` listening for `BOOT_COMPLETED` and `QUICKBOOT_POWERON` to reschedule all alarms from the local Room DB immediately after reboot.
2. **Incoming Call Receiver & Full-Screen Activity (`IncomingCallActivity.kt`):**
   - When the alarm fires, acquire a partial `WakeLock` and immediately write `status = "RINGED"`, `ringed_at = now()` to Firestore (if online).
   - Post a `NotificationCompat.CallStyle.forIncomingCall(...)` notification attached to a Full-Screen Intent launching `IncomingCallActivity`.
   - Configure `IncomingCallActivity` with `setShowWhenLocked(true)` and `setTurnScreenOn(true)`.
   - Check `TelephonyManager` call state: if the user is currently on a real phone call, snooze the medication call by 5 minutes automatically.
   - Ring for 60 seconds with custom ringtone + vibration. If unanswered, schedule retry #2 (+10 mins) and retry #3 (+20 mins). After 3 missed tries (30 mins total), log `NO_ANSWER_3_TRIES` and fire an `IMMEDIATE` alert (via Firestore + SMS fallback if offline).

### Phase 3: French Conversation Flow & Safety Decision Tree
1. **Audio & Voice Engine (`FrenchVoiceHelper.kt`):**
   - Initialize Android `TextToSpeech` with `Locale.FRENCH`. Set speech rate slightly slower (`0.9f`) for clarity.
   - Route audio through the **speakerphone** (`AudioManager.MODE_IN_COMMUNICATION`, `isSpeakerphoneOn = true`) by default so she can hear clearly while looking at the pill image on screen.
2. **Deterministic Conversation State Machine (`CallViewModel.kt`):**
   - **Step 1 (Greeting & Pill Display):**
     - Speak: *"Bonjour [Prénom]. C'est l'heure de votre médicament : [Nom]. C'est [visual_description_fr] que vous voyez sur l'écran. [dosage_fr], [meal_timing_fr]. L'avez-vous déjà pris ?"*
     - Display giant buttons: **OUI, JE L'AI PRIS** (Green), **PAS ENCORE** (Blue), **J'AI UN PROBLÈME** (Orange), **PLUS TARD (15 MIN)** (Gray).
   - **Step 2 (Voice Keyword Recognition + Touch Fallback):**
     - Optionally activate Android `SpeechRecognizer` (`fr-FR`) when she taps "Answer".
     - Parse transcript locally using deterministic keyword sets (no paid LLM required):
       - **Emergency Keywords (VITE):** `["tourne", "vertige", "tombée", "faiblesse", "bras", "jambe", "visage", "parler", "vue", "voir", "mal à la tête", "poitrine", "étourdie"]` -> **Immediate transition to Step 4 (Emergency).**
       - **Affirmative Keywords:** `["oui", "pris", "c'est fait", "déjà"]` -> Log `TAKEN`, say *"Merci, bonne journée !"*, end call.
       - **Negative / Delay Keywords:** `["non", "pas encore", "oublié", "plus tard"]` -> Transition to Step 3.
   - **Step 3 (Clarification Screen - When "Pas encore" or "Problème" is selected):**
     - Ask: *"D'accord. Pouvez-vous le prendre maintenant, ou y a-t-il un problème ?"*
     - Display 4 clear French buttons:
       1. **Je le prends maintenant** -> Snooze 15 mins to verify (`SNOOZED_15M`).
       2. **J'ai oublié plus tôt** -> Read `on_missed_instruction_fr`, log `FORGOT`.
       3. **Je n'ai plus de boîtes** -> Log `OUT_OF_STOCK`, alert children `TODAY`.
       4. **Je ne me sens pas bien** -> Transition to Step 4 (Safety Check).
   - **Step 4 (VITE Safety Check & 101 Emergency Screen):**
     - Speak: *"Merci de me le dire. Je préviens votre fils et votre fille. Question importante : avez-vous soudain une faiblesse d'un côté du corps, du mal à parler, ou un problème de vue ?"*
     - Display **OUI (URGENCE 101)** (Red) and **NON, AUTRE CHOSE** (Orange).
     - If **OUI** (or if an emergency keyword was spoken):
       - Fire `CRITICAL` alert to children immediately (Firestore + SMS fallback with her home address).
       - Speak: *"Appelez immédiatement le 101. Appuyez sur le grand bouton rouge."*
       - Launch `Intent(Intent.ACTION_DIAL, Uri.parse("tel:101"))`.

### Phase 4: Meuhedet Prescription Renewal & Medical Exams Module
1. **Meuhedet Prescription Workflow:**
   - Every day at 10:00 AM, check local medications where `prescription_expiry_date - today <= 14 days` or `stock_empty_date - today <= 14 days`.
   - Trigger a friendly non-urgent French screen:
     - Shows a step-by-step visual guide in French explaining how to request a renewal in Meuhedet, or offers a button: **"Demander à mes enfants de renouveler"** (since the Meuhedet app is only in Hebrew/English).
     - Includes a button to launch the Meuhedet app (`context.packageManager.getLaunchIntentForPackage("org.meuhedet.android")`) and a button **"C'est fait"** (`REQUEST_OPENED_MEUHEDET`).
     - If unconfirmed after 3 days, repeats reminder. At 7 days before expiry, escalates to the children (`TODAY` alert).
2. **Medical Exam Tracker (Carotid Duplex & Blood Tests):**
   - Weekly check of `medical_exams`. If an exam is within 30 days of `next_target_date` and still in `NEEDS_REFERRAL` or `HAS_REFERRAL`, prompts the user or alerts the children so no exam stalls.

### Phase 5: Zero-Cost Cloud Watchdog & Google Sheet Sync (Google Apps Script)
Create a Google Sheet with 3 tabs: `Medications`, `Exams`, `Config_And_Contacts`. Open **Extensions > Apps Script** and deploy the following logic:

1. **Sheet-to-Firestore Sync (`syncSheetToFirestore`):**
   - Uses the free `FirestoreApp` Apps Script library (authenticating with a Firebase Service Account JSON key).
   - Whenever a child edits a medication time, dosage, or prescription date in the Google Sheet, pushes the updated JSON to the `medications` collection in Firestore and sends a silent FCM data message to the mother's phone to refresh `AlarmManager`.
2. **The 5-Minute Watchdog (`runWatchdogCheck` - Time-driven trigger every 5 mins):**
   - Queries today's expected medication times against Firestore `call_logs`.
   - **Silent Failure Detection:** If `now > scheduled_time + 10 minutes` AND `ringed_at == null` (meaning the phone is off, out of battery, offline, or HyperOS killed the app):
     - Sends a High-Priority FCM message to `caregiver_alerts` topic.
     - Sends an urgent email via `MailApp.sendEmail()` to the son and daughter: *"⚠️ ALERTE : Le téléphone de Maman n'a pas sonné pour le médicament de [Heure]. Il est peut-être éteint ou bloqué. Cliquez ici pour l'appeler : tel:+972..."*
   - **Unanswered Call Detection:** If `now > scheduled_time + 35 minutes` AND `completed_at == null`, triggers the same `IMMEDIATE` alert.
3. **Weekly Summary Email (`sendWeeklySummary` - Every Sunday at 20:00):**
   - Aggregates all `TAKEN` and `FORGOT` logs from the past 7 days and emails a clean French/Hebrew summary table to the children.

---

## 5. 14-Day Feasibility Test Protocol (Phase 1 Gate)

Before adding medical content, install the Phase 1-2 prototype on her Redmi phone and test:
1. Overnight test (screen off, locked, untouched for 8+ hours) -> Morning alarm must turn on screen and speak French.
2. Phone reboot -> Alarms must reschedule automatically.
3. "Clear All" in Xiaomi Recents menu -> Alarm must still fire.
4. Battery Saver mode & Low Battery (<15%).
5. Silent / Do Not Disturb mode -> App must bypass DND.
6. Active regular phone call -> App must wait and retry 5 minutes later.
7. Airplane mode (No internet) -> App must still ring, speak French via on-device TTS, and queue SMS/logs.
8. Speakerphone volume and clarity check with the mother.

**Pass Criterion:** At most 1 missed ring over 14 consecutive days, and never 2 consecutive misses.