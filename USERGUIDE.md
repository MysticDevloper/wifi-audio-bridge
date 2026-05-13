# WiFi Audio Bridge — User Guide

## Table of Contents

1. [Overview](#overview)
2. [Requirements](#requirements)
3. [Installation](#installation)
4. [Quick Start](#quick-start)
5. [Windows Server Guide](#windows-server-guide)
6. [Android App Guide](#android-app-guide)
7. [Features in Detail](#features-in-detail)
8. [Network Configuration](#network-configuration)
9. [Troubleshooting](#troubleshooting)
10. [Technical Reference](#technical-reference)

---

## Overview

WiFi Audio Bridge turns your Android phone into a **wireless microphone and speaker** for your Windows PC. Both audio directions are always active simultaneously — speak into your phone and hear the PC's audio on your phone, in real time.

**Use cases:**
- Use your phone as a wireless headset for video calls on PC
- Remote microphone for presentations
- Wireless speaker for PC audio in another room
- Assistive listening (hear PC audio through your phone)

---

## Requirements

### Windows Server
- Windows 10 or 11 (64-bit)
- .NET 8 Runtime (included with Windows Update, or install from [dotnet.microsoft.com](https://dotnet.microsoft.com))
- Microphone connected to your PC
- Speakers or headphones connected to your PC
- WiFi network (both devices on same network)

### Android App
- Android 8.1 (Oreo, API 27) or newer
- WiFi connection (same network as Windows PC)
- Microphone permission granted
- For automatic discovery: Location permission (Android 10+, required for WiFi scanning)

---

## Installation

### Windows Server

1. Download `WiFiAudioBridge-Server-v2.0.exe` from the `dist/` folder
2. Place it anywhere on your PC (desktop, documents, etc.)
3. Double-click to run — no installation needed
4. Windows may show a SmartScreen warning; click "Run anyway"

### Android App

**Option A — Install the APK directly:**
1. Download `WiFiAudioBridge-v2.0.apk` from the `dist/` folder
2. Transfer it to your phone (USB, email, cloud, etc.)
3. On your phone, open the APK file
4. If asked, enable "Install from unknown sources" for your file manager
5. Tap "Install"

**Option B — Build from source:**
```bash
cd android
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

---

## Quick Start

### Step 1: Start the Windows Server
1. Run `WiFiAudioBridge-Server-v2.0.exe`
2. Click the **▶ Start Server** button (bottom of the window)
3. The status should change to "● Waiting for client..."
4. Note the server IP shown in the window (e.g., `192.168.1.100`)

### Step 2: Connect from Android
1. Open the WiFi Audio Bridge app on your phone
2. **Auto-discover:** Tap "Auto-Discover Server" — the app will find the server automatically
3. **Manual:** Enter the server IP address and tap "Connect"
4. The status should show "Connected"

### Step 3: Audio Flows
- Speak into your phone → hear audio from your PC speakers
- Speak into your PC microphone → hear audio from your phone speaker
- Use the mute buttons to control either direction

---

## Windows Server Guide

### Server Window Layout

```
┌─────────────────────────────────────────────┐
│  WiFi Audio Bridge                          │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ ● Waiting for client...             │    │
│  │ TCP 8888 | UDP 8890/8891 | 192.168.1.100 │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ 🎤  MICROPHONE           0%    🔊   │    │
│  │ ████████████████████████████████░░  │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ 🔊  SPEAKER              0%    🔊   │    │
│  │ ████████████████████████████████░░  │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ NETWORK                             │    │
│  │ Upload:   0.0 KB/s                  │    │
│  │ Download: 0.0 KB/s                  │    │
│  │ Buffer:   0 ms        Clients: 0    │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │         ■  Stop Server              │    │
│  └─────────────────────────────────────┘    │
└─────────────────────────────────────────────┘
```

### Controls

| Control | Action |
|---------|--------|
| ▶ Start Server | Begin listening for Android connections |
| ■ Stop Server | Stop server, disconnect all clients |
| 🔊 / 🔇 (Mic) | Toggle PC microphone mute |
| 🔊 / 🔇 (Speaker) | Toggle PC speaker mute |

### Status Indicators

| Status | Meaning |
|--------|---------|
| ● Stopped (gray) | Server not running |
| ● Starting... (orange) | Server initializing |
| ● Waiting for client... (orange) | Server ready, waiting for phone |
| ● Streaming (green) | Phone connected, audio flowing |
| ● Request from 192.168.x.x | Discovery request received |
| Clients: 0/1 | Number of connected phones |

### Understanding the Metrics

| Metric | What it shows |
|--------|---------------|
| Upload | PC microphone audio being sent TO phone (KB/s) |
| Download | Phone microphone audio being received FROM phone (KB/s) |
| Buffer | Audio buffer fill level (lower = better, <20ms ideal) |
| Mic level bar | PC microphone input volume |
| Speaker level bar | Phone microphone audio level being played on PC |

---

## Android App Guide

### App Screen Layout

```
┌─────────────────────────────┐
│  WiFi Audio Bridge          │
│                             │
│  ● Connected                │
│  Connected (AAudio)         │
│                             │
│  [192.168.1.100      ]     │
│  [ Connect ]  [Auto-Discover]│
│                             │
│  🎤 MICROPHONE              │
│  ████████████████░░░░░ 42%  │
│  [🔊 Mute]                  │
│                             │
│  🔊 SPEAKER                 │
│  ████████████████░░░░░ 38%  │
│  [🔊 Mute]                  │
│                             │
│  Upload:  45.2 KB/s         │
│  Download: 38.7 KB/s        │
│  Buffer: 12 ms  12:34       │
└─────────────────────────────┘
```

### Controls

| Control | Action |
|---------|--------|
| IP Input | Enter server IP address manually |
| Connect | Connect to the entered IP |
| Auto-Discover | Find server automatically on LAN (disconnects if already connected) |
| Mic Mute | Toggle phone microphone mute |
| Speaker Mute | Toggle phone speaker mute |

### Status Messages

| Status | Meaning |
|--------|---------|
| Discovering... | Searching for server on LAN |
| Found: 192.168.x.x | Server discovered, connecting... |
| Connected (AAudio) | Connected using native low-latency audio |
| Connected (UDP) | Connected using Java audio fallback |
| Handshake failed: ... | TCP connection succeeded but handshake failed (see Troubleshooting) |
| Error: ... | Connection error (see message for details) |
| Server disconnected | Connection lost during streaming |

### Connection Modes

**Auto-Discover:**
1. App sends a UDP broadcast "WIFI_AUDIO_WHO?" on port 9999
2. Windows server responds with "WIFI_AUDIO_HERE:IP"
3. App connects automatically

**Manual:**
- Type the server's IP address in the input field
- Tap "Connect"

---

## Features in Detail

### Bidirectional Audio (Open Trunk)
Both audio directions are always active. There is no push-to-talk. When connected:
- Your phone's microphone audio plays through your PC speakers
- Your PC's microphone audio plays through your phone speaker
- Both streams run simultaneously and independently

### Audio Quality
- **Sample rate:** 44100 Hz (CD quality)
- **Bit depth:** 16-bit PCM
- **Channels:** Mono
- **Codec:** Uncompressed PCM (no audio compression — highest quality, minimal latency)

### Latency
Typical end-to-end latency:
- **AAudio path (Android 8.1+):** ~30-60ms
- **Java AudioRecord path:** ~50-100ms

Latency depends on:
- WiFi network quality
- Phone model and CPU speed
- Audio buffer sizes

### Native vs Java Audio Path

| Feature | AAudio (Native) | AudioRecord (Java) |
|---------|----------------|-------------------|
| Android version | 8.1+ (API 27+) | 7.0+ (API 24+) |
| Latency | Lower (~30ms) | Moderate (~60ms) |
| CPU usage | Lower | Moderate |
| Automatic fallback | — | Used if native fails |

The app automatically tries the native path first and falls back to Java if unavailable.

### Connection Protocol

```
TCP CONTROL CHANNEL (port 8888):
  Android → Server:  "UDP\n"           (handshake request)
  Server → Android:  "OK\n"            (handshake accepted)
  Android → Server:  "PING\n" (every 5s) (keepalive)

UDP AUDIO CHANNELS:
  Android → Server (port 8890):  Mic audio packets
  Server → Android (port 8891):  Speaker audio packets

UDP DISCOVERY (port 9999):
  Android → Broadcast:  "WIFI_AUDIO_WHO?"
  Server → Android:     "WIFI_AUDIO_HERE:IP"
```

---

## Network Configuration

### Ports Used

| Port | Protocol | Direction | Purpose |
|------|----------|-----------|---------|
| 8888 | TCP | Bidirectional | Control / handshake / keepalive |
| 8890 | UDP | Android → Server | Phone mic audio |
| 8891 | UDP | Server → Android | PC mic audio |
| 9999 | UDP | Bidirectional | Server discovery |

### Firewall Configuration

If you have a firewall enabled, you may need to allow these ports:

**Windows Firewall:**
1. Open "Windows Defender Firewall with Advanced Security"
2. Create Inbound Rules for ports 8888 (TCP), 8890-8891 (UDP), 9999 (UDP)
3. Or simply allow `WifiAudioBridge.exe` through the firewall when prompted

**Network Requirements:**
- Both devices must be on the same subnet
- WiFi router must allow client-to-client traffic (AP isolation must be OFF)
- Enterprise WiFi networks may block peer-to-peer traffic

### Multiple Networks

If you have multiple network adapters (WiFi + Ethernet + VPN):
- The server auto-selects the best IP (WiFi preferred, then Ethernet)
- Virtual adapters (VMware, Docker, Hyper-V, VPN) are ignored
- You can see the selected IP in the server window

---

## Troubleshooting

### Connection Issues

| Symptom | Cause | Solution |
|---------|-------|----------|
| "Discovery failed" | UDP broadcast blocked | Enter IP manually |
| "Handshake failed: server closed connection" | Server has stale client state | Stop and restart the server, wait 2 seconds, try again |
| "Handshake failed: server rejected (rebuild server?)" | Server/APK version mismatch | Rebuild both from same source code |
| "Handshake failed: got '...'" | Unknown server response | Server binary is outdated — rebuild server |
| "Error: Connection refused" | Server not running on that IP | Start the server, check IP address |
| "Error: Network unreachable" | Wrong network | Both devices must be on same WiFi network |

### Audio Issues

| Symptom | Cause | Solution |
|---------|-------|----------|
| No audio from phone mic to PC | Mic muted on phone | Check mic mute button on phone |
| No audio from PC mic to phone | Mic muted on PC | Check mic mute button on server |
| Audio is choppy or stuttering | Poor WiFi signal | Move closer to router, reduce interference |
| High latency | Network congestion | Close bandwidth-heavy apps (streaming, downloads) |
| Echo / feedback | Phone near PC speakers | Use headphones on PC, lower phone volume |
| No sound on PC | Wrong playback device | Check Windows sound settings, select correct speaker |
| No sound on phone | Phone volume low | Increase phone media volume |
| Audio starts then stops | Network packet loss | Check WiFi stability, restart server |

### Windows Server Issues

| Symptom | Cause | Solution |
|---------|-------|----------|
| "Port already in use" | Another app on port 8888 | Close other apps, or change port in source |
| NAudio initialization fails | No microphone connected | Connect a microphone to your PC |
| WasapiOut fails | Audio device in use | Close other apps using audio |
| Server crashes on start | Corrupted NAudio install | Restart PC, rebuild server |
| Server process won't die | Use Task Manager | End task for WifiAudioBridge.exe |

### Android App Issues

| Symptom | Cause | Solution |
|---------|-------|----------|
| App shows "Connected" but no audio | Audio permission denied | Grant microphone permission in Settings |
| Auto-discover doesn't find server | Location permission denied | Grant location permission (required for WiFi scanning on Android 10+) |
| App crashes on start | Insufficient permissions | Check all permissions are granted |
| Service not running | App was force-stopped | Reopen the app |
| Connection drops after screen off | Battery optimization | Disable battery optimization for the app |

---

## Technical Reference

### Audio Packet Format

Every UDP audio packet has a 13-byte header followed by PCM audio data:

```
Byte 0-1:    Magic number (0xABCD, little-endian)
Byte 2-3:    Sequence number (big-endian)
Byte 4-7:    Reserved (zero)
Byte 8-11:   Payload length in bytes (little-endian)
Byte 12:     Stream type (0=MIC, 1=SPEAKER)
Byte 13+:    PCM 16-bit mono audio samples
```

### Audio Format
- **Sample format:** Signed 16-bit PCM (little-endian)
- **Sample rate:** 44100 Hz
- **Channels:** 1 (mono)
- **Bytes per sample:** 2
- **Bytes per second:** 88200

### Ring Buffer
The native C implementation uses a lock-free single-producer single-consumer ring buffer:
- Power-of-two capacity (8192 samples per buffer)
- Unsigned integer indices for correct wraparound
- Atomic operations for thread-safe read/write
- Two buffers: one for mic audio, one for speaker audio

### Build from Source

**Android:**
```bash
# Prerequisites: Android SDK, NDK 25+, CMake 3.22+, JDK 17+
cd android
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

**Windows:**
```bash
# Prerequisites: .NET 8 SDK
cd windows/WifiAudioBridge
dotnet build --configuration Release
# Output: bin/Release/net8.0-windows/WifiAudioBridge.exe
```
