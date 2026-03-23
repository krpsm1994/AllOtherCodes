import { HttpResponse } from '@angular/common/http';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { Injectable, Inject } from '@angular/core';
import { RestCallService } from './rest-call.service';
import { OtmmSystemDetails } from '../models/otmm-system-details';

@Injectable({
  providedIn: 'root'
})
export class OtmmSystemDetailsService {

  private otmmSysDetailUrl = '/oauth/otmmsystemdetails';
  private otmmSysDetails: OtmmSystemDetails;

  constructor(@Inject('LOCAL_STORAGE') private locaStorage: Storage,
    private restService: RestCallService) { }

  get otmmSystemDetails$(): Observable<OtmmSystemDetails> {
    return this.restService.get<string>('', this.otmmSysDetailUrl).pipe(
      map((response: HttpResponse<string>) => {
        this.locaStorage.setItem('otmm_system_info', response.body);
        this.otmmSysDetails = JSON.parse(response.body);
        return this.otmmSysDetails;
      })
    );
  }
}
