import { User } from '../models/user';
import { Injectable, Inject } from '@angular/core';
import {
  CanActivate, CanActivateChild, ActivatedRouteSnapshot,
  RouterStateSnapshot, UrlTree, Router, CanDeactivate
} from '@angular/router';
import { Observable } from 'rxjs';
import { UserService } from '../services/user.service';
import { take, delay, map } from 'rxjs/operators';
import { ComponentCanDeactivate } from '../abstract/component.deactivate';
import { AccessTokenService } from '../services/access-token.service';

type CanActivateReturnType = Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree;


@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate, CanActivateChild, CanDeactivate<ComponentCanDeactivate> {

  userInfo: User;
  constructor(@Inject('LOCAL_STORAGE') private locaStorage: Storage,
    private userService: UserService,
    private router: Router,
    private tokenService: AccessTokenService) {
  }

  canActivate(next: ActivatedRouteSnapshot, state: RouterStateSnapshot): CanActivateReturnType {
    if (this.tokenService.accessToken) {
      this.tokenService.otdsLogoutUrl = "";
      return this.userService.getUserInfo$.pipe(
        delay(0),
        map((user: User) => {
          if (user && (user.rolename === 'Administrator' || user.rolename === 'Business Administrator')) {
            return true;
          }
          this.router.navigate(['/notauthorized']);
          return false;
        }),
        take(1)
      );
    } else {
      window.location.replace(this.tokenService.otdsLogoutUrl);
      return false;
    }
  }

  canActivateChild(
    next: ActivatedRouteSnapshot,
    state: RouterStateSnapshot): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
    if (this.tokenService.accessToken) {
      return this.userService.getUserInfo$.pipe(
        delay(0),
        map((user: User) => {
          if (user && (user.rolename === 'Administrator' || user.rolename === 'Business Administrator')) {
            return true;
          }
          this.router.navigate(['/notauthorized']);
          return false;
        }),
        take(1)
      );
    } else {
      window.location.replace(this.tokenService.otdsLogoutUrl);
      return false;
    }
  }

  canDeactivate(component: ComponentCanDeactivate,
    currentRoute: ActivatedRouteSnapshot,
    currentState: RouterStateSnapshot,
    nextState?: RouterStateSnapshot): boolean | UrlTree | Observable<boolean | UrlTree> | Promise<boolean | UrlTree> {
    if (!component.canDeactivate()) {
      if (confirm('You have unsaved changes! If you leave, your changes will be lost.')) {
        return true;
      } else {
        return false;
      }
    }
    return true;
  }


}
