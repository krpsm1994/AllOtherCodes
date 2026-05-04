import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, forkJoin } from 'rxjs';
import { map, catchError, tap } from 'rxjs/operators';

// ── Group / key constants ────────────────────────────────────────────────────
export const GRP_ALGO_SCAN = 'Algo Scan';
export const GRP_ORDERS    = 'Orders';

// ── Typed settings model ─────────────────────────────────────────────────────
export interface AlgoScanSettings {
  numberOfCandles: number;
}

export interface OrderSettings {
  place10MinOrders:       boolean;
  placeDailyOrders:       boolean;
  orderType:              'MARKET' | 'LIMIT';
  pollingIntervalMinutes: number;
  max10MinOrders:         number;
  maxDailyOrders:         number;
}

export interface AlgoSettings {
  algoScan: AlgoScanSettings;
  orders:   OrderSettings;
}

export const DEFAULT_ALGO_SETTINGS: AlgoSettings = {
  algoScan: {
    numberOfCandles: 15
  },
  orders: {
    place10MinOrders:       true,
    placeDailyOrders:       true,
    orderType:              'MARKET',
    pollingIntervalMinutes: 5,
    max10MinOrders:         5,
    maxDailyOrders:         5
  }
};

/**
 * SettingsService: no localStorage usage. Settings are loaded from
 * /api/settings/all and kept in-memory. All saves POST to /api/settings/bulk.
 */
@Injectable({ providedIn: 'root' })
export class SettingsService {
  private cached: Record<string, Record<string, string>> | null = null;

  constructor(private http: HttpClient) {}

  // ── Fetch ────────────────────────────────────────────────────────────────

  /** Loads all settings from server and caches the raw map. */
  fetchAllSettingsFromServer(): Observable<Record<string, Record<string, string>> | null> {
    return this.http.get<any>('/api/settings/all').pipe(
      map(r => r?.success ? (r.settings as Record<string, Record<string, string>>) : null),
      tap(s => { if (s) this.cached = s; }),
      catchError(() => of(null))
    );
  }

  /** Fetches and parses settings into the typed {@link AlgoSettings} model. */
  fetchSettings(): Observable<AlgoSettings> {
    return this.fetchAllSettingsFromServer().pipe(
      map(raw => this.parseSettings(raw))
    );
  }

  /** Returns the typed settings from cache (falls back to defaults). */
  getCachedSettings(): AlgoSettings {
    return this.parseSettings(this.cached);
  }

  // ── Save ─────────────────────────────────────────────────────────────────

  /** Bulk-saves all algo settings to the server. */
  saveSettings(settings: AlgoSettings): Observable<boolean> {
    const { algoScan, orders } = settings;
    const entries = [
      { group: GRP_ALGO_SCAN, key: 'numberOfCandles',        value: String(algoScan.numberOfCandles) },
      { group: GRP_ORDERS,    key: 'place10MinOrders',       value: String(orders.place10MinOrders) },
      { group: GRP_ORDERS,    key: 'placeDailyOrders',       value: String(orders.placeDailyOrders) },
      { group: GRP_ORDERS,    key: 'orderType',              value: orders.orderType },
      { group: GRP_ORDERS,    key: 'pollingIntervalMinutes', value: String(orders.pollingIntervalMinutes) },
      { group: GRP_ORDERS,    key: 'max10MinOrders',         value: String(orders.max10MinOrders) },
      { group: GRP_ORDERS,    key: 'maxDailyOrders',         value: String(orders.maxDailyOrders) }
    ];

    return this.http.post<any>('/api/settings/bulk', entries).pipe(
      map(r => {
        if (r?.success) {
          // Update local cache
          if (!this.cached) this.cached = {};
          entries.forEach(e => {
            if (!this.cached![e.group]) this.cached![e.group] = {};
            this.cached![e.group][e.key] = e.value;
          });
          return true;
        }
        return false;
      }),
      catchError(() => of(false))
    );
  }

  // ── Legacy (kept for backward compat) ────────────────────────────────────

  getCachedNumberOfCandles(): number | null {
    const n = this.cached?.[GRP_ALGO_SCAN]?.['numberOfCandles'];
    const parsed = n != null ? parseInt(n, 10) : NaN;
    return Number.isFinite(parsed) ? parsed : null;
  }

  setNumberOfCandles(n: number): Observable<boolean> {
    const val = String(Math.floor(n));
    return this.http.post<any>('/api/settings',
      { group: GRP_ALGO_SCAN, key: 'numberOfCandles', value: val }).pipe(
      map(_ => {
        if (!this.cached) this.cached = {};
        if (!this.cached[GRP_ALGO_SCAN]) this.cached[GRP_ALGO_SCAN] = {};
        this.cached[GRP_ALGO_SCAN]['numberOfCandles'] = val;
        return true;
      }),
      catchError(() => of(false))
    );
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private parseSettings(raw: Record<string, Record<string, string>> | null): AlgoSettings {
    const d = DEFAULT_ALGO_SETTINGS;
    const algoScanRaw = raw?.[GRP_ALGO_SCAN] ?? {};
    const ordersRaw   = raw?.[GRP_ORDERS]    ?? {};

    return {
      algoScan: {
        numberOfCandles: this.parseInt(algoScanRaw['numberOfCandles'], d.algoScan.numberOfCandles)
      },
      orders: {
        place10MinOrders:       this.parseBool(ordersRaw['place10MinOrders'],       d.orders.place10MinOrders),
        placeDailyOrders:       this.parseBool(ordersRaw['placeDailyOrders'],       d.orders.placeDailyOrders),
        orderType:              (ordersRaw['orderType'] === 'LIMIT' ? 'LIMIT' : 'MARKET'),
        pollingIntervalMinutes: this.parseInt(ordersRaw['pollingIntervalMinutes'],  d.orders.pollingIntervalMinutes),
        max10MinOrders:         this.parseInt(ordersRaw['max10MinOrders'],          d.orders.max10MinOrders),
        maxDailyOrders:         this.parseInt(ordersRaw['maxDailyOrders'],          d.orders.maxDailyOrders)
      }
    };
  }

  private parseInt(val: string | undefined, fallback: number): number {
    const n = val != null ? Number(val) : NaN;
    return Number.isFinite(n) ? n : fallback;
  }

  private parseBool(val: string | undefined, fallback: boolean): boolean {
    if (val == null) return fallback;
    return val.trim().toLowerCase() === 'true';
  }
}

