package com.eband.aligner

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var pendingCameraRequest: PermissionRequest? = null
    private lateinit var deviceBridge: DeviceBridge
    private var maxBrightness = true
    private var wifiLock: WifiManager.WifiLock? = null

    // Keeps Wi-Fi out of power-save while the app is open, so radio replies arrive
    // steadily instead of in bursts (some phones buffer Wi-Fi traffic to save battery).
    @Suppress("DEPRECATION")
    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifiLock = wm.createWifiLock(mode, "EbandAligner:radio").apply {
            setReferenceCounted(false)
            try { acquire() } catch (e: Exception) { }
        }
    }

    private fun releaseWifiLock() {
        try { wifiLock?.takeIf { it.isHeld }?.release() } catch (e: Exception) { }
    }

    // Only affects this app's window; Android restores normal brightness when you leave the app.
    private fun applyBrightness() {
        val lp = window.attributes
        lp.screenBrightness = if (maxBrightness) 1.0f
            else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }
    private var pendingGeo: Pair<String, GeolocationPermissions.Callback>? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.any { it }
            pendingGeo?.let { (origin, cb) -> cb.invoke(origin, granted, false) }
            pendingGeo = null
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingCameraRequest?.let {
                if (granted) it.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) else it.deny()
            }
            pendingCameraRequest = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        maxBrightness = getSharedPreferences("ui", MODE_PRIVATE).getBoolean("maxBright", true)
        applyBrightness()

        webView = WebView(this)
        setContentView(webView)

        // Serves app/src/main/assets over https://appassets.androidplatform.net/ so the page
        // is a "secure context" (required for camera access via getUserMedia).
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                val has = ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (has) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeo = origin to callback
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        val has = ContextCompat.checkSelfPermission(
                            this@MainActivity, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (has) {
                            request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                        } else {
                            pendingCameraRequest = request
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    } else {
                        request.deny()
                    }
                }
            }
        }

        webView.addJavascriptInterface(RadioBridge(this, webView), "RadioBridge")
        deviceBridge = DeviceBridge(applicationContext) { on ->
            runOnUiThread {
                maxBrightness = on
                getSharedPreferences("ui", MODE_PRIVATE).edit().putBoolean("maxBright", on).apply()
                applyBrightness()
            }
        }
        webView.addJavascriptInterface(deviceBridge, "DeviceBridge")
        webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    override fun onPause() {
        super.onPause()
        deviceBridge.stop()
        releaseWifiLock()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        deviceBridge.start()
        acquireWifiLock()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
