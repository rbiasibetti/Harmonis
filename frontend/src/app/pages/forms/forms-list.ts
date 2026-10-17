import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { FormsService } from '../../core/forms.service';
import { FormSummary } from '../../core/models';
import { TPipe } from '../../core/i18n/i18n';
import { tr } from '../../core/i18n/i18n';

@Component({
  selector: 'app-forms-list',
  imports: [TPipe, RouterLink],
  templateUrl: './forms-list.html',
  styleUrl: './forms.scss',
})
export class FormsList {
  protected auth = inject(AuthService);
  private api = inject(FormsService);

  forms = signal<FormSummary[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list().subscribe({
      next: (f) => { this.forms.set(f); this.loading.set(false); },
      error: () => { this.error.set(tr('Não foi possível carregar os formulários.')); this.loading.set(false); },
    });
  }

  remove(f: FormSummary): void {
    if (!confirm(tr('Remover o formulário "{name}" e todas as respostas?', { name: f.name }))) return;
    this.api.remove(f.id).subscribe({
      next: () => this.load(),
      error: (e) => alert(e?.error?.message ?? tr('Não foi possível remover.')),
    });
  }
}
