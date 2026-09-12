# TurnstileKit

> A lightweight Android SDK for embedding [Cloudflare Turnstile](https://developers.cloudflare.com/turnstile/) bot-protection challenges inside any Android app.

```
call()  →  dialog opens  →  user passes challenge  →  onSuccess(contextId)
                                                    →  report(contextId)  →  token
                                                    →  send token to server
                                                    →  clear(contextId)
```

---

## Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
  - [Option A — AAR (recommended)](#option-a--aar-recommended)
  - [Option B — Source module](#option-b--source-module)
  - [Option C — GitHub Packages (Maven)](#option-c--github-packages-maven)
- [Getting a Sitekey](#getting-a-sitekey)
- [Testing the AAR](#testing-the-aar)
- [Usage](#usage)
  - [Step 1 · Start verification — `call()`](#step-1--start-verification--call)
  - [Step 2 · Retrieve the token — `report()`](#step-2--retrieve-the-token--report)
  - [Step 3 · Send the token to your server](#step-3--send-the-token-to-your-server)
  - [Step 4 · Clean up — `clear()` / `clearAll()`](#step-4--clean-up--clear--clearall)
  - [Check token expiry — `isExpired()`](#check-token-expiry--isexpired)
  - [Session reset — `clearAll()`](#session-reset--clearall)
- [Complete Example](#complete-example)
- [Java Usage](#java-usage)
- [API Reference](#api-reference)
- [Test Sitekeys](#test-sitekeys)
- [Error Codes](#error-codes)
- [How It Works](#how-it-works)
- [Behaviour Notes](#behaviour-notes)
- [Project Structure](#project-structure)
- [Building from Source](#building-from-source)

---

## Requirements

| Item | Minimum |
|---|---|
| Android minSdk | 21 (Android 5.0 Lollipop) |
| compileSdk | 34 |
| Kotlin | 1.9+ |
| Activity base class | `FragmentActivity` (all `AppCompatActivity` subclasses qualify) |
| Network | `INTERNET` permission — declared by the SDK, merged automatically |

---

## Installation

### Option A — AAR (recommended)

1. Copy `turnstile-sdk-release.aar` into your app's `libs/` folder.

2. In your **app-level** `build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/turnstile-sdk-release.aar"))

    // Required transitive dependencies
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("com.google.android.material:material:1.11.0")
}
```

3. Make sure your **root** `gradle.properties` contains:

```properties
android.useAndroidX=true
android.enableJetifier=true
```

---

### Option B — Source module

1. Copy the `turnstile-sdk/` folder into your project root.

2. In your root `settings.gradle.kts`:

```kotlin
include(":app", ":turnstile-sdk")
```

3. In your **app-level** `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":turnstile-sdk"))
}
```

---

### Option C — GitHub Packages (Maven)

Add the GitHub Packages repository and dependency:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            name = "GitHubPackages"
            url  = uri("https://maven.pkg.github.com/minatox-x/turnstilekit")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                            ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull
                            ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.minatox-x:turnstile-sdk:1.0.0")
}
```

> GitHub Packages requires authentication even for public packages. Add your GitHub username and a Personal Access Token (with `read:packages` scope) to `~/.gradle/gradle.properties`:
> ```
> gpr.user=YOUR_GITHUB_USERNAME
> gpr.key=YOUR_GITHUB_PAT
> ```

---

## Getting a Sitekey

1. Go to the [Cloudflare Dashboard](https://dash.cloudflare.com/) → **Turnstile**.
2. Click **Add widget**, give it a name, and add your domain (e.g. `my-app.com`).
3. Copy the **Sitekey** (public, used in the SDK) and **Secret Key** (private, used on your server).

> During development use the [test sitekeys](#test-sitekeys) below — no Cloudflare account needed.

---

## Testing the AAR

Before integrating into your real app, verify the AAR works with the included demo app or a fresh test project.

### Using the demo app

The `app/` module is a fully working demo. To run it:

```bash
# From the project root
./gradlew :app:installDebug
```

Open the app on a device or emulator, enter any [test sitekey](#test-sitekeys) with its matching URL, and tap **Run Verification**. You should see a dialog appear, complete automatically (for the always-pass key), and display the token.

### Manual AAR test in a new project

1. In Android Studio: **File → New → New Project** (Empty Activity).
2. Create a `libs/` folder in your `app/` module directory and paste `turnstile-sdk-release.aar` into it.
3. Add the dependencies from [Option A](#option-a--aar-recommended) above.
4. Sync Gradle.
5. Paste this minimal test into your `MainActivity`:

```kotlin
import com.turnstilekit.TurnstileCallback
import com.turnstilekit.TurnstileSDK

// Inside any button click or onCreate:
TurnstileSDK.call(
    activity = this,
    url      = "https://www.cloudflare.com",
    sitekey  = "1x00000000000000000000AA",   // always-pass test key
    callback = object : TurnstileCallback {
        override fun onSuccess(contextId: String) {
            val token = TurnstileSDK.report(contextId)?.token
            android.util.Log.d("Turnstile", "Token: $token")
            TurnstileSDK.clear(contextId)
        }
        override fun onFailure(error: String) {
            android.util.Log.e("Turnstile", "Error: $error")
        }
    }
)
```

6. Run on a **real device or an emulator with network access**. Check Logcat for `Token:` — a long string confirms the AAR is working correctly.

> **Emulator tip:** Cloudflare's Turnstile script is loaded over the network. Make sure the emulator has internet access (the default Google AVD image does). The always-pass test key (`1x00000000000000000000AA`) completes without any user interaction so you will see the token immediately.

---

## Usage

The full verification flow has four steps: **call → report → send to server → clear**.

### Step 1 · Start verification — `call()`

Opens a floating `DialogFragment` containing a WebView with the Turnstile widget. The `url` must be the domain your sitekey is registered for on the Cloudflare dashboard — it is used as the page origin so Cloudflare's domain check passes.

```kotlin
TurnstileSDK.call(
    activity = this,                        // FragmentActivity or AppCompatActivity
    url      = "https://my-app.com",        // must match your sitekey's registered domain
    sitekey  = "0x4AAAAAAA...",             // your Cloudflare Turnstile sitekey
    callback = object : TurnstileCallback {

        override fun onSuccess(contextId: String) {
            // Called on the main thread after the user passes the challenge.
            // contextId is your opaque handle to retrieve the token.
        }

        override fun onFailure(error: String) {
            // Called on the main thread if the challenge fails, times out,
            // or the user dismisses the dialog.
            // See Error Codes section for possible values.
        }
    }
)
```

**Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `activity` | `FragmentActivity` | The current activity. All `AppCompatActivity` subclasses qualify. |
| `url` | `String` | Base URL of the site the sitekey is registered for. Must start with `http://` or `https://`. |
| `sitekey` | `String` | Your Cloudflare Turnstile site key. Must not be blank. |
| `callback` | `TurnstileCallback` | Receives `onSuccess` or `onFailure` — exactly one is called per invocation. |

> `call()` throws `IllegalArgumentException` immediately (before showing anything) if `url` does not start with `http://` or `https://`, or if `sitekey` is blank.

---

### Step 2 · Retrieve the token — `report()`

After `onSuccess`, call `report()` with the `contextId` to get the full result object including the token string.

```kotlin
override fun onSuccess(contextId: String) {
    val result: TurnstileResult? = TurnstileSDK.report(contextId)

    val token     = result?.token       // the token — send this to your server
    val createdAt = result?.createdAt   // unix epoch ms when the token was obtained
    val expired   = result?.isExpired() // true if older than 4 minutes
}
```

`report()` returns `null` if the `contextId` is unknown or has already been cleared.

---

### Step 3 · Send the token to your server

The token must be verified server-side using Cloudflare's siteverify API. Never trust the token client-side.

```
POST https://challenges.cloudflare.com/turnstile/v0/siteverify
Content-Type: application/json

{
  "secret":   "YOUR_SECRET_KEY",
  "response": "<token from result.token>",
  "remoteip": "<optional: end user's IP address>"
}
```

A successful response:

```json
{
  "success": true,
  "challenge_ts": "2024-01-01T00:00:00Z",
  "hostname": "my-app.com",
  "error-codes": []
}
```

Typical Android implementation using your existing HTTP client:

```kotlin
override fun onSuccess(contextId: String) {
    val token = TurnstileSDK.report(contextId)?.token ?: return
    TurnstileSDK.clear(contextId)   // clean up before the network call

    // Fire and forget, or use your preferred coroutine / Retrofit / OkHttp setup
    lifecycleScope.launch(Dispatchers.IO) {
        val verified = myApiClient.verifyTurnstile(token)
        withContext(Dispatchers.Main) {
            if (verified) proceedWithLogin() else showError("Bot check failed")
        }
    }
}
```

---

### Step 4 · Clean up — `clear()` / `clearAll()`

Tokens are stored in memory. Remove them after your server call to avoid holding them longer than necessary.

```kotlin
// Remove a single token by contextId
TurnstileSDK.clear(contextId)

// Remove ALL stored tokens (e.g. on sign-out or session reset)
TurnstileSDK.clearAll()
```

---

### Check token expiry — `isExpired()`

Cloudflare invalidates Turnstile tokens server-side after ~300 seconds. Use `isExpired()` to check client-side before making a network call.

```kotlin
val result = TurnstileSDK.report(contextId)

// Default TTL: 4 minutes (conservative, leaves time for your network call)
if (result?.isExpired() == true) {
    // Token is stale — call TurnstileSDK.call() again
    TurnstileSDK.clear(contextId)
    startVerificationAgain()
    return
}

// Custom TTL: check if token is older than 2 minutes
if (result?.isExpired(ttlMs = 2 * 60 * 1_000L) == true) {
    // handle stale token
}
```

---

### Session reset — `clearAll()`

On sign-out or whenever you want to wipe all pending tokens at once:

```kotlin
override fun onSignOut() {
    TurnstileSDK.clearAll()
    // continue with sign-out flow
}
```

---

## Complete Example

A login screen that runs Turnstile before submitting credentials:

```kotlin
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener { runTurnstileChallenge() }
    }

    private fun runTurnstileChallenge() {
        binding.btnLogin.isEnabled = false

        TurnstileSDK.call(
            activity = this,
            url      = "https://my-app.com",
            sitekey  = BuildConfig.TURNSTILE_SITEKEY,
            callback = object : TurnstileCallback {

                override fun onSuccess(contextId: String) {
                    val result = TurnstileSDK.report(contextId)

                    if (result == null || result.isExpired()) {
                        // Shouldn't happen in practice, but guard anyway
                        TurnstileSDK.clear(contextId)
                        showError("Verification expired. Please try again.")
                        binding.btnLogin.isEnabled = true
                        return
                    }

                    val token = result.token
                    TurnstileSDK.clear(contextId)   // done with it
                    submitLogin(token)
                }

                override fun onFailure(error: String) {
                    binding.btnLogin.isEnabled = true
                    when (error) {
                        "cancelled"    -> { /* user dismissed — do nothing */ }
                        "load_timeout" -> showError("No internet connection. Please try again.")
                        "token_expired"-> showError("Verification expired. Please try again.")
                        else           -> showError("Verification failed ($error). Please try again.")
                    }
                }
            }
        )
    }

    private fun submitLogin(turnstileToken: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val success = myApiClient.login(
                    email    = binding.etEmail.text.toString(),
                    password = binding.etPassword.text.toString(),
                    turnstileToken = turnstileToken
                )
                withContext(Dispatchers.Main) {
                    if (success) {
                        startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                        finish()
                    } else {
                        showError("Login failed.")
                        binding.btnLogin.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("Network error: ${e.message}")
                    binding.btnLogin.isEnabled = true
                }
            }
        }
    }

    private fun showError(message: String) {
        binding.tvError.text    = message
        binding.tvError.visibility = View.VISIBLE
    }
}
```

---

## Java Usage

All public methods are annotated with `@JvmStatic` and work from Java without any wrappers:

```java
TurnstileSDK.call(
    this,
    "https://my-app.com",
    "0x4AAAAAAA...",
    new TurnstileCallback() {
        @Override
        public void onSuccess(@NonNull String contextId) {
            TurnstileResult result = TurnstileSDK.report(contextId);
            if (result != null) {
                String token = result.getToken();
                sendToServer(token);
            }
            TurnstileSDK.clear(contextId);
        }

        @Override
        public void onFailure(@NonNull String error) {
            Log.e("Turnstile", "Failed: " + error);
        }
    }
);
```

---

## API Reference

### `TurnstileSDK` (singleton object)

| Method | Returns | Description |
|---|---|---|
| `call(activity, url, sitekey, callback)` | `Unit` | Opens the Turnstile challenge dialog. Throws `IllegalArgumentException` for invalid `url` or blank `sitekey`. |
| `report(contextId: String)` | `TurnstileResult?` | Returns the stored result for the given ID, or `null` if unknown or already cleared. |
| `clear(contextId: String)` | `Unit` | Removes one result from the in-memory store. Call after your server round-trip completes. |
| `clearAll()` | `Unit` | Removes all stored results. Use on sign-out or session reset. |

---

### `TurnstileCallback` (interface)

Exactly one method is called per `call()` invocation, always on the **main thread**.

| Method | When called |
|---|---|
| `onSuccess(contextId: String)` | User passed the Turnstile challenge. `contextId` is the key for `report()`. |
| `onFailure(error: String)` | Challenge failed, timed out, or user dismissed the dialog. See [Error Codes](#error-codes). |

---

### `TurnstileResult` (data class)

| Member | Type | Description |
|---|---|---|
| `contextId` | `String` | Opaque UUID matching the one passed to `onSuccess`. |
| `token` | `String` | The Cloudflare Turnstile token. Submit this to your server for verification. |
| `createdAt` | `Long` | Unix epoch milliseconds when the token was obtained. |
| `isExpired(ttlMs: Long)` | `Boolean` | Returns `true` when the token is older than `ttlMs` ms. Default TTL is 4 minutes (240,000 ms). |

---

## Test Sitekeys

Use these during development — no Cloudflare account or domain setup needed:

| Sitekey | URL to use | Behaviour |
|---|---|---|
| `1x00000000000000000000AA` | `https://www.cloudflare.com` | Always passes immediately — no interaction needed |
| `2x00000000000000000000AB` | `https://www.cloudflare.com` | Always fails / blocks |
| `3x00000000000000000000FF` | `https://www.cloudflare.com` | Forces an interactive challenge (checkbox) |

The demo app ships with the always-pass key pre-filled so you can verify the integration end-to-end without a Cloudflare account.

---

## Error Codes

These are the values passed to `onFailure(error: String)`:

| Error value | Cause |
|---|---|
| `"cancelled"` | User dismissed the dialog by tapping outside it |
| `"load_timeout"` | Cloudflare's Turnstile script did not load within 20 seconds (no network / blocked) |
| `"token_expired"` | The widget expired before the user completed the challenge |
| `"110200"`, `"110420"`, etc. | Cloudflare-specific error codes — see [Turnstile error codes](https://developers.cloudflare.com/turnstile/troubleshooting/client-side-errors/) |

---

## How It Works

```
┌─ Your Activity ──────────────────────────────────────────────────────┐
│                                                                       │
│  TurnstileSDK.call(activity, url, sitekey, callback)                 │
│         │                                                             │
│         ▼                                                             │
│  TurnstileDialog (DialogFragment)                                     │
│  ┌─────────────────────────────────────────────────────────────┐     │
│  │  WebView                                                    │     │
│  │  loadDataWithBaseURL(url, html)  ← sets page origin to url │     │
│  │                                                             │     │
│  │  <script src="challenges.cloudflare.com/turnstile/...">    │     │
│  │                                                             │     │
│  │  turnstile.render() ──success──► TurnstileInterface         │     │
│  │                     ──error───► TurnstileInterface         │     │
│  │                     ──height──► resize dialog window       │     │
│  └─────────────────────────────────────────────────────────────┘     │
│         │                                                             │
│         ▼ (on main thread)                                            │
│  store[contextId] = TurnstileResult(contextId, token, createdAt)     │
│  callback.onSuccess(contextId)  ──► your code                        │
│                                                                       │
│  TurnstileSDK.report(contextId)  →  TurnstileResult                  │
│  TurnstileSDK.clear(contextId)   →  removes from ConcurrentHashMap   │
└───────────────────────────────────────────────────────────────────────┘
```

1. `call()` creates a `TurnstileDialog` (`DialogFragment`) and attaches a listener before calling `show()`.
2. The dialog's `WebView` uses `loadDataWithBaseURL(url, …)` to load a self-contained HTML page whose **origin** is set to your `url` — so Cloudflare's domain verification passes for your registered sitekey.
3. A 20-second timeout guard fires `onError('load_timeout')` if the Cloudflare script never loads (e.g. no network).
4. After the widget renders, JS reports the widget's pixel height so the dialog window resizes to fit snugly (capped at 350 dp).
5. On success, the SDK generates a UUID `contextId`, stores `TurnstileResult` in a `ConcurrentHashMap`, and invokes `onSuccess(contextId)` on the main thread.
6. `report(contextId)` is a simple thread-safe map lookup.
7. The listener fires **at most once** per dialog — duplicate callbacks are suppressed by an `emitted` flag.
8. If the activity is recreated (screen rotation) while the dialog is open, the dialog auto-dismisses because the `listener` reference is `null` — preventing orphaned UI or stale callbacks.

---

## Behaviour Notes

- **Memory only.** Tokens are stored in a `ConcurrentHashMap` and are never written to disk. They are lost when the app process dies.
- **One callback per call.** Exactly one of `onSuccess` or `onFailure` is called per `call()` invocation — never both, never twice.
- **Main thread callbacks.** Both `onSuccess` and `onFailure` are always dispatched on the main thread — safe to update UI directly.
- **Config changes.** If the user rotates the screen while the dialog is open, the dialog auto-dismisses and `onFailure("cancelled")` is NOT called — the dialog simply closes silently. Call `call()` again to restart verification.
- **Invisible Turnstile.** For sitekeys configured as "Invisible" on the Cloudflare dashboard, the dialog will appear briefly and close automatically once the silent challenge completes.
- **`report()` after `clear()`.** Returns `null`. Always call `report()` before `clear()`.
- **`clearAll()` on sign-out.** If you hold multiple pending tokens across flows, call `clearAll()` on sign-out to prevent token leaks across user sessions.

---

## Project Structure

```
TurnstileKit/
├── turnstile-sdk/                      ← the library module (ship this)
│   ├── build.gradle.kts
│   ├── consumer-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml         declares INTERNET permission
│       └── java/com/turnstilekit/
│           ├── TurnstileSDK.kt         public singleton — call(), report(), clear(), clearAll()
│           ├── TurnstileCallback.kt    public interface — onSuccess(), onFailure()
│           ├── TurnstileResult.kt      public data class — token, createdAt, isExpired()
│           └── TurnstileDialog.kt      internal DialogFragment + WebView + JS bridge
│
├── app/                                ← demo app (not shipped)
│   └── src/main/java/com/turnstilekit/demo/
│       └── MainActivity.kt             end-to-end usage example
│
├── build.gradle.kts                    root — plugin versions only
├── settings.gradle.kts
├── gradle.properties                   useAndroidX=true, enableJetifier=true
├── gradlew / gradlew.bat
└── gradle/wrapper/
    ├── gradle-wrapper.jar
    └── gradle-wrapper.properties       Gradle 8.6
```

---

## Building from Source

```bash
# Clone
git clone https://github.com/minatox-x/turnstilekit.git
cd turnstilekit

# Build the release AAR
./gradlew :turnstile-sdk:assembleRelease

# Output
turnstile-sdk/build/outputs/aar/turnstile-sdk-release.aar

# Run the demo app on a connected device / emulator
./gradlew :app:installDebug

# Publish to GitHub Packages (CI does this automatically on version tag push)
SDK_VERSION=1.0.0 ./gradlew :turnstile-sdk:publish
```

**Triggering a release via CI:**

```bash
git tag v1.0.0
git push origin v1.0.0
# → release.yml builds the AAR, creates a GitHub Release, and publishes to GitHub Packages
```

---

## License

```
MIT License

Copyright (c) 2024 minatox-x

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
