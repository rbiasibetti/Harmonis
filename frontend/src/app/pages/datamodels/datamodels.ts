import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { DataModelSummary, DataModelsService } from '../../core/datamodels.service';
import { TPipe, tr } from '../../core/i18n/i18n';

@Component({
  selector: 'app-datamodels',
  imports: [TPipe, RouterLink],
  templateUrl: './datamodels.html',
  styleUrl: './datamodels.scss',
})
export class DataModels {
  protected auth = inject(AuthService);
  private api = inject(DataModelsService);

  items = signal<DataModelSummary[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  busy = signal<number | null>(null);

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: (l) => { this.items.set(l); this.loading.set(false); },
      error: () => { this.error.set(tr('Não foi possível carregar os modelos de dados.')); this.loading.set(false); },
    });
  }

  sourceLabel(t: string): string { return t === 'query' ? tr('Consulta SQL') : tr('Dados (CSV / manual)'); }

  togglePublish(i: DataModelSummary): void {
    this.error.set(null);
    this.busy.set(i.id);
    const call = i.status === 'published' ? this.api.unpublish(i.id) : this.api.publish(i.id);
    call.subscribe({
      next: () => { this.busy.set(null); this.load(); },
      error: (e) => { this.busy.set(null); this.error.set(e?.error?.message ?? tr('Não foi possível alterar a publicação.')); },
    });
  }

  remove(i: DataModelSummary): void {
    if (!confirm(tr('Excluir o modelo de dados "{name}"?', { name: i.name }))) return;
    this.error.set(null);
    this.api.remove(i.id).subscribe({
      next: () => this.load(),
      error: (e) => this.error.set(e?.error?.message ?? tr('Não foi possível excluir.')),
    });
  }
}
