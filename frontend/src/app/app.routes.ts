import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { Login } from './pages/login/login';
import { Shell } from './layout/shell';
import { Dashboard } from './pages/dashboard/dashboard';
import { Placeholder } from './pages/placeholder/placeholder';
import { Users } from './pages/users/users';
import { Roles } from './pages/roles/roles';
import { FormsList } from './pages/forms/forms-list';
import { FormBuilder } from './pages/forms/form-builder';
import { FormFill } from './pages/forms/form-fill';
import { FormSubmissionsPage } from './pages/forms/form-submissions';

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
      { path: 'processes', component: Placeholder, data: { title: 'Processos', milestone: 'M3', desc: 'Fluxos de aprovação: paralelo, condicional e gateways.' } },
      { path: 'documents', component: Placeholder, data: { title: 'Documentos', milestone: 'M4', desc: 'Pastas, versionamento, download seguro e busca.' } },
      { path: 'portal', component: Placeholder, data: { title: 'Portal', milestone: 'M5', desc: 'Páginas em Markdown (HTML sanitizado).' } },
    ],
  },
  { path: '**', redirectTo: '' },
];
