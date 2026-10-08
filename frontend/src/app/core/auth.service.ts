import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { API_BASE } from './api';
import { AuthUser, LoginRequest, LoginResponse } from './models';

const TOKEN_KEY = 'harmonis.token';
const USER_KEY = 'harmonis.user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);

  private readonly _token = signal<string | null>(this.readToken());
  private readonly _user = signal<AuthUser | null>(this.readUser());

  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._token() !== null);

  login(req: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${API_BASE}/auth/login`, req).pipe(
      tap((res) => this.persist(res.token, res.user))
    );
  }

  /** Revalida o usuario pelo token atual (ex.: apos reload). */
  refreshMe(): Observable<AuthUser> {
    return this.http.get<AuthUser>(`${API_BASE}/me`).pipe(
      tap((user) => {
        this._user.set(user);
        localStorage.setItem(USER_KEY, JSON.stringify(user));
      })
    );
  }

  logout(): void {
    this._token.set(null);
    this._user.set(null);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  token(): string | null {
    return this._token();
  }

  can(permission: string): boolean {
    const u = this._user();
    return !!u && (u.owner || u.permissions.includes(permission));
  }

  private persist(token: string, user: AuthUser): void {
    this._token.set(token);
    this._user.set(user);
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  }

  private readToken(): string | null {
    try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
  }

  private readUser(): AuthUser | null {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? (JSON.parse(raw) as AuthUser) : null;
    } catch {
      return null;
    }
  }
}
