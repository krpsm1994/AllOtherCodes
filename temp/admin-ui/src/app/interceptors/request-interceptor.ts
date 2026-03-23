import { LoggingService } from './../services/logging.service';
import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AccessTokenService } from '../services/access-token.service';

@Injectable()
export class RequestInterceptor implements HttpInterceptor {

    constructor(private tokenService: AccessTokenService,
        private log: LoggingService) {
    }

    intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {

        // append token as header
        if (this.tokenService.accessToken) {
            request = request.clone({
                headers: request.headers.set(
                    'Authorization', 'Bearer ' + this.tokenService.accessToken
                ).set('cache-control', 'no-cache').set('pragma', 'no-cache'),
                responseType: 'text'
            });
        }

        return next.handle(request).pipe(
            catchError(err => {
                this.log.error('Encountered by interceptor', err);
                if (err.status === 401) {
                    location.reload();
                }
                let error = '';
                if (err.error && (typeof (err.error) === 'string' && err.error.startsWith('{'))) {
                    error = JSON.parse(err.error).message || err.status;
                } else {
                    // send error status if not error text, so that user can guess the cause.
                    error = err.error || err.status;
                }
                return throwError(() => new Error(error));
            })
        );
    }
}
