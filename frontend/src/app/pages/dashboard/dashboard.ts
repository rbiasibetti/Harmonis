import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { DashboardService, DashboardView } from '../../core/dashboard.service';

interface Card { label: string; value: number | null; icon: string; link: string; }

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DatePipe],
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
      { label: 'Aprovações pendentes', value: m.pendingApprovals, icon: '⏳', link: '/processes' },
      { label: 'Processos ativos', value: m.activeProcesses, icon: '⇄', link: '/processes' },
      { label: 'Formulários publicados', value: m.publishedForms, icon: '▤', link: '/forms' },
      { label: 'Documentos', value: m.documents, icon: '🗀', link: '/documents' },
      { label: 'Páginas publicadas', value: m.publishedPages, icon: '◈', link: '/portal' },
      { label: 'Usuários ativos', value: m.activeUsers, icon: '◎', link: '/users' },
    ];
    return all.filter((c) => c.value !== null);
  }
}
