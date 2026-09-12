# TurnstileKit — Android SDK

A minimal Android library for embedding [Cloudflare Turnstile](https://developers.cloudflare.com/turnstile/) challenges inside any Android app.

```
TurnstileSDK.call(...)  →  dialog opens  →  user passes challenge
                                          →  onSuccess(contextId)
                                          →  TurnstileSDK.report(contextId)  →  token
```

---

## Contents

```
TurnstileKit/
├── turnstile-sdk/          ← the library (import this)
│   └── src/main/java/com/turnstilekit/
│       ├── TurnstileSDK.kt       public entry point
│       ├── TurnstileCallback.kt  success / failure interface
│       ├── TurnstileResult.kt    token + metadata data class
│       └── TurnstileDialog.kt    internal — WebView dialog
└── app/                    ← demo app showing end-to-end usage
```

---

## Installation

### Option A — from source (recommended for development)

1. Copy the `turnstile-sdk/` folder into your project root.
2. In your root `settings.gradle.kts`, add:
   ```kotlin
   include(":turnstile-sdk")
   ```
3. In your app module's `build.gradle.kts`, add:
   ```kotlin
   dependencies {
       implementation(project(":turnstile-sdk"))
   }
   ```

### Option B — AAR

Build the AAR once:
```bash
./gradlew :turnstile-sdk:assembleRelease
# Output: turnstile-sdk/build/outputs/aar/turnstile-sdk-release.aar
```

Copy the AAR into your app's `libs/` folder, then:
```kotlin
// app/build.gradle.kts
dependencies {
    implementation(files("libs/turnstile-sdk-release.aar"))

    // These transitive deps are required:
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("com.google.android.material:material:1.11.0")
}
```

> **Note:** The SDK declares `INTERNET` permission in its own manifest; it is
> merged into your app automatically — you do not need to add it manually.

---

## Usage

### 1 — call

Open a Turnstile dialog. The `url` parameter must be the domain your sitekey
is registered for on the Cloudflare dashboard (used as the page origin so
Cloudflare's domain check passes).

```kotlin
TurnstileSDK.call(
    activity = this,               // any FragmentActivity / AppCompatActivity
    url      = "https://your-site.com",
    sitekey  = "0x4AAAAAAA...",
    callback = object : TurnstileCallback {

        override fun onSuccess(contextId: String) {
            // contextId is your handle — use it to get the token
        }

        override fun onFailure(error: String) {
            // error is one of:
            //   "cancelled"      — user dismissed the dialog
            //   "load_timeout"   — Turnstile JS did not load within 20 s
            //   "token_expired"  — widget expired before user completed it
            //   Cloudflare code  — e.g. "110200"
        }
    }
)
```

### 2 — report

Retrieve the Turnstile token at any time after `onSuccess`:

```kotlin
val result: TurnstileResult? = TurnstileSDK.report(contextId)

result?.token       // the token string — send this to your server
result?.createdAt   // unix epoch ms when the token was obtained
result?.isExpired() // true if older than 4 minutes (configurable)
```

### 3 — send the token to your server

Your backend verifies the token with Cloudflare's API:

```
POST https://challenges.cloudflare.com/turnstile/v0/siteverify
Content-Type: application/json

{
  "secret":   "YOUR_SECRET_KEY",
  "response": "<token from result.token>"
}
```

A successful response looks like `{ "success": true, ... }`.

### 4 — clean up

Remove the token from memory after your server call succeeds:

```kotlin
TurnstileSDK.clear(contextId)    // remove one entry
TurnstileSDK.clearAll()          // remove all entries (e.g. on sign-out)
```

---

## Complete example

```kotlin
class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ...

        binding.loginButton.setOnClickListener {
            TurnstileSDK.call(
                activity = this,
                url      = "https://my-app.com",
                sitekey  = BuildConfig.TURNSTILE_SITEKEY,
                callback = object : TurnstileCallback {
                    override fun onSuccess(contextId: String) {
                        val token = TurnstileSDK.report(contextId)?.token ?: return
                        TurnstileSDK.clear(contextId)
                        submitLogin(token)
                    }
                    override fun onFailure(error: String) {
                        showError("Verification failed: $error")
                    }
                }
            )
        }
    }

    private fun submitLogin(turnstileToken: String) {
        // POST turnstileToken to your server along with credentials
    }
}
```

---

## API reference

### `TurnstileSDK` (object)

| Method | Description |
|---|---|
| `call(activity, url, sitekey, callback)` | Opens the verification dialog |
| `report(contextId): TurnstileResult?` | Returns the token result, or `null` if unknown/cleared |
| `clear(contextId)` | Removes one stored result |
| `clearAll()` | Removes all stored results |

### `TurnstileCallback` (interface)

| Method | When called |
|---|---|
| `onSuccess(contextId: String)` | User passed the challenge |
| `onFailure(error: String)` | Challenge failed, timed out, or was cancelled |

### `TurnstileResult` (data class)

| Property | Type | Description |
|---|---|---|
| `contextId` | `String` | Opaque ID matching the one from `onSuccess` |
| `token` | `String` | The Turnstile token — submit this to your server |
| `createdAt` | `Long` | Unix epoch ms when the token was created |
| `isExpired(ttlMs)` | `Boolean` | Whether the token is older than `ttlMs` ms (default: 4 min) |

---

## Test sitekeys (Cloudflare-provided)

Use these during development — no Cloudflare account needed:

| Sitekey | Behaviour |
|---|---|
| `1x00000000000000000000AA` | Always passes immediately |
| `2x00000000000000000000AB` | Always fails / blocks |
| `3x00000000000000000000FF` | Forces an interactive challenge |

The demo app ships with `1x00000000000000000000AA` pre-filled.

---

## How it works

1. `call()` creates a `DialogFragment` containing a `WebView`.
2. The WebView loads a self-contained HTML page using
   `WebView.loadDataWithBaseURL(url, …)` — this sets the page's **origin** to
   `url`, so Cloudflare's domain verification sees the correct domain.
3. The Turnstile widget communicates back via a `@JavascriptInterface`
   (`TurnstileInterface`).
4. On success the SDK generates a UUID `contextId`, stores
   `TurnstileResult(contextId, token)` in a `ConcurrentHashMap`, and calls
   `onSuccess(contextId)`.
5. `report(contextId)` is a simple map lookup.

---

## Requirements

| Item | Minimum |
|---|---|
| Android SDK | 21 (Android 5.0 Lollipop) |
| Kotlin | 1.9 |
| Activity type | `FragmentActivity` (`AppCompatActivity` is fine) |
| Network | INTERNET permission (declared by the library) |

---

## Notes

- Tokens are stored **in memory only** — they are lost when the process dies.
- `report()` returns `null` if `clear()` was already called or if the
  contextId is unknown.
- If the user rotates the screen while the dialog is open, the dialog
  auto-dismisses (the callback loses its reference across config changes).
  Your app should call `call()` again to start a new verification.
- For invisible Turnstile widgets (where no human interaction is needed), the
  dialog will appear briefly and close automatically once the challenge
  completes.
