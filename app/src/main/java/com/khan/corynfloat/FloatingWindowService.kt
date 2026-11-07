package com.khan.corynfloat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.max
import kotlin.math.min

class FloatingWindowService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var panel: FrameLayout? = null
    private var web: WebView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var resizing = false
    private var downX = 0f
    private var downY = 0f
    private var startW = 0
    private var startH = 0
    private var startX = 0
    private var startY = 0
    private var dragStartX = 0f
    private var dragStartY = 0f

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotification()
        createBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification() {
        val channelId = "coryn_float_running"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "Coryn Float", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Coryn Float ativo")
            .setContentText("Toque no círculo para abrir o Coryn Club")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, notification)
        else startForeground(7, notification)
    }

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun createBubble() {
        if (bubble != null) return
        val size = dp(58)
        val view = DashedCircleView(this).apply {
            contentDescription = "Abrir Coryn Club"
            setOnClickListener { togglePanel() }
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    startX = bubbleParams?.x ?: 0
                    startY = bubbleParams?.y ?: 0
                    true
                } else if (event.action == MotionEvent.ACTION_MOVE) {
                    val dx = (event.rawX - dragStartX).toInt()
                    val dy = (event.rawY - dragStartY).toInt()
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(7)) {
                        bubbleParams?.let {
                            it.x = startX + dx
                            it.y = startY + dy
                            wm.updateViewLayout(v, it)
                        }
                    }
                    true
                } else if (event.action == MotionEvent.ACTION_UP) {
                    val moved = kotlin.math.abs(event.rawX - dragStartX) + kotlin.math.abs(event.rawY - dragStartY) > dp(7)
                    if (!moved) v.performClick()
                    true
                } else true
            }
        }
        val params = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(180)
        }
        bubble = view
        bubbleParams = params
        wm.addView(view, params)
    }

    private fun togglePanel() {
        if (panel != null) hidePanel() else showPanel()
    }

    private fun showPanel() {
        if (panel != null) return
        val metrics = resources.displayMetrics
        val landscape = metrics.widthPixels > metrics.heightPixels
        val width = if (landscape) (metrics.widthPixels * 0.56f).toInt() else (metrics.widthPixels * 0.88f).toInt()
        val height = if (landscape) (metrics.heightPixels * 0.88f).toInt() else (metrics.heightPixels * 0.78f).toInt()

        val root = FrameLayout(this).apply {
            background = rounded(0xFFF9FBFC.toInt(), dp(16).toFloat(), 0xFFD8E2E8.toInt())
            elevation = dp(12).toFloat()
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(5), 0)
            setBackgroundColor(0xFFE8EDF0.toInt())
        }
        val title = TextView(this).apply {
            text = "Coryn Club"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF24495B.toInt())
            setPadding(dp(7), 0, 0, 0)
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(42), 1f))
        val minimize = control("—") { hidePanel() }
        val resize = control("⤢") { toggleResizeMode() }
        val close = control("×", 0xFFB00020.toInt()) { confirmClose() }
        header.addView(minimize, LinearLayout.LayoutParams(dp(38), dp(38)))
        header.addView(resize, LinearLayout.LayoutParams(dp(38), dp(38)))
        header.addView(close, LinearLayout.LayoutParams(dp(38), dp(38)))
        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { dragStartX = event.rawX; dragStartY = event.rawY; startX = panelParams?.x ?: 0; startY = panelParams?.y ?: 0; true }
                MotionEvent.ACTION_MOVE -> {
                    panelParams?.let {
                        it.x = startX + (event.rawX - dragStartX).toInt()
                        it.y = startY + (event.rawY - dragStartY).toInt()
                        wm.updateViewLayout(root, it)
                    }
                    true
                }
                else -> true
            }
        }

        val browser = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            setBackgroundColor(Color.WHITE)
            loadUrl("https://coryn.club/index2.php")
        }
        web = browser

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
        }
        footer.addView(control("‹") { if (browser.canGoBack()) browser.goBack() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        footer.addView(control("›") { if (browser.canGoForward()) browser.goForward() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        footer.addView(control("⟳") { browser.reload() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        footer.addView(control("⤢") { toggleResizeMode() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        footer.addView(control("×", 0xFFB00020.toInt()) { confirmClose() }, LinearLayout.LayoutParams(0, dp(46), 1f))

        column.addView(header, LinearLayout.LayoutParams(-1, dp(42)))
        column.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        column.addView(footer, LinearLayout.LayoutParams(-1, dp(46)))
        root.addView(column, FrameLayout.LayoutParams(-1, -1))

        val handle = TextView(this).apply {
            text = "◢"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(0xFF527080.toInt())
            setBackgroundColor(0x1A527080)
            contentDescription = "Arraste para redimensionar"
            setOnTouchListener { _, event -> handleResize(root, event) }
        }
        val handleParams = FrameLayout.LayoutParams(dp(30), dp(30), Gravity.BOTTOM or Gravity.END)
        root.addView(handle, handleParams)

        val params = WindowManager.LayoutParams(
            width, height, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = max(dp(8), (metrics.widthPixels - width) / 2)
            y = max(dp(28), (metrics.heightPixels - height) / 3)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hidePanel()
                true
            } else false
        }
        panel = root
        panelParams = params
        wm.addView(root, params)
    }

    private fun handleResize(root: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                resizing = true
                downX = event.rawX
                downY = event.rawY
                startW = panelParams?.width ?: root.width
                startH = panelParams?.height ?: root.height
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val p = panelParams ?: return true
                val maxW = resources.displayMetrics.widthPixels - dp(16)
                val maxH = resources.displayMetrics.heightPixels - dp(40)
                p.width = min(maxW, max(dp(260), startW + (event.rawX - downX).toInt()))
                p.height = min(maxH, max(dp(240), startH + (event.rawY - downY).toInt()))
                wm.updateViewLayout(root, p)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { resizing = false; return true }
        }
        return true
    }

    private fun toggleResizeMode() {
        Toast.makeText(this, "Arraste a alça no canto inferior direito para redimensionar", Toast.LENGTH_SHORT).show()
    }

    private fun control(label: String, color: Int = 0xFF263238.toInt(), action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = if (label == "×") 28f else 22f
            gravity = Gravity.CENTER
            setTextColor(color)
            setOnClickListener { action() }
            isClickable = true
        }

    private fun confirmClose() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Fechar Coryn Float?")
            .setMessage("O botão flutuante e a janela do Coryn Club serão encerrados.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Fechar") { _, _ -> stopSelf() }
            .show()
    }

    private fun hidePanel() {
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        panelParams = null
        web?.destroy()
        web = null
    }

    private fun rounded(color: Int, radius: Float, stroke: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        hidePanel()
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble = null
        super.onDestroy()
    }
}
