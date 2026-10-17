import { Injectable, Pipe, PipeTransform } from '@angular/core';
import { EN } from './en';
import { ES } from './es';

export type Lang = 'pt-BR' | 'en' | 'es';

export const LANGS: { code: Lang; label: string }[] = [
  { code: 'pt-BR', label: 'Português (Brasil)' },
  { code: 'en', label: 'English' },
  { code: 'es', label: 'Español' },
];

const KEY = 'harmonis.lang';

function normalize(code: string | null | undefined): Lang | null {
  if (!code) return null;
  const c = code.toLowerCase();
  if (c.startsWith('pt')) return 'pt-BR';
  if (c.startsWith('en')) return 'en';
  if (c.startsWith('es')) return 'es';
  return null;
}

/** Idioma ativo: preferencia salva > idioma do navegador > pt-BR. */
export function detectLang(): Lang {
  try {
    const saved = normalize(localStorage.getItem(KEY));
    if (saved) return saved;
  } catch { /* ignore */ }
  return normalize(typeof navigator !== 'undefined' ? navigator.language : null) ?? 'pt-BR';
}

/**
 * Textos-fonte em portugues (chave = o proprio texto). pt-BR devolve a chave; en/es consultam o
 * dicionario e, se nao houver traducao, caem no portugues. A troca de idioma recarrega a pagina,
 * entao o idioma e constante durante a vida do app (pipe puro).
 */
@Injectable({ providedIn: 'root' })
export class I18n {
  readonly lang: Lang = detectLang();
  private readonly dict: Record<string, string> =
    this.lang === 'en' ? EN : this.lang === 'es' ? ES : {};

  t(key: string, params?: Record<string, string | number | null | undefined>): string {
    let s = this.dict[key] ?? key;
    if (params) {
      for (const [k, v] of Object.entries(params)) s = s.split('{' + k + '}').join(v == null ? '' : String(v));
    }
    return s;
  }

  /** Grava a preferencia e recarrega se o idioma mudou (aplica textos, datas e formatos). */
  apply(code: string | null | undefined): void {
    const next = normalize(code);
    if (!next) return;
    try { localStorage.setItem(KEY, next); } catch { /* ignore */ }
    if (next !== this.lang) {
      // Na tela de login volta para a raiz (ja autenticado); nas demais apenas recarrega.
      if (location.pathname.startsWith('/login')) location.replace('/');
      else location.reload();
    }
  }
}

const instance = new I18n();

/** Traduz fora de templates (mensagens, rotulos de constantes). */
export function tr(key: string, params?: Record<string, string | number | null | undefined>): string {
  return instance.t(key, params);
}

/** Idioma ativo (pt-BR | en | es). */
export const currentLang: Lang = instance.lang;

@Pipe({ name: 't' })
export class TPipe implements PipeTransform {
  transform(key: string | null | undefined, params?: Record<string, string | number | null | undefined>): string {
    return key == null || key === '' ? '' : instance.t(key, params);
  }
}

/** Aplica o idioma do usuario (grava a preferencia e recarrega se mudou). */
export function applyLanguage(code: string | null | undefined): void {
  instance.apply(code);
}
