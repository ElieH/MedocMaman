# MedocMaman — Assistant Médicaments & Suivi Médical (Zéro Coût)

Application Android (Kotlin + Jetpack Compose) conçue sur mesure pour une utilisatrice francophone senior sur **Xiaomi Redmi (HyperOS / MIUI)**, accompagnée d'un mode **Aidant (Enfants)** dans le même APK et d'un **Watchdog Cloud gratuit** (Firebase Spark + Google Apps Script).

## Points Clés de l'Architecture

1. **Zéro Navigation / Zéro Clavier (Côté Maman) :**
   - Réveil plein écran automatique sur l'écran verrouillé (`AlarmManager.setAlarmClock` + `NotificationCompat.CallStyle` + `setShowWhenLocked(true)`).
   - Polices géantes (`26.sp` à `32.sp`), contrastes **WCAG AAA**, boutons de `88.dp` à `110.dp` espacés de `16.dp`.
   - Haut-parleur activé automatiquement + voix française claire (`Locale.FRENCH`, vitesse `0.9f`, `Vouvoiement` ou `Tutoiement` configurable) + reconnaissance vocale hors-ligne/locale des mots-clés (**Oui / Non / Signes d'urgence VITE**).
2. **Sécurité Médicale Stricte & Protocole d'Urgence VITE (101) :**
   - Ne modifie jamais le traitement ; lit strictement la consigne du médecin (`on_missed_instruction_fr`) en cas d'oubli.
   - Détection immédiate des symptômes d'alerte AVC / carotides (**VITE**) à la voix ou au toucher -> alerte critique aux enfants (Firestore + SMS automatique hors-ligne avec l'adresse de Maman) et ouverture du composeur téléphonique pré-rempli sur le **101** (`Intent.ACTION_DIAL`).
3. **Renouvellement d'Ordonnances Meuhedet & Suivi d'Examens (Écho-Doppler Carotides) :**
   - Vérification quotidienne à 10h00 (rappel à J-14, escalade aux enfants à J-7, bouton 1-clic *"Demander à mes enfants de renouveler"* ou ouverture directe de `org.meuhedet.android`).
   - Suivi de l'état des examens médicaux (`NEEDS_REFERRAL` -> `HAS_REFERRAL` -> `APPOINTMENT_BOOKED` -> `EXAM_COMPLETED` -> `RESULTS_AT_DOCTOR` -> `NEXT_DATE_SET`).
4. **Mode Aidant (Fils & Fille) & Watchdog Google Apps Script :**
   - Basculez l'application en **Mode Téléphone Enfant** via un appui long de **3 secondes** sur l'icône `⚙️` en haut à droite de l'écran d'accueil.
   - Sirène d'alerte continue jusqu'à ce qu'un enfant appuie sur **"Prendre en charge"** (synchronisé via `acknowledged_by` dans Firestore).
   - Le script Google Apps Script ([backend-apps-script/Code.gs](file:///home/antigravity/ElieH/MedocMaman/backend-apps-script/Code.gs)) synchronise le Google Sheet avec Firestore et vérifie toutes les 5 minutes qu'aucun appel n'a échoué silencieusement (`> 10 min` sans `RINGED` ou `> 35 min` sans `COMPLETED`).

## Télécharger l'APK directement depuis GitHub

Chaque push sur `main` déclenche automatiquement le workflow GitHub Actions [`.github/workflows/build-apk.yml`](file:///home/antigravity/ElieH/MedocMaman/.github/workflows/build-apk.yml) qui compile le projet et publie l'artefact **`MedocMaman-APK`** (`MedocMaman-debug.apk`) prêt à être installé sur les téléphones.

## Documentation Complète
- [Spécification UI/UX Senior](file:///home/antigravity/ElieH/MedocMaman/docs/ui_ux_design_specification.md)
- [Plan Technique & Architecture Zéro Coût](file:///home/antigravity/ElieH/MedocMaman/docs/development_plan_zero_cost_architecture.md)
- [Guide d'Installation Google Sheet, Firebase & Xiaomi Redmi](file:///home/antigravity/ElieH/MedocMaman/backend-apps-script/SETUP_GUIDE.md)
