import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RestCallService } from 'src/app/services/rest-call.service';
import { ToastrService } from 'ngx-toastr';
import { LoggingService } from 'src/app/services/logging.service';
import { HttpResponse, HttpHeaders } from '@angular/common/http';

declare let $: any;

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.less']
})
export class AdminDashboardComponent {

  private updateSettingsUri = '/damuiconfig/updatesettings';
  private regex = /^[a-zA-Z0-9_-]+$/;
  validationError: string;

  cardSelected = {
    card: 'commerceCloud',
    card1: true,
    card3: false
  };

  card1OptionSelected = {
    option: 'postInstall',
    postInstall: true
  };

  urlError: boolean;
  urlvalidationError: any;
  systemIdError: boolean;
  usernameError: boolean;
  usernamevalidationError: any;
  passwordError: boolean;
  passwordvalidationError: any;

  constructor(private restService: RestCallService,
    private toastr: ToastrService,
    private log: LoggingService) {

  }

  handleChange(event: any) {

    $('#systemId')[0].value = '';
    $('#url')[0].value = '';
    this.urlError = false;
    this.systemIdError = false;
    this.validationError = '';
    this.urlvalidationError = '';

    switch (event.target.value) {
      case 'commerceCloud': {
        $('.settings .collapse').collapse('hide');
        this.cardSelected.card = 'commerceCloud';
        this.cardSelected.card1 = true;
        this.cardSelected.card3 = false;
        break;
      }
      case 's4Hana': {
        $('.settings .collapse').collapse('hide');
        this.cardSelected.card = 's4Hana';
        this.cardSelected.card1 = false;
        this.cardSelected.card3 = true;
        break;
      }
      case 'postInstall': {
        this.card1OptionSelected.option = 'postInstall';
        this.card1OptionSelected.postInstall = true;
        break;
      }
    }

  }

  updateSettings() {
    const systemId = $('#systemId')[0].value;
    const url = $('#url')[0].value;
    const user = $('#username')[0] ? $('#username')[0].value : '';
    const pass = $('#password')[0] ? $('#password')[0].value : '';

    let jsonData: string;

    this.systemIdError = false;
    this.urlError = false;

    if (!systemId) {
      this.systemIdError = true;
      this.validationError = 'System Id is required!';
      return;
    }
    if (!this.regex.test(systemId)) {
      this.systemIdError = true;
      this.validationError = "Special characters are not allowed!";
      return;
    }

    if (url !== '') {
      try {
        new URL(url);
      } catch (error) {
        this.urlError = true;
        this.urlvalidationError = error;
        return;
      }
    }
    if (this.cardSelected.card3 && !url) {
      this.urlError = true;
      this.urlvalidationError = 'Url is mandatory';
      return;
    }

    if (this.cardSelected.card1 && pass !== '' && pass.indexOf('>') !== -1) {
      this.passwordError = true;
      this.passwordvalidationError = "Password cannot contain '>'";
      return;
    }

    // constructing json data to be sent to server
    jsonData = '{'
      + '"systemId": "' + systemId + '",'
      + '"leadingSystem": "' + this.cardSelected.card + '",'
      + '"url": "' + url + '",'
      + '"username": "' + user + '",'
      + '"password": "' + pass + '"'
      + '}';

    const header = new HttpHeaders({ 'Content-Type': 'application/json; charset=utf-8' });
    // make call to DB
    this.restService.post<string>('', this.updateSettingsUri, jsonData, header)
      .subscribe({
        next: (res: HttpResponse<string>) => {
          if (res) {
            this.log.info('Configuration created successfully');
            this.toastr.success('Configuration created successfully');
            this.reset();
          }
        },
        error: (error: Error) => {
          this.log.error('Error during creating configuration: ', error);
          this.toastr.error("" + error);
        }
      });
  }

  reset(): void {
    $('#systemId')[0].value = '';
    $('#url')[0].value = '';
    $('#username')[0].value = '';
    $('#password')[0].value = '';
  }

}
