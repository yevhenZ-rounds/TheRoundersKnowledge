# Target SDK 35 → 36 Migration

Migrate an Android app from targetSdk 35 to targetSdk 36 (Android 16), handling all
mandatory breaking changes introduced by the new platform version.

## Source

[View skill on GitHub](https://github.com/yevhenZ-rounds/TheRoundersKnowledge/blob/main/skills/TargetSdkUpdate-35-36/SKILL.md)

## What it covers

- Updating `compileSdk` / `targetSdk` to 36 across all modules, AGP to ≥ 8.9.0, and Gradle wrapper to ≥ 8.11.1
- Replacing `onBackPressed()` and `KEYCODE_BACK` with `OnBackPressedCallback` (predictive back now enforced)
- Removing `windowOptOutEdgeToEdgeEnforcement` and implementing inset handling where the opt-out was used
- Adding `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` for apps with portrait-lock or non-resizable activities on large screens
- Replacing `BODY_SENSORS` with granular `android.permission.health.*` permissions
- Removing `elegantTextHeight` attributes and auditing fixed-height TextViews for text clipping
- Setting `jniLibs.useLegacyPackaging = false` and verifying 16 KB page-size alignment for `.so` libraries
- Removing `setImportantWhileForeground()` from `JobScheduler` and handling `STOP_REASON_TIMEOUT_ABANDONED`
- Migrating `announceForAccessibility()` calls to live regions, pane titles, or `setError()`
- Removing `RESULT_DISCOVERY_TIMEOUT` handling from `CompanionDeviceManager` pairing flows
- Adding a monochrome layer to adaptive icons for Android 16 themed icon support

## References

- [Behavior changes: apps targeting Android 16](https://developer.android.com/about/versions/16/behavior-changes-16)
- [Behavior changes: all apps on Android 16](https://developer.android.com/about/versions/16/behavior-changes-all)
- [Migration guide](https://developer.android.com/about/versions/16/migration)
- [SDK setup guide (AGP/tooling requirements)](https://developer.android.com/about/versions/16/setup-sdk)
- [Predictive back gesture guide](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)
- [Non-SDK interface restrictions for API 36](https://developer.android.com/about/versions/16/changes/non-sdk-16)
- [Google Play deadline: Aug 31, 2026 (extension to Nov 1, 2026 available)](https://developer.android.com/google/play/requirements/target-sdk)
