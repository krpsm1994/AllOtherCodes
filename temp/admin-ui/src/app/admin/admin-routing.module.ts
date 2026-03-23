import { Routes } from '@angular/router';
import { AuthGuard } from '../guard/auth.guard';
import { AdminDashboardComponent } from './admin-dashboard/admin-dashboard.component';
import { MetadataSchemaComponent } from './metadata-schema/metadata-schema.component';
import { MonitorMessagesComponent } from './monitor-messages/monitor-messages.component';
import { RouteDetails } from '../common/route-details';
import { PropertiesEditor } from './properties-editor/properties-editor.component';
import { UploadTaxonomy } from './tags-taxonomy/upload-taxonomy.component';


export const adminRoutes: Routes = [
  { path: '', redirectTo: 'home', pathMatch: 'full', runGuardsAndResolvers: 'always' },
  { path: 'home', component: AdminDashboardComponent, canActivate: [AuthGuard] },
  { path: 'damlinkconfig', component: PropertiesEditor, canActivate: [AuthGuard], data: RouteDetails.damLinkConfig },
  { path: 'pwconfig', component: PropertiesEditor, canActivate: [AuthGuard], data: RouteDetails.productWorkspaceConfig },
  { path: 'eamconfig', component: PropertiesEditor, canActivate: [AuthGuard], data: RouteDetails.eamWorkspaceConfig },
  { path: 'taxonomy', component: UploadTaxonomy, canActivate: [AuthGuard] },
  { path: 'aiconfig', component: PropertiesEditor, canActivate: [AuthGuard], data: RouteDetails.aiConfig },
  //{ path: 'groceryAttributes', component: PropertiesEditor, canActivate: [AuthGuard], data: RouteDetails.groceryAttributesConfig },
  { path: 'foldermapping', component: PropertiesEditor, canActivate: [AuthGuard], data: RouteDetails.folderMappingConfig },
  { path: 'messages', component: MonitorMessagesComponent, canActivate: [AuthGuard] },
  { path: 'createmetadata', component: MetadataSchemaComponent, canActivate: [AuthGuard] }
];
