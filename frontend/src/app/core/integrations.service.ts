import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from './api';

export interface NameValue { name: string; value: string; }

export type AuthType = 'none' | 'basic' | 'apikey' | 'bearer' | 'bearer_preauth' | 'headers';

/** Autenticação enviada ao salvar. Segredos vazios/nulos = manter o valor atual. */
export interface AuthSpec {
  type: AuthType;
  username?: string | null;
  password?: string | null;
  keyName?: string | null;
  keyValue?: string | null;
  keyIn?: 'header' | 'query' | null;
  token?: string | null;
  tokenPrefix?: string | null;
  tokenUrl?: string | null;
  tokenBodyFormat?: 'json' | 'form' | null;
  usernameField?: string | null;
  passwordField?: string | null;
  tokenExtra?: NameValue[] | null;
  tokenPath?: string | null;
  expiresInPath?: string | null;
  tokenTtlSeconds?: number | null;
}

/** Autenticação devolvida: segredos nunca vêm, só o indicador "definido". */
export interface AuthView {
  type: AuthType;
  username: string | null;
  passwordSet: boolean;
  keyName: string | null;
  keyValueSet: boolean;
  keyIn: 'header' | 'query' | null;
  tokenSet: boolean;
  tokenPrefix: string | null;
  tokenUrl: string | null;
  tokenBodyFormat: 'json' | 'form' | null;
  usernameField: string | null;
  passwordField: string | null;
  tokenExtra: NameValue[] | null;
  tokenPath: string | null;
  expiresInPath: string | null;
  tokenTtlSeconds: number | null;
}

export interface HeaderSpec { name: string; value: string | null; secret: boolean; }
export interface HeaderView { name: string; value: string | null; secret: boolean; valueSet: boolean; }

export interface SaveIntegrationRequest {
  name: string;
  description: string | null;
  url: string;
  method: string;
  auth: AuthSpec;
  headers: HeaderSpec[];
  signingSecret: string | null;
  clearSigningSecret: boolean;
  timeoutSeconds: number;
  retries: number;
}

export interface IntegrationView {
  id: number;
  name: string;
  description: string | null;
  url: string;
  method: string;
  auth: AuthView;
  headers: HeaderView[];
  signed: boolean;
  timeoutSeconds: number;
  retries: number;
  status: 'draft' | 'published';
  publishedAt: string | null;
  usageCount: number;
  updatedAt: string;
}

export interface IntegrationSummary {
  id: number;
  name: string;
  description: string | null;
  url: string;
  method: string;
  authType: AuthType;
  status: 'draft' | 'published';
  usageCount: number;
  lastStatus: LogStatus | null;
  lastAt: string | null;
}

export interface IntegrationOption { id: number; name: string; description: string | null; }
export interface UsageRef { formId: number; formName: string; event: string; }
export interface HookRef { id: number; name: string; published: boolean; }

export type LogStatus = 'success' | 'error' | 'skipped';

export interface LogRow {
  id: number;
  deliveryId: string;
  attempt: number;
  integrationId: number | null;
  integrationName: string;
  formId: number | null;
  formName: string | null;
  submissionId: number | null;
  event: string;
  status: LogStatus;
  httpStatus: number | null;
  durationMs: number | null;
  errorMessage: string | null;
  willRetry: boolean;
  manual: boolean;
  createdAt: string;
}

export interface LogDetail extends LogRow {
  method: string | null;
  requestUrl: string | null;
  requestBody: string | null;
  responseBody: string | null;
}

export interface LogPage { items: LogRow[]; total: number; page: number; size: number; }
export interface LogStats { success24h: number; error24h: number; skipped24h: number; }

export interface LogFilter {
  status?: string;
  event?: string;
  integrationId?: number | null;
  from?: string | null;
  q?: string;
  page?: number;
  size?: number;
}

export const EVENT_LABELS: Record<string, string | undefined> = {
  submitted: 'Formulário enviado',
  approved: 'Processo aprovado',
  rejected: 'Processo reprovado',
  test: 'Teste',
};

export const AUTH_LABELS: Record<string, string> = {
  none: 'Sem autenticação',
  basic: 'Usuário e senha (Basic)',
  apikey: 'API Key',
  bearer: 'Bearer token',
  bearer_preauth: 'Bearer com pré-autenticação',
  headers: 'Cabeçalhos customizados',
};

@Injectable({ providedIn: 'root' })
export class IntegrationsService {
  private http = inject(HttpClient);

  list(): Observable<IntegrationSummary[]> {
    return this.http.get<IntegrationSummary[]>(`${API_BASE}/integrations`);
  }

  options(): Observable<IntegrationOption[]> {
    return this.http.get<IntegrationOption[]>(`${API_BASE}/integrations/options`);
  }

  get(id: number): Observable<IntegrationView> {
    return this.http.get<IntegrationView>(`${API_BASE}/integrations/${id}`);
  }

  usage(id: number): Observable<UsageRef[]> {
    return this.http.get<UsageRef[]>(`${API_BASE}/integrations/${id}/usage`);
  }

  create(req: SaveIntegrationRequest): Observable<IntegrationView> {
    return this.http.post<IntegrationView>(`${API_BASE}/integrations`, req);
  }

  update(id: number, req: SaveIntegrationRequest): Observable<IntegrationView> {
    return this.http.put<IntegrationView>(`${API_BASE}/integrations/${id}`, req);
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/integrations/${id}`);
  }

  publish(id: number): Observable<IntegrationView> {
    return this.http.post<IntegrationView>(`${API_BASE}/integrations/${id}/publish`, {});
  }

  unpublish(id: number): Observable<IntegrationView> {
    return this.http.post<IntegrationView>(`${API_BASE}/integrations/${id}/unpublish`, {});
  }

  test(id: number): Observable<LogDetail> {
    return this.http.post<LogDetail>(`${API_BASE}/integrations/${id}/test`, {});
  }

  logs(f: LogFilter): Observable<LogPage> {
    let p = new HttpParams();
    if (f.status) p = p.set('status', f.status);
    if (f.event) p = p.set('event', f.event);
    if (f.integrationId) p = p.set('integrationId', f.integrationId);
    if (f.from) p = p.set('from', f.from);
    if (f.q) p = p.set('q', f.q);
    p = p.set('page', f.page ?? 0).set('size', f.size ?? 25);
    return this.http.get<LogPage>(`${API_BASE}/integration-logs`, { params: p });
  }

  logDetail(id: number): Observable<LogDetail> {
    return this.http.get<LogDetail>(`${API_BASE}/integration-logs/${id}`);
  }

  logStats(): Observable<LogStats> {
    return this.http.get<LogStats>(`${API_BASE}/integration-logs/stats`);
  }

  resend(logId: number): Observable<LogDetail> {
    return this.http.post<LogDetail>(`${API_BASE}/integration-logs/${logId}/resend`, {});
  }
}
