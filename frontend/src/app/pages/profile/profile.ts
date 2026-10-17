import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { LANGS, TPipe, tr } from '../../core/i18n/i18n';

/** Perfil do proprio usuario: dados de leitura e idioma preferido da interface. */
@Component({
  selector: 'app-profile',
  imports: [FormsModule, TPipe],
  templateUrl: './profile.html',
})
export class Profile {
  protected auth = inject(AuthService);
  readonly langs = LANGS;

  language = this.auth.user()?.language ?? 'pt-BR';
  saving = signal(false);
  saved = signal(false);
  error = signal<string | null>(null);

  save(): void {
    this.saving.set(true);
    this.saved.set(false);
    this.error.set(null);
    this.auth.setLanguage(this.language).subscribe({
      next: () => { this.saving.set(false); this.saved.set(true); },
      error: (e) => { this.saving.set(false); this.error.set(e?.error?.message ?? tr('Não foi possível salvar.')); },
    });
  }
}
