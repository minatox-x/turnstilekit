package com.turnstilekit.demo

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.turnstilekit.TurnstileCallback
import com.turnstilekit.TurnstileSDK
import com.turnstilekit.demo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVerify.setOnClickListener { startVerification() }
    }

    private fun startVerification() {
        val url     = binding.etUrl.text?.toString()?.trim()     ?: ""
        val sitekey = binding.etSitekey.text?.toString()?.trim() ?: ""

        if (url.isEmpty() || sitekey.isEmpty()) {
            showResult(success = false, title = "Input error", body = "Please fill in both fields.")
            return
        }

        // Disable the button while the dialog is open.
        binding.btnVerify.isEnabled   = false
        binding.cardResult.visibility = View.GONE

        // ── Step 1: call ──────────────────────────────────────────────────────
        TurnstileSDK.call(
            activity = this,
            url      = url,
            sitekey  = sitekey,
            callback = object : TurnstileCallback {

                override fun onSuccess(contextId: String) {
                    // ── Step 2: report ────────────────────────────────────────
                    val result = TurnstileSDK.report(contextId)

                    val body = buildString {
                        appendLine("Context ID:")
                        appendLine(contextId)
                        appendLine()
                        appendLine("Token:")
                        appendLine(result?.token ?: "(not found)")
                        appendLine()
                        appendLine("Obtained at: ${result?.createdAt}")
                        appendLine("Expired?     ${result?.isExpired()}")
                    }

                    showResult(success = true, title = "✓  Verification passed", body = body)

                    // ── Step 3: clean up (do this after your server call) ─────
                    TurnstileSDK.clear(contextId)

                    binding.btnVerify.isEnabled = true
                }

                override fun onFailure(error: String) {
                    showResult(
                        success = false,
                        title   = "✗  Verification failed",
                        body    = "Error: $error"
                    )
                    binding.btnVerify.isEnabled = true
                }
            }
        )
    }

    private fun showResult(success: Boolean, title: String, body: String) {
        binding.cardResult.visibility = View.VISIBLE
        binding.tvResultTitle.text    = title
        binding.tvResultBody.text     = body.trim()
        binding.tvResultTitle.setTextColor(
            if (success) 0xFF1A7A1A.toInt() else 0xFFB00020.toInt()
        )
    }
}
