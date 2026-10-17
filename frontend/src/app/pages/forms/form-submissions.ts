import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { FormsService } from '../../core/forms.service';
import { AttachmentRef, FormDetail, FormField, SubmissionView } from '../../core/models';
import { downloadBlob } from '../../core/download';
import { TPipe } from '../../core/i18n/i18n';
import { tr } from '../../core/i18n/i18n';

@Component({
  selector: 'app-form-submissions',
  imports: [TPipe, RouterLink, DatePipe],
  templateUrl: './form-submissions.html',
  styleUrl: './forms.scss',
})
export class FormSubmissionsPage {
  private api = inject(FormsService);
  private route = inject(ActivatedRoute);

  form = signal<FormDetail | null>(null);
  rows = signal<SubmissionView[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  private formId!: number;

  constructor() {
    this.formId = Number(this.route.snapshot.paramMap.get('id'));
    forkJoin({ form: this.api.get(this.formId), subs: this.api.submissions(this.formId) }).subscribe({
      next: ({ form, subs }) => { this.form.set(form); this.rows.set(subs); this.loading.set(false); },
      error: () => { this.error.set(tr('Não foi possível carregar as respostas.')); this.loading.set(false); },
    });
  }

  columns(): FormField[] {
    return (this.form()?.fields ?? []).filter((f) => f.type !== 'section');
  }

  cell(field: FormField, data: Record<string, unknown>): string {
    const v = data[field.key!];
    if (v === null || v === undefined || v === '' || (Array.isArray(v) && v.length === 0)) return '—';
    if (field.type === 'checkbox') return v === true || v === 'true' ? tr('Sim') : tr('Não');
    if (Array.isArray(v)) return v.join(', ');
    return String(v);
  }

  isFileField(f: FormField): boolean { return f.type === 'file' || f.type === 'multifile'; }

  refsOf(field: FormField, data: Record<string, unknown>): AttachmentRef[] {
    const v = data[field.key!];
    if (!v) return [];
    return (Array.isArray(v) ? v : [v]) as AttachmentRef[];
  }

  download(ref: AttachmentRef): void {
    this.api.downloadAttachment(ref.id).subscribe({ next: (blob) => downloadBlob(blob, ref.name) });
  }

  pdf(submissionId: number): void {
    this.api.downloadSubmissionPdf(this.formId, submissionId).subscribe({
      next: (blob) => downloadBlob(blob, `registro-${this.form()?.slug ?? this.formId}-${submissionId}.pdf`),
      error: () => alert(tr('Não foi possível gerar o PDF.')),
    });
  }

  exportCsv(): void {
    this.api.downloadCsv(this.formId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `respostas-${this.form()?.slug ?? this.formId}.csv`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => alert(tr('Não foi possível exportar o CSV.')),
    });
  }
}
