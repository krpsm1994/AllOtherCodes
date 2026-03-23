import { Observable, throwError } from 'rxjs';
import { HttpClient, HttpHeaders, HttpResponse, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map, delay, catchError, timeout, finalize } from 'rxjs/operators';
import { LoadingService } from './loading.service';

@Injectable({
  providedIn: 'root'
})
export class RestCallService {

  baseUrl = '';
  private baseApiUrl = '/ot-damlink/api';
  private eTagHeaders: HttpHeaders;

  constructor(private http: HttpClient,
    private loadingService: LoadingService) {
  }


  get<T>(loaderMessage: string, resourceUri: string, params?: HttpParams): Observable<HttpResponse<T>> {
    this.loadingService.show(loaderMessage);
    const encodedUrl = encodeURI(this.baseUrl + this.baseApiUrl + resourceUri);
    return this.http.get<T>(encodedUrl, { observe: 'response', params }).pipe(
      delay(0),
      catchError((error: any) => {
        this.loadingService.hide();
        return throwError(() => new Error(error.error || error.message || error));
      }),
      finalize(() => this.loadingService.hide())
    );
  }

  delete(loaderMessage: string, resourceUri: string, params?: HttpParams): Observable<any> {
    this.loadingService.show(loaderMessage);
    const encodedUrl = encodeURI(this.baseUrl + this.baseApiUrl + resourceUri);
    return this.http.delete(encodedUrl, { params }).pipe(
      catchError((error: any) => {
        this.loadingService.hide();
        return throwError(() => new Error(error.error || error.message || error));
      }),
      finalize(() => this.loadingService.hide())
    );
  }

  post<T>(loaderMessage: string, resourceUri: string, body: string | FormData, headers?: HttpHeaders, params?: HttpParams): Observable<HttpResponse<T>> {
    this.loadingService.show(loaderMessage);
    const encodedUrl = encodeURI(this.baseUrl + this.baseApiUrl + resourceUri);
    return this.http.post<T>(encodedUrl, body, { headers, observe: 'response', params }).pipe(
      timeout(300000),
      catchError((error: any) => {
        this.loadingService.hide();
        return throwError(() => new Error(error.error || error.message || error));
      }),
      finalize(() => this.loadingService.hide())
    );
  }

  put(loaderMessage: string, resourceUri: string, body: string, headers?: HttpHeaders, params?: HttpParams): Observable<any> {
    this.loadingService.show(loaderMessage);
    const encodedUrl = encodeURI(this.baseUrl + this.baseApiUrl + resourceUri);
    return this.http.put(encodedUrl, body, { headers, observe: 'response', params }).pipe(
      map((response: HttpResponse<any>) => {
        this.setETagHeaders = response.headers;
        return response;
      }),
      catchError((error: any) => {
        this.loadingService.hide();
        return throwError(() => new Error(error.error || error.message || error));
      }),
      finalize(() => this.loadingService.hide())
    );
  }

  get getEtagHeaders(): HttpHeaders {
    return this.eTagHeaders;
  }

  set setETagHeaders(headers: HttpHeaders) {
    if (headers.get('ETag')) {
      this.eTagHeaders = new HttpHeaders({ 'If-Match': headers.get('ETag'), 'If-Unmodified-Since': headers.get('Last-Modified') });
    } else if (this.eTagHeaders) {
      this.eTagHeaders = this.eTagHeaders.set('If-Unmodified-Since', headers.get('Last-Modified'));
    }
  }

}
