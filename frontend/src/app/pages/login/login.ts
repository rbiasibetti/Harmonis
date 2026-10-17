import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ThemeService } from '../../core/theme.service';
import { LANGS, TPipe, applyLanguage, currentLang, tr } from '../../core/i18n/i18n';

@Component({
  selector: 'app-login',
  imports: [TPipe, FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private auth = inject(AuthService);
  private router = inject(Router);
  protected theme = inject(ThemeService);

  tenant = 'acme';
  email = 'admin@acme.test';
  password = 'password';

  readonly langs = LANGS;
  lang: string = currentLang;

  changeLang(code: string): void { applyLanguage(code); }

  loading = signal(false);
  error = signal<string | null>(null);

  submit(): void {
    this.error.set(null);
    this.loading.set(true);
    this.auth.login({ tenant: this.tenant.trim(), email: this.email.trim(), password: this.password }).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigateByUrl('/');
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.status === 401 ? tr('Credenciais inválidas.') : tr('Não foi possível entrar. Verifique o servidor.'));
      },
    });
  }
}
