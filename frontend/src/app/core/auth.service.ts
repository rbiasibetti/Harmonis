import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, finalize, map, of, shareReplay, tap } from 'rxjs';
import { API_BASE } from './api';
import { AuthUser, LoginRequest, SessionResponse } from './models';
import { applyLanguage } from './i18n/i18n';

/**
 * Sessao baseada em cookies HttpOnly (access + refresh), emitidos e renovados pelo servidor.
 * Nenhum token fica acessivel ao JavaScript nem em localStorage; aqui so existe o perfil do
 * usuario, em memoria. Ao recarregar a pagina a sessao e restaurada por /me (com renovacao
 * automatica via refresh token, se o access token tiver expirado).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);

  private readonly _user = signal<AuthUser | null>(null);
  private refreshing$: Observable<boolean> | null = null;

  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null);

  constructor() {
    // Remove restos da versao anterior, que guardava o JWT no localStorage.
    try {
      localStorage.removeItem('harmonis.token');
      localStorage.removeItem('harmonis.user');
    } catch { /* ignore */ }
  }

  login(req: LoginRequest): Observable<SessionResponse> {
    return this.http.post<SessionResponse>(`${API_BASE}/auth/login`, req).pipe(
      tap((res) => this.setUser(res.user))
    );
  }

  /** Restaura a sessao a partir dos cookies (reload/novo acesso). true = autenticado. */
  restore(): Observable<boolean> {
    return this.http.get<AuthUser>(`${API_BASE}/me`).pipe(
      tap((user) => this.setUser(user)),
      map(() => true),
      catchError(() => of(false))
    );
  }

  /** Atualiza o perfil/permissoes do usuario logado. */
  refreshMe(): Observable<AuthUser> {
    return this.http.get<AuthUser>(`${API_BASE}/me`).pipe(tap((user) => this.setUser(user)));
  }

  /**
   * Renova a sessao pelo refresh token (cookie). Requisicoes simultaneas compartilham uma
   * unica chamada (evita rotacoes concorrentes). true = renovou.
   */
  refreshSession(): Observable<boolean> {
    if (!this.refreshing$) {
      this.refreshing$ = this.http.post<SessionResponse>(`${API_BASE}/auth/refresh`, null).pipe(
        tap((res) => this.setUser(res.user)),
        map(() => true),
        catchError(() => of(false)),
        finalize(() => (this.refreshing$ = null)),
        shareReplay(1)
      );
    }
    return this.refreshing$;
  }

  /** Encerra a sessao no servidor (revoga o refresh token e limpa os cookies). */
  logout(): Observable<void> {
    return this.http.post<void>(`${API_BASE}/auth/logout`, null).pipe(
      catchError(() => of(void 0)),
      tap(() => this.clearLocal())
    );
  }

  /** Atualiza o idioma preferido no servidor e aplica na interface (recarrega se mudou). */
  setLanguage(language: string): Observable<AuthUser> {
    return this.http.put<AuthUser>(`${API_BASE}/me/language`, { language }).pipe(
      tap((user) => this.setUser(user))
    );
  }

  private setUser(user: AuthUser): void {
    this._user.set(user);
    applyLanguage(user.language);
  }

  /** Descarta o estado local (sessao expirada ou logout). */
  clearLocal(): void {
    this._user.set(null);
  }

  can(permission: string): boolean {
    const u = this._user();
    return !!u && (u.owner || u.permissions.includes(permission));
  }
}
