# WiFi Audio Bridge

[![Release](https://img.shields.io/github/v/release/MysticDevloper/wifi-audio-bridge?color=blue&label=Download)](https://github.com/MysticDevloper/wifi-audio-bridge/releases/latest)
[![Build Android](https://github.com/MysticDevloper/wifi-audio-bridge/actions/workflows/android-build.yml/badge.svg)](https://github.com/MysticDevloper/wifi-audio-bridge/actions/workflows/android-build.yml)
[![Build Windows](https://github.com/MysticDevloper/wifi-audio-bridge/actions/workflows/windows-build.yml/badge.svg)](https://github.com/MysticDevloper/wifi-audio-bridge/actions/workflows/windows-build.yml)
[![License](https://img.shields.io/github/license/MysticDevloper/wifi-audio-bridge?color=green)](LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)
[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-donate-yellow.svg)](https://www.buymeacoffee.com/MysticDevloper)

Bidirectional real-time audio streaming between Android and Windows over WiFi. Turn your Android phone into a wireless microphone and speaker for your PC.

> **⬇️ Download:** [WiFiAudioBridge-v2.0.apk](https://github.com/MysticDevloper/wifi-audio-bridge/releases/latest/download/WiFiAudioBridge-v2.0.apk) · [WiFiAudioBridge-Server-v2.0.exe](https://github.com/MysticDevloper/wifi-audio-bridge/releases/latest/download/WiFiAudioBridge-Server-v2.0.exe)

---

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

## Screenshots

| Android App | Windows Server |
|-------------|---------------|
| *(add screenshot here)* | *(add screenshot here)* |

## Quick Start

1. **Download** the [Windows server](https://github.com/MysticDevloper/wifi-audio-bridge/releases/latest/download/WiFiAudioBridge-Server-v2.0.exe) — just run it, click **▶ Start Server**
2. **Install** the [Android APK](https://github.com/MysticDevloper/wifi-audio-bridge/releases/latest/download/WiFiAudioBridge-v2.0.apk) on your phone
3. **Open the app** — grant microphone permission, tap **Auto-Discover Server**
4. **Audio flows both ways** — speak into your phone, hear it from your PC speakers

For detailed setup: see the **[User Guide](USERGUIDE.md)**

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

## Building from Source

### Android
```bash
cd android
./gradlew assembleDebug
# APK → android/app/build/outputs/apk/debug/app-debug.apk
```
*Requires: Android SDK 34+35, NDK 25.2.9519653, CMake 3.22.1, JDK 17+*

### Windows
```bash
cd windows/WifiAudioBridge
dotnet build --configuration Release
# EXE → windows/WifiAudioBridge/bin/Release/net8.0-windows/WifiAudioBridge.exe
```
*Requires: .NET 8 SDK*

## Project Structure

```
wifi-audio-bridge/
├── android/                          # Android app (Kotlin + C native)
│   └── app/src/main/
│       ├── java/com/audiobridge/     # Kotlin service & UI
│       ├── cpp/                      # C native AAudio engine + JNI
│       └── AndroidManifest.xml
├── windows/                          # Windows server (C# .NET 8)
│   └── WifiAudioBridge/
│       ├── Form1.cs                  # UI, audio pipeline, TCP/UDP
│       └── Program.cs
├── dist/                             # Pre-built binaries
│   ├── WiFiAudioBridge-v2.0.apk
│   └── WiFiAudioBridge-Server-v2.0.exe
├── .github/workflows/                # CI build pipelines
├── USERGUIDE.md                      # Detailed user guide
├── CHANGELOG.md                      # Release history
├── CONTRIBUTING.md                   # Contribution guidelines
└── LICENSE                           # MIT license
```

## Documentation

| Document | Description |
|----------|-------------|
| **[User Guide](USERGUIDE.md)** | Full setup, controls, troubleshooting, network config |
| **[Contributing](CONTRIBUTING.md)** | How to report issues, submit PRs, and code style |
| **[Changelog](CHANGELOG.md)** | Release history and version notes |
| **[Security](SECURITY.md)** | Vulnerability reporting policy |
| **[Code of Conduct](CODE_OF_CONDUCT.md)** | Community guidelines |

## License

MIT — see [LICENSE](LICENSE)

---

[!["Buy Me A Coffee"](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-donate-yellow.svg)](https://www.buymeacoffee.com/MysticDevloper)
