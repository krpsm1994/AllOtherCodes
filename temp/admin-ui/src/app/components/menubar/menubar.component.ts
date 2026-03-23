import { Component, AfterContentInit, Inject } from '@angular/core';
import { User } from '../../models/user';
import { OtmmSystemDetails } from 'src/app/models/otmm-system-details';
import { GlobalVariables } from 'src/app/common/global-variables';
import { CommonModule, DatePipe } from '@angular/common';
import { MenuOptionsComponent } from '../menu-options/menu-options.component';

@Component({
  selector: 'app-menubar',
  standalone: true,
  imports: [CommonModule, MenuOptionsComponent],
  templateUrl: './menubar.component.html',
  styleUrls: ['./menubar.component.less']
})
export class MenubarComponent implements AfterContentInit {

  user: User;
  systemDetail: OtmmSystemDetails;
  version = GlobalVariables.DAMLINK_VERSION;
  lastLoginDate: string;

  constructor(@Inject('LOCAL_STORAGE') private locaStorage: Storage,
    private datePipe: DatePipe) { }


  ngAfterContentInit(): void {
    const userInfo = this.locaStorage.getItem('user_info');
    if (userInfo !== null && userInfo.length > 0) {
      this.user = JSON.parse(userInfo);
      this.lastLoginDate = this.user.lastLogin ? this.datePipe.transform(new Date(this.user.lastLogin), 'MM/dd/yyyy HH:mm:ss', Intl.DateTimeFormat().resolvedOptions().timeZone) : '';
    }
    const systemDetails = this.locaStorage.getItem('otmm_system_info');
    if (systemDetails !== null && systemDetails.length > 0) {
      this.systemDetail = JSON.parse(systemDetails);
    }
  }

}
