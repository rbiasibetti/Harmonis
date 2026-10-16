import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { Login } from './pages/login/login';
import { Shell } from './layout/shell';
import { Dashboard } from './pages/dashboard/dashboard';
import { Users } from './pages/users/users';
import { Roles } from './pages/roles/roles';
import { FormsList } from './pages/forms/forms-list';
import { FormBuilder } from './pages/forms/form-builder';
import { FormFill } from './pages/forms/form-fill';
import { FormSubmissionsPage } from './pages/forms/form-submissions';
import { FlowEditor } from './pages/forms/flow-editor';
import { Processes } from './pages/processes/processes';
import { Documents } from './pages/documents/documents';
import { Portal } from './pages/portal/portal';
import { PortalPage } from './pages/portal/portal-page';
import { PagesAdmin } from './pages/portal/pages-admin';
import { PageEditor } from './pages/portal/page-editor';
import { Integrations } from './pages/integrations/integrations';
import { IntegrationEditor } from './pages/integrations/integration-editor';
import { IntegrationLogs } from './pages/integrations/integration-logs';

export const routes: Routes = [
  { path: 'login', component: Login },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: Dashboard },
      { path: 'users', component: Users },
      { path: 'roles', component: Roles },
      { path: 'forms', component: FormsList },
      { path: 'forms/new', component: FormBuilder },
      { path: 'forms/:id/edit', component: FormBuilder },
      { path: 'forms/:id/fill', component: FormFill },
      { path: 'forms/:id/submissions', component: FormSubmissionsPage },
      { path: 'forms/:id/flow', component: FlowEditor },
      { path: 'processes', component: Processes },
      { path: 'documents', component: Documents },
      { path: 'documents/folder/:id', component: Documents },
      { path: 'portal', component: Portal },
      { path: 'portal/:slug', component: PortalPage },
      { path: 'pages', component: PagesAdmin },
      { path: 'pages/new', component: PageEditor },
      { path: 'pages/:id/edit', component: PageEditor },
      { path: 'integrations', component: Integrations },
      { path: 'integrations/new', component: IntegrationEditor },
      { path: 'integrations/:id/edit', component: IntegrationEditor },
      { path: 'integration-logs', component: IntegrationLogs },
    ],
  },
  { path: '**', redirectTo: '' },
];
