import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { PageSummary, PortalService } from '../../core/portal.service';
import { TPipe } from '../../core/i18n/i18n';
import { tr } from '../../core/i18n/i18n';

@Component({
  selector: 'app-pages-admin',
  imports: [TPipe, RouterLink, DatePipe],
  templateUrl: './pages-admin.html',
  styleUrl: './portal.scss',
})
export class PagesAdmin {
  protected auth = inject(AuthService);
  private api = inject(PortalService);

  pages = signal<PageSummary[]>([]);
  loading = signal(true);

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: (p) => { this.pages.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  remove(p: PageSummary): void {
    if (!confirm(tr('Excluir a página "{title}"?', { title: p.title }))) return;
    this.api.remove(p.id).subscribe({ next: () => this.load() });
  }
}
