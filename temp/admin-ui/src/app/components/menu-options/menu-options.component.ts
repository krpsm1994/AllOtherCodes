import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UserService } from '../../services/user.service';
import { OtmmSystemDetails } from 'src/app/models/otmm-system-details';

@Component({
  selector: 'app-menu-options',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './menu-options.component.html',
  styleUrls: ['./menu-options.component.less']
})
export class MenuOptionsComponent {

  @Input() username: string;
  @Input() lastLoginDate: string;
  @Input() systemDetails: OtmmSystemDetails;

  constructor(private userService: UserService) { }

  signout(): void {
    this.userService.logoutUser();
  }
}
