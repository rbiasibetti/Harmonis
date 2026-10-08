import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { UsersService } from '../../core/users.service';
import { RolesService } from '../../core/roles.service';
import { RoleView, UserView } from '../../core/models';

@Component({
  selector: 'app-users',
  imports: [FormsModule],
  templateUrl: './users.html',
  styleUrl: './users.scss',
})
export class Users {
  protected auth = inject(AuthService);
  private usersApi = inject(UsersService);
  private rolesApi = inject(RolesService);

  users = signal<UserView[]>([]);
  availableRoles = signal<RoleView[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  search = '';

  // modal
  showModal = signal(false);
  editing = signal<UserView | null>(null);
  saving = signal(false);
  formError = signal<string | null>(null);
  createdPassword = signal<string | null>(null);

  f = { name: '', email: '', jobTitle: '', active: true, roles: new Set<string>() };

  constructor() {
    this.load();
    this.rolesApi.list().subscribe({ next: (r) => this.availableRoles.set(r) });
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.usersApi.list(this.search).subscribe({
      next: (u) => { this.users.set(u); this.loading.set(false); },
      error: () => { this.error.set('Não foi possível carregar os usuários.'); this.loading.set(false); },
    });
  }

  openCreate(): void {
    this.editing.set(null);
    this.f = { name: '', email: '', jobTitle: '', active: true, roles: new Set(['member']) };
    this.formError.set(null);
    this.createdPassword.set(null);
    this.showModal.set(true);
  }

  openEdit(u: UserView): void {
    this.editing.set(u);
    this.f = {
      name: u.name,
      email: u.email,
      jobTitle: u.jobTitle ?? '',
      active: u.active,
      roles: new Set(u.roles),
    };
    this.formError.set(null);
    this.createdPassword.set(null);
    this.showModal.set(true);
  }

  toggleRole(name: string): void {
    if (this.f.roles.has(name)) this.f.roles.delete(name);
    else this.f.roles.add(name);
  }

  save(): void {
    this.formError.set(null);
    this.saving.set(true);
    const roles = Array.from(this.f.roles);
    const editing = this.editing();

    if (editing) {
      this.usersApi.update(editing.id, {
        name: this.f.name, jobTitle: this.f.jobTitle || null, roles, active: this.f.active,
      }).subscribe({
        next: () => { this.saving.set(false); this.showModal.set(false); this.load(); },
        error: (e) => this.fail(e),
      });
    } else {
      this.usersApi.create({
        name: this.f.name, email: this.f.email, jobTitle: this.f.jobTitle || null, roles,
      }).subscribe({
        next: (res) => {
          this.saving.set(false);
          this.createdPassword.set(res.initialPassword);
          this.load();
        },
        error: (e) => this.fail(e),
      });
    }
  }

  remove(u: UserView): void {
    if (!confirm(`Remover o usuário ${u.name}?`)) return;
    this.usersApi.remove(u.id).subscribe({
      next: () => this.load(),
      error: (e) => alert(e?.error?.message ?? 'Não foi possível remover.'),
    });
  }

  closeModal(): void {
    this.showModal.set(false);
  }

  private fail(e: any): void {
    this.saving.set(false);
    this.formError.set(e?.error?.message ?? 'Não foi possível salvar.');
  }
}
