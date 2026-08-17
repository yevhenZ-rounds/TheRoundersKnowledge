---
name: skill-target-sdk-35-36-migration
description:  >
    Migrate Android app from targetSdk 35 to targetSdk 36 (Android 16). Handles all mandatory breaking changes: predictive back navigation, edge-to-edge enforcement, large screen adaptive layouts, health permissions, 16 KB page size, typography, JobScheduler, accessibility, ART/non-SDK, MediaStore, intent redirection, companion device pairing, Bluetooth bond handling. Use when updating targetSdk, compileSdk to 36, or asked to support Android 16.
version: 1.3.0
projectTypes: [android, kotlin]
autoTrigger: true
---

# Migrating from targetSdk 35 → targetSdk 36 (Android 16)

## Context: What SDK 35 Already Enforced (and What SDK 36 Changes)

This skill is specifically about apps **already on SDK 35** moving to **SDK 36**. Understanding what each SDK introduced matters:

| Behavior | SDK 35 (Android 15) | SDK 36 (Android 16) |
|---|---|---|
| Edge-to-edge | **Enforced**, but opt-out available via `windowOptOutEdgeToEdgeEnforcement` | **Enforced**, opt-out attribute is **disabled/ignored** |
| Predictive back animations | Available via `enableOnBackInvokedCallback="true"` opt-in | **On by default**, `onBackPressed()` no longer dispatched |
| Portrait lock on large screens | Respected on all screen sizes | **Ignored** on screens ≥ 600dp |
| `OnBackPressedCallback` | Works, recommended | **Only way to intercept back** |

So migrating from SDK 35 → 36 means: **removing the escape hatches you may have added to survive SDK 35**, and replacing them with proper implementations.

Two categories of changes:
- **Part A** — Only for apps that set `targetSdk 36`.
- **Part B** — All apps running on Android 16 devices, regardless of targetSdk.

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

Update ALL modules (app + every library module in the project):

### `app/build.gradle` and all `*/build.gradle` library modules
```groovy
android {
    compileSdk 36
    defaultConfig {
        targetSdk 36   // app module only; library modules don't need targetSdk
    }
}
```

### Root `build.gradle` — toolchain requirements
- **AGP**: ≥ 8.9.0 — required to compile against SDK 36. Run the AGP Upgrade Assistant in Android Studio if below this. If using `build.gradle` (Groovy):
  ```groovy
  classpath 'com.android.tools.build:gradle:8.9.0'
  ```
- **Gradle wrapper** (`gradle/wrapper/gradle-wrapper.properties`): ≥ 8.11.1 — AGP 8.9.0's minimum Gradle requirement:
  ```properties
  distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
  ```
- **Kotlin**: no version mandated by Android 16 docs — use whatever your AGP/Compose compiler combination requires.

---

# PART A — Changes for Apps Targeting SDK 36

## A1 — Predictive Back Navigation [BREAKING — app silently loses ALL back press handling]

### What changed from SDK 35 → 36

- **SDK 35**: `onBackPressed()` was deprecated but still dispatched. Predictive back was opt-in via `android:enableOnBackInvokedCallback="true"` in the manifest.
- **SDK 36**: `onBackPressed()` and `KeyEvent.KEYCODE_BACK` are **no longer dispatched at all** on Android 16. Any override is silently ignored — no crash, just broken behavior. `android:enableOnBackInvokedCallback` now defaults to `true` for SDK 36 apps; you do not need to add it.

The only correct way to intercept back on SDK 36 is `OnBackPressedCallback` registered on `onBackPressedDispatcher`.

### Detection
```bash
grep -r "onBackPressed" app/src/
grep -r "KEYCODE_BACK" app/src/
```

### Fix patterns

**Pattern 1 — Simple custom action (no fallthrough)**
App that opens a custom dialog or navigates to a specific screen instead of the default back:
```kotlin
// BEFORE (broken on SDK 36):
@SuppressLint("MissingSuperCall")
override fun onBackPressed() {
    showExitConfirmationDialog()
}

// AFTER:
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            showExitConfirmationDialog()
        }
    })
}
```

**Pattern 2 — Conditional: handle or fall through to default**
App that intercepts back only when something specific is shown (e.g., hide a panel before closing):
```kotlin
// BEFORE (broken on SDK 36):
override fun onBackPressed() {
    if (binding.sidePanel.isVisible) {
        hideSidePanel()
        return
    }
    super.onBackPressed()
}

// AFTER:
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (binding.sidePanel.isVisible) {
                hideSidePanel()
                return
            }
            // Fall through to default (close activity)
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    })
}
```

**Pattern 3 — Block back entirely (e.g., processing screen)**
App where back must be completely disabled during a critical operation:
```kotlin
// BEFORE (broken on SDK 36):
@SuppressLint("MissingSuperCall")
override fun onBackPressed() {
    // intentionally empty
}

// AFTER:
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() { /* back blocked */ }
    })
}
// To re-enable back later: backCallback.isEnabled = false
```

**Pattern 4 — Fragment back stack (abstract base Activity)**
Activity that manages a fragment stack and pops on back:
```kotlin
// BEFORE (broken on SDK 36):
override fun onBackPressed() {
    if (!supportFragmentManager.popBackStackImmediate()) {
        super.onBackPressed()
    }
}

// AFTER — register in onCreate of the base activity:
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    })
}
```

**Pattern 5 — Subclass overriding parent's back behavior**
When a child Activity needs higher-priority back handling than its parent:
```kotlin
// Parent registers its callback in onCreate (lower priority — registered first)
// Child registers its callback in onCreate AFTER super.onCreate() (higher priority — registered last)
// OnBackPressedDispatcher is LIFO: last registered = first checked

class ChildActivity : ParentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)  // parent registers its callback here
        // This callback is checked BEFORE parent's callback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isRootFragment().not()) {
                    popFragment()  // handle directly, bypass parent logic
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()  // fall through to parent callback
                    isEnabled = true
                }
            }
        })
    }
}
```

### Compose alternative — `PredictiveBackHandler`
For Compose screens that need to animate during the back gesture swipe:
```kotlin
PredictiveBackHandler(enabled = isBackHandlerEnabled) { progress: Flow<BackEventCompat> ->
    try {
        progress.collect { backEvent ->
            // backEvent.progress: 0.0 → 1.0 as the user swipes
            // backEvent.swipeEdge: LEFT or RIGHT
            animateExitProgress(backEvent.progress)
        }
        // Gesture committed — perform the actual back action
        navigateBack()
    } catch (e: CancellationException) {
        // Gesture cancelled — reset any animation
        resetExitAnimation()
    }
}
```

### Temporary opt-out (buys time, not a fix)
Disables predictive back system animations while keeping `OnBackPressedCallback` functional:
```xml
<application
    android:enableOnBackInvokedCallback="false"
    ...>
```

### Dependency — check before adding anything
`OnBackPressedCallback` has been in `androidx.activity:activity` since version **1.1.0**. Your app almost certainly already has it transitively via `appcompat`. **Check your resolved version before adding any dependency:**
```bash
./gradlew app:dependencies --configuration releaseRuntimeClasspath | grep "androidx.activity"
```
- **Resolved version ≥ 1.1.0** → `OnBackPressedCallback` works. No dependency change needed.
- **Resolved version ≥ 1.12.0** → `PredictiveBackHandler` (Compose) and `BackEventCompat` (swipe animations) also work.
- **Resolved version < 1.1.0** → extremely unlikely; declare `activity:1.1.0` as minimum.

Only add an explicit declaration if your resolved version is too old for the API you're using. Prefer the base artifact over `-ktx` to minimise conflict surface:
```groovy
// Only if your resolved activity version is genuinely too old:
implementation 'androidx.activity:activity:1.13.0'  // base artifact, no ktx needed
```

---

## A2 — Edge-to-Edge Enforcement [BREAKING only if opt-out was used in SDK 35]

### What changed from SDK 35 → 36

- **SDK 35 (Android 15)**: Edge-to-edge was enforced, but apps could escape it with `android:windowOptOutEdgeToEdgeEnforcement="true"` in the manifest or theme.
- **SDK 36 (Android 16)**: The opt-out attribute is **completely disabled** — setting it has no effect. Edge-to-edge is now unconditional.

### Starting assumption when migrating from SDK 35

Since SDK 35 already enforced edge-to-edge, it should already be properly implemented. The expected state is:
- No `windowOptOutEdgeToEdgeEnforcement` opt-out present, AND
- Inset handling already in place

If that is the case — **nothing to do for A2**. Skip to A3.

### Detection — check which case you are in

```bash
# Is the opt-out still present? (means edge-to-edge was never properly implemented)
grep -r "windowOptOutEdgeToEdgeEnforcement" app/src/main/AndroidManifest.xml
grep -r "windowOptOutEdgeToEdgeEnforcement" app/src/main/res/

# Is inset handling already in place?
grep -r "enableEdgeToEdge\|setOnApplyWindowInsetsListener\|handleInsets" app/src/
```

**Result A — opt-out NOT present, inset handling found** → Already compliant. Nothing to do.

**Result B — opt-out IS present** → ⚠️ The app was using a workaround that no longer exists on SDK 36. Apply all fix steps below.

> **Warning**: Never remove `windowOptOutEdgeToEdgeEnforcement` without implementing inset handling at the same time — removing it alone will cause content to be hidden behind system bars.

### Fix Step 1 — Remove the opt-out

From `AndroidManifest.xml`:
```xml
<!-- REMOVE — no-op on SDK 36 -->
android:windowOptOutEdgeToEdgeEnforcement="true"
```

From `res/values/themes.xml` (if present):
```xml
<!-- REMOVE -->
<item name="android:windowOptOutEdgeToEdgeEnforcement">true</item>
```

### Fix Step 2 — Add the `handleInsets` helper

Add the following helper in a proper utils file (create one if necessary). This is the only prescribed approach — do not use `fitsSystemWindows` in the theme (causes padding corruption on API 26–29) and do not use `WindowCompat.setDecorFitsSystemWindows(window, false)` (same root cause).

**Kotlin:**

```kotlin
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

fun Activity.handleInsets(topView: View? = null, bottomView: View? = null) {
    val content = this@handleInsets.findViewById<ViewGroup>(android.R.id.content)
    val rootView = content?.getChildAt(0) ?: return

    val viewForTopPadding = topView ?: rootView
    val viewForBottomPadding = bottomView ?: rootView

    ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.systemBars())
        viewForTopPadding.updatePadding(top = bars.top)
        viewForBottomPadding.updatePadding(bottom = bars.bottom)
        WindowInsetsCompat.CONSUMED
    }
}
```

**Java:**

```java
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public static void handleInsets(Activity activity, View topView, View bottomView) {
    ViewGroup content = activity.findViewById(android.R.id.content);
    if (content == null) return;

    View rootView = content.getChildAt(0);
    if (rootView == null) return;

    View viewForTopPadding = topView != null ? topView : rootView;
    View viewForBottomPadding = bottomView != null ? bottomView : rootView;

    ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
        Insets bars = insets.getInsets(
                WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemBars()
        );
        viewForTopPadding.setPadding(
                viewForTopPadding.getPaddingLeft(),
                bars.top,
                viewForTopPadding.getPaddingRight(),
                viewForTopPadding.getPaddingBottom()
        );
        viewForBottomPadding.setPadding(
                viewForBottomPadding.getPaddingLeft(),
                viewForBottomPadding.getPaddingTop(),
                viewForBottomPadding.getPaddingRight(),
                bars.bottom
        );
        return WindowInsetsCompat.CONSUMED;
    });
}
```

Why this is safe across all API levels:
- Sets the listener on the **root view** and returns `CONSUMED` — child views never receive the inset dispatch, so their XML `paddingStart`/`paddingEnd` values are never overwritten
- Uses `updatePadding` (Kotlin) / side-preserving `setPadding` (Java) — only top and bottom are touched
- Covers `displayCutout()` in addition to `systemBars()` — notch-safe

### Fix Step 3 — Enable edge-to-edge and call the helper in every activity

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()          // BEFORE super.onCreate(); handles bar transparency + icon contrast
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    // Action bar setup (if applicable)
    handleInsets()              // AFTER action bar setup
    // rest of initialization
}
```

```java
public void onCreate(Bundle bundle) {
    EdgeToEdge.enable(this);    // BEFORE super.onCreate()
    super.onCreate(bundle);
    setContentView(R.layout.activity_main);
    // Action bar setup (if applicable)
    Utils.handleInsets(this, null, null);   // AFTER action bar setup
    // rest of initialization
}
```

If the activity has a **bottom navigation bar or a RecyclerView that reaches the screen bottom**, pass it as `bottomView` so the bottom inset lands on that view instead of the root:

```kotlin
handleInsets(bottomView = binding.bottomNavigationView)
// or for RecyclerView — also set clipToPadding=false so the last item scrolls fully into view:
binding.recyclerView.clipToPadding = false
handleInsets(bottomView = binding.recyclerView)
```

### Fix Step 4 — Fix toolbar height

All toolbars must use `android:layout_height="wrap_content"`. The `?attr/actionBarSize` value does not account for insets and clips content:

```xml
<!-- WRONG -->
<androidx.appcompat.widget.Toolbar
    android:layout_height="?attr/actionBarSize" />

<!-- CORRECT -->
<androidx.appcompat.widget.Toolbar
    android:layout_height="wrap_content" />
```

### What NOT to do

| Approach | Why to avoid |
|---|---|
| `<item name="android:fitsSystemWindows">true</item>` in theme | On API 26–29 the system calls `setPadding()` on child views during inset dispatch, wiping any `paddingStart`/`paddingEnd` set in XML |
| `WindowCompat.setDecorFitsSystemWindows(window, false)` | Same root cause — leaves the legacy inset dispatch active on API 26–29 |
| `enableEdgeToEdge()` without `handleInsets()` | Content draws behind system bars |
| Removing the opt-out without any inset handling | Same result as above |

## A3 — Large Screen Adaptive Layouts [BREAKING on tablets and foldables]

### What changed from SDK 35 → 36

- **SDK 35**: `screenOrientation`, `resizeableActivity="false"`, aspect ratio constraints were all respected on all screen sizes.
- **SDK 36 (Android 16)**: On devices with **smallest width ≥ 600dp** (tablets, foldables, desktop), the system **ignores all of these**:
  - `android:screenOrientation="portrait"` (or any fixed orientation)
  - `android:resizeableActivity="false"`
  - `android:minAspectRatio` / `android:maxAspectRatio`
  - Runtime `setRequestedOrientation()` calls

Apps with portrait-only layouts displayed on a tablet in landscape will look stretched, cropped, or broken.

### Detection
```bash
# Fixed orientation declarations:
grep -r "screenOrientation" app/src/main/AndroidManifest.xml

# Non-resizable declaration:
grep -r "resizeableActivity" app/src/main/AndroidManifest.xml

# Aspect ratio constraints:
grep -r "AspectRatio" app/src/main/AndroidManifest.xml

# Runtime orientation changes:
grep -r "setRequestedOrientation" app/src/
```

### Fix — compat opt-out (covers API 36, removed in API 37)
Add once to `<application>` to cover all activities, or per `<activity>` for specific screens:
```xml
<application ...>
    <!-- Preserves portrait-lock and resizability restrictions on large screens through API 36 -->
    <property
        android:name="android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY"
        android:value="true" />
</application>
```
> **Warning**: This property is removed in API 37. Before targeting API 37, adaptive layout work will be required.

### Common issue: dialog-style activities break on large screens
If your activity uses `android:theme="@style/Theme.AppCompat.Dialog"` and sets a fixed size, it may be forcibly resized on tablets. Fix: use a real `DialogFragment` instead of an activity styled as a dialog.

### No dependency needed
The compat opt-out is a manifest-only change. No new library is required.

---

## A4 — Health & Body Sensor Permissions [BREAKING if BODY_SENSORS used]

### What changed
`BODY_SENSORS` and `BODY_SENSORS_BACKGROUND` are replaced by granular `android.permission.health.*` permissions targeting SDK 36.

### Detection
```bash
grep -r "BODY_SENSORS" app/src/main/AndroidManifest.xml
```

### Fix in `AndroidManifest.xml`
```xml
<!-- REMOVE: -->
<uses-permission android:name="android.permission.BODY_SENSORS" />
<uses-permission android:name="android.permission.BODY_SENSORS_BACKGROUND" />

<!-- ADD the specific ones your app actually uses: -->
<uses-permission android:name="android.permission.health.READ_HEART_RATE" />
<uses-permission android:name="android.permission.health.READ_STEPS" />
<uses-permission android:name="android.permission.health.READ_SLEEP" />
<uses-permission android:name="android.permission.health.READ_BLOOD_OXYGEN" />
<!-- Full list: https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started -->
```

An in-app privacy policy link is **required** when requesting health permissions.

### Fix in code — request at runtime
```kotlin
val healthPermissions = setOf(
    HealthPermission.getReadPermission(HeartRateRecord::class),
    HealthPermission.getReadPermission(StepsRecord::class)
)
val client = HealthConnectClient.getOrCreate(context)
val granted = client.permissionController.getGrantedPermissions()
if (!granted.containsAll(healthPermissions)) {
    requestPermissions.launch(healthPermissions)
}
```

---

## A5 — Typography: `elegantTextHeight` Ignored [VISUAL]

### What changed
`android:elegantTextHeight="false"` is silently ignored on SDK 36. Text for Arabic, Lao, Myanmar, Tamil, Gujarati, Kannada, etc. always uses tall (elegant) metrics, which increases line height and can cause text clipping in fixed-height TextViews.

### Detection
```bash
grep -r "elegantTextHeight" app/src/
grep -r "elegantTextHeight" app/src/main/res/
```

### Fix
Remove all `android:elegantTextHeight` attributes — they have no effect:
```xml
<!-- REMOVE: -->
<TextView
    android:elegantTextHeight="false"
    android:layout_height="40dp"  <!-- This fixed height may now clip tall scripts -->
    ... />

<!-- FIX: use wrap_content or minHeight instead of fixed height for text containers -->
<TextView
    android:layout_height="wrap_content"
    android:minHeight="40dp"
    ... />
```

Then audit TextViews with fixed `layout_height` in layouts that support non-Latin locales — they may need to become `wrap_content` or `minHeight`.

---

## A6 — `scheduleAtFixedRate` Behavior Change [LOW RISK]

### What changed
`ScheduledThreadPoolExecutor.scheduleAtFixedRate` now executes **at most one** missed task when the app resumes after being backgrounded (previously executed all accumulated missed tasks in rapid succession).

### Detection
```bash
grep -r "scheduleAtFixedRate" app/src/
```

### Fix
Only relevant if you depend on exact catch-up execution after resume. If you use it for polling or periodic UI updates, no change needed. If you need guaranteed delivery of every missed tick:
```kotlin
// Replace with WorkManager for reliable periodic execution that survives process death:
val request = PeriodicWorkRequestBuilder<MyWorker>(15, TimeUnit.MINUTES).build()
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "my_periodic_work",
    ExistingPeriodicWorkPolicy.KEEP,
    request
)
```

---

## A7 — Safer Intent Resolution [LOW RISK — opt-in only]

### What changed
Receiving apps can now declare `android:intentMatchingFlags="enforceIntentFilter"` on their `<application>` or per-component. When set: explicit intents sent TO that app must match a declared `<intent-filter>`, and intents without an action never match. This is an opt-in on the RECEIVING side — your app is only affected if you send intents to an app that declares this flag.

### Detection
```bash
# Check logcat for blocked intents at runtime:
adb logcat | grep -E "PackageManager.*(Intent does not match|Access blocked)"
```

### Fix if your app is the sender
Ensure any `Intent()` you send to other packages has a matching action and matches the target's declared filter:
```kotlin
// RISKY on SDK 36 if target declares enforceIntentFilter:
val intent = Intent().apply {
    setComponent(ComponentName("com.other.app", "com.other.app.SomeActivity"))
    // No setAction() call — may be blocked
}

// SAFE — includes the action the target declared in its intent-filter:
val intent = Intent("com.other.app.ACTION_DO_THING").apply {
    setComponent(ComponentName("com.other.app", "com.other.app.SomeActivity"))
}
```

### Opt in for your own app (if you want stricter incoming intent filtering)
```xml
<application
    android:intentMatchingFlags="enforceIntentFilter"
    ...>
```

---

## A8 — MediaStore.getVersion() App-Specific [INFO]

### What changed
`MediaStore.getVersion(context)` now returns a **per-app unique value** instead of a device-wide shared value. This prevents apps from fingerprinting the device by comparing version strings.

### Action
Check if your code compares `MediaStore.getVersion()` results across apps or processes. If so, remove the comparison — the values will no longer be equal across app boundaries even when the media database hasn't changed.

---

## A9 — Photo Picker Pre-Selection [INFO — automatic]

### What changed
When a user grants partial media access ("Select photos and videos"), their previously-shared photos are pre-selected in the photo picker dialog. Users can deselect them to revoke individual photo access.

**No code changes required.** The system handles this automatically via the photo picker UI.

---

## A10 — Bluetooth Bond Management [ONLY IF using CompanionDeviceManager]

### What changed
Apps can now programmatically remove Bluetooth bonds via `CompanionDeviceManager`. New broadcast intents signal bond/encryption state changes.

### Fix (if applicable)
```kotlin
// Remove bond programmatically (new in SDK 36):
val cdm = getSystemService(CompanionDeviceManager::class.java)
cdm.removeBond(associationId)

// Listen for remote bond loss (e.g., device was factory reset):
val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothDevice.ACTION_KEY_MISSING -> {
                // Remote device lost the bond — prompt user to re-pair
                showRepairPrompt()
            }
            BluetoothDevice.ACTION_ENCRYPTION_CHANGE -> {
                // Encryption algorithm or key changed
                val encryptionStatus = intent.getIntExtra(
                    BluetoothDevice.EXTRA_ENCRYPTION_STATUS, -1
                )
                handleEncryptionChange(encryptionStatus)
            }
        }
    }
}
registerReceiver(receiver, IntentFilter().apply {
    addAction(BluetoothDevice.ACTION_KEY_MISSING)
    addAction(BluetoothDevice.ACTION_ENCRYPTION_CHANGE)
})
```

---

## A11 — GPU Syscall Filtering [INFO]

### What changed
The Mali GPU driver now blocks internal/development-only IOCTLs in production builds.

**No action needed** for standard apps using Vulkan or OpenGL ES through the NDK. Only affects apps calling internal GPU driver APIs directly (extremely rare).

---

## A12 — Local Network Permission [FUTURE — not yet enforced]

### What changed
Apps that access local network resources (mDNS discovery, local HTTP servers, Bonjour/Zeroconf) will require a new runtime permission. Not enforced in production yet — guarded by the `RESTRICT_LOCAL_NETWORK` compat flag.

### Action — prepare now
```xml
<!-- Declare in manifest now so the system sees your intent: -->
<uses-permission android:name="android.permission.LOCAL_NETWORK" />
```
```kotlin
// Request at runtime when enforcement lands:
if (checkSelfPermission(Manifest.permission.LOCAL_NETWORK) != PERMISSION_GRANTED) {
    requestPermissions(arrayOf(Manifest.permission.LOCAL_NETWORK), REQUEST_CODE)
}
```

---

# PART B — Changes for All Apps Running on Android 16

These apply regardless of your `targetSdk`. If your app runs on an Android 16 device, these affect it.

---

## B1 — 16 KB Page Size for Native Libraries [BREAKING if .so present]

### What changed
Android 16 devices use 16 KB memory page alignment. `.so` libraries compiled with 4 KB alignment (`p_align = 0x1000`) **crash at launch** on these devices with a linker error.

### Detection
```bash
find app/src/main/jni -name "*.so"
find app/src/main/jniLibs -name "*.so"
# Check alignment of a specific .so:
readelf -l path/to/libfoo.so | grep -A1 LOAD
# p_align should show 0x4000 (16 KB), not 0x1000 (4 KB)
```

### Fix 1 — `jniLibs.useLegacyPackaging = false` [REQUIRED if any .so present]
Stores `.so` files uncompressed in the APK so the OS can memory-map them directly at the correct 16 KB boundary. Without this, files are compressed and extracted to a temp path with no alignment guarantee.
```groovy
android {
    packagingOptions {
        jniLibs.useLegacyPackaging = false
    }
}
```
Check if already set before adding — some projects have this from earlier Android 15 work.

### Fix 2 — `ndkVersion "27.2.12479018"` [ONLY if you build your own C/C++ code]
NDK r27c is the first version that outputs 16 KB-aligned `.so` files by default. **Skip this entirely** if your app only uses pre-built `.so` files from Gradle dependencies or a local `jniLibs/` folder — setting `ndkVersion` has no effect on pre-built libraries.
```groovy
android {
    ndkVersion "27.2.12479018"  // r27c — only needed if you compile native code yourself
}
```

### Fix 3 — pre-built .so files from vendors that aren't 16 KB aligned
Try to patch it yourself first. If patching fails or the file cannot be safely modified, log a warning and contact the vendor for a properly aligned `.so`.

`patchelf` is **Linux-only** natively — it does not run on Windows or macOS without workarounds:

| OS | Command |
|---|---|
| Linux | `patchelf --set-pagesize 0x4000 path/to/libvendor.so` |
| macOS | `brew install patchelf` then same command |
| Windows | `wsl patchelf --set-pagesize 0x4000 /mnt/c/path/to/libvendor.so` (requires WSL) |

**Cross-platform alternative — Python + `lief` (works on all OS without WSL):**
```bash
pip install lief
```
```python
import lief, sys
lib = lief.parse(sys.argv[1])
for seg in lib.segments:
    if seg.type in (lief.ELF.SEGMENT_TYPES.LOAD, lief.ELF.SEGMENT_TYPES.GNU_RELRO):
        seg.alignment = 0x4000
lib.write(sys.argv[1])
```
```bash
python patch_16kb.py path/to/libvendor.so
```

**Which approach to use:**

| Situation | Approach |
|---|---|
| Vendor `.so` committed to repo, rarely changes | Patch once manually, commit the patched file — no Gradle task needed |
| Vendor `.so` downloaded from Maven at build time, or updates frequently | Wire patching into Gradle so it runs automatically on every build |

**Gradle automation (only needed for the second case above):**
```groovy
task patch16kbLibraries {
    doLast {
        fileTree('src/main/jniLibs').matching { include '**/*.so' }.each { soFile ->
            def isWindows = System.properties['os.name'].toLowerCase().contains('windows')
            // Try patchelf first
            try {
                def cmd = isWindows
                    ? ['wsl', 'patchelf', '--set-pagesize', '0x4000', soFile.absolutePath.replace('\\', '/').replaceFirst('C:/', '/mnt/c/')]
                    : ['patchelf', '--set-pagesize', '0x4000', soFile.absolutePath]
                exec { commandLine cmd }
                return
            } catch (ignored) {}
            // Fall back to Python + lief
            try {
                exec { commandLine 'python', 'scripts/patch_16kb.py', soFile.absolutePath }
            } catch (ignored) {
                logger.warn("[16KB] Could not patch ${soFile.name} — install patchelf or: pip install lief")
            }
        }
    }
}
preBuild.dependsOn patch16kbLibraries
```

## B2 — JobScheduler Changes [MEDIUM RISK]

### What changed
- `JobInfo.setImportantWhileForeground()` is deprecated and **has no effect** — jobs are subject to runtime quotas even when the app is foregrounded.
- Jobs that time out because the `JobParameters` object was garbage-collected now report `STOP_REASON_TIMEOUT_ABANDONED` (new stop reason).

### Detection
```bash
grep -r "setImportantWhileForeground\|JobScheduler\|JobService" app/src/
```

### Fix
```kotlin
// REMOVE — no effect on Android 16:
// jobInfoBuilder.setImportantWhileForeground(true)

// INSTEAD — for large user-initiated data transfers exempt from quotas:
val jobInfo = JobInfo.Builder(jobId, ComponentName(context, MyJobService::class.java))
    .setUserInitiated(true)                           // user explicitly triggered this
    .setRequiredNetwork(NetworkRequest.Builder().build()) // requires network
    .setEstimatedNetworkBytes(
        JobInfo.NETWORK_BYTES_UNKNOWN,
        JobInfo.NETWORK_BYTES_UNKNOWN
    )
    .build()

// Handle the new stop reason:
class MyJobService : JobService() {
    override fun onStopJob(params: JobParameters): Boolean {
        if (params.stopReason == JobParameters.STOP_REASON_TIMEOUT_ABANDONED) {
            // JobParameters object was GC'd — keep a strong reference during execution
            Timber.w("Job stopped: abandoned timeout. Ensure JobParameters isn't GC'd.")
        }
        return true // reschedule
    }
}
```

### Debug pending job reasons (new API)
```kotlin
val scheduler = getSystemService(JobScheduler::class.java)
val pendingReasons = scheduler.getPendingJobReasons(jobId)
val reasonsHistory = scheduler.getPendingJobReasonsHistory(jobId)
Timber.d("Job $jobId pending because: $pendingReasons")
```

---

## B3 — `announceForAccessibility` Deprecated [MEDIUM RISK]

### What changed
`View.announceForAccessibility()` and `AccessibilityEvent.TYPE_ANNOUNCEMENT` are deprecated. They were unreliable and caused TalkBack to skip or duplicate announcements.

### Detection
```bash
grep -r "announceForAccessibility\|TYPE_ANNOUNCEMENT" app/src/
```

### Fix patterns

**Dynamic status text (e.g., "File saved successfully"):**
```kotlin
// BEFORE:
statusView.announceForAccessibility("File saved")

// AFTER — set the view as a live region; TalkBack announces when text changes:
ViewCompat.setAccessibilityLiveRegion(statusView, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
statusView.text = "File saved"  // TalkBack announces this automatically
// Use ACCESSIBILITY_LIVE_REGION_ASSERTIVE for critical updates that interrupt current speech
```

**Screen/section titles:**
```kotlin
// BEFORE:
container.announceForAccessibility("Settings screen")

// AFTER:
ViewCompat.setAccessibilityPaneTitle(container, "Settings")
```

**Form validation errors:**
```kotlin
// BEFORE:
inputField.announceForAccessibility("Invalid email address")

// AFTER:
ViewCompat.setError(inputField, "Invalid email address")
// TalkBack reads the error when the field gets focus
```

---

## B4 — Intent Redirection Hardening [AUTO — default protection]

### What changed
Android 16 automatically protects against Intent redirection attacks where a malicious app passes a `PendingIntent` to your app which you then launch on their behalf with elevated permissions.

**No code changes required** for most apps. If a legitimate use case is blocked (e.g., a multi-process architecture that intentionally forwards intents):
```kotlin
// Only use if you understand the security trade-off:
intent.removeLaunchSecurityProtection()
```

---

## B5 — ART / Non-SDK Interface Enforcement [RISK if using reflection on internals]

### What changed
Android 16 ships with OpenJDK 21 in the Android Runtime. Internal ART structures and non-SDK interfaces accessed via reflection may no longer exist or have moved.

### Detection
```bash
adb logcat | grep "Accessing hidden field\|Accessing hidden method"
```

Enable in debug builds to catch these proactively:
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

### Fix
Migrate each flagged usage to the public SDK API. If no public API exists, file a feature request with Google. Full restricted interface list:
> https://developer.android.com/about/versions/16/changes/non-sdk-16

---

## B6 — Ordered Broadcast Priority Cross-Process [LOW RISK]

### What changed
`BroadcastReceiver` priority ordering (`android:priority`) is now respected **only within the same app process**. Cross-process ordered broadcast ordering is no longer guaranteed.

### Fix (if your app coordinates with other apps via ordered broadcasts)
Replace with explicit IPC mechanisms:
```kotlin
// Instead of ordered broadcasts across apps, use:
// - ContentProvider queries
// - Bound Services with AIDL
// - Messenger
// - BroadcastReceiver with explicit package targeting (not ordered)
```

---

## B7 — CompanionDeviceManager: No Discovery-Timeout Callback [LOW RISK]

### What changed
During companion device pairing, apps no longer receive `RESULT_DISCOVERY_TIMEOUT`. The system shows its own dialog on timeout; if the user dismisses it, the app receives `RESULT_USER_REJECTED`. Discovery duration has also been extended.

### Detection
```bash
grep -r "RESULT_DISCOVERY_TIMEOUT" app/src/
```

### Fix
```kotlin
// BEFORE:
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when (resultCode) {
        RESULT_DISCOVERY_TIMEOUT -> showManualPairingInstructions()  // REMOVE
        RESULT_USER_REJECTED -> showRetryDialog()
    }
}

// AFTER — treat timeout and rejection as the same outcome:
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when (resultCode) {
        RESULT_OK -> handlePairingSuccess(data)
        RESULT_USER_REJECTED -> showRetryDialog()  // covers both rejection AND timeout
        else -> handlePairingCancelled()
    }
}
```

---

## B8 — Bluetooth Bond-Loss Handling [INFO]

### What changed
When a previously bonded device can't re-authenticate, Android 16 no longer silently re-pairs in the background. It disconnects, keeps the local bond record, and shows a system dialog to re-pair.

**No code changes required** unless your app has custom silent-reconnect UX that assumed the old behavior. In that case, verify your UX still makes sense when a system dialog interrupts it.

---

## B9 — Automatic Themed App Icons [VISUAL — QPR2+]

### What changed
Android 16 QPR2+ automatically generates a monochrome/Material You themed version of your icon if you don't provide one. The auto-generated version may look poor.

### Fix — add a monochrome layer to your adaptive icon
```xml
<!-- res/mipmap-anydpi-v26/ic_launcher.xml -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <!-- Add this — a single-color silhouette of your icon: -->
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
```
The monochrome drawable should be a single-path vector, filled with a single color (the system tints it). Preview with: Device > Wallpaper & style > Themed icons.

---

## B10 — Virtual Device Owner Overrides [INFO — large-screen testing]

### What changed
On select virtual/streamed devices, the device owner can override per-app orientation and resizability regardless of manifest declarations (similar to A3, but driven by the virtual device owner, not screen size).

**No code change required.** When testing on virtual devices, orientation/size overrides may not reflect real device behavior. Always test A3 large-screen layout fixes on physical hardware too.

---

# Migration Checklist

```
BUILD CONFIG:
[ ] Update compileSdk → 36 in app module and ALL library modules
[ ] Update targetSdk → 36 in app module
[ ] Update AGP → ≥ 8.9.0 in root build.gradle
[ ] Update Gradle wrapper → ≥ 8.11.1 in gradle-wrapper.properties

PART A — Targeting SDK 36:
[ ] A1. grep for onBackPressed() / KEYCODE_BACK — replace ALL with OnBackPressedCallback
[ ] A1. Register callbacks in onCreate(); LIFO order — child Activity registers after super.onCreate()
[ ] A2. Check: grep for windowOptOutEdgeToEdgeEnforcement + enableEdgeToEdge/setDecorFitsSystemWindows/fitsSystemWindows
[ ] A2. If opt-out NOT present and inset handling found → already compliant, skip A2
[ ] A2. If opt-out IS present → WARN: edge-to-edge was never implemented; remove opt-out AND add inset handling (see A2 fixes)
[ ] A3. grep for screenOrientation / resizeableActivity / AspectRatio / setRequestedOrientation
[ ] A3. Add PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY to <application> in manifest (manifest-only, no dependency needed)
[ ] A4. Replace BODY_SENSORS with granular health.* permissions (if used)
[ ] A5. Remove all elegantTextHeight attributes; audit fixed-height TextViews for clipping
[ ] A6. Review scheduleAtFixedRate — replace with WorkManager if exact catch-up count matters
[ ] A7. Audit cross-package Intent() calls without setAction() — may be blocked by strict receivers
[ ] A8. Remove any cross-process MediaStore.getVersion() comparisons
[ ] A10. Migrate Bluetooth bond management to CompanionDeviceManager APIs (if applicable)
[ ] A12. Declare android.permission.LOCAL_NETWORK in manifest if app uses LAN discovery

PART B — All apps on Android 16:
[ ] B1. If .so files present: set jniLibs.useLegacyPackaging = false in packagingOptions (required)
[ ] B1. If building own C/C++ code: set ndkVersion "27.2.12479018" (skip if only using pre-built .so)
[ ] B1. If using pre-built .so from vendors: verify 16 KB alignment with readelf -l; patch or request update if not aligned
[ ] B2. Remove setImportantWhileForeground() calls; use setUserInitiated(true) for data transfers
[ ] B3. Replace announceForAccessibility() with live regions / pane titles / setError()
[ ] B5. Run on Android 16 emulator with StrictMode.detectNonSdkApiUsage() and check logcat
[ ] B6. Review ordered broadcast priority usage across process boundaries
[ ] B7. Remove RESULT_DISCOVERY_TIMEOUT handling — map to RESULT_USER_REJECTED
[ ] B9. Add monochrome layer to adaptive icon (res/mipmap-anydpi-v26/ic_launcher.xml)

TESTING:
[ ] Test on Android 16 emulator (API 36 system image)
[ ] Test on Android 15 device/emulator — edge-to-edge is now active there too via your explicit opt-in
[ ] Test on 600dp+ screen AVD (Pixel Tablet profile or 7" WSVGA tablet)
[ ] Test back navigation in every activity and fragment manually
[ ] Check Google Play targetSdk deadline: Aug 31, 2026 (extension to Nov 1, 2026 available)
```

---

## Dependencies — Conservative Approach

> **Primary rule: do not touch dependencies unless a build error or missing API forces you to.**
> The SDK 36 breaking changes are code-level — `onBackPressed()` removal, edge-to-edge enforcement, manifest attributes. None of them strictly require a new library version if your existing transitive versions already provide the needed APIs. Unnecessary version bumps risk breaking a large existing library ecosystem.

### Before adding anything, check what you already have
```bash
./gradlew app:dependencies --configuration releaseRuntimeClasspath \
  | grep -E "androidx\.(activity|core|window)"
```

### Decision table

| Library | API you need | Available since | Action |
|---|---|---|---|
| `androidx.activity` | `OnBackPressedCallback` | **1.1.0** | Check resolved version. Almost certainly already present via `appcompat`. Only bump if resolved < 1.1.0 (very rare). |
| `androidx.activity` | `PredictiveBackHandler`, `BackEventCompat` | **1.12.0** | Only needed for swipe-progress animation UI. Skip if not implementing predictive back animations. |
| `androidx.core` | `WindowCompat`, `ViewCompat`, `WindowInsetsCompat` | **1.5.0** | Check resolved version. Almost certainly already present. Only bump if resolved < 1.5.0 (very rare). |
| `androidx.core` | `WindowInsetsAnimationCompat` | **1.8.0** | Only needed for smooth keyboard slide animation. Skip if not implementing it. |

### If you do need to add a dependency — use base artifacts, not ktx
`-ktx` variants only add Kotlin extension functions. The APIs themselves are in the base artifacts. Using base artifacts reduces the surface area of potential conflicts with your large existing library:
```groovy
// Prefer base artifacts:
implementation 'androidx.activity:activity:1.13.0'   // NOT activity-ktx
implementation 'androidx.core:core:1.19.0'           // NOT core-ktx (identical at 1.19.0 anyway)

// Only use ktx if you specifically want the Kotlin extension functions
// (e.g., view.updatePadding() instead of view.setPadding())
```

> Versions above were latest stable as of mid-2026 — check [AndroidX releases](https://developer.android.com/jetpack/androidx/versions) before applying.

