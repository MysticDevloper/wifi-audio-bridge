package com.audiobridge

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private var service: AudioBridgeService? = null
    private val serviceBound = AtomicBoolean(false)

    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var ipInput: EditText
    private lateinit var connectBtn: Button
    private lateinit var discoverBtn: Button
    private lateinit var micBar: ProgressBar
    private lateinit var micLevelText: TextView
    private lateinit var spkBar: ProgressBar
    private lateinit var spkLevelText: TextView
    private lateinit var micMuteBtn: Button
    private lateinit var spkMuteBtn: Button
    private lateinit var txRateText: TextView
    private lateinit var rxRateText: TextView
    private lateinit var bufferText: TextView
    private lateinit var latencyText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val dotConnected = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setSize(12, 12)
        setColor(Color.parseColor("#FF4CAF50"))
    }
    private val dotDisconnected = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setSize(12, 12)
        setColor(Color.parseColor("#FFBDBDBD"))
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }.not()) {
            Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, svc: IBinder?) {
            service = (svc as AudioBridgeService.LocalBinder).getService()
            serviceBound.set(true)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            serviceBound.set(false)
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            try {
                val s = service
                if (s == null) { handler.postDelayed(this, 100); return }
                statusText.text = s.status
                setStatusDot(s.connected)
                if (s.connected) {
                    discoverBtn.text = "\u23F9  Disconnect"
                    micMuteBtn.visibility = View.VISIBLE
                    spkMuteBtn.visibility = View.VISIBLE
                    val micPct = s.micLevel.toInt().coerceIn(0, 100)
                    val spkPct = s.spkLevel.toInt().coerceIn(0, 100)
                    micBar.progress = micPct
                    micLevelText.text = "$micPct%"
                    spkBar.progress = spkPct
                    spkLevelText.text = "$spkPct%"
                    txRateText.text = String.format(Locale.US, "%.1f KB/s", s.txRate)
                    rxRateText.text = String.format(Locale.US, "%.1f KB/s", s.rxRate)
                    bufferText.text = "${s.bufferMs}ms"
                    val elapsed = (System.currentTimeMillis() - s.connectTime) / 1000
                    latencyText.text = String.format(Locale.US, "%d:%02d", elapsed / 60, elapsed % 60)
                } else {
                    discoverBtn.text = "Auto-Discover Server"
                    micMuteBtn.visibility = View.GONE
                    spkMuteBtn.visibility = View.GONE
                    micBar.progress = 0; micLevelText.text = "0%"
                    spkBar.progress = 0; spkLevelText.text = "0%"
                    bufferText.text = "0 ms"; latencyText.text = ""
                }
            } catch (_: Exception) {}
            handler.postDelayed(this, 80)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            statusText = findViewById(R.id.statusText)
            statusDot = findViewById(R.id.statusDot)
            ipInput = findViewById(R.id.ipInput)
            connectBtn = findViewById(R.id.connectBtn)
            discoverBtn = findViewById(R.id.discoverBtn)
            micBar = findViewById(R.id.micLevelBar)
            micLevelText = findViewById(R.id.micLevelText)
            spkBar = findViewById(R.id.spkLevelBar)
            spkLevelText = findViewById(R.id.spkLevelText)
            micMuteBtn = findViewById(R.id.micMuteBtn)
            spkMuteBtn = findViewById(R.id.spkMuteBtn)
            txRateText = findViewById(R.id.txRateText)
            rxRateText = findViewById(R.id.rxRateText)
            bufferText = findViewById(R.id.bufferText)
            latencyText = findViewById(R.id.latencyText)
        } catch (e: Exception) {
            Toast.makeText(this, "UI init error", Toast.LENGTH_LONG).show()
            finish(); return
        }

        connectBtn.setOnClickListener {
            try {
                val ip = ipInput.text.toString().trim()
                if (ip.isNotEmpty()) service?.connect(ip)
            } catch (_: Exception) {}
        }

        discoverBtn.setOnClickListener {
            try {
                val s = service
                if (s != null && s.connected) s.disconnect()
                else s?.discoverAndConnect()
            } catch (_: Exception) {}
        }

        micMuteBtn.setOnClickListener {
            try {
                val s = service ?: return@setOnClickListener
                s.micMuted = !s.micMuted
                micMuteBtn.text = if (s.micMuted) "\uD83D\uDD07" else "\uD83C\uDFA4"
                micMuteBtn.setBackgroundColor(
                    if (s.micMuted) Color.parseColor("#FFE0E0E0") else Color.TRANSPARENT
                )
            } catch (_: Exception) {}
        }

        spkMuteBtn.setOnClickListener {
            try {
                val s = service ?: return@setOnClickListener
                s.spkMuted = !s.spkMuted
                spkMuteBtn.text = if (s.spkMuted) "\uD83D\uDD07" else "\uD83D\uDD0A"
                spkMuteBtn.setBackgroundColor(
                    if (s.spkMuted) Color.parseColor("#FFE0E0E0") else Color.TRANSPARENT
                )
            } catch (_: Exception) {}
        }

        try {
            val intent = Intent(this, AudioBridgeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Toast.makeText(this, "Service error: ${e.message}", Toast.LENGTH_LONG).show()
        }

        requestNeededPermissions()
        handler.post(updateRunnable)
    }

    private fun requestNeededPermissions() {
        try {
            val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 29) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            val missing = needed.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
        } catch (_: Exception) {}
    }

    private fun setStatusDot(connected: Boolean) {
        statusDot.background = if (connected) dotConnected else dotDisconnected
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        if (serviceBound.getAndSet(false)) {
            try { unbindService(connection) } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}
