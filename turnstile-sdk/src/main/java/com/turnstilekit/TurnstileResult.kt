package com.turnstilekit

/**
 * Holds the Turnstile token from a successful challenge.
 *
 * Retrieve it with [TurnstileSDK.report].
 *
 * @property contextId  The ID that maps to this result (returned by [TurnstileCallback.onSuccess]).
 * @property token      The Cloudflare Turnstile token. Send this to your server for verification.
 * @property createdAt  Unix epoch milliseconds when the token was obtained.
 */
data class TurnstileResult(
    val contextId: String,
    val token: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Returns `true` when the token is older than [ttlMs] milliseconds.
     *
     * Cloudflare invalidates Turnstile tokens server-side after ~300 seconds.
     * The conservative default of 4 minutes leaves time for your network call.
     */
    fun isExpired(ttlMs: Long = 4 * 60 * 1_000L): Boolean =
        System.currentTimeMillis() - createdAt > ttlMs
}
