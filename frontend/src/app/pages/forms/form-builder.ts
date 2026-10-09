import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsService } from '../../core/forms.service';
import { FieldType, FormField } from '../../core/models';

@Component({
  selector: 'app-form-builder',
  imports: [FormsModule, RouterLink],
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

  loading = signal(false);
  saving = signal(false);
  error = signal<string | null>(null);
  saved = signal(false);

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
          this.fields.set(f.fields);
          this.loading.set(false);
        },
        error: () => { this.error.set('Não foi possível carregar o formulário.'); this.loading.set(false); },
      });
    }
  }

  hasOptions(type: string): boolean {
    return this.types().find((t) => t.code === type)?.hasOptions ?? false;
  }

  typeLabel(type: string): string {
    return this.types().find((t) => t.code === type)?.label ?? type;
  }

  addField(type: string): void {
    const f: FormField = {
      id: null, key: null, type,
      label: 'Novo campo', placeholder: null, help: null, required: false,
      options: this.hasOptions(type) ? ['Opção 1'] : null,
    };
    this.fields.set([...this.fields(), f]);
  }

  remove(i: number): void {
    const arr = [...this.fields()];
    arr.splice(i, 1);
    this.fields.set(arr);
  }

  move(i: number, delta: number): void {
    const arr = [...this.fields()];
    const j = i + delta;
    if (j < 0 || j >= arr.length) return;
    [arr[i], arr[j]] = [arr[j], arr[i]];
    this.fields.set(arr);
  }

  addOption(f: FormField): void {
    f.options = [...(f.options ?? []), `Opção ${(f.options?.length ?? 0) + 1}`];
    this.fields.set([...this.fields()]);
  }

  removeOption(f: FormField, idx: number): void {
    f.options = (f.options ?? []).filter((_, k) => k !== idx);
    this.fields.set([...this.fields()]);
  }

  trackByIndex(i: number): number { return i; }

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
        next: (f) => { this.fields.set(f.fields); this.saving.set(false); this.saved.set(true); },
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
