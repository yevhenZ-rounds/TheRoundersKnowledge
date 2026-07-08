---
name: skill-assetsfetcher
description: >
  Build a standalone Kotlin tool that downloads every asset an Android app fetches
  from its backend API (JSON + images) and lays them out ready to upload to
  DigitalOcean Spaces, so the app can later migrate from the API to DigitalOcean by
  changing only the base URLs. Use when asked to create an assets fetcher / asset
  downloader / migration tool, or to mirror a remote API's assets locally.
---

# Assets Fetcher (API → DigitalOcean migration tool)

This skill scaffolds a **self-contained Kotlin/JVM Gradle tool** inside an Android
project (folder `assetsFetcher/`) that:

1. Analyses how the app talks to its backend.
2. Downloads all remote JSON responses (per language) and all referenced images.
3. Writes them into a folder layout that **mirrors the asset URLs**, so uploading the
   output to a DigitalOcean Space is a drop-in swap — only the base URL changes in the
   app.

Downloads are **multithreaded** (coroutines + a bounded `Semaphore`), **resumable**
(existing files are skipped), and support a **test mode** (a couple of languages, a
few images) for quick verification before a full run.

**Reference implementation:** `C:\Users\ascri\AndroidStudioProjects\weight-checker\assetsFetcher`
(`Config.kt`, `network/ApiClient.kt`, `network/FileDownloader.kt`,
`fetch/AssetsFetcher.kt`, `Main.kt`, `data/Models.kt`, `data/Metadata.kt`).
When in doubt, open those files and mirror them — they are the source of truth.

---

## Step 1 — Analyse the app's API usage (do this first)

Do not write any tool code until you know exactly how the app fetches assets. Read the
app's networking layer (`Utils/Api`, Retrofit `ApiService`, image-loading adapters) and
extract:

| What | Where to look | Example (weight-checker) |
|------|---------------|--------------------------|
| Base API URL | `BlogUtils` / config constants | `https://eclix.tech/api/` |
| Endpoint(s) + verb + params | Retrofit `ApiService` | `POST temp-data-lelo`, fields `lang`, `app_name` |
| Auth | Interceptor | `Authorization: Bearer <token>` |
| Image URL layout | image path builder (`ApiUtils.getImagePath`) | `https://eclix.tech/images/bmi/<type>/<name>.webp` |
| Content types | distinct calls / suffixes | `g` general, `f` featured, `c` categories, `r` recipes |
| Which field is the image name | model classes + adapter `.load(...)` calls | item's `image` field, `? " \n` stripped |
| Languages | `res/values-*` folders + `Locale.getDefault().getLanguage()` | base codes only; region variants collapse |

**Verify against the live API with `curl` before coding** — confirm the request shape,
that images resolve, and how the server signals a missing language (200 vs 404 vs 403).
This drives the whole design.

```bash
# data endpoint
curl -s -X POST "https://<api>/<endpoint>" -H "Authorization: Bearer <token>" \
  --data-urlencode "lang=en_r" --data-urlencode "app_name=bmi/r" -o /tmp/r.json -w "%{http_code}\n"
# image
curl -s -o /dev/null -w "%{http_code} %{content_type}\n" "https://<api>/images/bmi/r/Caesar%20Salad.webp"
```

Check whether **image names differ across languages**. In weight-checker they are
identical (English keys), so each image is downloaded once (deduped). If they differ,
key images per language instead.

---

## Adapt per app — what varies vs. what stays

This skill is a **methodology + template**, not a copy-paste script. The reference
implementation encodes weight-checker's particular API shape; on a different app,
re-derive the app-specific parts from Step 1 and keep the generic machinery.

**Generic — reuse as-is (rarely changes):**

- The Gradle scaffold (standalone Kotlin/JVM app + copied wrapper).
- `FileDownloader` (skip-existing + `.part`-rename), coroutine + `Semaphore` concurrency.
- Test mode, resumability, the `metadata.json` summary.
- The principle: **output folder layout mirrors the asset URL** so upload = URL swap.
- The 403-vs-404 fallback gotcha on DigitalOcean Spaces.

**App-specific — re-derive every time (this is where apps differ):**

| Variation point | weight-checker | What to do for a different app |
|---|---|---|
| **Endpoint shape** | one POST `temp-data-lelo` + `lang`/`app_name` fields | Map each real endpoint. REST routes (`GET /categories`, `/recipes/{id}`), query params, headers → adjust `ApiClient` + `Config.TYPES`. |
| **Pagination** | none (whole list per call) | If paged, loop pages until empty/last (see the wallpaper-downloader `page`/`limit` pattern) before collecting assets. |
| **Localization** | per-language files, discovery by probing | If not localized, drop the language dimension entirely: no `_<lang>`, no discovery, layout becomes `data/<type>.json`. |
| **Asset reference** | item `image` field = a *name*, `? " \n` stripped, `.webp` appended | Could be a full URL (download verbatim), a relative path, or an ID. Build the URL/filename to match exactly what the app requests. |
| **Extensions / sizes** | single `.webp` | Handle `.png`/`.jpg`, or multiple variants (thumbnail + full) — download each variant into its own subfolder. |
| **Dedup** | images shared across languages → dedup globally | If names differ per language, key per language instead. |
| **Auth** | `Bearer` token (public assets, optional) | Keep whatever the real API requires for downloading; static CDNs usually need none. |
| **Candidate languages / types** | from `res/values-*` + the 4 suffixes | Rebuild `Config.CANDIDATE_LANGUAGES` and `Config.TYPES` from the target app. |

If the app's networking is genuinely different (GraphQL, signed URLs, binary
protobufs, per-item detail calls), treat the reference only as structure and rewrite
`ApiClient` + `AssetsFetcher` around Step 1's findings.

---

## Step 2 — Scaffold a standalone Gradle project

Create `assetsFetcher/` as its own Kotlin/JVM app (not an Android module) and copy the
Gradle wrapper from the parent so `./gradlew` works standalone:

```bash
mkdir -p assetsFetcher/gradle/wrapper assetsFetcher/src/main/kotlin/tools/assetsfetcher/{config,data,network,fetch}
cp gradlew gradlew.bat assetsFetcher/
cp gradle/wrapper/gradle-wrapper.* assetsFetcher/gradle/wrapper/
```

`assetsFetcher/settings.gradle.kts`:

```kotlin
pluginManagement { repositories { gradlePluginPortal(); google(); mavenCentral() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "assetsFetcher"
```

`assetsFetcher/build.gradle.kts` (pin the Kotlin version to the parent's, e.g. 2.2.0):

```kotlin
plugins { kotlin("jvm") version "2.2.0"; application }
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
application { mainClass.set("tools.assetsfetcher.MainKt") }
kotlin { jvmToolchain(17) }
```

Add `assetsFetcher/.gitignore` for `/downloads/`, `/.gradle/`, `/build/`.

---

## Step 3 — Implement the tool

Mirror the reference files. The pieces and their responsibilities:

- **`config/Config.kt`** — every tunable: base URLs, auth token, `APP_NAME`,
  `PARALLEL_DOWNLOADS` (16) / `PARALLEL_METADATA` (8), test limits, the list of
  content types `(suffix, label, nested)`, and the candidate language list (derived
  from `res/values-*`).
- **`data/Models.kt`** — minimal parsing models. Only the `image` field is read; the
  rest of each response is preserved by saving the raw JSON verbatim. Two shapes:
  flat list of items, and nested `{category, data:[…]}`.
- **`data/Metadata.kt`** — the run-summary written to `downloads/metadata.json`
  (languages, per-type counts, and how to rebuild URLs on DigitalOcean).
- **`network/ApiClient.kt`** — one OkHttp call reproducing the app's request (verb,
  fields, auth header). Returns `(code, body)`.
- **`network/FileDownloader.kt`** — downloads a URL to a file. Skips existing files;
  writes to a `.part` temp file then renames so an interrupted run never leaves a
  half-written file treated as complete. Build the URL with `HttpUrl.Builder`
  /`addPathSegment` so spaces and special characters in image names are encoded.
- **`fetch/AssetsFetcher.kt`** — the orchestrator (see Step 4).
- **`Main.kt`** — arg parsing: `test` / `download` / `download <langs…>`, plus a usage
  block.

### Reproduce the app's image-name cleaning exactly

The on-disk filename **must equal** what the app requests so the DigitalOcean object key
matches. Apply the same transform the app uses and nothing more:

```kotlin
private fun cleanImageName(name: String): String =
    name.replace("?", "").replace("\"", "").replace("\n", "")   // matches ApiUtils.getImagePath
// on-disk / DO key = cleanImageName(name) + ".webp"
```

---

## Step 4 — Orchestration (AssetsFetcher.run)

Three stages, each parallelised with `runBlocking` + `launch(Dispatchers.IO)` guarded by
a `Semaphore.withPermit`:

1. **Discover languages** — probe every candidate language against one always-present
   type; keep those returning HTTP 200. (Or accept an explicit language override.)
2. **Fetch JSON** — for every `(language, type)`: save the raw body to
   `data/<type>/<lang>.json` and collect the referenced image names into a
   per-type `ConcurrentHashMap.newKeySet()` (dedupes across languages).
3. **Download images** — for each type, download every unique name to
   `images/<appName>/<type>/<name>.webp`.

Then write `downloads/metadata.json` and print a summary (downloaded / skipped / failed).

Test mode: restrict to `Config.TEST_LANGUAGES` and `take(TEST_IMAGE_COUNT)` per type.

---

## Output layout (upload-ready for DigitalOcean)

```
downloads/
├── metadata.json                 run summary + URL-rebuild note
├── data/
│   ├── g/  en.json  es.json  …    one JSON per language, per type
│   ├── f/  …
│   ├── c/  …
│   └── r/  …
└── images/
    └── <appName>/
        ├── g/  <name>.webp
        ├── f/  <name>.webp
        ├── c/  <name>.webp
        └── r/  <name>.webp
```

The image path is a **byte-for-byte mirror** of the app's URL. Upload `images/` to the
Space root and `<space-url>/images/<appName>/<type>/<name>.webp` reproduces the original
image URL; serve `data/<type>/<lang>.json` statically to replace the data endpoint.

---

## Examples

Run from the `assetsFetcher/` folder:

```bash
# 1. Test first — a couple of languages, max 5 images per type. Fast sanity check.
./gradlew run --args="test"

# 2. Test a specific language
./gradlew run --args="test en"

# 3. Full download — auto-discovers every available language, downloads everything
./gradlew run --args="download"

# 4. Full download restricted to specific languages (skips discovery)
./gradlew run --args="download en es fr"
```

Expected test output (weight-checker):

```
== STAGE 1: metadata (JSON) ==
  [OK] recipes/en  (336 images referenced)
  [OK] categories/en  (190 images referenced)
  ...
== STAGE 2: images ==
  recipes: 5 images -> images/bmi/r/
  .....
DONE
  Images downloaded: 20
  Already existed:   0
  Failed:            0
```

Re-running is resumable — a second `test en` prints `downloaded: 0, skipped: 20`.

---

## Migrating the app to DigitalOcean (the payoff)

Once the output is uploaded to the Space, point the app at it. Minimal app changes:

- **Images:** change the image base in the path builder to the Space URL, e.g.
  `PhUtils.getDigitalOceanUrl() + "/images/" + APP_NAME + "/" + type + "/" + name + ".webp"`.
- **JSON:** switch the Retrofit calls from the old `POST` endpoint to `GET @Url`
  absolute URLs: `<space-url>/data/<type>/<lang>.json`.
- **Gotcha — 403 vs 404:** the app's "fall back to English for missing languages" logic
  usually checks for **404**. **DigitalOcean Spaces returns 403** for a nonexistent
  object, not 404. Update the fallback to treat **both** 403 and 404 as "not available
  in this language", or unsupported-language devices get nothing instead of English.
- Public Spaces need no auth, so the old auth interceptor/token can be removed.

---

## Checklist

- [ ] API usage analysed and confirmed with `curl` (request shape, image URLs, missing-lang code)
- [ ] Decided whether images are shared across languages (dedup) or per-language
- [ ] `assetsFetcher/` scaffolded as standalone Kotlin/JVM app with copied wrapper
- [ ] Config holds all URLs/auth/parallelism/test limits/type list/candidate languages
- [ ] Image-name cleaning reproduces the app's transform exactly (DO key matches)
- [ ] Downloads are parallel (Semaphore), resumable (skip existing, `.part` rename)
- [ ] Test mode limits languages + images
- [ ] Output layout mirrors the asset URLs; `metadata.json` written
- [ ] Verified with `./gradlew run --args="test"` and a resume re-run
