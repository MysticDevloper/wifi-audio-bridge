# Contributing to WiFi Audio Bridge

Thanks for your interest in contributing! Here's how you can help.

## Reporting Issues

- Search existing issues before opening a new one
- Include your device model, Android version, and Windows version
- Provide steps to reproduce, expected behavior, and actual behavior
- Include any error messages from the app (screenshot or text)

## Feature Requests

- Check if the feature already exists or was already requested
- Describe the use case clearly
- Explain why it would be useful to other users

## Pull Requests

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes with clear commit messages
4. Push to your fork
5. Open a Pull Request

### Code Style

**Android (Kotlin):**
- Follow existing patterns in the codebase
- Use `camelCase` for functions and variables
- Add `Throwable` catch-all at coroutine top-levels (never let the service crash)
- Keep functions focused and single-purpose

**Android (C):**
- Follow the existing style in `audio_engine.c` and `audio_engine.h`
- Use `snake_case` for functions and variables
- Always check return values (especially for JNI and socket calls)
- Never introduce undefined behavior

**Windows (C#):**
- Use .NET naming conventions (PascalCase for methods/properties)
- Use expression-bodied members where appropriate
- Always wrap audio/network code in try-catch

### Before Submitting

1. Build the Android app: `cd android && ./gradlew assembleDebug`
2. Build the Windows server: `cd windows/WifiAudioBridge && dotnet build`
3. Test that both sides can connect and stream audio
4. Verify the app survives: connect/disconnect cycles, app close, screen off

## Code of Conduct

Please note that this project follows a Code of Conduct. Be respectful, inclusive, and constructive in all interactions.
