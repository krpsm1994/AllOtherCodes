import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class AccessTokenService {

  public accessToken: string;
  public otdsLogoutUrl: string;

  constructor() {
    this.accessToken = (window as any).otdamToken;
  }

  get getAccessToken(): string {
    return this.accessToken;
  }
}
