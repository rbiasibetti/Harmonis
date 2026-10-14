import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsService } from '../../core/forms.service';
import { FormDetail, FormField } from '../../core/models';
import { FormRenderer } from './form-renderer';

@Component({
  selector: 'app-form-fill',
  imports: [RouterLink, FormRenderer],
  templateUrl: './form-fill.html',
  styleUrl: './forms.scss',
})
export class FormFill {
  private api = inject(FormsService);
  private route = inject(ActivatedRoute);

  form = signal<FormDetail | null>(null);
  loading = signal(true);
  saving = signal(false);
  done = signal(false);
  error = signal<string | null>(null);
  errors = signal<Record<string, string>>({});

  values: Record<string, any> = {};
  private formId!: number;

  constructor() {
    this.formId = Number(this.route.snapshot.paramMap.get('id'));
    this.api.get(this.formId).subscribe({
      next: (f) => { this.form.set(f); this.reset(f); this.loading.set(false); },
      error: () => { this.error.set('Não foi possível carregar o formulário.'); this.loading.set(false); },
    });
  }

  /** Campos preenchidos pelo solicitante no envio (sem fillRole); blocos continuam visíveis. */
  requesterFields(f: FormDetail): FormField[] {
    return f.fields.filter((x) => x.type === 'section' || !x.fillRole);
  }

  private reset(f: FormDetail): void {
    const v: Record<string, any> = {};
    this.requesterFields(f).forEach((fld) => {
      if (fld.type === 'section' || !fld.key) return;
      if (fld.type === 'checkbox') v[fld.key] = false;
      else if (fld.type === 'multiselect') v[fld.key] = [];
      else v[fld.key] = '';
    });
    this.values = v;
    this.errors.set({});
    this.done.set(false);
  }

  submit(): void {
    this.error.set(null);
    this.errors.set({});
    this.saving.set(true);
    this.api.submit(this.formId, this.values).subscribe({
      next: () => { this.saving.set(false); this.done.set(true); },
      error: (e) => {
        this.saving.set(false);
        if (e?.status === 422 && e.error?.errors) this.errors.set(e.error.errors);
        else this.error.set(e?.error?.message ?? 'Não foi possível enviar.');
      },
    });
  }

  again(): void {
    const f = this.form();
    if (f) this.reset(f);
  }
}
