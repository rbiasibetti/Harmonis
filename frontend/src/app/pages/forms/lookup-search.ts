import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DataModelOption, DataModelsService } from '../../core/datamodels.service';
import { LookupSelection } from '../../core/models';
import { TPipe, tr } from '../../core/i18n/i18n';

/** Modal sobreposto de consulta a um modelo de dados: filtros + resultados + seleção. */
@Component({
  selector: 'app-lookup-search',
  imports: [TPipe, FormsModule],
  templateUrl: './lookup-search.html',
  styleUrl: './lookup-search.scss',
})
export class LookupSearch {
  private api = inject(DataModelsService);

  @Input() dataModelId!: number;
  @Input() multiple = false;
  @Output() picked = new EventEmitter<LookupSelection[]>();
  @Output() cancelled = new EventEmitter<void>();

  model = signal<DataModelOption | null>(null);
  filterValues: Record<string, string> = {};
  results = signal<Record<string, unknown>[]>([]);
  total = signal(0);
  page = signal(0);
  size = 10;
  loading = signal(false);
  searched = signal(false);
  error = signal<string | null>(null);
  selected = signal<LookupSelection[]>([]);

  ngOnInit(): void {
    this.api.option(this.dataModelId).subscribe({
      next: (m) => { this.model.set(m); this.search(); },
      error: () => this.error.set(tr('Não foi possível carregar o modelo de dados.')),
    });
  }

  inputType(t: string): string { return t === 'number' ? 'number' : t === 'date' ? 'date' : 'text'; }

  search(page = 0): void {
    this.error.set(null); this.loading.set(true); this.page.set(page);
    const filters: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(this.filterValues)) if (v != null && String(v).trim() !== '') filters[k] = v;
    this.api.query(this.dataModelId, filters, page, this.size).subscribe({
      next: (r) => { this.results.set(r.rows); this.total.set(r.total); this.loading.set(false); this.searched.set(true); },
      error: (e) => { this.loading.set(false); this.error.set(e?.error?.message ?? tr('Não foi possível consultar.')); },
    });
  }

  pages(): number { return Math.max(1, Math.ceil(this.total() / this.size)); }
  prev(): void { if (this.page() > 0) this.search(this.page() - 1); }
  next(): void { if (this.page() < this.pages() - 1) this.search(this.page() + 1); }

  cell(row: Record<string, unknown>, key: string): string { const v = row[key]; return v == null ? '' : String(v); }

  private labelOf(row: Record<string, unknown>): string {
    const m = this.model();
    if (!m) return '';
    const lk = m.labelKey;
    if (lk && row[lk] != null && String(row[lk]).trim() !== '') return String(row[lk]);
    return m.columns.map((c) => row[c.key]).filter((v) => v != null && String(v).trim() !== '').join(' · ');
  }

  private selectionOf(row: Record<string, unknown>): LookupSelection { return { label: this.labelOf(row), values: { ...row } }; }

  isSelected(row: Record<string, unknown>): boolean {
    const s = this.selectionOf(row);
    return this.selected().some((x) => JSON.stringify(x.values) === JSON.stringify(s.values));
  }

  toggle(row: Record<string, unknown>): void {
    const s = this.selectionOf(row);
    if (this.multiple) {
      const cur = this.selected();
      const key = JSON.stringify(s.values);
      this.selected.set(cur.some((x) => JSON.stringify(x.values) === key)
        ? cur.filter((x) => JSON.stringify(x.values) !== key) : [...cur, s]);
    } else {
      this.picked.emit([s]); // seleção única confirma na hora
    }
  }

  confirm(): void { this.picked.emit(this.selected()); }
  cancel(): void { this.cancelled.emit(); }
}
