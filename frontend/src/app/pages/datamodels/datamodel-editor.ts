import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  ColumnSpec, ColumnType, DataModelsService, FilterSpec, SaveDataModelRequest, SourceType,
} from '../../core/datamodels.service';
import { TPipe, tr } from '../../core/i18n/i18n';

interface FilterRow { key: string; label: string; on: boolean; }

@Component({
  selector: 'app-datamodel-editor',
  imports: [TPipe, FormsModule, RouterLink],
  templateUrl: './datamodel-editor.html',
  styleUrl: './datamodels.scss',
})
export class DataModelEditor {
  private api = inject(DataModelsService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  readonly types: { value: ColumnType; label: string }[] = [
    { value: 'text', label: tr('Texto') },
    { value: 'number', label: tr('Número') },
    { value: 'date', label: tr('Data') },
  ];

  id = signal<number | null>(null);
  status = signal<'draft' | 'published'>('draft');
  name = signal('');
  description = signal('');
  sourceType = signal<SourceType>('static');
  querySql = signal('');
  columns = signal<ColumnSpec[]>([{ key: '', label: '', type: 'text' }]);
  filters = signal<FilterRow[]>([]);
  labelKey = signal<string>('');

  rows = signal<Record<string, unknown>[]>([]);
  loading = signal(false);
  saving = signal(false);
  saved = signal(false);
  error = signal<string | null>(null);
  rowsMsg = signal<string | null>(null);

  constructor() {
    const param = this.route.snapshot.paramMap.get('id');
    if (param) { this.id.set(Number(param)); this.loadModel(Number(param)); }
    else this.syncFilters();
  }

  private loadModel(id: number): void {
    this.loading.set(true);
    this.api.get(id).subscribe({
      next: (m) => {
        this.status.set(m.status);
        this.name.set(m.name);
        this.description.set(m.description ?? '');
        this.sourceType.set(m.sourceType);
        this.querySql.set(m.querySql ?? '');
        this.columns.set(m.columns.length ? m.columns : [{ key: '', label: '', type: 'text' }]);
        this.labelKey.set(m.labelKey ?? '');
        const active = new Set(m.filters.map((f) => f.key));
        this.filters.set(m.columns.map((c) => ({ key: c.key, label: active.has(c.key) ? (m.filters.find((f) => f.key === c.key)?.label ?? c.label) : c.label, on: active.has(c.key) })));
        this.loading.set(false);
        if (m.sourceType === 'static') this.loadRows(id);
      },
      error: () => { this.error.set(tr('Não foi possível carregar o modelo.')); this.loading.set(false); },
    });
  }

  // ----- colunas -----
  addColumn(): void { this.columns.set([...this.columns(), { key: '', label: '', type: 'text' }]); this.syncFilters(); }
  removeColumn(i: number): void { const a = [...this.columns()]; a.splice(i, 1); this.columns.set(a); this.syncFilters(); }
  onColumnChange(): void { this.syncFilters(); }

  /** Mantém a lista de filtros alinhada às colunas (por key), preservando seleção/rótulo. */
  private syncFilters(): void {
    const prev = new Map(this.filters().map((f) => [f.key, f]));
    const next: FilterRow[] = [];
    for (const c of this.columns()) {
      if (!c.key) continue;
      const p = prev.get(c.key);
      next.push({ key: c.key, label: p?.label || c.label || c.key, on: p?.on ?? false });
    }
    this.filters.set(next);
    const keys = this.columns().map((c) => c.key).filter(Boolean);
    if (!keys.includes(this.labelKey())) this.labelKey.set(keys[0] ?? '');
  }

  columnKeys(): string[] { return this.columns().map((c) => c.key).filter(Boolean); }

  private buildReq(): SaveDataModelRequest {
    const columns = this.columns()
      .filter((c) => c.key.trim())
      .map((c) => ({ key: c.key.trim(), label: (c.label || c.key).trim(), type: c.type }));
    const filters: FilterSpec[] = this.filters()
      .filter((f) => f.on)
      .map((f) => ({ key: f.key, label: f.label.trim() || f.key, type: 'text' }));
    return {
      name: this.name().trim(),
      description: this.description().trim() || null,
      sourceType: this.sourceType(),
      querySql: this.sourceType() === 'query' ? this.querySql().trim() || null : null,
      columns,
      filters,
      labelKey: this.labelKey() || null,
    };
  }

  save(): void {
    this.error.set(null); this.saved.set(false); this.saving.set(true);
    const req = this.buildReq();
    const id = this.id();
    const call = id ? this.api.update(id, req) : this.api.create(req);
    call.subscribe({
      next: (m) => {
        this.saving.set(false); this.saved.set(true); this.status.set(m.status);
        if (!id) this.router.navigate(['/datamodels', m.id, 'edit']);
      },
      error: (e) => { this.saving.set(false); this.error.set(e?.error?.message ?? tr('Não foi possível salvar.')); },
    });
  }

  togglePublish(): void {
    const id = this.id(); if (!id) return;
    this.error.set(null);
    const call = this.status() === 'published' ? this.api.unpublish(id) : this.api.publish(id);
    call.subscribe({
      next: (m) => this.status.set(m.status),
      error: (e) => this.error.set(e?.error?.message ?? tr('Não foi possível alterar a publicação.')),
    });
  }

  // ----- linhas (static) -----
  private loadRows(id: number): void {
    this.api.rows(id).subscribe({ next: (r) => this.rows.set(r.map((x) => ({ ...x }))), error: () => {} });
  }
  addRow(): void {
    const row: Record<string, unknown> = {};
    for (const c of this.columns()) if (c.key) row[c.key] = '';
    this.rows.set([...this.rows(), row]);
  }
  removeRow(i: number): void { const a = [...this.rows()]; a.splice(i, 1); this.rows.set(a); }
  cellValue(row: Record<string, unknown>, key: string): string { const v = row[key]; return v == null ? '' : String(v); }
  setCell(row: Record<string, unknown>, key: string, value: string): void { row[key] = value; }

  saveRows(): void {
    const id = this.id(); if (!id) return;
    this.rowsMsg.set(null);
    this.api.saveRows(id, this.rows()).subscribe({
      next: () => { this.rowsMsg.set(tr('Linhas salvas.')); this.loadRows(id); },
      error: (e) => this.error.set(e?.error?.message ?? tr('Não foi possível salvar as linhas.')),
    });
  }

  onCsvPick(input: HTMLInputElement): void {
    const id = this.id(); const file = input.files?.[0];
    if (!id || !file) return;
    input.value = '';
    this.error.set(null); this.rowsMsg.set(null);
    this.api.importCsv(id, file).subscribe({
      next: (r) => { this.rowsMsg.set(tr('{n} linha(s) importada(s).', { n: r.imported })); this.loadRows(id); },
      error: (e) => this.error.set(e?.error?.message ?? tr('Não foi possível importar o CSV.')),
    });
  }
}
