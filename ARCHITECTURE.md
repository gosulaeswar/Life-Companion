# LifeWeave — Local-First Personal Life Management App

**LifeWeave** is a privacy-first Android wellness companion built with **Kotlin**, **Jetpack Compose (Material 3)**, and **Room (SQLite)**. Personal wellness records—profiles, hydration logs, cycle history, medicines, doctor follow-ups, synced wearable metrics, and alert sounds—are persisted locally on the user's device, paired with **Health Connect** wearable syncing and **Firebase AI Logic** (protected by App Check) for multilingual voice guidance and prescription photo extraction.

---

## 1. On-Device Data Vault, Wearables & AI Architecture

- **Native Android Stack**: Built with Kotlin + Jetpack Compose + Room SQLite for direct hardware-backed keystore security, native notification channels, Android Health Connect IPC, and zero-permission Storage Access Framework / Photo Picker flows.
- **Local Wellness Database**: All user wellness tables live strictly in Room SQLite on-device (`android:allowBackup="false"` with explicit `backup_rules.xml` / `data_extraction_rules.xml` exclusions). No personal records are uploaded to a remote database.
- **Connected Apps & Devices (`healthsync/`)**:
  - **Android Health Connect (`HealthConnectManager`)**: Reads aggregated steps, distance, active/total calories, workouts, sleep, heart rate, hydration, and weight from **Fitbit**, **Google Fit**, **Samsung Health**, **Garmin Connect**, **Oura**, **Strava**, **Withings**, and **Polar** with full source attribution and rationale activity (`HealthPermissionsRationaleActivity`).
  - **Apple Health & Export Importer (`AppleAndFileFitnessImporter`)**: Zero-permission `OpenDocument` parser supporting Apple Health `export.xml` (`<Record>` and `<Workout>` elements) plus JSON and CSV fitness exports.
- **Multilingual AI Voice Companion & Prescription Scanner (`ai/`)**:
  - **Firebase AI Logic (`LifeWeaveAiService`)**: Uses `gemini-3.8-flash` with Firebase App Check (`configureAppCheck(this, intent)` in `MainActivity.onCreate()`) and strict JSON schemas (`Schema.obj`) for:
    - Natural-language & voice metric logging across multiple languages (e.g., *"I just had a coffee"*, *"200 ml water"*) that directly writes to Hydration, Diet, Medicines, Steps, Exercise, or Sleep.
    - Multimodal prescription photo analysis via zero-permission `PickVisualMedia`, automatically populating Medicines (doses/day, intervals, meal timing) and Doctor Consultations/Follow-ups.
  - **Voice Engine (`VoiceAssistantManager`)**: Multilingual `SpeechRecognizer` input and `TextToSpeech` spoken guidance across English, Hindi, Spanish, French, German, Telugu, Tamil, and Arabic.

---

## 2. MVVM + Repository Architecture

- **UI Layer (Jetpack Compose)**: `WelcomeScreen`, `ProfileSetupScreen`, `HomeDashboardScreen`, `WaterTrackerScreen`, `PeriodTrackerScreen`, `ModulesHubScreen`, `ConnectedDevicesScreen`, `FitnessYogaScreen`, `DietNutritionScreen`, `MedicalCareScreen`, `BeautyCareScreen`, `DigitalWellbeingScreen`, `ReportsAnalyticsScreen`, `OnDeviceAiScreen`, and `SettingsPrivacyScreen` inside an adaptive `NavigationBar` / `NavigationRail` shell (`LifeCompanionApp`).
- **ViewModel Layer**: `AppSessionViewModel`, `ProfileSetupViewModel`, `HomeViewModel`, `WaterViewModel`, `PeriodViewModel`, `ModulesHubViewModel`, and `SettingsPrivacyViewModel` expose immutable `StateFlow` UI states observed via `collectAsStateWithLifecycle()`.
- **Repository Layer**: `UserProfileRepository`, `WaterRepository`, `PeriodRepository`, `CustomAlarmRepository`, `AllModulesRepository`, `FitnessSyncRepository`, `ModuleRepository`, and `PrivacyBackupRepository`.
- **Local Persistence Layer (Room SQLite v4, 17 Tables)**:
  - Tables: `user_profiles`, `water_records`, `period_records`, `medicines`, `medicine_intakes`, `doctor_consultations`, `exercise_records`, `sleep_records`, `step_records`, `diet_records`, `reminders`, `beauty_care_records`, `digital_wellbeing_records`, `ai_preferences`, `module_activation_status`, `connected_fitness_sources`, `health_vital_metrics`.
  - Non-destructive migrations: `MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`.

---

## 3. Security & All-Free Access

1. **Data Encryption Key (`KeystoreVaultManager`)**: Generates an app-private `AES-256-GCM` key inside `AndroidKeyStore`.
2. **All Modules Unlocked Free**: All 12 modules (including Connected Apps & Devices, AI Voice & Scanner, and Doctors & Medicines) are unlocked free with zero key required.
3. **Firebase App Check**: Configured in `MainActivity.onCreate()` before UI/ViewModel initialization (`DebugAppCheckProviderFactory` for debug builds with synchronous `.commit()`, and `RecaptchaAppCheckProviderFactory` for release builds).

---

## 4. Testing Instructions

Run all JVM unit and Robolectric tests:

```bash
gradle :app:testDebugUnitTest
```

Build the debug APK:

```bash
gradle :app:assembleDebug
```
