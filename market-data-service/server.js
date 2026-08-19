const http = require('node:http');
const TradingView = require('@mathieuc/tradingview');

const PORT = Number(process.env.PORT || 3000);
const HOST = process.env.HOST || '0.0.0.0';
const MAX_LIMIT = 500;
const TIMEOUT_MS = 8000;
const TIMEFRAMES = Object.freeze({ '5M': '5', '15M': '15', '1H': '60', D: 'D', W: 'W' });
const SYMBOL = /^[A-Z0-9&._-]{1,32}$/;

function csvEscape(value) {
  const text = String(value);
  return /[",\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

function toCsv(periods) {
  const chronological = [...periods].sort((a, b) => a.time - b.time);
  if (chronological.length < 3) throw new Error('Insufficient closed-bar history');

  // chart.periods is newest-first. Drop the newest bar because it may still be forming.
  const closed = chronological.slice(0, -1);
  if (closed.length < 2) throw new Error('Insufficient closed-bar history');

  const rows = ['Timestamp,Open,High,Low,Close,Previous Close'];
  for (let i = 1; i < closed.length; i += 1) {
    const bar = closed[i];
    const previous = closed[i - 1];
    const timestamp = new Date(bar.time * 1000).toISOString();
    const values = [timestamp, bar.open, bar.max, bar.min, bar.close, previous.close];
    if (!values.slice(1).every((v) => Number.isFinite(v) && v > 0)) {
      throw new Error('TradingView returned invalid OHLC data');
    }
    rows.push(values.map(csvEscape).join(','));
  }
  return `${rows.join('\n')}\n`;
}

function fetchClosedBars(symbol, timeframe, limit) {
  return new Promise((resolve, reject) => {
    const client = new TradingView.Client();
    const chart = new client.Session.Chart();
    let settled = false;

    const finish = (error, value) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      try { chart.delete(); } catch (_) {}
      try { client.end(); } catch (_) {}
      if (error) reject(error); else resolve(value);
    };

    const timer = setTimeout(() => finish(new Error('TradingView request timed out')), TIMEOUT_MS);
    chart.onError((...args) => finish(new Error(args.map(String).join(' '))));
    chart.onUpdate(() => {
      try {
        if (chart.periods.length < 3) return;
        const requested = Math.min(limit + 2, MAX_LIMIT + 2);
        const periods = chart.periods.slice(0, requested);
        const csv = toCsv(periods);
        const lines = csv.trimEnd().split('\n');
        const header = lines[0];
        const data = lines.slice(1).slice(-limit);
        finish(null, `${header}\n${data.join('\n')}\n`);
      } catch (error) {
        finish(error);
      }
    });

    chart.setMarket(`NSE:${symbol}`, {
      timeframe: TIMEFRAMES[timeframe],
      range: Math.min(limit + 3, MAX_LIMIT + 3),
    });
  });
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    if (req.method === 'GET' && url.pathname === '/health') {
      res.writeHead(200, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
      res.end(JSON.stringify({ ok: true }));
      return;
    }
    if (req.method !== 'GET' || url.pathname !== '/v1/ohlc') {
      res.writeHead(404, { 'content-type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ ok: false, error: 'Not found' }));
      return;
    }

    const symbol = (url.searchParams.get('symbol') || '').trim().toUpperCase();
    const timeframe = (url.searchParams.get('timeframe') || '').trim().toUpperCase();
    const limit = Number(url.searchParams.get('limit') || 200);
    if (!SYMBOL.test(symbol)) throw new Error('Invalid NSE symbol');
    if (!Object.hasOwn(TIMEFRAMES, timeframe)) throw new Error('Unsupported timeframe');
    if (!Number.isInteger(limit) || limit < 20 || limit > MAX_LIMIT) throw new Error('limit must be 20-500');

    const csv = await fetchClosedBars(symbol, timeframe, limit);
    res.writeHead(200, {
      'content-type': 'text/csv; charset=utf-8',
      'cache-control': 'no-store',
      'x-data-provenance': 'TRADINGVIEW_UNOFFICIAL_NODE_ADAPTER',
    });
    res.end(csv);
  } catch (error) {
    res.writeHead(502, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
    res.end(JSON.stringify({ ok: false, error: error instanceof Error ? error.message : 'Market data unavailable' }));
  }
});

server.listen(PORT, HOST, () => {
  console.log(`Market data service listening on http://${HOST}:${PORT}`);
});

module.exports = { toCsv };
