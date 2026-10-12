import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsService } from '../../core/forms.service';
import { FieldType, FormField } from '../../core/models';
import { FormRenderer } from './form-renderer';

interface Opt { value: string; label: string; }

@Component({
  selector: 'app-form-builder',
  imports: [FormsModule, RouterLink, FormRenderer],
  templateUrl: './form-builder.html',
  styleUrl: './forms.scss',
})
export class FormBuilder {
  private api = inject(FormsService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  id = signal<number | null>(null);
  name = signal('');
  description = signal('');
  status = signal('draft');
  fields = signal<FormField[]>([]);
  types = signal<FieldType[]>([]);

  tab = signal<'fields' | 'layout'>('fields');
  previewValues: Record<string, any> = {};

  loading = signal(false);
  saving = signal(false);
  error = signal<string | null>(null);
  saved = signal(false);

  readonly widths: Opt[] = [
    { value: 'full', label: 'Linha inteira' },
    { value: 'half', label: 'Metade' },
    { value: 'third', label: 'Um terço' },
  ];

  readonly operators: Opt[] = [
    { value: 'not_empty', label: 'estiver preenchido' },
    { value: 'empty', label: 'estiver vazio' },
    { value: '==', label: 'for igual a' },
    { value: '!=', label: 'for diferente de' },
    { value: '>', label: 'for maior que' },
    { value: '>=', label: 'for maior ou igual a' },
    { value: '<', label: 'for menor que' },
    { value: '<=', label: 'for menor ou igual a' },
    { value: 'contains', label: 'contiver' },
  ];

  constructor() {
    this.api.fieldTypes().subscribe({ next: (t) => this.types.set(t) });
    const param = this.route.snapshot.paramMap.get('id');
    if (param) {
      this.id.set(Number(param));
      this.loading.set(true);
      this.api.get(Number(param)).subscribe({
        next: (f) => {
          this.name.set(f.name);
          this.description.set(f.description ?? '');
          this.status.set(f.status);
          this.fields.set(f.fields.map((x) => this.hydrate(x)));
          this.loading.set(false);
        },
        error: () => { this.error.set('Não foi possível carregar o formulário.'); this.loading.set(false); },
      });
    }
  }

  private hydrate(f: FormField): FormField {
    if (f.type !== 'section') {
      f.validation = f.validation ?? {};
      f.visibleWhen = f.visibleWhen ?? { field: '', operator: 'not_empty', value: '' };
      f.width = f.width ?? 'full';
    }
    return f;
  }

  // ----- helpers de tipo -----
  typeLabel(type: string): string { return this.types().find((t) => t.code === type)?.label ?? type; }
  hasOptions(type: string): boolean { return this.types().find((t) => t.code === type)?.hasOptions ?? false; }
  isSection(f: FormField): boolean { return f.type === 'section'; }
  isSingle(f: FormField): boolean { return f.type === 'select'; }
  isMulti(f: FormField): boolean { return f.type === 'multiselect'; }
  supportsValidation(f: FormField): boolean {
    return ['text', 'textarea', 'number', 'email', 'date'].includes(f.type);
  }
  isText(f: FormField): boolean { return ['text', 'textarea', 'email'].includes(f.type); }
  isNumber(f: FormField): boolean { return f.type === 'number'; }
  isDate(f: FormField): boolean { return f.type === 'date'; }

  /** Outros campos (com key, nao-secao) para condicao/comparacao. */
  otherFields(current: FormField): Opt[] {
    return this.fields()
      .filter((f) => f !== current && f.type !== 'section' && f.key)
      .map((f) => ({ value: f.key!, label: f.label || f.key! }));
  }
  compareFields(current: FormField): Opt[] {
    const kind = current.type; // compara com campos do mesmo tipo
    return this.fields()
      .filter((f) => f !== current && f.type === kind && f.key)
      .map((f) => ({ value: f.key!, label: f.label || f.key! }));
  }

  needsValue(op: string): boolean { return op !== 'empty' && op !== 'not_empty'; }

  // ----- edicao de campos -----
  addField(type: string): void {
    let f: FormField;
    if (type === 'section') {
      f = { id: null, key: null, type, label: 'Novo bloco', placeholder: null, help: null, required: false, options: null, width: 'full' };
    } else {
      f = {
        id: null, key: null, type,
        label: 'Novo campo', placeholder: null, help: null, required: false,
        options: this.hasOptions(type) ? ['Opção 1'] : null,
        display: this.hasOptions(type) ? 'combo' : null,
        width: 'full',
        validation: {},
        visibleWhen: { field: '', operator: 'not_empty', value: '' },
      };
    }
    this.fields.set([...this.fields(), f]);
  }

  remove(i: number): void { const a = [...this.fields()]; a.splice(i, 1); this.fields.set(a); }
  move(i: number, d: number): void {
    const a = [...this.fields()]; const j = i + d;
    if (j < 0 || j >= a.length) return;
    [a[i], a[j]] = [a[j], a[i]]; this.fields.set(a);
  }
  addOption(f: FormField): void { f.options = [...(f.options ?? []), `Opção ${(f.options?.length ?? 0) + 1}`]; this.touch(); }
  removeOption(f: FormField, i: number): void { f.options = (f.options ?? []).filter((_, k) => k !== i); this.touch(); }
  private touch(): void { this.fields.set([...this.fields()]); }

  openPreview(): void {
    // valores limpos para a previa (nao envia)
    const v: Record<string, any> = {};
    this.fields().forEach((f) => {
      if (f.type === 'section' || !f.key) return;
      v[f.key] = f.type === 'checkbox' ? false : f.type === 'multiselect' ? [] : '';
    });
    this.previewValues = v;
    this.tab.set('layout');
  }

  save(): void {
    this.error.set(null);
    this.saved.set(false);
    this.saving.set(true);
    const req = {
      name: this.name(),
      description: this.description() || null,
      status: this.status(),
      fields: this.fields(),
    };
    const id = this.id();
    if (id) {
      this.api.update(id, req).subscribe({
        next: (f) => { this.fields.set(f.fields.map((x) => this.hydrate(x))); this.saving.set(false); this.saved.set(true); },
        error: (e) => this.fail(e),
      });
    } else {
      this.api.create(req).subscribe({
        next: (f) => { this.saving.set(false); this.router.navigate(['/forms', f.id, 'edit']); },
        error: (e) => this.fail(e),
      });
    }
  }

  private fail(e: any): void {
    this.saving.set(false);
    this.error.set(e?.error?.message ?? 'Não foi possível salvar.');
  }
}
