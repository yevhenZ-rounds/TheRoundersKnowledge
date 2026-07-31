---
name: skill-target-sdk-35-36-migration
description:  >
    Migrate Android app from targetSdk 35 to targetSdk 36 (Android 16). Handles all mandatory breaking changes: predictive back navigation, edge-to-edge enforcement, large screen adaptive layouts, health permissions, 16 KB page size, typography, JobScheduler, accessibility, ART/non-SDK, MediaStore, intent redirection, companion device pairing, Bluetooth bond handling. Use when updating targetSdk, compileSdk to 36, or asked to support Android 16.
version: 1.2.0
projectTypes: [android, kotlin]
autoTrigger: true
---

# Target SDK 36 Migration (Android 16)

Two categories of changes exist:

- **Part A** — Changes only affecting apps that set `targetSdk 36` (targeting SDK 36).
- **Part B** — Changes affecting **all apps** running on Android 16 devices, regardless of targetSdk.

> Sources:
> - [Behavior changes: apps targeting Android 16](https://developer.android.com/about/versions/16/behavior-changes-16)
> - [Behavior changes: all apps on Android 16](https://developer.android.com/about/versions/16/behavior-changes-all)
> - [Migration guide](https://developer.android.com/about/versions/16/migration)
> - [SDK setup guide (AGP/tooling requirements)](https://developer.android.com/about/versions/16/setup-sdk)
> - [Predictive back gesture guide](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)
> - [Non-SDK interface restrictions for API 36](https://developer.android.com/about/versions/16/changes/non-sdk-16)
> - [Google Play deadline: August 31, 2026, extension to Nov 1, 2026 available](https://developer.android.com/google/play/requirements/target-sdk)

---

## Step 1 — Update Build Configuration

### `app/build.gradle` (Groovy DSL)
```groovy
android {
    compileSdk 36
    defaultConfig {
        targetSdk 36
    }
}
```

### Root `build.gradle` toolchain requirements
- **AGP**: ≥ 8.9.0 (official minimum per [SDK setup guide](https://developer.android.com/about/versions/16/setup-sdk) is `8.9.0-rc01`; run the AGP Upgrade Assistant if below this). Prefer a current stable release.
- **Gradle wrapper** (`gradle/wrapper/gradle-wrapper.properties`): ≥ 8.11.1 (matches AGP 8.9.0's minimum/default Gradle requirement)
- **Kotlin**: no version is mandated by the Android 16 docs specifically — use whatever your AGP/Compose compiler combination requires.

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
```

---

# PART A — Changes for Apps Targeting SDK 36

## A1 — Predictive Back Navigation [BREAKING — app silently loses back press]

**What changed**: `onBackPressed()` and `KeyEvent.KEYCODE_BACK` are **no longer dispatched** on Android 16 for apps targeting SDK 36. Overrides are silently ignored. Predictive back system animations are **enabled by default** for apps targeting SDK 36 — you do **not** need to add `android:enableOnBackInvokedCallback="true"` to turn it on (that attribute now defaults to `true`). Set it to `false` only if you need a temporary opt-out.

### Detection:
```
grep -r "onBackPressed" app/src/
grep -r "KEYCODE_BACK" app/src/
```

### Fix — replace `onBackPressed()` override:
```kotlin
// BEFORE (broken on SDK 36 + Android 16):
override fun onBackPressed() {
    if (shouldHandle) { /* custom logic */ } else { super.onBackPressed() }
}

// AFTER:
private val backCallback = object : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
        if (shouldHandle) {
            // custom logic
        } else {
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
    }
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    onBackPressedDispatcher.addCallback(this, backCallback)
}
```

### Opt out temporarily in `AndroidManifest.xml` (only if you need to disable predictive back system animations; `OnBackPressedCallback` keeps working either way):
```xml
<application
    android:enableOnBackInvokedCallback="false"
    ...>
```

### Compose alternative — `PredictiveBackHandler`:
```kotlin
PredictiveBackHandler(enabled = isBackHandlerEnabled) { progress: Flow<BackEventCompat> ->
    try {
        progress.collect { backEvent -> /* update UI/animation with backEvent.progress */ }
        // handle final back action
    } catch (e: CancellationException) {
        // gesture cancelled, reset UI
    }
}
```

### Dependency:
```groovy
implementation 'androidx.activity:activity-ktx:1.13.0'
```

---

## A2 — Edge-to-Edge Enforcement [BREAKING — layout overlaps system bars]

**What changed**: `android:windowOptOutEdgeToEdgeEnforcement="true"` is **disabled** on Android 16. Apps must render content edge-to-edge and handle window insets.

### Detection:
```
grep -r "windowOptOutEdgeToEdgeEnforcement" app/src/
grep -r "fitsSystemWindows" app/src/
```

### Fix — `Activity.onCreate`:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    setContentView(binding.root)
}
```

### Fix — handle insets programmatically (preferred over `fitsSystemWindows`):
```kotlin
ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
    insets
}
```

### Remove from `res/values/themes.xml`:
```xml
<!-- REMOVE — no-op on Android 16 -->
<item name="android:windowOptOutEdgeToEdgeEnforcement">true</item>
```

### Dependency:
```groovy
implementation 'androidx.core:core-ktx:1.19.0'
```
> Note: as of core-ktx 1.19.0, the Kotlin extensions were merged into `androidx.core:core`; `core-ktx` is now an empty compatibility artifact, so this still works unchanged.

---

## A3 — Large Screen Adaptive Layouts [BREAKING on 600dp+ screens]

**What changed**: On devices with smallest width ≥ 600dp (tablets, foldables), Android 16 **ignores**:
- `android:screenOrientation`
- `android:resizeableActivity="false"`
- `android:minAspectRatio` / `android:maxAspectRatio`
- Runtime calls to `setRequestedOrientation()`

### Detection:
```
grep -r "screenOrientation\|resizeableActivity\|AspectRatio" app/src/main/AndroidManifest.xml
grep -r "setRequestedOrientation" app/src/
```

### Option A — Temporary opt-out (removed in API 37):
```xml
<!-- In AndroidManifest.xml under <application> or per <activity> -->
<property
    android:name="android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY"
    android:value="true" />
```

### Option B — Full fix (recommended):
- Use `WindowSizeClass` to adapt UI at runtime
- Handle configuration changes gracefully:
  ```kotlin
  override fun onConfigurationChanged(newConfig: Configuration) {
      super.onConfigurationChanged(newConfig)
      // Adapt UI based on screenWidthDp / screenHeightDp
  }
  ```
- Add to manifest activities if handling manually:
  ```xml
  android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout"
  ```

### Dependency for WindowSizeClass:
```groovy
implementation 'androidx.window:window:1.5.1'
```

---

## A4 — Health & Body Sensor Permissions [BREAKING if BODY_SENSORS used]

**What changed**: `BODY_SENSORS` and `BODY_SENSORS_BACKGROUND` are replaced by granular `android.permissions.health.*` permissions.

### Detection:
```
grep -r "BODY_SENSORS" app/src/main/AndroidManifest.xml
```

### Fix in `AndroidManifest.xml`:
```xml
<!-- REMOVE: -->
<uses-permission android:name="android.permission.BODY_SENSORS" />
<uses-permission android:name="android.permission.BODY_SENSORS_BACKGROUND" />

<!-- ADD the specific ones needed: -->
<uses-permission android:name="android.permission.health.READ_HEART_RATE" />
<uses-permission android:name="android.permission.health.READ_STEPS" />
<uses-permission android:name="android.permission.health.READ_SLEEP" />
<!-- Full list: https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started -->
```

An in-app privacy policy link is **required** when using health permissions.

---

## A5 — Typography: `elegantTextHeight` Ignored [VISUAL — affects non-Latin scripts]

**What changed**: `android:elegantTextHeight` is **ignored**. Text for Arabic, Lao, Myanmar, Tamil, Gujarati, etc. always uses elegant metrics.

### Detection:
```
grep -r "elegantTextHeight" app/src/
```

### Fix: Remove the attribute; audit layouts for text overflow/clipping with affected scripts:
```xml
<!-- REMOVE -->
<TextView android:elegantTextHeight="false" ... />
```

---

## A6 — `scheduleAtFixedRate` Behavior [LOW RISK]

**What changed**: `ScheduledThreadPoolExecutor.scheduleAtFixedRate` now executes **at most one** missed task when an app resumes (previously ran all missed tasks).

### Detection:
```
grep -r "scheduleAtFixedRate" app/src/
```

### Fix: If exact missed-task-count matters, replace with `WorkManager` or explicit manual rescheduling.

---

## A7 — Safer Intent Resolution [LOW RISK — opt-in by receivers]

**What changed**: Receiving apps can opt into strict resolution by declaring `android:intentMatchingFlags="enforceIntentFilter"` on the `<application>` element (or per-component). When enabled: explicit intents must match the target component's declared `<intent-filter>`, and intents with no action never match any filter.

### Opt in (only if you want stricter resolution for your own components):
```xml
<application
    android:intentMatchingFlags="enforceIntentFilter"
    ...>
```
Supported values: `enforceIntentFilter`, `allowNullAction`, `none`.

**Impact**: Only affects your app if you send intents cross-package to components that declare strict matching, or if you declare `intentMatchingFlags` in your own app.

### Detection — check logcat for blocked intents:
```
adb logcat | grep -E "PackageManager.*(Intent does not match component's intent filter|Access blocked)"
```

### Action: Audit `Intent()` calls without `.setAction()` that cross package boundaries.

---

## A8 — MediaStore.getVersion() App-Specific [INFO — no code change needed usually]

**What changed**: `MediaStore.getVersion(context)` now returns a **per-app unique value** instead of a shared device value, to prevent fingerprinting.

### Action: Ensure your app does not rely on comparing `MediaStore.getVersion()` values across different processes or apps.

---

## A9 — Photo Picker Pre-Selection [INFO — automatic behavior]

**What changed**: When a user selects "Select photos and videos" (partial media access), app-owned photos are pre-selected in the photo picker. Users can deselect them to revoke access.

**No code changes required.** The system handles this automatically.

---

## A10 — Bluetooth Bond Management [ONLY IF using CompanionDeviceManager / Bluetooth bonding]

**What changed**: Apps can now use `CompanionDeviceManager` to programmatically remove Bluetooth bonds. New intents broadcast remote key/bond loss and encryption-status changes: `ACTION_KEY_MISSING` (remote bond loss detected) and `ACTION_ENCRYPTION_CHANGE` (encryption, algorithm, or key size changed). Also track `ACTION_BOND_STATE_CHANGED` for bond-state monitoring after calling `removeBond()`.

### Action (if applicable):
- Use `CompanionDeviceManager.removeBond(int)` to remove bonds programmatically
- Listen for `ACTION_KEY_MISSING` and `ACTION_ENCRYPTION_CHANGE` intents if bond/encryption monitoring is needed
- Monitor `ACTION_BOND_STATE_CHANGED` after removing a bond

---

## A11 — GPU Syscall Filtering [INFO — automatic security hardening]

**What changed**: Mali GPU driver now blocks deprecated/development-only IOCTLs in production builds. Apps using internal GPU APIs directly (very rare) may be affected.

**No action needed** for standard apps using Vulkan/OpenGL through the NDK.

---

## A12 — Local Network Permission [FUTURE ENFORCEMENT]

**What changed**: Apps accessing LAN devices (mDNS, local HTTP servers) will require a runtime permission. Currently enforced only via `RESTRICT_LOCAL_NETWORK` compat flag — not production-enforced yet.

### Action: If the app communicates with local network devices, prepare to declare and request the future `android.permission.LOCAL_NETWORK` permission.

---

# PART B — Changes for All Apps Running on Android 16

These apply regardless of your `targetSdk`. Apps running on Android 16 devices are affected.

---

## B1 — 16 KB Page Size for Native Libraries [BREAKING if .so present]

**What changed**: Android 16 devices use 16 KB memory page alignment. Apps with `.so` libraries built for 4 KB alignment **will crash at launch**.

### Detection:
```
find app/src/main/jni -name "*.so"
find app/src/main/jniLibs -name "*.so"
```

### Fix — `app/build.gradle`:
```groovy
packagingOptions {
    pickFirst '**/*.so'
    jniLibs.useLegacyPackaging = false  // store .so uncompressed for 16 KB alignment
}
```

### Fix — NDK version (r27c or higher):
```groovy
android {
    ndkVersion "27.2.12479018" // r27c
}
```

### Compat mode (suppress user warning if 4 KB libs remain temporarily):
```xml
<!-- AndroidManifest.xml <application> -->
<application android:pageSizeCompat="true" ...>
```
> Note: `pageSizeCompat` only suppresses the dialog — it does NOT fix the crash. Fix the libraries properly.

---

## B2 — JobScheduler Changes [MEDIUM RISK if using background jobs]

**What changed**:
- `JobInfo.setImportantWhileForeground()` is **deprecated and ignored**
- Runtime quotas are now enforced even for jobs started while the app is in top state (once the app becomes invisible)
- Jobs that time out because the `JobParameters` object is no longer referenced now report `STOP_REASON_TIMEOUT_ABANDONED`

### Detection:
```
grep -r "setImportantWhileForeground\|JobScheduler\|JobService" app/src/
```

### Fix:
```kotlin
// REMOVE setImportantWhileForeground usage:
// jobInfo.setImportantWhileForeground(true)  // DEPRECATED - no effect

// For data transfers that should be exempt from quotas, use user-initiated jobs:
JobInfo.Builder(jobId, componentName)
    .setUserInitiated(true)
    .setRequiredNetwork(NetworkRequest.Builder().build())
    .build()
```

### Debug job status with new APIs:
```kotlin
val scheduler = getSystemService(JobScheduler::class.java)
val pendingReasons = scheduler.getPendingJobReasons(jobId)
val reasonsHistory = scheduler.getPendingJobReasonsHistory(jobId)
```

---

## B3 — `announceForAccessibility` Deprecated [MEDIUM RISK if using accessibility announcements]

**What changed**: `View.announceForAccessibility()` and `AccessibilityEvent.TYPE_ANNOUNCEMENT` are **deprecated**. They were unreliable and broke TalkBack announcements.

### Detection:
```
grep -r "announceForAccessibility\|TYPE_ANNOUNCEMENT" app/src/
```

### Fix — replace with structured alternatives:
```kotlin
// BEFORE:
view.announceForAccessibility("Status updated")

// AFTER — use live regions for dynamic content:
ViewCompat.setAccessibilityLiveRegion(view, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
// Then update the text normally; TalkBack announces the change automatically

// AFTER — for pane title (screens/sections):
ViewCompat.setAccessibilityPaneTitle(view, "My Screen Title")

// AFTER — for errors on input fields:
ViewCompat.setError(editText, "Invalid email address")
```

---

## B4 — Intent Redirection Hardening [AUTO — default protection]

**What changed**: Android 16 automatically protects apps against Intent redirection attacks (forwarding untrusted intents as `PendingIntent` payloads). This is applied by default.

**No code changes required** for most apps. If a legitimate use case is blocked:
```kotlin
// Opt-out only if you understand the security implications:
intent.removeLaunchSecurityProtection()
```

---

## B5 — ART / Non-SDK Interface Enforcement [RISK if using reflection on internals]

**What changed**: Android 16 updates the Android Runtime to OpenJDK 21. Apps using internal ART structures or non-SDK interfaces (accessed via reflection) may break, as these updates happen via Google Play system updates on older Android versions too.

### Detection — check Logcat for warnings:
```
adb logcat | grep "Accessing hidden field\|Accessing hidden method"
```

Or enable StrictMode in debug:
```kotlin
if (BuildConfig.DEBUG) {
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
            .detectNonSdkApiUsage()
            .penaltyLog()
            .build()
    )
}
```

### Fix: Migrate to public APIs. Full list of restricted interfaces:
> https://developer.android.com/about/versions/16/changes/non-sdk-16

---

## B6 — Ordered Broadcast Priority Limited to Same Process [LOW RISK]

**What changed**: `BroadcastReceiver` priority ordering is now only respected within the same app process. Cross-process ordering of ordered broadcasts is no longer guaranteed.

### Action: If your app coordinates with other apps/processes using ordered broadcasts and priority values, redesign with explicit IPC (e.g., `ContentProvider`, services, or `Messenger`).

---

## B7 — Companion Device Pairing: No More Discovery-Timeout Callback [LOW RISK — only apps using CompanionDeviceManager]

**What changed**: During companion device pairing, apps no longer receive `RESULT_DISCOVERY_TIMEOUT`. Instead, the user sees a system dialog on timeout, and if they decline, the app is notified with `RESULT_USER_REJECTED`. Discovery duration has also been extended beyond the previous 20-second window.

### Detection:
```
grep -r "RESULT_DISCOVERY_TIMEOUT" app/src/
```

### Fix: Handle `RESULT_USER_REJECTED` as the outcome for both explicit user rejection and timeout scenarios; remove any logic branching specifically on `RESULT_DISCOVERY_TIMEOUT`.

---

## B8 — Improved Bluetooth Bond-Loss Handling [INFO — only apps managing Bluetooth bonds]

**What changed**: When a previously bonded device can't be re-authenticated, Android 16 no longer silently auto-re-pairs. It disconnects the link, keeps the local bond record, and shows a system dialog prompting the user to re-pair.

### Action: If your app has custom re-pairing UX built around the old silent-reconnect behavior, verify it still works with the new user-facing re-pair prompt; no code change is required for most apps.

---

## B9 — Automatic Themed App Icons [VISUAL — QPR2+, low risk]

**What changed**: Starting with Android 16 QPR2, the system automatically applies a Material You theme to app icons that don't provide their own themed/monochrome icon.

### Action: Add a `<monochrome>` layer to your adaptive icon (`res/mipmap-anydpi-v26/ic_launcher.xml`) if you want to control how your icon looks when themed, otherwise the system will generate one automatically.

---

## B10 — Virtual Device Owner Overrides [INFO — large-screen/virtual device testing]

**What changed**: On select virtual devices, the device owner can override per-app orientation, aspect ratio, and resizability settings — similar in spirit to A3's large-screen behavior, but controlled by the virtual device rather than the physical device's screen size.

### Action: If you test on virtual/streamed devices, be aware overridden orientation settings there don't necessarily reflect real device behavior; validate large-screen adaptive layout fixes (A3) on physical large-screen hardware too.

---

# Migration Checklist

```
PART A — Targeting SDK 36:
[ ] A1. Replace all onBackPressed() with OnBackPressedCallback (predictive back is ON by default at targetSdk 36)
[ ] A2. Remove windowOptOutEdgeToEdgeEnforcement from themes
[ ] A2. Implement WindowCompat.setDecorFitsSystemWindows + inset handling
[ ] A3. Audit screenOrientation/resizeableActivity — add opt-out property or fix adaptive layouts
[ ] A4. Replace BODY_SENSORS with granular health permissions (if used)
[ ] A5. Remove elegantTextHeight attributes (if any)
[ ] A6. Review scheduleAtFixedRate usages (if any)
[ ] A7. Audit cross-package Intent() calls without setAction(); consider intentMatchingFlags
[ ] A8. Ensure code does not compare MediaStore.getVersion() across processes
[ ] A10. Migrate to CompanionDeviceManager bond API + ACTION_KEY_MISSING/ACTION_ENCRYPTION_CHANGE (if using Bluetooth bonds)

PART B — All apps on Android 16:
[ ] B1. Verify jniLibs.useLegacyPackaging = false (if .so files present)
[ ] B1. Verify NDK r27c+ (if building native code)
[ ] B2. Remove setImportantWhileForeground() usages; migrate data transfer to user-initiated jobs
[ ] B3. Replace announceForAccessibility() with live regions / pane titles
[ ] B5. Run app on Android 16 emulator and check Logcat for non-SDK interface warnings
[ ] B6. Review ordered broadcast priority usage across process boundaries
[ ] B7. Replace RESULT_DISCOVERY_TIMEOUT handling with RESULT_USER_REJECTED (if using CompanionDeviceManager)
[ ] B9. Add a monochrome adaptive-icon layer if you want to control themed-icon appearance

BUILD:
[ ] Update compileSdk and targetSdk to 36
[ ] Verify AGP ≥ 8.9.0 (rc01+) and Gradle wrapper ≥ 8.11.1
[ ] Test on Android 16 emulator (API 36 system image)
[ ] Test on 600dp+ screen AVD (tablet/foldable profile)
[ ] Check the Google Play targetSdk deadline (Aug 31, 2026 for new apps/updates; extension to Nov 1, 2026 available) — confirm this app is compliant well before that date
```

---

## Key Dependencies to Add/Update

```groovy
implementation 'androidx.activity:activity-ktx:1.13.0'    // OnBackPressedDispatcher (A1)
implementation 'androidx.core:core-ktx:1.19.0'            // WindowCompat / WindowInsetsCompat (A2)
implementation 'androidx.window:window:1.5.1'              // WindowSizeClass for adaptive layouts (A3)
```
> Versions above were latest stable as of mid-2026 — check the [AndroidX release notes](https://developer.android.com/jetpack/androidx/versions) for anything newer before applying.
