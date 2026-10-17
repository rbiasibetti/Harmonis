import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, of } from 'rxjs';
import { AuthService } from './auth.service';

/** Bloqueia rotas protegidas sem sessao; restaura a sessao pelos cookies ao recarregar. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated()) return of(true);
  return auth.restore().pipe(map((ok) => (ok ? true : router.createUrlTree(['/login']))));
};
