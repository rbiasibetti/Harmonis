import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import {
  AUTH_LABELS, AuthType, EVENT_LABELS, IntegrationView, IntegrationsService,
  LogDetail, NameValue, SaveIntegrationRequest, UsageRef,
} from '../../core/integrations.service';

interface HeaderRow { name: string; value: string; secret: boolean; valueSet: boolean; }

interface Model {
  name: string;
  description: string;
  url: string;
  method: string;
  timeoutSeconds: number;
  retries: number;
  type: AuthType;
  // basic / pré-autenticação
  username: string;
  password: string;
  passwordSet: boolean;
  // api key
  keyName: string;
  keyValue: string;
  keyValueSet: boolean;
  keyIn: 'header' | 'query';
  // bearer
  token: string;
  tokenSet: boolean;
  tokenPrefix: string;
  // pré-autenticação
  tokenUrl: string;
  tokenBodyFormat: 'json' | 'form';
  usernameField: string;
  passwordField: string;
  tokenExtra: NameValue[];
  tokenPath: string;
  expiresInPath: string;
  tokenTtlSeconds: number;
  // cabeçalhos / assinatura
  headers: HeaderRow[];
  signingSecret: string;
  signed: boolean;
  clearSigningSecret: boolean;
}

@Component({
  selector: 'app-integration-editor',
  imports: [FormsModule, RouterLink],
  templateUrl: './integration-editor.html',
  styleUrl: './integrations.scss',
})
export class IntegrationEditor {
  protected auth = inject(AuthService);
  private api = inject(IntegrationsService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  readonly authTypes: { value: AuthType; label: string }[] =
    (Object.keys(AUTH_LABELS) as AuthType[]).map((value) => ({ value, label: AUTH_LABELS[value] }));
  readonly eventLabels = EVENT_LABELS;

  id = signal<number | null>(null);
  view = signal<IntegrationView | null>(null);
  usage = signal<UsageRef[]>([]);
  loading = signal(false);
  saving = signal(false);
  saved = signal(false);
  error = signal<string | null>(null);

  testing = signal(false);
  testResult = signal<LogDetail | null>(null);

  m: Model = this.blank();

  constructor() {
    const raw = this.route.snapshot.paramMap.get('id');
    if (raw) {
      this.id.set(Number(raw));
      this.load();
    }
  }

  private blank(): Model {
    return {
      name: '', description: '', url: '', method: 'POST', timeoutSeconds: 15, retries: 2,
      type: 'none', username: '', password: '', passwordSet: false,
      keyName: 'X-Api-Key', keyValue: '', keyValueSet: false, keyIn: 'header',
      token: '', tokenSet: false, tokenPrefix: 'Bearer',
      tokenUrl: '', tokenBodyFormat: 'json', usernameField: 'username', passwordField: 'password',
      tokenExtra: [], tokenPath: 'access_token', expiresInPath: 'expires_in', tokenTtlSeconds: 3000,
      headers: [], signingSecret: '', signed: false, clearSigningSecret: false,
    };
  }

  private load(): void {
    this.loading.set(true);
    this.api.get(this.id()!).subscribe({
      next: (v) => { this.fill(v); this.loading.set(false); },
      error: () => { this.error.set('Não foi possível carregar a integração.'); this.loading.set(false); },
    });
    if (this.auth.can('integrations.view')) {
      this.api.usage(this.id()!).subscribe({ next: (u) => this.usage.set(u), error: () => {} });
    }
  }

  private fill(v: IntegrationView): void {
    this.view.set(v);
    const a = v.auth;
    this.m = {
      name: v.name, description: v.description ?? '', url: v.url, method: v.method,
      timeoutSeconds: v.timeoutSeconds, retries: v.retries,
      type: a.type, username: a.username ?? '', password: '', passwordSet: a.passwordSet,
      keyName: a.keyName ?? 'X-Api-Key', keyValue: '', keyValueSet: a.keyValueSet, keyIn: a.keyIn ?? 'header',
      token: '', tokenSet: a.tokenSet, tokenPrefix: a.tokenPrefix ?? 'Bearer',
      tokenUrl: a.tokenUrl ?? '', tokenBodyFormat: a.tokenBodyFormat ?? 'json',
      usernameField: a.usernameField ?? 'username', passwordField: a.passwordField ?? 'password',
      tokenExtra: (a.tokenExtra ?? []).map((x) => ({ ...x })),
      tokenPath: a.tokenPath ?? 'access_token', expiresInPath: a.expiresInPath ?? 'expires_in',
      tokenTtlSeconds: a.tokenTtlSeconds ?? 3000,
      headers: v.headers.map((h) => ({ name: h.name, value: h.value ?? '', secret: h.secret, valueSet: h.valueSet })),
      signingSecret: '', signed: v.signed, clearSigningSecret: false,
    };
  }

  // ----- listas -----
  addHeader(): void { this.m.headers.push({ name: '', value: '', secret: false, valueSet: false }); }
  removeHeader(i: number): void { this.m.headers.splice(i, 1); }
  addExtra(): void { this.m.tokenExtra.push({ name: '', value: '' }); }
  removeExtra(i: number): void { this.m.tokenExtra.splice(i, 1); }

  // ----- ações -----
  private request(): SaveIntegrationRequest {
    const m = this.m;
    const blank = (s: string) => (s && s.length ? s : null);
    return {
      name: m.name, description: blank(m.description), url: m.url, method: m.method,
      timeoutSeconds: m.timeoutSeconds, retries: m.retries,
      auth: {
        type: m.type,
        username: blank(m.username), password: blank(m.password),
        keyName: blank(m.keyName), keyValue: blank(m.keyValue), keyIn: m.keyIn,
        token: blank(m.token), tokenPrefix: blank(m.tokenPrefix),
        tokenUrl: blank(m.tokenUrl), tokenBodyFormat: m.tokenBodyFormat,
        usernameField: blank(m.usernameField), passwordField: blank(m.passwordField),
        tokenExtra: m.tokenExtra.filter((x) => x.name.trim()),
        tokenPath: blank(m.tokenPath), expiresInPath: blank(m.expiresInPath),
        tokenTtlSeconds: m.tokenTtlSeconds,
      },
      headers: m.headers.filter((h) => h.name.trim())
        .map((h) => ({ name: h.name.trim(), value: blank(h.value), secret: h.secret })),
      signingSecret: blank(m.signingSecret),
      clearSigningSecret: m.clearSigningSecret,
    };
  }

  save(): void {
    this.error.set(null);
    this.saved.set(false);
    this.saving.set(true);
    const req = this.request();
    const call = this.id() ? this.api.update(this.id()!, req) : this.api.create(req);
    call.subscribe({
      next: (v) => {
        this.saving.set(false);
        if (!this.id()) {
          this.router.navigate(['/integrations', v.id, 'edit']);
          return;
        }
        this.fill(v);
        this.saved.set(true);
      },
      error: (e) => { this.saving.set(false); this.error.set(e?.error?.message ?? 'Não foi possível salvar.'); },
    });
  }

  togglePublish(): void {
    const v = this.view();
    if (!v) return;
    this.error.set(null);
    const call = v.status === 'published' ? this.api.unpublish(v.id) : this.api.publish(v.id);
    call.subscribe({
      next: (nv) => this.view.set(nv),
      error: (e) => this.error.set(e?.error?.message ?? 'Não foi possível alterar a publicação.'),
    });
  }

  runTest(): void {
    if (!this.id()) return;
    this.testing.set(true);
    this.testResult.set(null);
    this.error.set(null);
    this.api.test(this.id()!).subscribe({
      next: (r) => { this.testResult.set(r); this.testing.set(false); },
      error: (e) => { this.testing.set(false); this.error.set(e?.error?.message ?? 'Falha ao executar o teste.'); },
    });
  }

  pretty(json: string | null): string {
    if (!json) return '';
    try { return JSON.stringify(JSON.parse(json), null, 2); } catch { return json; }
  }
}
