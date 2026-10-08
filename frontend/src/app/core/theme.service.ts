import { Injectable, signal } from '@angular/core';

const KEY = 'harmonis.theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly dark = signal<boolean>(this.initial());

  constructor() {
    this.apply(this.dark());
  }

  toggle(): void {
    const next = !this.dark();
    this.dark.set(next);
    this.apply(next);
    try { localStorage.setItem(KEY, next ? 'dark' : 'light'); } catch { /* ignore */ }
  }

  private apply(dark: boolean): void {
    document.documentElement.classList.toggle('dark', dark);
  }

  private initial(): boolean {
    try {
      const saved = localStorage.getItem(KEY);
      if (saved) return saved === 'dark';
    } catch { /* ignore */ }
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
  }
}
