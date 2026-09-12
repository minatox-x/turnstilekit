package com.turnstilekit

/**
 * Callback for [TurnstileSDK.call].
 *
 * Exactly one method is called per [TurnstileSDK.call] invocation.
 */
interface TurnstileCallback {

    /**
     * The user passed the Turnstile challenge.
     *
     * @param contextId Opaque ID. Pass it to [TurnstileSDK.report] to retrieve the token.
     */
    fun onSuccess(contextId: String)

    /**
     * The challenge failed, timed out, or the user cancelled the dialog.
     *
     * Common [error] values:
     * - `"cancelled"` — user dismissed the dialog
     * - `"load_timeout"` — Turnstile script did not load within 20 s
     * - `"token_expired"` — token expired before it was used
     * - Cloudflare error codes (e.g. `"110200"`)
     */
    fun onFailure(error: String)
}
