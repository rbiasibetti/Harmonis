import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { FormsService } from '../../core/forms.service';
import { FormDetail, FormField, SubmissionView } from '../../core/models';

@Component({
  selector: 'app-form-submissions',
  imports: [RouterLink, DatePipe],
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
      error: () => { this.error.set('Não foi possível carregar as respostas.'); this.loading.set(false); },
    });
  }

  cell(field: FormField, data: Record<string, unknown>): string {
    const v = data[field.key!];
    if (v === null || v === undefined || v === '') return '—';
    if (field.type === 'checkbox') return v === true || v === 'true' ? 'Sim' : 'Não';
    return String(v);
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
      error: () => alert('Não foi possível exportar o CSV.'),
    });
  }
}
