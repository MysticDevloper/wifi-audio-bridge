package com.audiobridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Process
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

class AudioBridgeService : Service() {
    companion object {
        const val SAMPLE_RATE = 44100
        const val FRAME_SIZE = 256
        const val TCP_PORT = 8888
        const val MIC_UDP_PORT = 8890
        const val SPK_UDP_PORT = 8891
        const val DISCOVERY_PORT = 9999
        const val HEADER_SIZE = 13
        const val MAGIC: Short = 0xABCD.toShort()
        const val STREAM_MIC: Byte = 0
        const val STREAM_SPEAKER: Byte = 1
        const val NOTIF_ID = 1001
        const val CHANNEL_ID = "audio_bridge"

        private val USE_NATIVE = Build.VERSION.SDK_INT >= 27
        private var nativeLoaded = false

        init {
            if (USE_NATIVE) {
                try {
                    System.loadLibrary("audiobridge")
                    nativeLoaded = true
                } catch (_: UnsatisfiedLinkError) {}
            }
        }
    }

    private external fun nativeStart(ip: String, micPort: Int, spkPort: Int, sr: Int, frameSize: Int): Boolean
    private external fun nativeStop()
    private external fun nativeGetMicLevel(): Float
    private external fun nativeGetSpkLevel(): Float
    private external fun nativeGetTxBytes(): Long
    private external fun nativeGetRxBytes(): Long
    private external fun nativeIsRunning(): Boolean

    inner class LocalBinder : Binder() {
        fun getService(): AudioBridgeService = this@AudioBridgeService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var sock: Socket? = null
    private val running = AtomicBoolean(false)

    @Volatile var status = "Ready"
    @Volatile var micLevel = 0f
    @Volatile var spkLevel = 0f
    @Volatile var txRate = 0f
    @Volatile var rxRate = 0f
    @Volatile var bufferMs = 0
    @Volatile var connected = false
    @Volatile var micMuted = false
    @Volatile var spkMuted = false
    @Volatile var connectTime = 0L

    private var lastTxBytes = 0L
    private var lastRxBytes = 0L
    private val txBytes = AtomicLong(0)
    private val rxBytes = AtomicLong(0)

    private var udpSend: DatagramSocket? = null
    private var udpRecv: DatagramSocket? = null

    override fun onCreate() {
        super.onCreate()
        try { createNotificationChannel() } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIF_ID, buildNotification("Ready"))
        } catch (_: Throwable) {
            android.util.Log.e("AudioBridge", "startForeground failed")
        }
        return START_STICKY
    }

    fun connect(ip: String) {
        if (running.getAndSet(true)) disconnect()
        status = "Connecting..."
        connected = false
        connectTime = System.currentTimeMillis()
        txBytes.set(0); rxBytes.set(0)
        lastTxBytes = 0; lastRxBytes = 0
        scope.launch {
            try {
                val tcp = Socket(ip, TCP_PORT).apply {
                    tcpNoDelay = true; soTimeout = 5000
                }
                if (!running.get()) { tcp.close(); return@launch }
                sock = tcp

                val out = tcp.getOutputStream()
                val reader = BufferedReader(InputStreamReader(tcp.getInputStream()))

                out.write("UDP\n".toByteArray()); out.flush()
                val resp = reader.readLine()
                if (resp != "OK") {
                    tcp.close()
                    status = if (resp == null) "Handshake failed: server closed connection"
                             else if (resp == "NO") "Handshake failed: server rejected (rebuild server?)"
                             else "Handshake failed: got '$resp'"
                    running.set(false); return@launch
                }

                connected = true
                status = if (nativeLoaded) "Connected (AAudio)" else "Connected (UDP)"

                try {
                    val nativeOk = nativeLoaded && nativeStart(ip, MIC_UDP_PORT, SPK_UDP_PORT,
                        SAMPLE_RATE, FRAME_SIZE)
                    if (nativeOk) {
                        launch { nativeStatsLoop() }
                    } else {
                        startJavaUdp(ip, tcp.inetAddress)
                    }
                } catch (_: Throwable) {
                    startJavaUdp(ip, tcp.inetAddress)
                }

                launch { keepaliveLoop(out) }
            } catch (e: Throwable) {
                status = "Error: ${e.message}"
                connected = false; running.set(false)
                try { sock?.close() } catch (_: Throwable) {}
            }
        }
    }

    private suspend fun nativeStatsLoop() {
        while (running.get()) {
            delay(500)
            if (!running.get()) break
            micLevel = nativeGetMicLevel()
            spkLevel = nativeGetSpkLevel()
            val nowTx = nativeGetTxBytes()
            val nowRx = nativeGetRxBytes()
            txRate = (nowTx - lastTxBytes) / 1024f
            rxRate = (nowRx - lastRxBytes) / 1024f
            lastTxBytes = nowTx; lastRxBytes = nowRx
            bufferMs = 0
        }
    }

    private fun startJavaUdp(serverIp: String, serverAddr: InetAddress) {
        try {
            val send = DatagramSocket()
            val recv = DatagramSocket(SPK_UDP_PORT)
            udpSend = send; udpRecv = recv

            val srvAddr = InetAddress.getByName(serverIp)

            val srvUdp = DatagramPacket(ByteArray(1), 1, srvAddr, MIC_UDP_PORT)

            scope.launch {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                javaMicLoop(send, srvAddr)
            }
            scope.launch {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                javaSpkLoop(recv)
            }
        } catch (e: Throwable) {
            status = "UDP init: ${e.message}"
            connected = false
        }
    }

    private suspend fun javaMicLoop(send: DatagramSocket, srvAddr: InetAddress) {
        val buf = ByteArray(FRAME_SIZE * 2)
        val minBufSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
            FRAME_SIZE * 4
        )
        val record = try {
            AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                minBufSize)
        } catch (_: Exception) {
            AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                minBufSize)
        }
        val hdr = ByteArray(HEADER_SIZE)
        var seq: Short = 0
        try {
            record.startRecording()
            val pkt = ByteArray(HEADER_SIZE + FRAME_SIZE * 2)
            while (running.get()) {
                val read = record.read(buf, 0, buf.size)
                if (read <= 0) continue
                val rms = calcRms(buf, 0, read)
                micLevel = micLevel * 0.5f + (rms / 32768.0f * 100f).coerceIn(0f, 100f) * 0.5f
                if (micMuted) continue
                encodeHeader(hdr, seq, STREAM_MIC, read)
                seq = (seq + 1).toShort()
                System.arraycopy(hdr, 0, pkt, 0, HEADER_SIZE)
                System.arraycopy(buf, 0, pkt, HEADER_SIZE, read)
                send.send(DatagramPacket(pkt, HEADER_SIZE + read, srvAddr, MIC_UDP_PORT))
                txBytes.addAndGet((HEADER_SIZE + read).toLong())
            }
        } catch (_: Throwable) { if (running.get()) status = "Mic error" }
        finally { try { record.stop() } catch (_: Throwable) {}; record.release() }
    }

    private suspend fun javaSpkLoop(recv: DatagramSocket) {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(minBuf, FRAME_SIZE * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .apply { if (Build.VERSION.SDK_INT >= 31)
                setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) }
            .build()
        val pkt = ByteArray(HEADER_SIZE + 4096)
        track.play()
        try {
            while (running.get()) {
                val dp = DatagramPacket(pkt, pkt.size)
                try { recv.soTimeout = 500; recv.receive(dp) } catch (_: SocketTimeoutException) { continue }
                val n = dp.length
                if (n < HEADER_SIZE) continue
                val magic = (pkt[0].toInt() and 0xFF) or ((pkt[1].toInt() and 0xFF) shl 8)
                if (magic != MAGIC.toInt()) continue
                val payloadLen = ByteBuffer.wrap(pkt, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (payloadLen < 1 || payloadLen > n - HEADER_SIZE) continue
                rxBytes.addAndGet(n.toLong())
                if (pkt[12] == STREAM_SPEAKER) {
                    val rms = calcRms(pkt, HEADER_SIZE, payloadLen)
                    spkLevel = spkLevel * 0.5f + (rms / 32768.0f * 100f).coerceIn(0f, 100f) * 0.5f
                    bufferMs = (track.bufferSizeInFrames * 1000 / SAMPLE_RATE).coerceAtLeast(0)
                    if (!spkMuted) track.write(pkt, HEADER_SIZE, payloadLen)
                }
            }
        } catch (_: Throwable) { if (running.get()) status = "Spk error" }
        finally { try { track.stop() } catch (_: Throwable) {}; track.release() }
    }

    private suspend fun keepaliveLoop(out: OutputStream) {
        while (running.get()) {
            delay(5000)
            if (!running.get()) break
            try {
                out.write("PING\n".toByteArray()); out.flush()
            } catch (_: Throwable) {
                status = "Server disconnected"
                disconnect()
            }
        }
    }

    fun discoverAndConnect() {
        if (running.get()) return
        status = "Discovering..."
        scope.launch {
            try {
                val ip = discoverServer()
                status = "Found: $ip"
                connect(ip)
            } catch (e: Throwable) {
                status = "Discovery failed: ${e.message}"
            }
        }
    }

    private fun discoverServer(): String {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("AudioBridgeDiscovery").apply {
            setReferenceCounted(false); acquire()
        }
        val sock = DatagramSocket(0).apply { broadcast = true; soTimeout = 3000 }
        try {
            val sendData = "WIFI_AUDIO_WHO?".toByteArray()
            val bc = getBroadcastAddress(wifi)
            sock.send(DatagramPacket(sendData, sendData.size, bc, DISCOVERY_PORT))
            sock.send(DatagramPacket(sendData, sendData.size,
                InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT))
            val buf = ByteArray(256)
            val deadline = System.currentTimeMillis() + 3000
            while (System.currentTimeMillis() < deadline) {
                val recv = DatagramPacket(buf, buf.size)
                try { sock.receive(recv) } catch (_: SocketTimeoutException) { break }
                val resp = String(recv.data, 0, recv.length).trim()
                if (resp.startsWith("WIFI_AUDIO_HERE:"))
                    return resp.removePrefix("WIFI_AUDIO_HERE:").trim()
            }
            throw IOException("No server responded")
        } finally {
            sock.close(); lock.release()
        }
    }

    private fun getBroadcastAddress(wifi: WifiManager): InetAddress {
        val dhcp = wifi.dhcpInfo ?: return InetAddress.getByName("255.255.255.255")
        val bc = (dhcp.ipAddress.toLong() and dhcp.netmask.toLong()) or
                (dhcp.netmask.toLong().inv() and 0xFFFFFFFFL)
        val parts = byteArrayOf(
            (bc shr 0).toByte(), (bc shr 8).toByte(),
            (bc shr 16).toByte(), (bc shr 24).toByte())
        return InetAddress.getByAddress(parts)
    }

    fun disconnect() {
        try {
            running.set(false)
            connected = false
            if (nativeLoaded) { try { nativeStop() } catch (_: Throwable) {} }
            try { udpSend?.close() } catch (_: Throwable) {}
            try { udpRecv?.close() } catch (_: Throwable) {}
            udpSend = null; udpRecv = null
            try { sock?.close() } catch (_: Throwable) {}
            sock = null
            try { scope.coroutineContext.cancelChildren() } catch (_: Throwable) {}
            status = "Disconnected"
            micLevel = 0f; spkLevel = 0f
            txRate = 0f; rxRate = 0f; bufferMs = 0
        } catch (_: Throwable) {
            status = "Disconnected"
        }
    }

    private fun encodeHeader(hdr: ByteArray, seq: Short, type: Byte, pcmLen: Int) {
        hdr[0] = (MAGIC.toInt() and 0xFF).toByte()
        hdr[1] = ((MAGIC.toInt() shr 8) and 0xFF).toByte()
        hdr[2] = ((seq.toInt() shr 8) and 0xFF).toByte()
        hdr[3] = (seq.toInt() and 0xFF).toByte()
        (4..7).forEach { hdr[it] = 0 }
        ByteBuffer.wrap(hdr, 8, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(pcmLen)
        hdr[12] = type
    }

    private fun calcRms(buf: ByteArray, off: Int, bytes: Int): Float {
        val samples = bytes / 2; if (samples == 0) return 0f
        var sumSq = 0L
        for (i in off until off + bytes - 1 step 2) {
            val s = ((buf[i].toInt() and 0xFF) or ((buf[i + 1].toInt() and 0xFF) shl 8)).toShort()
            sumSq += s.toLong() * s.toLong()
        }
        return sqrt(sumSq.toDouble() / samples).toFloat()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Audio Bridge",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "WiFi Audio Bridge status"
                setSound(null, null)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WiFi Audio Bridge")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    override fun onDestroy() {
        try { disconnect() } catch (_: Throwable) {}
        try { scope.cancel() } catch (_: Throwable) {}
        super.onDestroy()
    }
}
