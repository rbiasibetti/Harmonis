import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { RolesService } from '../../core/roles.service';
import { PermissionGroup, RoleView } from '../../core/models';
import { TPipe } from '../../core/i18n/i18n';
import { tr } from '../../core/i18n/i18n';

@Component({
  selector: 'app-roles',
  imports: [TPipe, FormsModule],
  templateUrl: './roles.html',
  styleUrl: './roles.scss',
})
export class Roles {
  protected auth = inject(AuthService);
  private api = inject(RolesService);

  roles = signal<RoleView[]>([]);
  catalog = signal<PermissionGroup[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  showModal = signal(false);
  editing = signal<RoleView | null>(null);
  readonly = signal(false);
  saving = signal(false);
  formError = signal<string | null>(null);

  f = { name: '', label: '', permissions: new Set<string>() };

  constructor() {
    this.load();
    this.api.catalog().subscribe({ next: (c) => this.catalog.set(c.groups) });
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list().subscribe({
      next: (r) => { this.roles.set(r); this.loading.set(false); },
      error: () => { this.error.set(tr('Não foi possível carregar os papéis.')); this.loading.set(false); },
    });
  }

  openCreate(): void {
    this.editing.set(null);
    this.readonly.set(false);
    this.f = { name: '', label: '', permissions: new Set() };
    this.formError.set(null);
    this.showModal.set(true);
  }

  open(role: RoleView): void {
    this.editing.set(role);
    this.readonly.set(role.system || !this.auth.can('roles.update'));
    this.f = { name: role.name, label: role.label, permissions: new Set(role.permissions) };
    this.formError.set(null);
    this.showModal.set(true);
  }

  togglePermission(p: string): void {
    if (this.readonly()) return;
    if (this.f.permissions.has(p)) this.f.permissions.delete(p);
    else this.f.permissions.add(p);
  }

  toggleGroup(g: PermissionGroup): void {
    if (this.readonly()) return;
    const all = g.permissions.every((p) => this.f.permissions.has(p));
    g.permissions.forEach((p) => (all ? this.f.permissions.delete(p) : this.f.permissions.add(p)));
  }

  groupState(g: PermissionGroup): 'all' | 'some' | 'none' {
    const sel = g.permissions.filter((p) => this.f.permissions.has(p)).length;
    if (sel === 0) return 'none';
    return sel === g.permissions.length ? 'all' : 'some';
  }

  save(): void {
    this.formError.set(null);
    this.saving.set(true);
    const req = { name: this.f.name, label: this.f.label, permissions: Array.from(this.f.permissions) };
    const editing = this.editing();
    const done = {
      next: () => { this.saving.set(false); this.showModal.set(false); this.load(); },
      error: (e: any) => { this.saving.set(false); this.formError.set(e?.error?.message ?? tr('Não foi possível salvar.')); },
    };
    if (editing) this.api.update(editing.id, req).subscribe(done);
    else this.api.create(req).subscribe(done);
  }

  remove(role: RoleView): void {
    if (!confirm(tr('Remover o papel {name}?', { name: role.label }))) return;
    this.api.remove(role.id).subscribe({
      next: () => this.load(),
      error: (e) => alert(e?.error?.message ?? tr('Não foi possível remover.')),
    });
  }

  closeModal(): void {
    this.showModal.set(false);
  }
}
