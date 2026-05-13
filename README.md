# WiFi Audio Bridge

Bidirectional real-time audio streaming between Android and Windows over WiFi. Turn your Android phone into a wireless microphone and speaker for your PC.

[!["Buy Me A Coffee"](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-donate-yellow.svg)](https://www.buymeacoffee.com/MysticDevloper)

## Features

- **Bidirectional audio** — Phone mic streams to PC speakers; PC mic streams to phone speaker
- **Ultra-low latency** — AAudio (native C) path on Android 8.1+; Java AudioRecord/AudioTrack fallback
- **Auto-discovery** — Finds the Windows server on your LAN via UDP broadcast
- **Crash-proof** — Every error path caught; service survives connection drops and app close
- **Open trunk** — Both audio directions always active simultaneously
- **Level meters** — Real-time mic/speaker volume display on both sides
- **Mute controls** — Independent mute for mic and speaker on both sides
- **Foreground service** — Android foreground service with persistent notification
- **Single-client** — Windows server handles one Android device at a time

## Architecture

```
┌──────────────────────┐          TCP (port 8888)          ┌──────────────────────┐
│   Android Phone      │◄───── control / keepalive ──────►│   Windows PC         │
│   (AudioBridge App)  │                                   │   (AudioBridge Svr)  │
│                      │                                   │                      │
│  ┌────────────────┐  │   UDP (port 8890) STREAM_MIC=0   │  ┌────────────────┐  │
│  │  AudioRecord   │──┼──────────────►┐ ┌───────────────►│  │   WaveInEvent  │  │
│  │  or AAudio cap │  │               │ │                │  │  (mic capture)  │  │
│  └────────────────┘  │               │ │                │  └────────────────┘  │
│                      │               │ │                │                      │
│  ┌────────────────┐  │               ▼ │                │  ┌────────────────┐  │
│  │  AudioTrack    │◄─┼◄──────────────┘ └────────────────┼──│  BufferedWave  │  │
│  │  or AAudio play│  │   UDP (port 8891) STREAM_SPEAKER=1│  │  Provider+Wasapi│  │
│  └────────────────┘  │                                   │  └────────────────┘  │
└──────────────────────┘                                   └──────────────────────┘
```

### Protocol

| Layer | Transport | Port | Purpose |
|-------|-----------|------|---------|
| Control | TCP | 8888 | Handshake, keepalive (PING) |
| Mic → PC | UDP | 8890 | Phone mic audio to PC speakers |
| PC mic → Phone | UDP | 8891 | PC mic audio to phone speaker |
| Discovery | UDP | 9999 | Server auto-discovery broadcast |

### Audio Packet Header (13 bytes)

| Offset | Size | Field | Description |
|--------|------|-------|-------------|
| 0 | 2 | Magic | `0xABCD` (little-endian) |
| 2 | 2 | Sequence | Packet sequence number (big-endian) |
| 4 | 4 | Reserved | Zero |
| 8 | 4 | Payload length | PCM byte count (little-endian) |
| 12 | 1 | Stream type | `0` = MIC, `1` = SPEAKER |

## Project Structure

```
wifi-audio-bridge/
├── android/                          # Android app (Kotlin + C native)
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/audiobridge/
│   │   │   │   ├── AudioBridgeService.kt    # Foreground service, audio pipeline
│   │   │   │   └── MainActivity.kt          # UI, user interaction
│   │   │   ├── cpp/
│   │   │   │   ├── audio_engine.h           # Ring buffer + engine header
│   │   │   │   ├── audio_engine.c           # AAudio + UDP native implementation
│   │   │   │   ├── audio_bridge_jni.c       # JNI bridge to Kotlin
│   │   │   │   └── CMakeLists.txt           # CMake build config
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle.properties
│   └── settings.gradle.kts
│
└── windows/                          # Windows server (C# .NET 8)
    └── WifiAudioBridge/
        ├── Form1.cs                  # Main UI, audio pipeline, TCP/UDP handling
        ├── Program.cs                # Entry point
        └── WifiAudioBridge.csproj    # .NET 8 project (NAudio dependency)
```

## Building

### Prerequisites

**Android:**
- Android SDK (platforms 34+35, build-tools 33+)
- NDK 25.2.9519653 (for native AAudio path)
- CMake 3.22.1
- JDK 17+
- Gradle 8.4 (wrapper included)

**Windows:**
- .NET 8 SDK
- NAudio 2.2.1 (NuGet, restored automatically)

### Android

```bash
cd android
./gradlew assembleDebug
# APK at: android/app/build/outputs/apk/debug/app-debug.apk
```

### Windows

```bash
cd windows/WifiAudioBridge
dotnet build --configuration Release
# EXE at: windows/WifiAudioBridge/bin/Release/net8.0-windows/WifiAudioBridge.exe
```

## Usage

1. **Start the Windows server** — double-click `WifiAudioBridge.exe`, click ▶ Start Server
2. **Install the Android APK** on your phone
3. **Open the app** — grant microphone and notification permissions
4. **Auto-discover** — tap "Auto-Discover Server" to find the server on your LAN
5. **Or enter IP manually** — type the server's IP and tap "Connect"
6. **Audio flows both ways** — phone mic → PC speakers, PC mic → phone speaker

On the Android side you can:
- Monitor levels in real-time
- Mute/unmute mic or speaker
- Disconnect manually

On the Windows side you can:
- Monitor levels and data rates
- Mute/unmute mic or speaker
- See buffering status

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| "Handshake failed: server closed connection" | Server not running, or stale client slot | Restart server, wait 2s, connect again |
| "Handshake failed: got ..." | Server binary doesn't match source | Rebuild server with `dotnet build` |
| "Discovery failed" | Firewall blocking UDP 9999 | Check firewall, or enter IP manually |
| No audio (AAudio path) | Device < Android 8.1 | Falls back to Java AudioRecord automatically |
| Audio cuts out after ~13 hours | Ring buffer index overflow (fixed) | Already fixed in current code |

## Technical Notes

- **Ring buffer**: Lock-free single-producer single-consumer ring buffer using atomic unsigned integer indices with power-of-two capacity
- **Thread priorities**: Audio threads run at `SCHED_FIFO` priority 10 on Android native path; `THREAD_PRIORITY_URGENT_AUDIO` on Java path
- **Keepalive**: Android sends `"PING\n"` every 5 seconds over the control TCP connection to detect disconnection
- **Fallback chain**: AAudio native → Java AudioRecord/AudioTrack → graceful error message

## License

MIT
