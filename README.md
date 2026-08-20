# Institutional Trading System — Android Cleanroom

Signal-only Android application for Indian equities and F&O analysis. The app accepts a stock/index name or NSE symbol plus timeframe and returns deterministic closed-bar analysis in-app. It never asks the operator for a TradingView chart URL or CSV file; symbol resolution, market-data intake, chart loading, and signal presentation happen inside the app.

Security and scope:
- No Telegram integration exists.
- No broker login, broker credential, order-execution surface, `.env`, account profile, or API secret is included in source or APK artifacts.
- The current TradingView WebSocket adapter is a temporary development market-data provider behind a swappable provider interface.
- Stocks mode analyzes verified underlying OHLC data. F&O mode fails closed unless a provider supplies verified derivative-contract data; strike, premium, OI, IV, and Greeks are never fabricated.
- Forming, stale, future-dated, malformed, delayed, low-quality, or insufficient data produces `NO_TRADE`.
- Signal analysis uses confirmed market structure with EMA 20/50/200, RSI, ATR, momentum, breakout/support-resistance logic, and VWAP/liquidity confirmation only when verified volume exists.
- Valid directional setups must also pass a 1–2% structural-stop gate and expose 1.5R and 3R exits; otherwise the app returns `NO_TRADE`.

Build matrix: Java 17, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile/target SDK 35, minimum SDK 24.
