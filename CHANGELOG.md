# Changelog

## [2.0] - 2026-05-13

### Added
- Initial public release
- Bidirectional open-trunk audio streaming (phone mic ↔ PC speaker)
- AAudio native C path for low-latency audio on Android 8.1+
- Java AudioRecord/AudioTrack fallback path
- UDP broadcast auto-discovery (WIFI_AUDIO_WHO? / WIFI_AUDIO_HERE:IP)
- TCP control channel with handshake protocol (UDP\n / OK\n)
- Keepalive PING every 5 seconds over TCP
- Lock-free ring buffer for audio data transfer (SPSC, power-of-two capacity)
- Real-time level meters on both Android and Windows
- Independent mute controls for mic and speaker on both sides
- Android foreground service with persistent notification
- Windows server with NAudio (WasapiOut / WaveInEvent) audio
- Server auto-detects best network interface (WiFi preferred)
- Single-client architecture with automatic cleanup
- Crash-proof error handling — all paths caught with Throwable
- Compiled APK and EXE available in dist/ and GitHub Releases
- Comprehensive user guide (USERGUIDE.md)
