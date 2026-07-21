---
name: skill-proxy
description: >
  Add the ZipoApps Proxy SDK
---

# Proxy SDK integration (ZipoApps)

This skill adds proxy support to an Android app using `com.zipoapps.proxysdk`. The
proxy hides API keys: instead of embedding a key in the APK, requests go to a
ZipoApps Cloud Function that injects the real key server-side. The app can still
fall back to a direct (un-proxied) call with a locally-configured key when one is
available (e.g. delivered via PremiumHelper remote config).


## How it works

1. `ProxyService.initialize(...)` is called once in `Application.onCreate()` with the
   project's Cloud Functions base URL and the Firebase/GCP project number.
2. The `Application` class implements `ProxySetupProvider`. The SDK calls back into it
   per request to decide **whether** to proxy (`isProxyEnabled`) and **what auth** to
   add server-side (`provideHeaderAuthParam` / `provideQueryAuthParam`).
3. An OkHttp interceptor from `proxyService.provideProxyInterceptor()` is added to the
   `OkHttpClient` used by Retrofit. When `isProxyEnabled` returns `true`, the interceptor
   rewrites the request to go through the proxy; otherwise the request passes through and
   the app supplies the key itself via the query/header auth params.

## Prerequisites (confirm with the user)

- The Cloud Functions base URL for the project, e.g.
  `https://us-<region>-<project-id>.cloudfunctions.net/`.
- The GCP/Firebase **project number** (a long numeric ID, not the project id string).
- The remote-config / BuildConfig key name that holds the API key for the fall-back
  path (in the reference project: `key_gmaps_api` via PremiumHelper).
- Which base URL(s) should be proxied (each third-party API the app calls).

## Steps

### 1. Add the Maven repository

The proxy SDK is published to the same GitHub Packages repo as PremiumHelper. In the
root `build.gradle` `allprojects { repositories { ... } }` (or `settings.gradle`
`dependencyResolutionManagement` for newer projects):

```gradle
maven {
    name = "GithubPackages"
    url = uri("https://maven.pkg.github.com/ZipoApps/premium-helper")
    credentials {
        username = 'ZipoApps'
        password = '<github-packages-token>'   // same token used for premium-helper
    }
}
```

If PremiumHelper is already integrated, this repo is almost certainly present — reuse it,
don't duplicate it.

### 2. Add the dependencies (app/build.gradle)

```gradle
implementation 'com.zipoapps.proxysdk:library:1.0.2-alpha'   // check for a newer version

// Required for the client that uses the proxy interceptor:
implementation 'com.squareup.retrofit2:retrofit:2.11.0'
implementation 'com.squareup.retrofit2:converter-scalars:2.11.0' // or the converter you use
implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0' // for the optional debug logger
```

Confirm the latest `proxysdk:library` version before pinning `1.0.2-alpha`.

### 3. Create the `ProxyUtil` helper

Create `utils/ProxyUtil.java` mirroring the reference. Replace the URL and project number
with the values gathered in Prerequisites.

```java
package <package>.utils;

import android.app.Application;

import <package>.BuildConfig;
import com.zipoapps.proxysdk.ProxyService;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

public class ProxyUtil {
    private static ProxyService proxyService;

    public static void initialize(Application application) {
        proxyService = ProxyService.Companion.initialize(
                application,
                "https://us-<region>-<project-id>.cloudfunctions.net/",
                <PROJECT_NUMBER>L
        );
    }

    public static OkHttpClient.Builder getOkHttpClientBuilder(boolean addLogs) {
        Interceptor proxySdkInterceptor = proxyService.provideProxyInterceptor();
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .addInterceptor(proxySdkInterceptor);

        if (addLogs && BuildConfig.DEBUG) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(logging);
        }

        return builder;
    }
}
```

### 4. Wire up the `Application` class

The `Application` must implement `ProxySetupProvider`, call `ProxyUtil.initialize(this)`
in `onCreate()` (before anything that builds the API client), and implement the three
callbacks. Match the per-URL logic to the APIs being proxied.

```java
public class MyApplication extends MultiDexApplication implements ProxySetupProvider {

    @Override
    public void onCreate() {
        ProxyUtil.initialize(this);
        super.onCreate();
    }

    // Proxy only when the app does NOT have a local key (so the server injects it).
    @Override
    public boolean isProxyEnabled(ProxyApiRequest apiRequest) {
        if (apiRequest.getUrl().startsWith(SomeApiClient.BASE_URL)) {
            return TextUtils.isEmpty(PhUtils.getApiKey());
        }
        return false; // unknown URL -> don't proxy
    }

    @Override
    public List<Pair<String, String>> provideHeaderAuthParam(ProxyApiRequest apiRequest) {
        return null; // return header pairs if the API authenticates via headers
    }

    // Fall-back path: when not proxied, attach the locally-held key as a query param.
    @Override
    public List<Pair<String, String>> provideQueryAuthParam(ProxyApiRequest apiRequest) {
        if (apiRequest.getUrl().startsWith(SomeApiClient.BASE_URL)) {
            return Collections.singletonList(new Pair<>("key", PhUtils.getApiKey()));
        }
        return null;
    }
}
```

Notes:
- Use header vs query auth depending on how the target API expects the key. Google Maps
  uses a `key` query param; many REST APIs use an `Authorization` header.
- `isProxyEnabled` returning `false` for unknown URLs is important so unrelated requests
  are never rewritten.

### 5. Build API clients through `ProxyUtil`

Every Retrofit/OkHttp client that hits a proxied API must build its `OkHttpClient` from
`ProxyUtil.getOkHttpClientBuilder(...)` so the interceptor is attached.

```java
OkHttpClient client = ProxyUtil.getOkHttpClientBuilder(false)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

Retrofit retrofit = new Retrofit.Builder()
        .baseUrl(SomeApiClient.BASE_URL)
        .client(client)
        .addConverterFactory(ScalarsConverterFactory.create())
        .build();
```

### 6. Verify

- Confirm `MyApplication` is registered in `AndroidManifest.xml` and INTERNET permission
  is present.
- Build the project.
- With **no** local key configured: requests should hit the Cloud Functions URL and
  succeed (key injected server-side). Use the debug `HttpLoggingInterceptor`
  (`getOkHttpClientBuilder(true)`) to confirm the rewritten URL.
- With a local key configured: requests should go directly to the API base URL with the
  key as a query/header param, bypassing the proxy.

## Checklist

- [ ] GitHub Packages maven repo present (credentials valid)
- [ ] `proxysdk:library` dependency added
- [ ] `ProxyUtil` created with correct Cloud Functions URL + project number
- [ ] `Application implements ProxySetupProvider` and calls `ProxyUtil.initialize` first
- [ ] `isProxyEnabled` / `provideQueryAuthParam` / `provideHeaderAuthParam` cover each API
- [ ] All relevant API clients build through `ProxyUtil.getOkHttpClientBuilder`
- [ ] Verified both proxied and direct-key paths
