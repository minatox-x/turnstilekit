package com.turnstilekit

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.DialogFragment

// ── Internal result type ────────────────────────────────────────────────────

internal sealed class TurnstileInternalResult {
    data class Success(val token: String) : TurnstileInternalResult()
    data class Failure(val error: String) : TurnstileInternalResult()
}

// ── Dialog ──────────────────────────────────────────────────────────────────

internal class TurnstileDialog : DialogFragment() {

    companion object {
        private const val ARG_URL     = "url"
        private const val ARG_SITEKEY = "sitekey"

        fun newInstance(url: String, sitekey: String) = TurnstileDialog().apply {
            arguments = Bundle().apply {
                putString(ARG_URL,     url)
                putString(ARG_SITEKEY, sitekey)
            }
        }
    }

    /**
     * Set by [TurnstileSDK] before [show] is called.
     * If null when the dialog is resumed (e.g. after a config change), the dialog
     * auto-dismisses so there is no orphaned UI.
     */
    var listener: ((TurnstileInternalResult) -> Unit)? = null

    private var webView: WebView? = null
    private var emitted  = false   // fire the listener exactly once

    private val baseUrl  get() = requireArguments().getString(ARG_URL)!!
    private val sitekey  get() = requireArguments().getString(ARG_SITEKEY)!!

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val wv = WebView(requireContext()).also { webView = it }

        wv.settings.apply {
            javaScriptEnabled    = true
            domStorageEnabled    = true
            useWideViewPort      = true
            loadWithOverviewMode = true
        }

        wv.setBackgroundColor(Color.WHITE)
        wv.addJavascriptInterface(JsBridge(), "TurnstileInterface")
        wv.webViewClient = WebViewClient() // keep navigation inside the WebView

        // loadDataWithBaseURL sets the origin to `baseUrl` so Cloudflare sees
        // the correct domain for the sitekey.
        wv.loadDataWithBaseURL(baseUrl, buildPage(), "text/html", "UTF-8", null)

        return wv
    }

    override fun onStart() {
        super.onStart()

        // No listener means the activity was recreated and the caller is gone.
        if (listener == null) {
            dismiss()
            return
        }

        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val dm = resources.displayMetrics
            val w  = (dm.widthPixels  * 0.92).toInt()
            val h  = (160 * dm.density).toInt()   // default; JS may resize this
            setLayout(w, h)
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        emit(TurnstileInternalResult.Failure("cancelled"))
    }

    override fun onDestroyView() {
        webView?.destroy()
        webView = null
        super.onDestroyView()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun emit(result: TurnstileInternalResult) {
        if (!emitted) {
            emitted = true
            listener?.invoke(result)
        }
    }

    /** Builds the self-contained HTML page that renders the Turnstile widget. */
    private fun buildPage(): String {
        // language=HTML
        return """
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"/>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body {
      width: 100%;
      background: #ffffff;
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100%;
      padding: 16px;
    }
    #wrap {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 10px;
    }
    #msg {
      font: 13px/1.5 -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
      color: #6b7280;
    }
  </style>
</head>
<body>
  <div id="wrap">
    <p id="msg">Loading verification…</p>
    <div id="ts-widget"></div>
  </div>

  <script>
    // Fail fast if the Cloudflare script never loads (e.g. no network).
    var _timeout = setTimeout(function () {
      TurnstileInterface.onError('load_timeout');
    }, 20000);

    function _onTurnstileReady() {
      clearTimeout(_timeout);
      document.getElementById('msg').style.display = 'none';

      turnstile.render('#ts-widget', {
        sitekey: '${sitekey}',
        callback: function (token) {
          TurnstileInterface.onSuccess(token);
        },
        'error-callback': function (code) {
          TurnstileInterface.onError(String(code));
        },
        'expired-callback': function () {
          TurnstileInterface.onError('token_expired');
        }
      });

      // After the widget renders, report its true height so Android can
      // resize the dialog window to fit snugly.
      setTimeout(function () {
        TurnstileInterface.onHeight(document.body.scrollHeight);
      }, 500);
    }
  </script>

  <script src="https://challenges.cloudflare.com/turnstile/v0/api.js?onload=_onTurnstileReady"
          async defer></script>
</body>
</html>
        """.trimIndent()
    }

    // ── JavaScript → Android bridge ──────────────────────────────────────────

    inner class JsBridge {

        /** Called by Turnstile on success. */
        @JavascriptInterface
        fun onSuccess(token: String) = runOnMain {
            emit(TurnstileInternalResult.Success(token))
            dismiss()
        }

        /** Called by Turnstile on error, or by our timeout guard. */
        @JavascriptInterface
        fun onError(error: String) = runOnMain {
            emit(TurnstileInternalResult.Failure(error))
            dismiss()
        }

        /**
         * Called ~500 ms after the widget renders with the page's scroll-height
         * in CSS pixels (≈ dp). We use it to resize the dialog to exactly fit
         * the widget without empty space.
         */
        @JavascriptInterface
        fun onHeight(cssPixels: Int) = runOnMain {
            val dm      = resources.displayMetrics
            val maxH    = (350 * dm.density).toInt()           // cap at 350 dp
            val widgetH = (cssPixels * dm.density).toInt()     // CSS px → physical px
            val padH    = (20  * dm.density).toInt()           // small breathing room
            val finalH  = (widgetH + padH).coerceAtMost(maxH)
            dialog?.window?.setLayout(
                (dm.widthPixels * 0.92).toInt(),
                finalH
            )
        }

        private fun runOnMain(block: () -> Unit) {
            activity?.runOnUiThread(block)
        }
    }
}
