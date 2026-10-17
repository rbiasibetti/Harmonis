import { Component, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { FormsService } from '../../core/forms.service';
import { AuthService } from '../../core/auth.service';
import { EVENT_LABELS, HookRef, IntegrationOption, IntegrationsService } from '../../core/integrations.service';
import { RolesService } from '../../core/roles.service';
import { UsersService } from '../../core/users.service';
import { FormField } from '../../core/models';
import { TPipe } from '../../core/i18n/i18n';
import { tr } from '../../core/i18n/i18n';

interface Opt { value: string; label: string; }

@Component({
  selector: 'app-flow-editor',
  imports: [TPipe, FormsModule, RouterLink, NgTemplateOutlet, DragDropModule],
  templateUrl: './flow-editor.html',
  styleUrl: './flow-editor.scss',
})
export class FlowEditor {
  private api = inject(FormsService);
  private rolesApi = inject(RolesService);
  private usersApi = inject(UsersService);
  private integrationsApi = inject(IntegrationsService);
  private route = inject(ActivatedRoute);
  protected auth = inject(AuthService);

  id!: number;
  nodes = signal<any[]>([]);
  fields = signal<FormField[]>([]);
  hooks = signal<Record<string, HookRef[]>>(FlowEditor.emptyHooks());
  hookOptions = signal<IntegrationOption[]>([]);
  roleOptions = signal<Opt[]>([]);
  userOptions = signal<Opt[]>([]);

  loading = signal(true);
  saving = signal(false);
  saved = signal(false);
  error = signal<string | null>(null);
  zoom = signal(1);

  readonly hookEvents: { key: string; title: string; desc: string }[] = [
    { key: 'submitted', title: tr('Ao enviar'), desc: tr('Quando o solicitante envia o formulário.') },
    { key: 'approved', title: tr('Ao aprovar'), desc: tr('Quando todas as etapas são aprovadas.') },
    { key: 'rejected', title: tr('Ao reprovar'), desc: 'Quando uma etapa reprova o processo.' },
    { key: 'completed', title: tr('Ao concluir'), desc: tr('Ao fim do fluxo, aprovado ou reprovado (veja process.status).') },
  ];
  readonly eventLabels = EVENT_LABELS;

  private static emptyHooks(): Record<string, HookRef[]> {
    return { submitted: [], approved: [], rejected: [], completed: [] };
  }

  readonly operators: Opt[] = [
    { value: '==', label: tr('igual a') },
    { value: '!=', label: tr('diferente de') },
    { value: '>', label: tr('maior que') },
    { value: '>=', label: tr('maior ou igual') },
    { value: '<', label: tr('menor que') },
    { value: '<=', label: tr('menor ou igual') },
    { value: 'contains', label: tr('contém') },
    { value: 'empty', label: tr('vazio') },
    { value: 'not_empty', label: tr('preenchido') },
  ];

  constructor() {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.rolesApi.list().subscribe({ next: (r) => this.roleOptions.set(r.map((x) => ({ value: x.name, label: x.label }))) });
    this.usersApi.list().subscribe({ next: (u) => this.userOptions.set(u.map((x) => ({ value: String(x.id), label: x.name }))) });
    this.integrationsApi.options().subscribe({ next: (o) => this.hookOptions.set(o), error: () => {} });
    this.api.getFlow(this.id).subscribe({
      next: (f) => {
        this.nodes.set((f.nodes || []).map((n) => this.hydrate(n)));
        this.fields.set(f.fields);
        this.hooks.set({ ...FlowEditor.emptyHooks(), ...(f.hooks ?? {}) });
        this.loading.set(false);
      },
      error: () => { this.error.set(tr('Não foi possível carregar o fluxo.')); this.loading.set(false); },
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

  // ----- integrações (webhooks) por evento -----
  hooksOf(event: string): HookRef[] { return this.hooks()[event] ?? []; }

  availableHooks(event: string): IntegrationOption[] {
    const used = new Set(this.hooksOf(event).map((h) => h.id));
    return this.hookOptions().filter((o) => !used.has(o.id));
  }

  addHook(event: string, rawId: string): void {
    const id = Number(rawId);
    const opt = this.hookOptions().find((o) => o.id === id);
    if (!opt) return;
    this.hooks.set({ ...this.hooks(), [event]: [...this.hooksOf(event), { id: opt.id, name: opt.name, published: true }] });
  }

  removeHook(event: string, id: number): void {
    this.hooks.set({ ...this.hooks(), [event]: this.hooksOf(event).filter((h) => h.id !== id) });
  }

  private hooksPayload(): Record<string, number[]> {
    const out: Record<string, number[]> = {};
    for (const e of this.hookEvents) out[e.key] = this.hooksOf(e.key).map((h) => h.id);
    return out;
  }

  // ----- drag-and-drop -----
  drop(event: CdkDragDrop<any[]>, list: any[]): void {
    moveItemInArray(list, event.previousIndex, event.currentIndex);
    this.touch();
  }

  // ----- zoom -----
  zoomIn(): void { this.zoom.set(Math.min(1.5, Math.round((this.zoom() + 0.1) * 10) / 10)); }
  zoomOut(): void { this.zoom.set(Math.max(0.5, Math.round((this.zoom() - 0.1) * 10) / 10)); }
  zoomPct(): number { return Math.round(this.zoom() * 100); }

  // ----- mini-mapa -----
  outline(): { i: number; label: string; gateway: boolean }[] {
    return this.nodes().map((n, i) => ({
      i,
      label: n.name || (this.isGateway(n) ? tr('Decisão') : tr('Etapa')),
      gateway: this.isGateway(n),
    }));
  }

  scrollTo(i: number): void {
    document.getElementById('flow-node-' + i)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

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
    this.api.saveFlow(this.id, clean, this.hooksPayload()).subscribe({
      next: (f) => {
        this.nodes.set((f.nodes || []).map((n) => this.hydrate(n)));
        this.hooks.set({ ...FlowEditor.emptyHooks(), ...(f.hooks ?? {}) });
        this.saving.set(false);
        this.saved.set(true);
      },
      error: (e) => { this.saving.set(false); this.error.set(e?.error?.message ?? tr('Não foi possível salvar.')); },
    });
  }
}
