package com.example.tscpro

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
        }
        webView.clearCache(true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("https://tscpro.payment/status") || url.contains("tscpro.payment/status")) {
                    val uri = request.url
                    val status = uri.getQueryParameter("code") ?: "PAYMENT_SUCCESS"
                    val txnId = uri.getQueryParameter("transactionId") ?: ""
                    webView.evaluateJavascript("javascript:handlePaymentRedirect('$status', '$txnId')", null)
                    webView.loadUrl("file:///android_asset/index.html")
                    return true
                }
                
                // Handle UPI payment deep links natively
                if (url.startsWith("upi:") || url.startsWith("phonepe:") || url.startsWith("paytm:") || url.startsWith("gpay:") || url.contains("tez.google.com")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        view.context.startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        return true
                    }
                }
                return false
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.startsWith("https://tscpro.payment/status") || url.contains("tscpro.payment/status")) {
                    val uri = Uri.parse(url)
                    val status = uri.getQueryParameter("code") ?: "PAYMENT_SUCCESS"
                    val txnId = uri.getQueryParameter("transactionId") ?: ""
                    webView.evaluateJavascript("javascript:handlePaymentRedirect('$status', '$txnId')", null)
                    webView.loadUrl("file:///android_asset/index.html")
                    return true
                }
                
                // Handle UPI payment deep links natively
                if (url.startsWith("upi:") || url.startsWith("phonepe:") || url.startsWith("paytm:") || url.startsWith("gpay:") || url.contains("tez.google.com")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        view.context.startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        return true
                    }
                }
                return false
            }
        }
        webView.webChromeClient = WebChromeClient()

        // Register the JS interface
        webView.addJavascriptInterface(AndroidBridge(this), "AndroidBridge")

        // Load local asset HTML file
        webView.loadUrl("file:///android_asset/index.html")

        // Handle native back presses to navigate back inside WebView
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    fun loadPaymentUrl(url: String) {
        webView.loadUrl(url)
    }
}
