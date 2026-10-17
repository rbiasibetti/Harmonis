import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { DashboardService, DashboardView } from '../../core/dashboard.service';
import { TPipe } from '../../core/i18n/i18n';
import { tr } from '../../core/i18n/i18n';

interface Card { label: string; value: number | null; icon: string; link: string; }

@Component({
  selector: 'app-dashboard',
  imports: [TPipe, RouterLink, DatePipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  protected auth = inject(AuthService);
  private api = inject(DashboardService);

  data = signal<DashboardView | null>(null);
  loading = signal(true);

  constructor() {
    this.api.get().subscribe({
      next: (d) => { this.data.set(d); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  cards(): Card[] {
    const m = this.data()?.metrics;
    if (!m) return [];
    const all: Card[] = [
      { label: tr('Aprovações pendentes'), value: m.pendingApprovals, icon: '⏳', link: '/processes' },
      { label: tr('Processos ativos'), value: m.activeProcesses, icon: '⇄', link: '/processes' },
      { label: tr('Formulários publicados'), value: m.publishedForms, icon: '▤', link: '/forms' },
      { label: tr('Documentos'), value: m.documents, icon: '🗀', link: '/documents' },
      { label: tr('Páginas publicadas'), value: m.publishedPages, icon: '◈', link: '/portal' },
      { label: tr('Usuários ativos'), value: m.activeUsers, icon: '◎', link: '/users' },
    ];
    return all.filter((c) => c.value !== null);
  }
}
