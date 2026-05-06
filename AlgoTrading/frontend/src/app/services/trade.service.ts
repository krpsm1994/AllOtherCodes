import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Trade {
  id?:         number;
  token:       string;
  name:        string;
  date:        string;
  buyPrice:    number;
  sellPrice:   number;
  status:      string;
  noOfShares:  number;
  pnl:         number | null;
  buyOrderId:  string | null;
  sellOrderId: string | null;
  type:        string | null;
  exchange:    string | null;
  optionType:  string | null;  // "CE" | "PE" for index option trades
  indexToken:  string | null;  // parent index NSE token for option trades
}

export interface Instrument {
  token:    string;
  name:     string;
  exchange: string;
  lotSize:  number;
  type:     string; // '10MinWatchlist' | 'DailyWatchlist'
}

export interface CandleRow {
  date:   string;
  open:   number;
  high:   number;
  low:    number;
  close:  number;
  volume: number;
}

@Injectable({ providedIn: 'root' })
export class TradeService {

  constructor(private http: HttpClient) {}

  getTrades(): Observable<Trade[]> {
    return this.http.get<Trade[]>('/api/trades');
  }

  createTrade(trade: Trade): Observable<Trade> {
    return this.http.post<Trade>('/api/trades', trade);
  }

  updateTrade(id: number, trade: Trade): Observable<Trade> {
    return this.http.put<Trade>(`/api/trades/${id}`, trade);
  }

  deleteTrade(id: number): Observable<void> {
    return this.http.delete<void>(`/api/trades/${id}`);
  }

  placeBuyOrder(id: number): Observable<Trade> {
    return this.http.post<Trade>(`/api/trades/${id}/buy`, {});
  }

  placeSellOrder(id: number): Observable<Trade> {
    return this.http.post<Trade>(`/api/trades/${id}/sell`, {});
  }

  refreshTokens(): Observable<any> {
    return this.http.post<any>('/api/instruments/refresh', {});
  }

  replaceCandles(clientcode: string): Observable<any> {
    const params = new HttpParams().set('clientcode', clientcode);
    return this.http.post<any>('/api/market-data/fetch', {}, { params });
  }

  getInstruments(): Observable<Instrument[]> {
    return this.http.get<Instrument[]>('/api/instruments');
  }

  getCandlesByToken(token: string): Observable<CandleRow[]> {
    const params = new HttpParams().set('token', token);
    return this.http.get<CandleRow[]>('/api/market-data/candles', { params });
  }

  getDailyCandles(token: string, clientcode: string): Observable<CandleRow[]> {
    const params = new HttpParams().set('token', token).set('clientcode', clientcode);
    return this.http.get<CandleRow[]>('/api/market-data/daily', { params });
  }

  /**
   * Opens an SSE connection to /api/trades/stream.
   * Returns a plain EventSource — caller must close it on destroy.
   * Events: trade_snapshot, new_trade, trade_update.
   */
  openTradeStream(): EventSource {
    const token = sessionStorage.getItem('algoJwt') ?? '';
    return new EventSource(`/api/trades/stream?token=${encodeURIComponent(token)}`);
  }
}
