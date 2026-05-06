# AlgoTrading — Application Documentation

Spring Boot 3.3.4 · Java 17 · TiDB Cloud · AngelOne SmartAPI · Zerodha Kite Connect

---

## Table of Contents
1. [Architecture Overview](#architecture-overview)
2. [How It Works — End to End](#how-it-works--end-to-end)
3. [Index Option Flow](#index-option-flow)
4. [All Configurable Settings](#all-configurable-settings)
5. [application.properties Reference](#applicationproperties-reference)
6. [Runtime Settings (DB-backed)](#runtime-settings-db-backed)
7. [instruments.txt Reference](#instrumentstxt-reference)

---

## Architecture Overview

```
AngelOne SmartAPI          Zerodha Kite Connect
  │ market data                  │ orders
  ▼                              ▼
TickStreamService         BrokerOrderService
  │ live ticks                   ▲
  ▼                              │
TradeMonitorService ─────────────┘
  │ TEMA signals
  ├─ AlgoScanService (10-min stocks)
  └─ IndexOptionScanService (intraday index options)
         │ option selection
         ▼
  OptionSelectionService (in-memory NFO cache)
         ▲
  InstrumentRefreshService (ScripMaster, Tues 15:45)
```

**Package:** `self.sai.stock.AlgoTrading`  
**Main class:** `App extends SpringBootServletInitializer`  
**DB schema:** `algo_trading` on TiDB Cloud (MySQL-compatible)

---

## How It Works — End to End

### Morning Startup (8:55 AM IST, Mon–Fri)
1. `MarketSessionScheduler` fetches the last 60 sessions of 10-min candles for all instruments in `instruments.txt`.
2. `IndexOptionScheduler` pre-loads historical 5-min index candles.
3. AngelOne tick stream starts for all NSE tokens.

### Intraday (9:20 AM – 3:30 PM, every 5 min)
- **10-min watchlist:** `AlgoScanService` runs TEMA on 10-min candles → creates `WATCHING` trades → `TradeMonitorService` places BUY orders on signal.
- **Index options:** `IndexOptionScanService` runs TEMA on 5-min index candles → BUY signal → buy CE; SELL signal → buy PE. Closes the opposite leg if active.

### Weekly (Tuesday 3:45 PM IST)
- `InstrumentRefreshService` downloads AngelOne ScripMaster, filters by `instruments.txt`, and refreshes the `instruments` table + in-memory NFO option cache.

---

## Index Option Flow

| Step | What happens |
|------|--------------|
| TEMA BUY crossover on Nifty | `closePeIfActive()` + `openOption(..., "CE", ...)` |
| TEMA SELL crossover on Nifty | `closeCeIfActive()` + `openOption(..., "PE", ...)` |
| `openOption` | Calls `OptionSelectionService.selectOption("NIFTY", "CE"/"PE", ltp, 3)` |
| Option selection filter | name = "NIFTY" → symbol contains CE/PE → expiry ≥ today+3 days → nearest expiry → nearest ATM strike |
| Order | `placeManualBuyOrder(trade)` → LIMIT with market protection at ltp × (1 + 0.5%) for BUY |
| Expiry today | Only close existing option — no new option opened |

**Name mapping:** `"Nifty 50"` → strips numeric tokens → `"NIFTY"` (matches ScripMaster `name` field)

---

## All Configurable Settings

### Section A — `application.properties` (server restart required to change)

#### Database
| Property | Default | Description |
|----------|---------|-------------|
| `spring.datasource.url` | TiDB Cloud JDBC URL | Full JDBC connection string |
| `spring.datasource.username` | `3VvNYUPCcPdL5Nm.root` | DB username |
| `spring.datasource.password` | `PzYCvkwSJa2Ucm8V` | DB password |
| `spring.datasource.hikari.maximum-pool-size` | `5` | Max DB connections |
| `spring.datasource.hikari.max-lifetime` | `180000` ms | Connection max lifetime (keep below TiDB idle timeout ~5 min) |

#### JWT (App Login)
| Property | Default | Description |
|----------|---------|-------------|
| `jwt.secret` | `AlGoTrAdInGsEcReTkEy2026MuStBe32CharsLong!` | HMAC-SHA256 secret — **change before deploying**; minimum 32 chars |
| `jwt.expiry.hours` | `24` | How long a login token stays valid |

#### AngelOne SmartAPI (Market Data)
| Property | Default | Description |
|----------|---------|-------------|
| `angelone.api.key` | `fYy1t3Zh` | AngelOne API key for candle fetch + tick stream |

#### Zerodha Kite Connect (Orders)
| Property | Default | Description |
|----------|---------|-------------|
| `zerodha.api.key` | `7mov9qt27tpmk2ft` | Production API key |
| `zerodha.api.secret` | `00jheezucvwwxaurf806p5jzp5gqsts3` | Production API secret |
| `zerodha.localhost.api.key` | `t0wronuskh3xc86e` | Dev/localhost API key |
| `zerodha.localhost.api.secret` | `yxdyz0xjoi7w3zgekrfctp85m90q128x` | Dev/localhost API secret |

#### Trade Execution
| Property | Default | Description |
|----------|---------|-------------|
| `trading.paper` | `false` | `true` = paper trading (no real orders; all instantly filled). `false` = live orders via Zerodha |
| `trading.exchange` | `NSE` | Exchange for all stock orders (`NSE` or `BSE`) |
| `trading.market-protection-pct` | `0.5` | Market protection buffer %. MARKET orders with a known reference price become LIMIT orders at ref × (1 ± pct/100). Set to `0` to disable and use plain MARKET |

#### Session Store
| Property | Default | Description |
|----------|---------|-------------|
| `session.file` | `session-store.json` | Path to file used to persist AngelOne + Zerodha session tokens across restarts |

---

### Section B — Runtime Settings (DB-backed, no restart needed)

These are stored in the `settings` table and editable via the Settings UI or the API (`POST /api/settings`). Defaults apply automatically when a key has no DB row.

#### Group: `Algo Scan`
| Key | Default | Type | Description |
|-----|---------|------|-------------|
| `numberOfCandles` | `15` | int | TEMA grouping width — number of candles used to compute the signal series. Applies to both the 10-min stock scan and the intraday 5-min index option scan |

#### Group: `Orders`
| Key | Default | Type | Description |
|-----|---------|------|-------------|
| `orderType` | `MARKET` | string | Order type for all auto/manual trades. `MARKET` or `LIMIT`. When `MARKET` + a reference price is available, market protection is applied automatically |
| `place10MinOrders` | `true` | boolean | Master toggle for 10-min watchlist auto-trading. `false` → WATCHING trades never place a BUY order |
| `placeDailyOrders` | `true` | boolean | Master toggle for daily watchlist auto-trading (currently disabled scheduler) |
| `max10MinOrders` | `5` | int | Maximum number of concurrently OPEN trades for the 10-min watchlist. New buys are skipped until open count drops below this cap |
| `maxDailyOrders` | `5` | int | Maximum number of concurrently OPEN trades for the daily watchlist |
| `pollingIntervalMinutes` | `5` | int | How often (in minutes) the order-poller thread checks pending BUY PLACED / SELL PLACED orders for fill status at Zerodha |

---

### Settings API

```
GET  /api/settings/all              → all groups + keys with defaults applied
GET  /api/settings?key=orderType    → value(s) for a specific key across groups
POST /api/settings                  → upsert single: { "group": "Orders", "key": "orderType", "value": "LIMIT" }
POST /api/settings/bulk             → upsert multiple: [ { "group":..., "key":..., "value":... }, ... ]
```

---

## application.properties Reference

```properties
# ── Database ─────────────────────────────────────────────────────────────────
spring.datasource.url=jdbc:mysql://<host>:<port>/algo_trading?sslMode=VERIFY_IDENTITY&...
spring.datasource.username=<user>
spring.datasource.password=<password>
spring.datasource.hikari.maximum-pool-size=5
spring.datasource.hikari.max-lifetime=180000

# ── JWT ───────────────────────────────────────────────────────────────────────
jwt.secret=<min 32 chars>
jwt.expiry.hours=24

# ── Brokers ───────────────────────────────────────────────────────────────────
angelone.api.key=<key>
zerodha.api.key=<prod key>
zerodha.api.secret=<prod secret>
zerodha.localhost.api.key=<dev key>
zerodha.localhost.api.secret=<dev secret>

# ── Trade Execution ───────────────────────────────────────────────────────────
trading.paper=false
trading.exchange=NSE
trading.market-protection-pct=0.5

# ── Session ───────────────────────────────────────────────────────────────────
session.file=session-store.json
```

---

## instruments.txt Reference

Located at `src/main/resources/instruments.txt`. Read by `InstrumentRefreshService` every Tuesday at 15:45 IST.

```
type : 10MinWatchlist
instruments : ADANIENT,TATAMOTORS,VEDL

type : Index
instruments : NIFTY 50
```

| Field | Description |
|-------|-------------|
| `type : 10MinWatchlist` | Stocks to scan on 10-min TEMA. Must match NSE `-EQ` symbol prefix in ScripMaster |
| `type : Index` | Index instruments. Name must match ScripMaster `symbol` field (e.g. `"Nifty 50"`). Used for both index tracking and option selection |
| `type : DailyWatchlist` | Daily candle scan (scheduler currently disabled) |

Options are matched from ScripMaster using `OptionSelectionService.toOptionName()`:
- `"Nifty 50"` → strips `"50"` (numeric) → `"NIFTY"` → matches ScripMaster `name` field on NFO OPTIDX entries
