# Proxy SDK

Add the ZipoApps Proxy SDK to an Android app so third-party API keys are injected
server-side via Cloud Functions instead of being embedded in the APK.

## Source

[View skill on GitHub](https://github.com/yevhenZ-rounds/TheRoundersKnowledge/blob/main/skills/Proxy/SKILL.md)

## What it covers

- Adding the GitHub Packages Maven repo and `proxysdk` dependency
- Creating `ProxyUtil` with Cloud Functions URL and project number
- Implementing `ProxySetupProvider` on the `Application` class
- Building Retrofit/OkHttp clients through the proxy interceptor
- Verifying both proxied and direct-key request paths
