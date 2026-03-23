import { NoPermComponent } from './no-perm/no-perm.component';
import { Routes } from '@angular/router';
import { AdminComponent } from './admin/admin/admin.component';
import { PagenotfoundComponent } from './pagenotfound/pagenotfound.component';
import { AuthGuard } from './guard/auth.guard';
import { adminRoutes } from './admin/admin-routing.module';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'home',
    pathMatch: 'full',
    runGuardsAndResolvers: 'always'
  },
  {
    path: '',
    component: AdminComponent,
    canActivate: [AuthGuard],
    canActivateChild: [AuthGuard],
    runGuardsAndResolvers: 'always',
    children: adminRoutes
  },
  {
    path: 'notauthorized',
    component: NoPermComponent
  },
  {
    path: '**',
    redirectTo: 'notfound'
  }, {
    path: '**',
    component: PagenotfoundComponent
  }
];
