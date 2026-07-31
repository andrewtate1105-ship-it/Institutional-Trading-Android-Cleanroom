# Institutional Trading System — Android Cleanroom

Signal-only Android application. This repository contains only the clean Android source project and its build workflow.

Security and scope:
- Telegram bot token is entered only inside the app and encrypted with Android Keystore.
- No Telegram token, chat ID, account profile, `.env`, or broker credential is included in source or build artifacts.
- No Groww login or broker order-execution surface exists.
- Insufficient or ambiguous evidence produces `NO_TRADE`.

Build matrix: Java 17, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile/target SDK 35, minimum SDK 24.
