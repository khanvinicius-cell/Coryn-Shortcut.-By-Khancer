package com.khan.corynfloat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Process
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FloatingWindowService : Service() {

    private class Tab(val web: WebView, var title: String = "Nova página")

    private companion object {
        const val HOME = "https://coryn.club/index2.php"
        const val GOOGLE = "https://www.google.com"
        const val CHANNEL_ID = "coryn_float_running"
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    // Botão flutuante
    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    // Janela (criada uma única vez e reaproveitada: assim as páginas não recarregam ao minimizar)
    private var root: FrameLayout? = null
    private var rootBg: GradientDrawable? = null
    private var content: FrameLayout? = null
    private var handle: View? = null
    private var expandBtn: GlyphView? = null
    private var tabsBtn: GlyphView? = null
    private var overlayView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelShown = false
    private var expanded = false
    private var lastLandscape = false
    private var closed = false

    private val tabs = mutableListOf<Tab>()
    private var current = 0

    // Última posição/tamanho da janela no modo janela
    private var savedX = 0
    private var savedY = 0
    private var savedW = 0
    private var savedH = 0

    private var dragStartX = 0f
    private var dragStartY = 0f
    private var startX = 0
    private var startY = 0
    private var downX = 0f
    private var downY = 0f
    private var startW = 0
    private var startH = 0

    // ---------------------------------------------------------------- ciclo de vida

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        lastLandscape = dm.widthPixels > dm.heightPixels
        val channel = NotificationChannel(CHANNEL_ID, "Coryn Float", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        startForegroundCompat()
        createBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Toda chamada de startForegroundService precisa ser seguida de startForeground.
        startForegroundCompat()
        // NOT_STICKY: o sistema NÃO recria o serviço depois que ele é encerrado.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val dm = resources.displayMetrics
        val landscape = dm.widthPixels > dm.heightPixels
        if (landscape == lastLandscape) return
        lastLandscape = landscape
        savedW = 0
        val p = panelParams ?: return
        p.width = 0
        if (panelShown) {
            if (expanded) setExpandedParams(p) else applyWindowed(p)
            root?.let { try { wm.updateViewLayout(it, p) } catch (_: Exception) {} }
        }
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Coryn Float ativo")
            .setContentText("Toque no círculo para abrir o Coryn Club")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(7, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(7, notification)
        }
    }

    // Encerra tudo e força a parada do processo.
    private fun quitApp() {
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        handler.postDelayed({ Process.killProcess(Process.myPid()) }, 250)
    }

    private fun cleanup() {
        if (closed) return
        closed = true
        hidePanel()
        tabs.forEach { try { it.web.destroy() } catch (_: Exception) {} }
        tabs.clear()
        bubble?.let { try { wm.removeViewImmediate(it) } catch (_: Exception) {} }
        bubble = null
    }

    // ---------------------------------------------------------------- botão flutuante

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
                    if (abs(dx) + abs(dy) > dp(7)) {
                        bubbleParams?.let {
                            it.x = startX + dx
                            it.y = startY + dy
                            wm.updateViewLayout(v, it)
                        }
                    }
                    true
                } else if (event.action == MotionEvent.ACTION_UP) {
                    val moved = abs(event.rawX - dragStartX) + abs(event.rawY - dragStartY) > dp(7)
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

    // ---------------------------------------------------------------- janela

    private fun togglePanel() {
        if (panelShown) hidePanel() else showPanel()
    }

    private fun showPanel() {
        if (panelShown || closed) return
        if (root == null) buildPanel()
        val r = root ?: return
        val p = panelParams ?: return
        if (expanded) setExpandedParams(p) else {
            captureBounds(p)
            applyWindowed(p)
        }
        try {
            wm.addView(r, p)
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível abrir a janela", Toast.LENGTH_SHORT).show()
            return
        }
        panelShown = true
        tabs.getOrNull(current)?.web?.onResume()
    }

    // Minimiza: só remove a janela da tela. As páginas continuam carregadas.
    private fun hidePanel() {
        if (!panelShown) return
        dismissOverlay()
        root?.let { try { wm.removeViewImmediate(it) } catch (_: Exception) {} }
        panelShown = false
        tabs.forEach { try { it.web.onPause() } catch (_: Exception) {} }
    }

    private fun buildPanel() {
        val bg = rounded(0xFFF9FBFC.toInt(), dp(16).toFloat(), 0xFFD8E2E8.toInt())
        rootBg = bg
        val r = FrameLayout(this).apply {
            background = bg
            elevation = dp(12).toFloat()
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Cabeçalho: título | minimizar | expandir/restaurar | fechar
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
        val minimize = GlyphView(this, GlyphView.Kind.MINIMIZE).apply {
            contentDescription = "Minimizar"
            setOnClickListener { hidePanel() }
        }
        val expand = GlyphView(this, GlyphView.Kind.EXPAND).apply {
            contentDescription = "Expandir"
            setOnClickListener { toggleExpand() }
        }
        val close = GlyphView(this, GlyphView.Kind.CLOSE).apply {
            contentDescription = "Encerrar"
            color = 0xFFB00020.toInt()
            setOnClickListener { confirmClose() }
        }
        expandBtn = expand
        header.addView(title, LinearLayout.LayoutParams(0, dp(42), 1f))
        header.addView(minimize, LinearLayout.LayoutParams(dp(38), dp(38)))
        header.addView(expand, LinearLayout.LayoutParams(dp(38), dp(38)))
        header.addView(close, LinearLayout.LayoutParams(dp(38), dp(38)))
        header.setOnTouchListener { _, event ->
            if (expanded) return@setOnTouchListener true
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX; dragStartY = event.rawY
                    startX = panelParams?.x ?: 0; startY = panelParams?.y ?: 0
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    panelParams?.let {
                        it.x = startX + (event.rawX - dragStartX).toInt()
                        it.y = startY + (event.rawY - dragStartY).toInt()
                        wm.updateViewLayout(r, it)
                    }
                    true
                }
                else -> true
            }
        }

        // Área das páginas
        val area = FrameLayout(this)
        content = area

        // Rodapé: voltar | avançar | recarregar | páginas | encerrar
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
        }
        val tabsButton = GlyphView(this, GlyphView.Kind.TABS).apply {
            contentDescription = "Páginas"
            setOnClickListener { showTabs() }
        }
        tabsBtn = tabsButton
        footer.addView(control("‹") { tabs.getOrNull(current)?.web?.let { if (it.canGoBack()) it.goBack() } }, LinearLayout.LayoutParams(0, dp(46), 1f))
        footer.addView(control("›") { tabs.getOrNull(current)?.web?.let { if (it.canGoForward()) it.goForward() } }, LinearLayout.LayoutParams(0, dp(46), 1f))
        footer.addView(control("⟳") { tabs.getOrNull(current)?.web?.reload() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        footer.addView(tabsButton, LinearLayout.LayoutParams(0, dp(46), 1f))
        footer.addView(control("×", 0xFFB00020.toInt()) { confirmClose() }, LinearLayout.LayoutParams(0, dp(46), 1f))

        column.addView(header, LinearLayout.LayoutParams(-1, dp(42)))
        column.addView(area, LinearLayout.LayoutParams(-1, 0, 1f))
        column.addView(footer, LinearLayout.LayoutParams(-1, dp(46)))
        r.addView(column, FrameLayout.LayoutParams(-1, -1))

        // Alça de redimensionar (canto inferior direito)
        val grip = TextView(this).apply {
            text = "◢"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(0xFF527080.toInt())
            setBackgroundColor(0x1A527080)
            contentDescription = "Arraste para redimensionar"
            setOnTouchListener { _, event -> handleResize(r, event) }
        }
        handle = grip
        r.addView(grip, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.BOTTOM or Gravity.END))

        val params = WindowManager.LayoutParams(
            dp(300), dp(400), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        savedW = 0
        params.width = 0

        r.setOnTouchListener { _, event ->
            // Toque fora minimiza (no modo janela), exceto enquanto o teclado está aberto.
            if (event.action == MotionEvent.ACTION_OUTSIDE && !expanded && !imeVisible(r)) {
                hidePanel()
                true
            } else false
        }

        root = r
        panelParams = params

        tabs.add(createTab(HOME))
        current = 0
        attach(0)
        updateTabsBadge()
    }

    private fun imeVisible(r: View): Boolean {
        if (Build.VERSION.SDK_INT >= 30) return r.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
        val p = panelParams ?: return false
        return p.height > 0 && r.height in 1 until (p.height - dp(120))
    }

    // ---------------------------------------------------------------- tamanho / expandir

    private fun captureBounds(p: WindowManager.LayoutParams) {
        if (!expanded && p.width > 0 && p.height > 0) {
            savedX = p.x; savedY = p.y; savedW = p.width; savedH = p.height
        }
    }

    private fun applyWindowed(p: WindowManager.LayoutParams) {
        val dm = resources.displayMetrics
        val sw = dm.widthPixels
        val sh = dm.heightPixels
        val landscape = sw > sh
        var w = savedW
        var h = savedH
        var x = savedX
        var y = savedY
        if (w <= 0 || h <= 0 || w > sw - dp(8) || h > sh - dp(24)) {
            w = if (landscape) (sw * 0.56f).toInt() else (sw * 0.88f).toInt()
            h = if (landscape) (sh * 0.88f).toInt() else (sh * 0.78f).toInt()
            x = max(dp(8), (sw - w) / 2)
            y = max(dp(28), (sh - h) / 3)
        }
        p.gravity = Gravity.TOP or Gravity.START
        p.width = w
        p.height = h
        p.x = x.coerceIn(0, max(0, sw - w))
        p.y = y.coerceIn(0, max(0, sh - h))
    }

    // Tela cheia (ainda como janela): ocupa a área disponível, respeitando a orientação.
    private fun setExpandedParams(p: WindowManager.LayoutParams) {
        p.gravity = Gravity.TOP or Gravity.START
        p.width = WindowManager.LayoutParams.MATCH_PARENT
        p.height = WindowManager.LayoutParams.MATCH_PARENT
        p.x = 0
        p.y = 0
    }

    private fun toggleExpand() {
        val p = panelParams ?: return
        val r = root ?: return
        if (!expanded) {
            captureBounds(p)
            expanded = true
            setExpandedParams(p)
        } else {
            expanded = false
            applyWindowed(p)
        }
        expandBtn?.kind = if (expanded) GlyphView.Kind.RESTORE else GlyphView.Kind.EXPAND
        expandBtn?.contentDescription = if (expanded) "Restaurar" else "Expandir"
        handle?.visibility = if (expanded) View.GONE else View.VISIBLE
        rootBg?.cornerRadius = if (expanded) 0f else dp(16).toFloat()
        if (panelShown) try { wm.updateViewLayout(r, p) } catch (_: Exception) {}
    }

    private fun handleResize(r: View, event: MotionEvent): Boolean {
        if (expanded) return true
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startW = panelParams?.width ?: r.width
                startH = panelParams?.height ?: r.height
            }
            MotionEvent.ACTION_MOVE -> {
                val p = panelParams ?: return true
                val maxW = resources.displayMetrics.widthPixels - dp(16)
                val maxH = resources.displayMetrics.heightPixels - dp(40)
                p.width = min(maxW, max(dp(260), startW + (event.rawX - downX).toInt()))
                p.height = min(maxH, max(dp(240), startH + (event.rawY - downY).toInt()))
                wm.updateViewLayout(r, p)
            }
        }
        return true
    }

    // ---------------------------------------------------------------- páginas (abas)

    private fun createTab(url: String?): Tab {
        val wv = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportMultipleWindows(true)
            setBackgroundColor(Color.WHITE)
        }
        val tab = Tab(wv)
        wv.webViewClient = WebViewClient()
        wv.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) tab.title = title
            }

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (!isUserGesture) return false
                val t = createTab(null)
                tabs.add(t)
                selectTab(tabs.lastIndex)
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = t.web
                resultMsg.sendToTarget()
                return true
            }
        }
        if (url != null) wv.loadUrl(url)
        return tab
    }

    private fun attach(i: Int) {
        val area = content ?: return
        val w = tabs.getOrNull(i)?.web ?: return
        (w.parent as? ViewGroup)?.removeView(w)
        area.removeAllViews()
        area.addView(w, FrameLayout.LayoutParams(-1, -1))
    }

    private fun selectTab(i: Int) {
        if (i !in tabs.indices) return
        if (i != current) tabs.getOrNull(current)?.web?.onPause()
        current = i
        attach(i)
        if (panelShown) tabs[i].web.onResume()
        updateTabsBadge()
    }

    private fun addTab(url: String) {
        tabs.add(createTab(url))
        selectTab(tabs.lastIndex)
    }

    private fun closeTab(i: Int) {
        if (i !in tabs.indices) return
        val t = tabs.removeAt(i)
        (t.web.parent as? ViewGroup)?.removeView(t.web)
        try { t.web.destroy() } catch (_: Exception) {}
        if (tabs.isEmpty()) {
            tabs.add(createTab(HOME))
            current = 0
        } else {
            current = when {
                i < current -> current - 1
                i == current -> min(i, tabs.lastIndex)
                else -> current
            }
        }
        attach(current)
        if (panelShown) tabs[current].web.onResume()
        updateTabsBadge()
    }

    private fun updateTabsBadge() {
        tabsBtn?.count = tabs.size
    }

    private fun showTabs() {
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(4), 0)
            setBackgroundColor(0xFFE8EDF0.toInt())
        }
        top.addView(TextView(this).apply {
            text = "Páginas abertas (${tabs.size})"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF24495B.toInt())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(pill("+ Nova (Google)", 0xFF08749B.toInt(), Color.WHITE) {
            addTab(GOOGLE)
            dismissOverlay()
        })
        top.addView(control("×") { dismissOverlay() }, LinearLayout.LayoutParams(dp(40), dp(40)))

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        tabs.forEachIndexed { i, t ->
            list.addView(tabRow(i, t), LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(dp(10), dp(4), dp(10), dp(4))
            })
        }
        val scroll = ScrollView(this).apply { addView(list, FrameLayout.LayoutParams(-1, -2)) }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF3F6F8.toInt())
            isClickable = true
        }
        box.addView(top, LinearLayout.LayoutParams(-1, dp(46)))
        box.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        showOverlay(box)
    }

    private fun tabRow(i: Int, t: Tab): View {
        val isCurrent = i == current
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(4), dp(6))
            background = rounded(Color.WHITE, dp(10).toFloat(), if (isCurrent) 0xFF08749B.toInt() else 0xFFD8E2E8.toInt())
            setOnClickListener {
                selectTab(i)
                dismissOverlay()
            }
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = t.title
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF24495B.toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        col.addView(TextView(this).apply {
            text = t.web.url ?: ""
            textSize = 11f
            setTextColor(0xFF6B7A82.toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(control("×") {
            closeTab(i)
            showTabs()
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        return row
    }

    // ---------------------------------------------------------------- confirmação de encerramento

    private fun confirmClose() {
        val msg = TextView(this).apply {
            text = "Você quer encerrar o app CorynClub?"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF24495B.toInt())
            gravity = Gravity.CENTER
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(pill("Não", 0xFFE8EDF0.toInt(), 0xFF24495B.toInt()) { dismissOverlay() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        row.addView(pill("Sim", 0xFFB00020.toInt(), Color.WHITE) { quitApp() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            background = rounded(Color.WHITE, dp(14).toFloat(), 0xFFD8E2E8.toInt())
        }
        box.addView(msg, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })
        box.addView(row, LinearLayout.LayoutParams(-1, -2))
        val scrim = FrameLayout(this).apply {
            setBackgroundColor(0xB3000000.toInt())
            isClickable = true
        }
        scrim.addView(box, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply {
            setMargins(dp(24), 0, dp(24), 0)
        })
        showOverlay(scrim)
    }

    private fun showOverlay(v: View) {
        dismissOverlay()
        root?.addView(v, FrameLayout.LayoutParams(-1, -1))
        overlayView = v
    }

    private fun dismissOverlay() {
        overlayView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        overlayView = null
    }

    // ---------------------------------------------------------------- utilidades de interface

    private fun control(label: String, color: Int = 0xFF263238.toInt(), action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = if (label == "×") 28f else 22f
            gravity = Gravity.CENTER
            setTextColor(color)
            setOnClickListener { action() }
            isClickable = true
        }

    private fun pill(label: String, fill: Int, textColor: Int, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = GradientDrawable().apply {
                setColor(fill)
                cornerRadius = dp(18).toFloat()
            }
            setOnClickListener { action() }
        }

    private fun rounded(color: Int, radius: Float, stroke: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
