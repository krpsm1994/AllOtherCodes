import { Component, OnInit } from '@angular/core';
import { UserService } from '../services/user.service';
import { GlobalVariables } from 'src/app/common/global-variables';

@Component({
  selector: 'app-no-perm',
  standalone: true,
  templateUrl: './no-perm.component.html',
  styleUrls: ['./no-perm.component.less']
})
export class NoPermComponent {

  version = GlobalVariables.DAMLINK_VERSION;

  constructor(private userService: UserService) { }

  signout(): void {
    this.userService.logoutUser();
  }

}
