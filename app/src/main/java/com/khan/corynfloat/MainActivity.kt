package com.khan.corynfloat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

// Sem tela: pede a permissão de sobreposição (se faltar) e inicia o botão flutuante.
class MainActivity : Activity() {
    private val requestOverlay = 4102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.canDrawOverlays(this)) {
            startFloating()
            finish()
        } else {
            Toast.makeText(this, "Permita a sobreposição de tela para usar o Coryn Float", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, requestOverlay)
        }
    }

    private fun startFloating() {
        startForegroundService(Intent(this, FloatingWindowService::class.java))
    }

    @Deprecated("Deprecated in Android API, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestOverlay && Settings.canDrawOverlays(this)) startFloating()
        finish()
    }
}
