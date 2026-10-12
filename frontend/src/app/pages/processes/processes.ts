import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { InboxItem, MineItem, ProcessDetail, ProcessesService } from '../../core/processes.service';
import { NotificationsService } from '../../core/notifications.service';
import { FormField } from '../../core/models';

@Component({
  selector: 'app-processes',
  imports: [FormsModule, DatePipe],
  templateUrl: './processes.html',
  styleUrl: './processes.scss',
})
export class Processes {
  private api = inject(ProcessesService);
  private notifications = inject(NotificationsService);

  tab = signal<'inbox' | 'mine'>('inbox');
  inbox = signal<InboxItem[]>([]);
  mine = signal<MineItem[]>([]);
  loading = signal(false);

  detail = signal<ProcessDetail | null>(null);
  comment = '';
  deciding = signal(false);
  detailError = signal<string | null>(null);

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    this.api.inbox().subscribe({ next: (i) => this.inbox.set(i), complete: () => this.loading.set(false) });
    this.api.mine().subscribe({ next: (m) => this.mine.set(m) });
  }

  open(instanceId: number): void {
    this.detailError.set(null);
    this.comment = '';
    this.api.detail(instanceId).subscribe({ next: (d) => this.detail.set(d) });
  }

  close(): void { this.detail.set(null); }

  decide(approved: boolean): void {
    const d = this.detail();
    if (!d || d.actionableTaskId == null) return;
    this.deciding.set(true);
    this.api.decide(d.actionableTaskId, approved, this.comment).subscribe({
      next: () => {
        this.deciding.set(false);
        this.close();
        this.load();
        this.notifications.refresh();
      },
      error: (e) => { this.deciding.set(false); this.detailError.set(e?.error?.message ?? 'Não foi possível decidir.'); },
    });
  }

  label(f: FormField): string { return f.label; }

  dataFields(fields: FormField[]): FormField[] {
    return (fields ?? []).filter((f) => f.type !== 'section');
  }

  value(f: FormField, data: Record<string, unknown>): string {
    const v = data[f.key!];
    if (v === null || v === undefined || v === '' || (Array.isArray(v) && v.length === 0)) return '—';
    if (f.type === 'checkbox') return v === true || v === 'true' ? 'Sim' : 'Não';
    if (Array.isArray(v)) return v.join(', ');
    return String(v);
  }

  statusBadge(s: string): string {
    return s === 'approved' ? 'ok' : s === 'rejected' ? 'off' : 'gray';
  }

  statusLabel(s: string): string {
    return s === 'approved' ? 'Aprovado' : s === 'rejected' ? 'Reprovado'
      : s === 'skipped' ? 'Dispensado' : s === 'pending' ? 'Pendente' : s;
  }
}
