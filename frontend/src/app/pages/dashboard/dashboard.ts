import { Component, inject } from '@angular/core';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  protected auth = inject(AuthService);

  modules = [
    { label: 'Usuários & papéis', desc: 'Identidade, RBAC e papéis personalizados', milestone: 'M1' },
    { label: 'Formulários', desc: 'Construtor, validação e respostas (CSV)', milestone: 'M2' },
    { label: 'Workflow', desc: 'Aprovações com paralelo, condicional e gateways', milestone: 'M3' },
    { label: 'Documentos (ECM)', desc: 'Pastas, versionamento e busca', milestone: 'M4' },
    { label: 'Portal', desc: 'Páginas em Markdown', milestone: 'M5' },
    { label: 'Modelador visual', desc: 'Drag-and-drop, zoom e mini-mapa', milestone: 'M6' },
  ];
}
