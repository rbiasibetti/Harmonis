import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { FormsService } from '../../core/forms.service';
import { FormSummary } from '../../core/models';

@Component({
  selector: 'app-forms-list',
  imports: [RouterLink],
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
      error: () => { this.error.set('Não foi possível carregar os formulários.'); this.loading.set(false); },
    });
  }

  remove(f: FormSummary): void {
    if (!confirm(`Remover o formulário "${f.name}" e todas as respostas?`)) return;
    this.api.remove(f.id).subscribe({
      next: () => this.load(),
      error: (e) => alert(e?.error?.message ?? 'Não foi possível remover.'),
    });
  }
}
