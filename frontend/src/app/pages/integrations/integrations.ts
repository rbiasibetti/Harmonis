import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { AUTH_LABELS, IntegrationSummary, IntegrationsService } from '../../core/integrations.service';

@Component({
  selector: 'app-integrations',
  imports: [RouterLink, DatePipe],
  templateUrl: './integrations.html',
  styleUrl: './integrations.scss',
})
export class Integrations {
  protected auth = inject(AuthService);
  private api = inject(IntegrationsService);

  items = signal<IntegrationSummary[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  busy = signal<number | null>(null);

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: (l) => { this.items.set(l); this.loading.set(false); },
      error: () => { this.error.set('Não foi possível carregar as integrações.'); this.loading.set(false); },
    });
  }

  authLabel(t: string): string { return AUTH_LABELS[t] ?? t; }

  togglePublish(i: IntegrationSummary): void {
    this.error.set(null);
    this.busy.set(i.id);
    const call = i.status === 'published' ? this.api.unpublish(i.id) : this.api.publish(i.id);
    call.subscribe({
      next: () => { this.busy.set(null); this.load(); },
      error: (e) => { this.busy.set(null); this.error.set(e?.error?.message ?? 'Não foi possível alterar a publicação.'); },
    });
  }

  remove(i: IntegrationSummary): void {
    if (!confirm(`Excluir a integração "${i.name}"? Os logs já gerados são mantidos.`)) return;
    this.error.set(null);
    this.api.remove(i.id).subscribe({
      next: () => this.load(),
      error: (e) => this.error.set(e?.error?.message ?? 'Não foi possível excluir.'),
    });
  }
}
