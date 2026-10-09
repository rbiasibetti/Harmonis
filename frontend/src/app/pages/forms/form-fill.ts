import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsService } from '../../core/forms.service';
import { FormDetail } from '../../core/models';

@Component({
  selector: 'app-form-fill',
  imports: [FormsModule, RouterLink],
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

  private reset(f: FormDetail): void {
    const v: Record<string, any> = {};
    f.fields.forEach((fld) => (v[fld.key!] = fld.type === 'checkbox' ? false : ''));
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

  err(key: string): string | null {
    return this.errors()[key] ?? null;
  }
}
