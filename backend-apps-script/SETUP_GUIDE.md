# Zero-Cost Cloud Watchdog & Xiaomi Setup Guide

## 1. Google Sheet & Apps Script Watchdog Setup ($0/month)

1. Create a Google Sheet with 3 tabs:
   - **`Medications`**: Columns:
     `id` | `name_fr` | `dosage_fr` | `meal_timing_fr` | `visual_description_fr` | `image_url` | `local_image_res` | `scheduled_times` | `on_missed_instruction_fr` | `prescription_expiry_date` | `stock_empty_date` | `prescription_status` | `active`
   - **`Exams`**: Columns:
     `id` | `name_fr` | `name_he` | `state` | `last_exam_date` | `next_target_date` | `state_updated_at`
   - **`Config_And_Contacts`**: Key-Value rows (Column A = Key, Column B = Value):
     - `mother_phone` -> `+97250...`
     - `caregiver_emails` -> `son@example.com,daughter@example.com`

2. Open **Extensions > Apps Script**:
   - Paste [Code.gs](file:///home/antigravity/ElieH/MedocMaman/backend-apps-script/Code.gs) and [appsscript.json](file:///home/antigravity/ElieH/MedocMaman/backend-apps-script/appsscript.json).
   - In **Project Settings > Script Properties**, add:
     - `FIREBASE_PROJECT_ID`
     - `FIREBASE_CLIENT_EMAIL`
     - `FIREBASE_PRIVATE_KEY`
   - In **Triggers (⏰ icon)**, add 3 triggers:
     1. `syncSheetToFirestore` -> From spreadsheet -> On edit
     2. `runWatchdogCheck` -> Time-driven -> Every 5 minutes
     3. `sendWeeklySummary` -> Time-driven -> Week timer -> Every Sunday at 20:00

---

## 2. Xiaomi Redmi (HyperOS / MIUI) Survival Checklist

1. Install `MedocMaman-debug.apk` on your mother's Xiaomi Redmi phone.
2. On the home screen (`Bonjour Maman 🌸`), **hold down the `⚙️` icon in the top-right for 3 seconds**.
3. Tap **"1. Autoriser Micro, SMS, Notifications & Téléphone"**, **"2. Désactiver l'optimisation de batterie"**, and **"📲 Ouvrir les Permissions Spéciales Xiaomi"**:
   - **Autostart (Démarrage automatique)** = **ON**
   - **Battery Saver (Économiseur de batterie)** = **No restrictions (Aucune restriction)**
   - **Show on Lock Screen (Afficher sur l'écran de verrouillage)** = **ON**
   - **Display pop-up windows while running in background** = **ON**
   - **Lock app in Recents (Cadenas dans les applications récentes)** = **ON**
4. Tap **"⏱️ Appel test dans 10s (Verrouillez l'écran !)"** and lock the phone immediately to verify the lock-screen wake-up and French voice speakerphone.
