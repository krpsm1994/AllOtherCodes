import { HttpClient, HttpBackend, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { LoadingService } from './loading.service';
import { throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class MdsService {

  private alphaNumChar = 'ABCDEFGHIJKMNOPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz0123456789';
  private httpClient: HttpClient;

  constructor(handler: HttpBackend,
    private loadingService: LoadingService) {
    this.httpClient = new HttpClient(handler);
  }

  private generateRandom(len: number): string {
    let randomChar = '';
    for (let index = 0; index < len; index++) {
      randomChar += this.alphaNumChar.charAt(Math.floor(Math.random() * this.alphaNumChar.length));
    }
    return randomChar;
  }

  private stringToHex(chars: string): string {
    let result = '';
    for (let index = 0; index < chars.length; index++) {
      result += chars.charCodeAt(index).toString(16);
    }
    return result;
  }

  /**
   * https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto/digest
   * @param message combination of salt and password
   */
  private async stringToHash(message: string): Promise<string> {
    const msgUint8 = new TextEncoder().encode(message);                           // encode as (utf-8) Uint8Array
    const hashBuffer = await crypto.subtle.digest('SHA-256', msgUint8);           // hash the message
    const hashArray = Array.from(new Uint8Array(hashBuffer));                     // convert buffer to byte array
    const hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join(''); // convert bytes to hex string
    return hashHex;
  }

  /**
   * get MDS properties when URL is provided
   * @param url Base URL of MDS
   * @param pwd Password of MDS
   */
  async getMDSProps(url: string, pwd: string): Promise<string> {
    this.loadingService.show('');
    const randomKey = this.generateRandom(10);
    const salt = this.stringToHex(randomKey);
    let xPass: string;
    xPass = await this.stringToHash(salt + pwd);
    const propUrl = url + '/' + salt + '/propFile';
    return this.httpClient.get(propUrl, { observe: 'body', headers: this.getHeaders(xPass), responseType: 'text' })
      .pipe(
        catchError((error: string) => {
          this.loadingService.hide();
          return throwError(() => new Error(error));
        }),
        map((response) => {
          this.loadingService.hide();
          return response;
        })
      ).toPromise();
  }

  async setMDSProps(url: string, pwd: string, content: string, header?: HttpHeaders): Promise<string> {
    this.loadingService.show('');
    const randomKey = this.generateRandom(10);
    const salt = this.stringToHex(randomKey);
    let xPass: string;
    xPass = await this.stringToHash(salt + pwd);
    const propUrl = url + '/' + salt + '/propFile';
    return this.httpClient.put(propUrl, content, { observe: 'body', headers: this.getHeaders(xPass), responseType: 'text' })
      .pipe(
        catchError((error: string) => {
          this.loadingService.hide();
          return throwError(() => new Error(error));
        }),
        map((response) => {
          this.loadingService.hide();
          return response;
        })
      ).toPromise();
  }

  private getHeaders(pass: string, headers?: HttpHeaders): HttpHeaders {
    const localHeader = new HttpHeaders({ 'X-passwd': pass });
    return localHeader;
  }
}
