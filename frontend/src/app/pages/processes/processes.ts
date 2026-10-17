import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { InboxItem, MineItem, ProcessDetail, ProcessesService } from '../../core/processes.service';
import { NotificationsService } from '../../core/notifications.service';
import { FormsService } from '../../core/forms.service';
import { AttachmentRef, FormField } from '../../core/models';
import { FormRenderer } from '../forms/form-renderer';
import { downloadBlob } from '../../core/download';
import { TPipe } from '../../core/i18n/i18n';
import { tr } from '../../core/i18n/i18n';

@Component({
  selector: 'app-processes',
  imports: [TPipe, FormsModule, DatePipe, FormRenderer],
  templateUrl: './processes.html',
  styleUrl: './processes.scss',
})
export class Processes {
  private api = inject(ProcessesService);
  private notifications = inject(NotificationsService);
  private formsApi = inject(FormsService);

  tab = signal<'inbox' | 'mine'>('inbox');
  inbox = signal<InboxItem[]>([]);
  mine = signal<MineItem[]>([]);
  loading = signal(false);

  detail = signal<ProcessDetail | null>(null);
  comment = '';
  deciding = signal(false);
  detailError = signal<string | null>(null);
  fillValues: Record<string, any> = {};
  fillErrors = signal<Record<string, string>>({});

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    this.api.inbox().subscribe({ next: (i) => this.inbox.set(i), complete: () => this.loading.set(false) });
    this.api.mine().subscribe({ next: (m) => this.mine.set(m) });
  }

  open(instanceId: number): void {
    this.detailError.set(null);
    this.fillErrors.set({});
    this.comment = '';
    this.api.detail(instanceId).subscribe({ next: (d) => { this.detail.set(d); this.initFill(d); } });
  }

  private initFill(d: ProcessDetail): void {
    const v: Record<string, any> = {};
    (d.fillFields ?? []).forEach((f) => {
      if (!f.key) return;
      v[f.key] = f.type === 'checkbox' ? false
        : (f.type === 'multiselect' || f.type === 'multifile') ? []
        : f.type === 'file' ? null : '';
    });
    this.fillValues = v;
  }

  pdf(instanceId: number): void {
    this.api.downloadPdf(instanceId).subscribe({
      next: (blob) => downloadBlob(blob, `processo-${instanceId}.pdf`),
      error: () => this.detailError.set(tr('Não foi possível gerar o PDF.')),
    });
  }

  close(): void { this.detail.set(null); }

  decide(approved: boolean): void {
    const d = this.detail();
    if (!d || d.actionableTaskId == null) return;
    this.detailError.set(null);
    this.fillErrors.set({});
    this.deciding.set(true);
    // Ao aprovar, envia os campos complementares preenchidos; ao reprovar, não exige preenchimento.
    const values = approved && (d.fillFields?.length ?? 0) > 0 ? this.fillValues : undefined;
    this.api.decide(d.actionableTaskId, approved, this.comment, values).subscribe({
      next: () => {
        this.deciding.set(false);
        this.close();
        this.load();
        this.notifications.refresh();
      },
      error: (e) => {
        this.deciding.set(false);
        if (e?.status === 422 && e.error?.errors) this.fillErrors.set(e.error.errors);
        else this.detailError.set(e?.error?.message ?? tr('Não foi possível decidir.'));
      },
    });
  }

  label(f: FormField): string { return f.label; }

  dataFields(fields: FormField[]): FormField[] {
    return (fields ?? []).filter((f) => f.type !== 'section');
  }

  value(f: FormField, data: Record<string, unknown>): string {
    const v = data[f.key!];
    if (v === null || v === undefined || v === '' || (Array.isArray(v) && v.length === 0)) return '—';
    if (f.type === 'checkbox') return v === true || v === 'true' ? tr('Sim') : tr('Não');
    if (Array.isArray(v)) return v.join(', ');
    return String(v);
  }

  isFileField(f: FormField): boolean { return f.type === 'file' || f.type === 'multifile'; }

  refsOf(f: FormField, data: Record<string, unknown>): AttachmentRef[] {
    const v = data[f.key!];
    if (!v) return [];
    return (Array.isArray(v) ? v : [v]) as AttachmentRef[];
  }

  downloadRef(ref: AttachmentRef): void {
    this.formsApi.downloadAttachment(ref.id).subscribe({ next: (blob) => downloadBlob(blob, ref.name) });
  }

  statusBadge(s: string): string {
    return s === 'approved' ? 'ok' : s === 'rejected' ? 'off' : 'gray';
  }

  statusLabel(s: string): string {
    return tr(s === 'approved' ? 'Aprovado' : s === 'rejected' ? 'Reprovado'
      : s === 'skipped' ? 'Dispensado' : s === 'pending' ? 'Pendente' : s);
  }
}
