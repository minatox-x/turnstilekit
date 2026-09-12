package com.turnstilekit

import androidx.fragment.app.FragmentActivity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * # TurnstileSDK
 *
 * A minimal Android SDK for embedding Cloudflare Turnstile challenges in any app.
 *
 * ## Quick start
 *
 * ```kotlin
 * // 1. Start the verification
 * TurnstileSDK.call(
 *     activity = this,
 *     url      = "https://your-site.com",   // domain registered for the sitekey
 *     sitekey  = "0x4AAAAAAA...",
 *     callback = object : TurnstileCallback {
 *         override fun onSuccess(contextId: String) {
 *             // 2. Retrieve the token
 *             val result = TurnstileSDK.report(contextId)
 *             sendToServer(result?.token)
 *
 *             // 3. Clean up
 *             TurnstileSDK.clear(contextId)
 *         }
 *         override fun onFailure(error: String) {
 *             Log.e("Turnstile", "Failed: $error")
 *         }
 *     }
 * )
 * ```
 *
 * Results are stored in memory only and are never written to disk.
 */
object TurnstileSDK {

    private val store = ConcurrentHashMap<String, TurnstileResult>()
    private const val TAG = "TurnstileSDK"

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Opens a Turnstile challenge dialog.
     *
     * The dialog is a [androidx.fragment.app.DialogFragment] backed by a WebView.
     * It loads the Turnstile widget for [sitekey] with [url] as the page origin,
     * so the domain check Cloudflare performs matches your registered domain.
     *
     * Exactly one of [TurnstileCallback.onSuccess] or [TurnstileCallback.onFailure]
     * will be called on the main thread before the dialog closes.
     *
     * @param activity The current [FragmentActivity] (all AppCompatActivity subclasses qualify).
     * @param url      Base URL of the site the sitekey is registered for.
     *                 Must start with `https://` or `http://`.
     * @param sitekey  Your Cloudflare Turnstile site key.
     * @param callback Receives success (with a [contextId]) or failure.
     *
     * @throws IllegalArgumentException if [url] or [sitekey] are blank / malformed.
     */
    @JvmStatic
    fun call(
        activity: FragmentActivity,
        url:      String,
        sitekey:  String,
        callback: TurnstileCallback
    ) {
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "url must start with http:// or https://  (got: \"$url\")"
        }
        require(sitekey.isNotBlank()) { "sitekey must not be blank" }

        val dialog = TurnstileDialog.newInstance(url, sitekey)

        dialog.listener = { result ->
            when (result) {
                is TurnstileInternalResult.Success -> {
                    val contextId = UUID.randomUUID().toString()
                    store[contextId] = TurnstileResult(
                        contextId = contextId,
                        token     = result.token,
                        createdAt = System.currentTimeMillis()
                    )
                    callback.onSuccess(contextId)
                }
                is TurnstileInternalResult.Failure -> {
                    callback.onFailure(result.error)
                }
            }
        }

        dialog.show(activity.supportFragmentManager, TAG)
    }

    /**
     * Returns the [TurnstileResult] (token + metadata) for the given [contextId].
     *
     * @param contextId The ID delivered by [TurnstileCallback.onSuccess].
     * @return The stored result, or `null` if the ID is unknown or was already [clear]ed.
     */
    @JvmStatic
    fun report(contextId: String): TurnstileResult? = store[contextId]

    /**
     * Removes the result for [contextId] from the in-memory store.
     *
     * Call this after you have successfully submitted the token to your server
     * to avoid holding tokens in memory longer than necessary.
     */
    @JvmStatic
    fun clear(contextId: String) { store.remove(contextId) }

    /**
     * Removes every stored result. Useful on sign-out or session reset.
     */
    @JvmStatic
    fun clearAll() { store.clear() }
}
