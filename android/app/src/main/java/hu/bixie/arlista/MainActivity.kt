package hu.bixie.arlista

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

/**
 * Teljes képernyős WebView, ami a Bixie árlista oldalát mutatja.
 * - ébren tartja a kijelzőt
 * - ha nincs net, a gyorsítótárból indul, és 30 mp-enként újrapróbálja
 * - OK / középső gomb: kézi újratöltés
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var failed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        web = WebView(this)
        web.setBackgroundColor(0xFF070C5E.toInt())
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        web.isFocusable = false
        web.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    failed = true
                    // net nélkül a legutóbb letöltött oldal a gyorsítótárból
                    view.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    handler.removeCallbacksAndMessages(null)
                    handler.postDelayed({ load() }, 30_000)
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!failed) view.settings.cacheMode = WebSettings.LOAD_DEFAULT
                failed = false
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                recreate()
                return true
            }
        }
        setContentView(web)
        hideSystemUi()
        load()

        WatchService.start(this)
        askOverlayPermissionOnce()
    }

    private fun load() {
        web.loadUrl(BuildConfig.ARLISTA_URL)
    }

    /** Az automatikus előtérbe hozáshoz kell a "Megjelenítés más alkalmazások felett" engedély. */
    private fun askOverlayPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) return
        val prefs = getSharedPreferences("bixie", MODE_PRIVATE)
        if (prefs.getBoolean("overlayAsked", false)) return
        prefs.edit().putBoolean("overlayAsked", true).apply()
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Az automatikus indításhoz engedély kell: Beállítások > Alkalmazások > Speciális hozzáférés > Megjelenítés más alkalmazások felett > Bixie árlista",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        web.onResume()
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                Toast.makeText(this, "Árlista újratöltése…", Toast.LENGTH_SHORT).show()
                web.reload()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        web.destroy()
        super.onDestroy()
    }
}
