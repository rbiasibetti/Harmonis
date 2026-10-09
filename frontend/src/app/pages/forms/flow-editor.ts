import { Component, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsService } from '../../core/forms.service';
import { RolesService } from '../../core/roles.service';
import { UsersService } from '../../core/users.service';
import { FormField } from '../../core/models';

interface Opt { value: string; label: string; }

@Component({
  selector: 'app-flow-editor',
  imports: [FormsModule, RouterLink, NgTemplateOutlet],
  templateUrl: './flow-editor.html',
  styleUrl: './flow-editor.scss',
})
export class FlowEditor {
  private api = inject(FormsService);
  private rolesApi = inject(RolesService);
  private usersApi = inject(UsersService);
  private route = inject(ActivatedRoute);

  id!: number;
  nodes = signal<any[]>([]);
  fields = signal<FormField[]>([]);
  roleOptions = signal<Opt[]>([]);
  userOptions = signal<Opt[]>([]);

  loading = signal(true);
  saving = signal(false);
  saved = signal(false);
  error = signal<string | null>(null);

  readonly operators: Opt[] = [
    { value: '==', label: 'igual a' },
    { value: '!=', label: 'diferente de' },
    { value: '>', label: 'maior que' },
    { value: '>=', label: 'maior ou igual' },
    { value: '<', label: 'menor que' },
    { value: '<=', label: 'menor ou igual' },
    { value: 'contains', label: 'contém' },
    { value: 'empty', label: 'vazio' },
    { value: 'not_empty', label: 'preenchido' },
  ];

  constructor() {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.rolesApi.list().subscribe({ next: (r) => this.roleOptions.set(r.map((x) => ({ value: x.name, label: x.label }))) });
    this.usersApi.list().subscribe({ next: (u) => this.userOptions.set(u.map((x) => ({ value: String(x.id), label: x.name }))) });
    this.api.getFlow(this.id).subscribe({
      next: (f) => { this.nodes.set((f.nodes || []).map((n) => this.hydrate(n))); this.fields.set(f.fields); this.loading.set(false); },
      error: () => { this.error.set('Não foi possível carregar o fluxo.'); this.loading.set(false); },
    });
  }

  private hydrate(n: any): any {
    if (n.type === 'gateway') {
      n.condition = n.condition ?? { field: '', operator: '==', value: '' };
      n.then = (n.then || []).map((s: any) => this.hydrateStep(s));
      n.else = (n.else || []).map((s: any) => this.hydrateStep(s));
      return n;
    }
    return this.hydrateStep(n);
  }

  private hydrateStep(s: any): any {
    if (!Array.isArray(s.approvers) || s.approvers.length === 0) {
      s.approvers = [{ approver_type: s.approver_type || 'role', role: s.role || 'manager', user_id: s.user_id || null }];
    }
    s.mode = s.mode === 'any' ? 'any' : 'all';
    s.condition = s.condition ?? { field: '', operator: '==', value: '' };
    return s;
  }

  private newStep(): any {
    return { name: '', approvers: [{ approver_type: 'role', role: this.roleOptions()[0]?.value ?? 'manager', user_id: null }], mode: 'all', condition: { field: '', operator: '==', value: '' } };
  }

  private newGateway(): any {
    return { type: 'gateway', name: '', condition: { field: '', operator: '==', value: '' }, then: [], else: [] };
  }

  isGateway(n: any): boolean { return n?.type === 'gateway'; }

  addStepTop(): void { this.nodes.set([...this.nodes(), this.newStep()]); }
  addGatewayTop(): void { this.nodes.set([...this.nodes(), this.newGateway()]); }
  addBranchStep(list: any[]): void { list.push(this.newStep()); this.touch(); }

  removeNode(list: any[], i: number): void { list.splice(i, 1); this.touch(); }
  move(list: any[], i: number, delta: number): void {
    const j = i + delta;
    if (j < 0 || j >= list.length) return;
    [list[i], list[j]] = [list[j], list[i]];
    this.touch();
  }

  addApprover(step: any): void {
    step.approvers.push({ approver_type: 'role', role: this.roleOptions()[0]?.value ?? 'manager', user_id: null });
    this.touch();
  }
  removeApprover(step: any, i: number): void { step.approvers.splice(i, 1); this.touch(); }

  needsValue(cond: any): boolean {
    return cond && cond.operator !== 'empty' && cond.operator !== 'not_empty';
  }

  private touch(): void { this.nodes.set([...this.nodes()]); }

  save(): void {
    this.error.set(null);
    this.saved.set(false);
    this.saving.set(true);
    // limpa condicoes "sempre" (field vazio) em etapas antes de enviar
    const clean = JSON.parse(JSON.stringify(this.nodes()));
    const strip = (arr: any[]) => arr.forEach((n) => {
      if (n.type === 'gateway') { strip(n.then || []); strip(n.else || []); }
      else if (n.condition && !n.condition.field) { delete n.condition; }
    });
    strip(clean);
    this.api.saveFlow(this.id, clean).subscribe({
      next: (f) => { this.nodes.set((f.nodes || []).map((n) => this.hydrate(n))); this.saving.set(false); this.saved.set(true); },
      error: (e) => { this.saving.set(false); this.error.set(e?.error?.message ?? 'Não foi possível salvar.'); },
    });
  }
}
