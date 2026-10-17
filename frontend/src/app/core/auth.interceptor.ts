import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { API_BASE } from './api';
import { AuthService } from './auth.service';

const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS'];
const NO_REFRESH = [`${API_BASE}/auth/login`, `${API_BASE}/auth/refresh`, `${API_BASE}/auth/logout`];

function readCookie(name: string): string | null {
  const m = document.cookie.split('; ').find((c) => c.startsWith(name + '='));
  return m ? decodeURIComponent(m.substring(name.length + 1)) : null;
}

/** Envia os cookies de sessao e o token CSRF (double-submit) em chamadas a API. */
function withSession(req: HttpRequest<unknown>): HttpRequest<unknown> {
  if (!req.url.startsWith(API_BASE)) return req;
  let headers = req.headers;
  if (!SAFE_METHODS.includes(req.method)) {
    const xsrf = readCookie('XSRF-TOKEN');
    if (xsrf) headers = headers.set('X-XSRF-TOKEN', xsrf);
  }
  return req.clone({ withCredentials: true, headers });
}

/**
 * Cookies HttpOnly + renovacao transparente: se a API responde 401 (access token expirado),
 * tenta uma renovacao pelo refresh token e repete a requisicao uma vez; se falhar, a sessao
 * acabou e o usuario volta ao login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(withSession(req)).pipe(
    catchError((err: unknown) => {
      const isApi = req.url.startsWith(API_BASE);
      if (!(err instanceof HttpErrorResponse) || err.status !== 401 || !isApi || NO_REFRESH.includes(req.url)) {
        return throwError(() => err);
      }
      return auth.refreshSession().pipe(
        switchMap((ok) => {
          if (ok) return next(withSession(req));
          auth.clearLocal();
          router.navigateByUrl('/login');
          return throwError(() => err);
        })
      );
    })
  );
};
