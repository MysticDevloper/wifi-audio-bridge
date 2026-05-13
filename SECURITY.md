# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in WiFi Audio Bridge, please report it privately.

**Do not open a public issue.** Instead, send a private report via one of these channels:

- **GitHub:** Use the [Security Advisories](https://github.com/MysticDevloper/wifi-audio-bridge/security/advisories) tab
- **Email:** Open a GitHub issue requesting contact information

We will acknowledge receipt within 48 hours and provide a timeline for a fix.

## What to Include

- Description of the vulnerability
- Steps to reproduce
- Affected versions
- Potential impact
- Any suggested fix (if applicable)

## Scope

This project handles raw audio data and network communication. The following are in scope:

- Remote code execution
- Privilege escalation
- Information disclosure via audio streams
- Denial of service

Please do not report:

- Dependency vulnerabilities already tracked by Dependabot
- Issues requiring physical access to the device
- Missing HTTPS on local network connections (expected for LAN-only apps)

## Supported Versions

| Version | Supported |
|---------|-----------|
| 2.0.x   | ✅        |

## Responsible Disclosure

We request a 90-day window from the time a vulnerability is reported to the time of public disclosure. This allows us to develop and distribute a fix before details are made public.
