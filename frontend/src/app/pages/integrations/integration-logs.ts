import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import {
  EVENT_LABELS, IntegrationSummary, IntegrationsService, LogDetail, LogRow, LogStats,
} from '../../core/integrations.service';

@Component({
  selector: 'app-integration-logs',
  imports: [FormsModule, RouterLink, DatePipe],
  templateUrl: './integration-logs.html',
  styleUrl: './integrations.scss',
})
export class IntegrationLogs {
  protected auth = inject(AuthService);
  private api = inject(IntegrationsService);
  private route = inject(ActivatedRoute);

  readonly eventLabels = EVENT_LABELS;
  readonly eventKeys = Object.keys(EVENT_LABELS);

  rows = signal<LogRow[]>([]);
  total = signal(0);
  stats = signal<LogStats | null>(null);
  integrations = signal<IntegrationSummary[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  page = 0;
  readonly size = 25;
  status = '';
  event = '';
  integrationId: number | null = null;
  period = '7d';
  q = '';

  detail = signal<LogDetail | null>(null);
  resending = signal(false);
  detailError = signal<string | null>(null);

  constructor() {
    const iid = this.route.snapshot.queryParamMap.get('integrationId');
    if (iid) this.integrationId = Number(iid);
    if (this.auth.can('integrations.view')) {
      this.api.list().subscribe({ next: (l) => this.integrations.set(l), error: () => {} });
    }
    this.reload();
  }

  private fromIso(): string | null {
    const hours = { '24h': 24, '7d': 24 * 7, '30d': 24 * 30 }[this.period];
    return hours ? new Date(Date.now() - hours * 3600_000).toISOString() : null;
  }

  /** Aplica os filtros voltando à primeira página. */
  apply(): void { this.page = 0; this.load(); }

  reload(): void {
    this.load();
    this.api.logStats().subscribe({ next: (s) => this.stats.set(s), error: () => {} });
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.logs({
      status: this.status, event: this.event, integrationId: this.integrationId,
      from: this.fromIso(), q: this.q.trim(), page: this.page, size: this.size,
    }).subscribe({
      next: (p) => { this.rows.set(p.items); this.total.set(p.total); this.loading.set(false); },
      error: (e) => { this.error.set(e?.error?.message ?? 'Não foi possível carregar os logs.'); this.loading.set(false); },
    });
  }

  pages(): number { return Math.max(1, Math.ceil(this.total() / this.size)); }
  prev(): void { if (this.page > 0) { this.page--; this.load(); } }
  next(): void { if (this.page + 1 < this.pages()) { this.page++; this.load(); } }

  statusLabel(r: { status: string; willRetry: boolean }): string {
    if (r.status === 'success') return 'Sucesso';
    if (r.status === 'skipped') return 'Ignorado';
    return r.willRetry ? 'Erro · repetirá' : 'Erro';
  }

  open(r: LogRow): void {
    this.detailError.set(null);
    this.api.logDetail(r.id).subscribe({
      next: (d) => this.detail.set(d),
      error: (e) => this.error.set(e?.error?.message ?? 'Não foi possível abrir o registro.'),
    });
  }

  close(): void { this.detail.set(null); this.detailError.set(null); }

  canResend(d: LogDetail): boolean {
    return this.auth.can('integrations.update') && d.integrationId !== null && !!d.requestBody;
  }

  resend(d: LogDetail): void {
    this.resending.set(true);
    this.detailError.set(null);
    this.api.resend(d.id).subscribe({
      next: (nd) => { this.resending.set(false); this.detail.set(nd); this.reload(); },
      error: (e) => { this.resending.set(false); this.detailError.set(e?.error?.message ?? 'Não foi possível reenviar.'); },
    });
  }

  pretty(json: string | null): string {
    if (!json) return '';
    try { return JSON.stringify(JSON.parse(json), null, 2); } catch { return json; }
  }
}
