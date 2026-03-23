import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ReactiveFormsModule } from '@angular/forms';
import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { ApplicationConfig, importProvidersFrom } from '@angular/core';

import { LoggerModule, NgxLoggerLevel } from 'ngx-logger';
import { ToastrModule, ToastrService } from 'ngx-toastr';
import { DatePipe } from '@angular/common';

import { RequestInterceptor } from './interceptors/request-interceptor';
import { AuthGuard } from './guard/auth.guard';
import { AccessTokenService } from './services/access-token.service';
import { ConfigService } from './services/config.service';
import { LoadingService } from './services/loading.service';
import { LoggingService } from './services/logging.service';
import { MdsService } from './services/mds.service';
import { OtmmSystemDetailsService } from './services/otmm-system-details.service';
import { RestCallService } from './services/rest-call.service';
import { UserService } from './services/user.service';
import { WindowSizeService } from './services/window-size-service';
import { routes } from './app-routing.module';
import { provideRouter, withHashLocation } from '@angular/router';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, withHashLocation()),
    provideHttpClient(withInterceptorsFromDi()),
    importProvidersFrom(
      BrowserModule,
      BrowserAnimationsModule,
      ReactiveFormsModule,
      LoggerModule.forRoot({ level: NgxLoggerLevel.DEBUG }),
      ToastrModule.forRoot({
        timeOut: 3000,
        positionClass: 'toast-top-center',
        preventDuplicates: true,
        maxOpened: 1,
        autoDismiss: true
      })
    ),
    { provide: 'LOCAL_STORAGE', useFactory: getLocalStorage },
    { provide: HTTP_INTERCEPTORS, useClass: RequestInterceptor, multi: true },
    DatePipe,
    AuthGuard,
    AccessTokenService,
    ConfigService,
    LoadingService,
    LoggingService,
    MdsService,
    OtmmSystemDetailsService,
    RestCallService,
    UserService,
    WindowSizeService,
    ToastrService
  ]
};

export function getLocalStorage() {
  return (typeof window !== 'undefined') ? window.localStorage : null;
}
