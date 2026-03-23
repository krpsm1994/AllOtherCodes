import { HttpResponse } from '@angular/common/http';
import { LoggingService } from './logging.service';
import { Injectable, Inject, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { RestCallService } from './rest-call.service';
import { User } from '../models/user';
import { Observable, of, Subscription } from 'rxjs';
import { map } from 'rxjs/operators';
import { OtmmSystemDetails } from '../models/otmm-system-details';
import { OtmmSystemDetailsService } from './otmm-system-details.service';
import { GlobalVariables } from '../common/global-variables';
import { AccessTokenService } from './access-token.service';

@Injectable({
  providedIn: 'root'
})
export class UserService implements OnDestroy {

  private userInfoUrl = '/oauth/userinfo';
  private userLogout = '/oauth/token';
  private user: User;

  private sysDetails: OtmmSystemDetails;
  private otmmSysDetails: string;

  private otmmDetailSubscription: Subscription;
  private deleteSubscription: Subscription;

  constructor(@Inject('LOCAL_STORAGE') private locaStorage: Storage,
    private restService: RestCallService,
    private otmmSysDetailsService: OtmmSystemDetailsService,
    private log: LoggingService,
    private tokenService: AccessTokenService,
    private router: Router) {

    this.otmmSysDetails = this.locaStorage.getItem('otmm_system_info');
    if (this.otmmSysDetails === null || this.otmmSysDetails.length < 1) {
      this.otmmDetailSubscription = this.otmmSysDetailsService.otmmSystemDetails$.subscribe({
        next: (res: OtmmSystemDetails) => {
          this.sysDetails = res;
          this.log.debug('Fetched OTMM System Details: ', this.sysDetails.otmmResourceId);
        },
        error: error => this.log.error('couldn\'t get system details', error)
      });
    } else {
      this.sysDetails = JSON.parse(this.otmmSysDetails);
    }
  }

  get getUserInfo$(): Observable<User> {
    const userStorage = this.locaStorage.getItem('user_info');
    this.user = userStorage ? JSON.parse(userStorage) : undefined;
    if (this.user?.username) {
      return of(this.user);
    }
    return this.restService.get<string>('', this.userInfoUrl).pipe(
      map((response: HttpResponse<string>) => {
        this.locaStorage.setItem('user_info', response.body);
        this.user = JSON.parse(response.body);
        return this.user;
      })
    );
  }

  logoutUser(): void {
    if (GlobalVariables.unSavedData) {
      alert('Unsaved data exist, please save them before continuing...');
      return;
    }
    this.locaStorage.removeItem('user_info');
    this.locaStorage.removeItem('otmm_system_info');
    this.tokenService.accessToken = "";
    document.cookie = 'JSESSIONID=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/;';
    this.deleteSubscription = this.restService.delete('', this.userLogout).subscribe({
      next: res => this.log.debug('Logged out successfully', res),
      error: (error: Error) => this.log.error('', error),
      complete: () => this.redirectToOtdsPage(this.sysDetails)
    });
  }

  private redirectToOtdsPage(sysDetails: OtmmSystemDetails): void {
    const otdsBaseUrl = sysDetails.otdsBaseURL;
    const otdsResourceId = sysDetails.otmmResourceId;
    const current = window.location.href;
    const homeurl = current.replace('notauthorized', '');
    const qParamsIndex = current.indexOf('?');
    let append = '';
    if (qParamsIndex > -1) {
      const qParams = current.substring(qParamsIndex + 1);
      if (qParams.indexOf('otdsauth') > -1) {
        const otdsParamIndex = qParams.indexOf('otdsauth');
        const nextIndexOfAmp = qParams.indexOf('&', otdsParamIndex);
        append += qParams.substring(otdsParamIndex, (nextIndexOfAmp > -1) ? nextIndexOfAmp : qParams.length);
        append += '&';
      }
      if (qParams.indexOf('authhandler') > -1) {
        const otdsParamIndex = qParams.indexOf('authhandler');
        const nextIndexOfAmp = qParams.indexOf('&', otdsParamIndex);
        append += qParams.substring(otdsParamIndex, (nextIndexOfAmp > -1) ? nextIndexOfAmp : qParams.length);
        append += '&';
      }
    }
    let otdswsIndex = otdsBaseUrl.indexOf('/otdsws');
    let otdsUrl =
      otdsBaseUrl + (otdswsIndex == -1 ? '/otdsws' : '') + '/login?logout&'
      + append + 'PostTicket=true&RFA='
      + otdsResourceId
      + encodeURIComponent(':' + homeurl);
    this.tokenService.otdsLogoutUrl = otdsUrl;
    (window as any).otdamToken = "";
    window.location.replace(otdsUrl);
  }

  ngOnDestroy(): void {
    this.otmmDetailSubscription.unsubscribe();
    if (this.deleteSubscription) {
      this.deleteSubscription.unsubscribe();
    }
  }

}
