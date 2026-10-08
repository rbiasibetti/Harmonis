import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { Login } from './pages/login/login';
import { Shell } from './layout/shell';
import { Dashboard } from './pages/dashboard/dashboard';
import { Placeholder } from './pages/placeholder/placeholder';

export const routes: Routes = [
  { path: 'login', component: Login },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: Dashboard },
      { path: 'users', component: Placeholder, data: { title: 'Usuários', milestone: 'M1', desc: 'Cadastro de usuários, ativação e atribuição de papéis.' } },
      { path: 'roles', component: Placeholder, data: { title: 'Papéis & permissões', milestone: 'M1', desc: 'Papéis de sistema e personalizados por tenant.' } },
      { path: 'forms', component: Placeholder, data: { title: 'Formulários', milestone: 'M2', desc: 'Construtor visual, validação dinâmica e respostas (CSV).' } },
      { path: 'processes', component: Placeholder, data: { title: 'Processos', milestone: 'M3', desc: 'Fluxos de aprovação: paralelo, condicional e gateways.' } },
      { path: 'documents', component: Placeholder, data: { title: 'Documentos', milestone: 'M4', desc: 'Pastas, versionamento, download seguro e busca.' } },
      { path: 'portal', component: Placeholder, data: { title: 'Portal', milestone: 'M5', desc: 'Páginas em Markdown (HTML sanitizado).' } },
    ],
  },
  { path: '**', redirectTo: '' },
];
