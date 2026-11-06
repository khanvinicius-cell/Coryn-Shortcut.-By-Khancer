package com.khan.corynfloat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val requestOverlay = 4102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showScreen()
    }

    private fun showScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
            setBackgroundColor(0xFFF3F6F8.toInt())
        }
        val title = TextView(this).apply {
            text = "Coryn Float"
            textSize = 28f
            setTextColor(0xFF125B7B.toInt())
            gravity = Gravity.CENTER
        }
        val description = TextView(this).apply {
            text = "Consulte o Coryn Club em uma janela flutuante enquanto joga Toram Online.\n\nPara funcionar sobre outros aplicativos, permita a sobreposição de tela."
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFF46545C.toInt())
            setPadding(0, 18, 0, 24)
        }
        val start = Button(this).apply {
            text = "Ativar botão flutuante"
            setOnClickListener { enableOverlay() }
        }
        val stop = Button(this).apply {
            text = "Desativar botão flutuante"
            setOnClickListener {
                stopService(Intent(this@MainActivity, FloatingWindowService::class.java))
                Toast.makeText(this@MainActivity, "Coryn Float desativado", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(description, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(start, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(stop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    private fun enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, requestOverlay)
            return
        }
        val intent = Intent(this, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        Toast.makeText(this, "Botão flutuante ativado", Toast.LENGTH_SHORT).show()
        finish()
    }

    @Deprecated("Deprecated in Android API, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestOverlay && Settings.canDrawOverlays(this)) enableOverlay()
    }
}
