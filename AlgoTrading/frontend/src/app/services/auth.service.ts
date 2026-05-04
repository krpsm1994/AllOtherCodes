import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface LoginResponse {
  success: boolean;
  message?: string;
  token?: string;
}

export interface AngelOneLoginRequest {
  clientcode: string;
  pin: string;
  totp: string;
}

export interface AngelOneStatusResponse {
  authenticated: boolean;
  clientcode?: string;
  message?: string;
}

export interface ZerodhaStatusResponse {
  authenticated: boolean;
  userId?: string;
  userName?: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {

  constructor(private http: HttpClient) {}

  // ── App auth ─────────────────────────────────────────────────────

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', { username, password });
  }

  saveSession(username: string, token: string): void {
    sessionStorage.setItem('algoLoggedIn', 'true');
    sessionStorage.setItem('algoUsername', username);
    sessionStorage.setItem('algoJwt', token);
  }

  isLoggedIn(): boolean {
    return sessionStorage.getItem('algoLoggedIn') === 'true';
  }

  getUsername(): string {
    return sessionStorage.getItem('algoUsername') ?? '';
  }

  logout(): void {
    sessionStorage.clear();
  }

  // ── AngelOne ─────────────────────────────────────────────────────

  angelLogin(req: AngelOneLoginRequest): Observable<any> {
    return this.http.post<any>('/api/broker/angel/login', req);
  }

  angelStatus(clientcode: string): Observable<any> {
    const params = new HttpParams().set('clientcode', clientcode);
    return this.http.get<any>('/api/broker/angel/status', { params });
  }

  saveAngelSession(clientcode: string): void {
    sessionStorage.setItem('angelDone', 'true');
    sessionStorage.setItem('angelClientcode', clientcode);
  }

  isAngelDone(): boolean {
    return sessionStorage.getItem('angelDone') === 'true';
  }

  getAngelClientcode(): string {
    return sessionStorage.getItem('angelClientcode') ?? '';
  }

  // ── Zerodha ──────────────────────────────────────────────────────

  /** Returns the Kite login URL (localhost auto-detected server-side). */
  zerodhaLoginUrl(): Observable<any> {
    return this.http.get<any>('/api/broker/zerodha/login-url');
  }

  zerodhaStatus(): Observable<any> {
    return this.http.get<any>('/api/broker/zerodha/status');
  }

  /** Exchanges a one-time request_token from the Kite OAuth redirect. */
  zerodhaExchange(requestToken: string): Observable<any> {
    const params = new HttpParams().set('request_token', requestToken);
    return this.http.post<any>('/api/broker/zerodha/exchange', null, { params });
  }

  saveZerodhaSession(): void {
    sessionStorage.setItem('zerodhaDone', 'true');
  }

  isZerodhaDone(): boolean {
    return sessionStorage.getItem('zerodhaDone') === 'true';
  }
}
