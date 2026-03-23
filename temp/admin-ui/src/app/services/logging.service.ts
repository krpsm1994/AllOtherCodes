import { environment } from './../../environments/environment';
import { Subscription } from 'rxjs';
import { ConfigService } from './config.service';
import { Injectable, Inject, OnDestroy } from '@angular/core';
import { User } from '../models/user';
import { NGXLogger, NgxLoggerLevel, INGXLoggerConfig } from 'ngx-logger';
import { AccessTokenService } from './access-token.service';


@Injectable({
  providedIn: 'root'
})
export class LoggingService implements OnDestroy {

  private configuredLogLevel: NgxLoggerLevel;
  private userInfo: User;
  private logConfig: INGXLoggerConfig;

  private configServiceSubscription: Subscription;

  constructor(private configService: ConfigService,
    @Inject('LOCAL_STORAGE') private locaStorage: Storage,
    private logger: NGXLogger,
    private tokenService: AccessTokenService) {

    this.configServiceSubscription = configService.getloglevel().subscribe(
      res => {
        this.configuredLogLevel = res;
        this.logConfig = this.logger.getConfigSnapshot();
        this.logConfig.level = environment.production ? NgxLoggerLevel.ERROR : res;
        this.logger.updateConfig(this.logConfig);
      }
    );

  }

  error(message: string, ...optionalParams: any[]): void {
    this.logger.error(message, optionalParams);
  }

  warn(message: string, ...optionalParams: any[]): void {
    this.logger.warn(message, optionalParams);
  }

  info(message: string, ...optionalParams: any[]): void {
    this.logger.info(message, optionalParams);
  }

  debug(message: string, ...optionalParams: any[]): void {
    this.logger.debug(message, optionalParams);
  }

  trace(message: string, ...optionalParams: any[]): void {
    this.logger.trace(message, optionalParams);
  }

  setUserInfo(): void {
    if (this.userInfo) {
      return;
    }
    const user = this.locaStorage.getItem('user_info');
    this.userInfo = JSON.parse(user);
  }

  ngOnDestroy(): void {
    this.configServiceSubscription.unsubscribe();
  }
}
