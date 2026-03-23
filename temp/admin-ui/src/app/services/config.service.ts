import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { NgxLoggerLevel } from 'ngx-logger';

@Injectable({
  providedIn: 'root'
})
export class ConfigService {

  loggingLevel = new BehaviorSubject<NgxLoggerLevel>(NgxLoggerLevel.TRACE);

  constructor() { }

  set loglevel(level: NgxLoggerLevel) {
    this.loggingLevel.next(level);
  }

  getloglevel(): Observable<NgxLoggerLevel> {
    return this.loggingLevel.asObservable();
  }
}
