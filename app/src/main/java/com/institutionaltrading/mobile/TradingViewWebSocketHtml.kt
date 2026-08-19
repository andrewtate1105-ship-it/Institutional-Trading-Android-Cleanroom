package com.institutionaltrading.mobile

object TradingViewWebSocketHtml {
    private val symbolPattern = Regex("^[A-Z0-9&._-]{1,32}$")
    private val intervals = mapOf(
        "5M" to "5",
        "15M" to "15",
        "1H" to "60",
        "D" to "D",
        "W" to "W",
    )

    fun render(symbol: String, timeframe: String, limit: Int, requestToken: String): String {
        val normalizedSymbol = symbol.trim().uppercase()
        require(symbolPattern.matches(normalizedSymbol)) { "Invalid TradingView symbol" }
        val interval = intervals[timeframe] ?: throw IllegalArgumentException("Unsupported timeframe")
        require(limit in 20..500) { "TradingView bar limit must be 20-500" }
        require(requestToken.matches(Regex("^[A-Za-z0-9_-]{8,80}$"))) { "Invalid request token" }

        val target = limit + 2
        val range = limit + 3
        val pricesKey = "${'$'}prices"
        return """
            <!doctype html>
            <html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body><script>
            (() => {
              'use strict';
              const TOKEN = '$requestToken';
              const SYMBOL = 'NSE:$normalizedSymbol';
              const TIMEFRAME = '$interval';
              const LIMIT = $limit;
              const TARGET = $target;
              const RANGE = $range;
              const session = 'cs_' + Math.random().toString(36).slice(2, 14).padEnd(12, '0').slice(0, 12);
              const periods = new Map();
              let done = false;
              let ws;

              const frame = (payload) => {
                const message = typeof payload === 'string' ? payload : JSON.stringify(payload);
                return '~m~' + message.length + '~m~' + message;
              };
              const send = (method, params) => ws.send(frame({m: method, p: params}));
              const fail = (message) => {
                if (done) return;
                done = true;
                try { if (ws) ws.close(); } catch (_) {}
                AndroidBridge.onError(TOKEN, String(message || 'TradingView data unavailable'));
              };
              const isExplicitlyDelayed = (info) => {
                if (!info || typeof info !== 'object') return false;
                const delayValues = [info.delay, info.delay_sec, info.delay_seconds, info.data_delay]
                  .map((value) => Number(value))
                  .filter((value) => Number.isFinite(value));
                if (delayValues.some((value) => value > 0)) return true;
                if (info.is_delayed === true || info.delayed === true) return true;
                const status = [info.data_status, info.update_mode, info.status, info.data_mode]
                  .filter((value) => typeof value === 'string')
                  .join(' ')
                  .toLowerCase();
                return status.includes('delay');
              };
              const finish = () => {
                if (done || periods.size < TARGET) return;
                try {
                  const sorted = Array.from(periods.values()).sort((a, b) => a.time - b.time);
                  const closed = sorted.slice(0, -1);
                  const selected = closed.slice(-(LIMIT + 1));
                  if (selected.length !== LIMIT + 1) throw new Error('Incomplete closed-bar history');
                  const rows = [];
                  for (let i = 1; i < selected.length; i += 1) {
                    const bar = selected[i];
                    const previous = selected[i - 1];
                    const values = [bar.open, bar.high, bar.low, bar.close, previous.close];
                    if (!values.every((value) => Number.isFinite(value) && value > 0)) {
                      throw new Error('Invalid OHLC values');
                    }
                    rows.push({
                      timestamp: new Date(bar.time * 1000).toISOString(),
                      open: bar.open,
                      high: bar.high,
                      low: bar.low,
                      close: bar.close,
                      previousClose: previous.close
                    });
                  }
                  done = true;
                  try { ws.close(); } catch (_) {}
                  AndroidBridge.onSuccess(TOKEN, JSON.stringify({rows}));
                } catch (error) {
                  fail(error && error.message ? error.message : String(error));
                }
              };
              const parse = (raw) => {
                const text = String(raw).replace(/~h~/g, '');
                const parts = text.split(/~m~[0-9]+~m~/g).filter(Boolean);
                for (const part of parts) {
                  let packet;
                  try { packet = JSON.parse(part); } catch (_) { continue; }
                  if (typeof packet === 'number') {
                    ws.send(frame('~h~' + packet));
                    continue;
                  }
                  if (!packet || !packet.m || !Array.isArray(packet.p)) continue;
                  if (packet.m === 'symbol_error' || packet.m === 'series_error' || packet.m === 'critical_error' || packet.m === 'protocol_error') {
                    fail(packet.m);
                    return;
                  }
                  if (packet.m === 'symbol_resolved') {
                    const info = packet.p[2];
                    if (isExplicitlyDelayed(info)) {
                      fail('TradingView reports delayed market data for this NSE symbol; no current signal will be generated');
                      return;
                    }
                    continue;
                  }
                  if (packet.m !== 'timescale_update' && packet.m !== 'du') continue;
                  const update = packet.p[1];
                  const priceSet = update && update['$pricesKey'];
                  const series = priceSet && priceSet.s;
                  if (!Array.isArray(series)) continue;
                  for (const point of series) {
                    const values = point && point.v;
                    if (!Array.isArray(values) || values.length < 6) continue;
                    periods.set(values[0], {
                      time: Number(values[0]),
                      open: Number(values[1]),
                      high: Number(values[2]),
                      low: Number(values[3]),
                      close: Number(values[4])
                    });
                  }
                  finish();
                }
              };

              setTimeout(() => fail('TradingView request timed out before full history loaded'), 12000);
              ws = new WebSocket('wss://data.tradingview.com/socket.io/websocket?from=chart&type=chart');
              ws.onopen = () => {
                send('set_auth_token', ['unauthorized_user_token']);
                send('chart_create_session', [session]);
                send('resolve_symbol', [session, 'ser_1', '=' + JSON.stringify({symbol: SYMBOL, adjustment: 'splits'})]);
                send('create_series', [session, '$pricesKey', 's1', 'ser_1', TIMEFRAME, RANGE]);
              };
              ws.onmessage = (event) => parse(event.data);
              ws.onerror = () => fail('TradingView websocket error');
              ws.onclose = () => { if (!done) fail('TradingView websocket closed before data completed'); };
            })();
            </script></body></html>
        """.trimIndent()
    }
}
