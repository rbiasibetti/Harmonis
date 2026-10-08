import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet, Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ThemeService } from '../core/theme.service';

interface NavItem { label: string; path: string; icon: string; permission?: string; }

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected auth = inject(AuthService);
  protected theme = inject(ThemeService);
  private router = inject(Router);

  menuOpen = signal(false);

  readonly nav: NavItem[] = [
    { label: 'Dashboard', path: '/dashboard', icon: '◧' },
    { label: 'Usuários', path: '/users', icon: '◎', permission: 'users.view' },
    { label: 'Papéis', path: '/roles', icon: '✦', permission: 'roles.view' },
    { label: 'Formulários', path: '/forms', icon: '▤', permission: 'forms.view' },
    { label: 'Processos', path: '/processes', icon: '⇄', permission: 'processes.view' },
    { label: 'Documentos', path: '/documents', icon: '🗀', permission: 'documents.view' },
    { label: 'Portal', path: '/portal', icon: '◈', permission: 'pages.view' },
  ];

  visibleNav(): NavItem[] {
    return this.nav.filter((i) => !i.permission || this.auth.can(i.permission));
  }

  initials(): string {
    const name = this.auth.user()?.name ?? '?';
    return name.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase();
  }

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
