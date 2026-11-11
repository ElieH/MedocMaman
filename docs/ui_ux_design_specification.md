# UI/UX Design Specification: Elderly-Friendly French Medication Assistant

**Target User:** French-speaking senior woman using a Xiaomi Redmi smartphone.  
**Design Philosophy:** Zero-Menu, High-Contrast, Touch-First (Voice-Assisted), Anxiety-Free.  
**UI Toolkit:** Jetpack Compose (Material 3 customized for Senior Accessibility).

---

## 1. Core Accessibility & Visual Principles

1. **Zero Navigation / No Login:**
   - The patient never logs in, never sees a navigation bar, never opens settings, and never types on a keyboard.
   - The app wakes up automatically onto the exact screen needed, and closes itself when the interaction finishes.
2. **French Only (Patient Side):**
   - Every word shown or spoken to the mother is in clear, polite French (`Vouvoiement` or `Tutoiement` configurable by the family, default polite/warm).
3. **Typography & Sizing:**
   - **Minimum Body Text:** `26.sp` (Bold or SemiBold).
   - **Primary Question / Pill Name:** `32.sp` (ExtraBold).
   - **Touch Targets (Buttons):** Minimum height `88.dp`, full width (`fillMaxWidth()`), corner radius `20.dp`, vertical spacing `16.dp` between buttons to prevent accidental mis-taps.
4. **High-Contrast Color Palette (WCAG AAA Compliant):**
   - **Background:** Warm Off-White (`#FAF9F6`) during active call screens to prevent glare; Deep Slate (`#121824`) on the initial incoming call ring screen so it looks like a native phone call.
   - **Affirmative / Taken (`OUI`):** Deep Emerald Green (`#15803D`) with white text (`#FFFFFF`).
   - **Secondary / Delay (`PAS ENCORE` / `PLUS TARD`):** Royal Blue (`#1D4ED8`) with white text.
   - **Warning / Unwell (`UN PROBLÈME`):** Amber Orange (`#D97706`) with black or white bold text.
   - **Emergency (`URGENCE 101`):** Crimson Red (`#DC2626`) with white text and a subtle pulsing border.

---

## 2. Screen-by-Screen Wireframes & Layouts

### Screen 1: Incoming Medication Call (Lock Screen Wake-up)
*Triggered by `AlarmManager.setAlarmClock` + Full-Screen Intent. Mimics a native phone call.*

```text
+---------------------------------------------------+
|  [Dark Slate Background #121824]                  |
|                                                   |
|              ( Animated Pulse Ring )              |
|            +-------------------------+            |
|            |                         |            |
|            |   [ HIGH-RES PHOTO OF   |            |
|            |    THE ACTUAL PILL &    |            |
|            |         BOX ]           |            |
|            |      (240dp x 240dp)    |            |
|            +-------------------------+            |
|                                                   |
|             Rappel de Médicament                  |
|                 08:00 — Matin                     |
|                                                   |
|          "Atorvastatine (1 comprimé)"             |
|                                                   |
|   +-------------------------------------------+   |
|   |  [📞]    RÉPONDRE (OUVRIR)                |   |
|   |          (Giant Green Button - 96dp)      |   |
|   +-------------------------------------------+   |
|                                                   |
|   +-------------------------------------------+   |
|   |  [🕒]    RAPPELER DANS 15 MINUTES         |   |
|   |          (Giant Blue-Gray Button - 80dp)  |   |
|   +-------------------------------------------+   |
+---------------------------------------------------+
```
* **Behavior:** Rings with a warm, familiar chime. Pressing **RÉPONDRE** stops the ringer, activates the speakerphone, and transitions immediately to **Screen 2**.

---

### Screen 2: Active Call — Pill Identification & Primary Question
*Spoken automatically in French while displaying visual cues and touch buttons.*

```text
+---------------------------------------------------+
|  [Warm White Background #FAF9F6]    [🔊 Haut-parleur]|
|                                                   |
|  +---------------------------------------------+  |
|  |         [ PHOTO OF THE PILL ]               |  |
|  |    "Petit comprimé rond et blanc"           |  |
|  |     1 comprimé — Après le petit-déjeuner    |  |
|  +---------------------------------------------+  |
|                                                   |
|   🎤 "L'avez-vous déjà pris ?"                    |
|   (Subtle animated waveform showing voice/mic)    |
|                                                   |
|   +-------------------------------------------+   |
|   |  ✅  OUI, JE L'AI PRIS                    |   |
|   |      (Green #15803D - Height 92dp)        |   |
|   +-------------------------------------------+   |
|                                                   |
|   +-------------------------------------------+   |
|   |  ⏳  NON, PAS ENCORE                      |   |
|   |      (Blue #1D4ED8 - Height 88dp)         |   |
|   +-------------------------------------------+   |
|                                                   |
|   +-------------------------------------------+   |
|   |  ⚠️  J'AI UN PROBLÈME / UNE QUESTION      |   |
|   |      (Orange #D97706 - Height 88dp)       |   |
|   +-------------------------------------------+   |
+---------------------------------------------------+
```
* **Multimodal Input:** She can either say *"Oui"* / *"Non"* out loud OR tap the giant buttons. If speech recognition fails or times out after 8 seconds, the voice gently says: *"Vous pouvez appuyer sur le bouton vert pour Oui, ou le bouton bleu pour Non."*

---

### Screen 3: Follow-up / Reason Selection ("Pas encore" or "Problème")
*Displayed if she says "Non" or taps "Non, pas encore" / "J'ai un problème".*

```text
+---------------------------------------------------+
|  [Warm White Background #FAF9F6]                  |
|                                                   |
|   "Pouvez-vous le prendre maintenant,             |
|    ou y a-t-il un problème ?"                     |
|                                                   |
|   +-------------------------------------------+   |
|   |  💊  JE LE PRENDS MAINTENANT              |   |
|   |      (Rappel de vérification dans 15 min) |   |
|   +-------------------------------------------+   |
|                                                   |
|   +-------------------------------------------+   |
|   |  ❓  J'AI OUBLIÉ LA PRISE PRÉCÉDENTE       |   |
|   |      (Lire la consigne du médecin)        |   |
|   +-------------------------------------------+   |
|                                                   |
|   +-------------------------------------------+   |
|   |  📦  JE N'AI PLUS DE MÉDICAMENT           |   |
|   |      (Prévenir mes enfants)               |   |
|   +-------------------------------------------+   |
|                                                   |
|   +-------------------------------------------+   |
|   |  🩺  JE NE ME SENS PAS BIEN               |   |
|   |      (Ouvre la vérification de sécurité)  |   |
|   +-------------------------------------------+   |
+---------------------------------------------------+
```

---

### Screen 4: VITE Safety Check & Emergency 101 Screen
*Triggered immediately if she taps "Je ne me sens pas bien" or if the voice recognizer detects keywords like "vertige", "tourne", "faiblesse", "mal", "tombée".*

```text
+---------------------------------------------------+
|  [Soft Red/Cream Alert Background #FEF2F2]        |
|                                                   |
|   ℹ️ Vos enfants viennent d'être prévenus.        |
|                                                   |
|   "Avez-vous soudain une faiblesse d'un côté,     |
|    du mal à parler, ou un problème de vue ?"      |
|                                                   |
|   +-------------------------------------------+   |
|   |  🚨  OUI — APPELER LE 101 (URGENCE)       |   |
|   |      (Crimson Red #DC2626 - Height 110dp) |   |
|   +-------------------------------------------+   |
|                                                   |
|   +-------------------------------------------+   |
|   |  📞  APPELER MA FILLE / MON FILS          |   |
|   |      (Blue #1D4ED8 - Height 90dp)         |   |
|   +-------------------------------------------+   |
|                                                   |
|   +-------------------------------------------+   |
|   |  💬  NON, C'EST AUTRE CHOSE               |   |
|   |      (Mes enfants vont me rappeler)       |   |
|   +-------------------------------------------+   |
+---------------------------------------------------+
```
* **Android 101 Compliance:** Because Android blocks apps from auto-dialing emergency numbers directly, tapping **OUI — APPELER LE 101** opens the native phone dialer with `101` pre-filled (`Intent.ACTION_DIAL`) and speaks: *"Appuyez sur le bouton vert d'appel pour parler au 101."* Meanwhile, the children receive an immediate siren alert with her address.

---

### Screen 5: Idle Home Screen (When Opened Manually)
*If the mother taps the app icon during the day when no call is active.*

```text
+---------------------------------------------------+
|  [Warm White Background #FAF9F6]   [⚙️ (LongPress)]|
|                                                   |
|   Bonjour [Prénom] 🌸                             |
|                                                   |
|   Prochain médicament :                           |
|   +-------------------------------------------+   |
|   |  🕗 Ce soir à 20h00                       |   |
|   |  Atorvastatine (1 comprimé)               |   |
|   +-------------------------------------------+   |
|                                                   |
|   Besoin d'aide ?                                 |
|   +-------------------------------------------+   |
|   |  📞  APPELER MON FILS                     |   |
|   +-------------------------------------------+   |
|   +-------------------------------------------+   |
|   |  📞  APPELER MA FILLE                     |   |
|   +-------------------------------------------+   |
|   +-------------------------------------------+   |
|   |  🚨  URGENCE 101                          |   |
|   +-------------------------------------------+   |
+---------------------------------------------------+
```
* **Hidden Admin Access:** Holding down the small `⚙️` icon in the top right for 3 seconds opens the **Xiaomi System Health & Permissions Checklist** and allows switching the app into **Caregiver Mode** (for the son's and daughter's phones).