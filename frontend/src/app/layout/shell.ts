import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet, Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ThemeService } from '../core/theme.service';
import { NotificationsService, NotificationView } from '../core/notifications.service';
import { TPipe } from '../core/i18n/i18n';
import { tr } from '../core/i18n/i18n';

interface NavItem { label: string; path: string; icon: string; permission?: string; }

@Component({
  selector: 'app-shell',
  imports: [TPipe, RouterOutlet, RouterLink, RouterLinkActive, DatePipe],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected auth = inject(AuthService);
  protected theme = inject(ThemeService);
  protected notifications = inject(NotificationsService);
  private router = inject(Router);

  menuOpen = signal(false);
  bellOpen = signal(false);

  constructor() {
    this.notifications.refresh();
  }

  toggleBell(): void {
    const next = !this.bellOpen();
    this.bellOpen.set(next);
    if (next) this.notifications.refresh();
  }

  /** Mensagens de notificacao geradas pelo servidor ("<formulario> aguarda sua decisão." etc.). */
  notifMessage(message: string | null): string {
    if (!message) return "";
    const rules: [string, string][] = [
      [' aguarda sua decisão.', '{form} aguarda sua decisão.'],
      [' foi aprovada.', '{form} foi aprovada.'],
      [' foi reprovada.', '{form} foi reprovada.'],
    ];
    for (const [suffix, key] of rules) {
      if (message.endsWith(suffix)) return tr(key, { form: message.slice(0, -suffix.length) });
    }
    return tr(message);
  }

  openNotification(n: NotificationView): void {
    this.notifications.markRead(n.id);
    this.bellOpen.set(false);
    if (n.url) this.router.navigateByUrl(n.url);
  }

  readonly nav: NavItem[] = [
    { label: 'Dashboard', path: '/dashboard', icon: '◧' },
    { label: tr('Usuários'), path: '/users', icon: '◎', permission: 'users.view' },
    { label: tr('Papéis'), path: '/roles', icon: '✦', permission: 'roles.view' },
    { label: tr('Formulários'), path: '/forms', icon: '▤', permission: 'forms.view' },
    { label: tr('Processos'), path: '/processes', icon: '⇄', permission: 'processes.view' },
    { label: tr('Documentos'), path: '/documents', icon: '🗀', permission: 'documents.view' },
    { label: tr('Portal'), path: '/portal', icon: '◈', permission: 'pages.view' },
    { label: tr('Integrações'), path: '/integrations', icon: '⚡', permission: 'integrations.view' },
    { label: tr('Logs de integração'), path: '/integration-logs', icon: '☰', permission: 'integrations.logs' },
  ];

  visibleNav(): NavItem[] {
    return this.nav.filter((i) => !i.permission || this.auth.can(i.permission));
  }

  initials(): string {
    const name = this.auth.user()?.name ?? '?';
    return name.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase();
  }

  logout(): void {
    this.auth.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
