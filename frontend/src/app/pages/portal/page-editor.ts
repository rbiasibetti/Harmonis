import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { PortalService } from '../../core/portal.service';
import { TPipe } from '../../core/i18n/i18n';
import { tr } from '../../core/i18n/i18n';

@Component({
  selector: 'app-page-editor',
  imports: [TPipe, FormsModule, RouterLink],
  templateUrl: './page-editor.html',
  styleUrl: './portal.scss',
})
export class PageEditor {
  private api = inject(PortalService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  id = signal<number | null>(null);
  title = signal('');
  excerpt = signal('');
  content = signal('');
  status = signal('draft');

  tab = signal<'edit' | 'preview'>('edit');
  previewHtml = signal('');
  loading = signal(false);
  saving = signal(false);
  saved = signal(false);
  error = signal<string | null>(null);

  constructor() {
    const param = this.route.snapshot.paramMap.get('id');
    if (param) {
      this.id.set(Number(param));
      this.loading.set(true);
      this.api.get(Number(param)).subscribe({
        next: (p) => {
          this.title.set(p.title);
          this.excerpt.set(p.excerpt ?? '');
          this.content.set(p.content ?? '');
          this.status.set(p.status);
          this.loading.set(false);
        },
        error: () => { this.error.set(tr('Não foi possível carregar a página.')); this.loading.set(false); },
      });
    }
  }

  showPreview(): void {
    this.tab.set('preview');
    this.api.preview(this.content()).subscribe({ next: (r) => this.previewHtml.set(r.html) });
  }

  save(): void {
    this.error.set(null);
    this.saved.set(false);
    this.saving.set(true);
    const body = {
      title: this.title(),
      excerpt: this.excerpt() || null,
      content: this.content(),
      status: this.status(),
    };
    const id = this.id();
    if (id) {
      this.api.update(id, body).subscribe({
        next: () => { this.saving.set(false); this.saved.set(true); },
        error: (e) => this.fail(e),
      });
    } else {
      this.api.create(body).subscribe({
        next: (p) => { this.saving.set(false); this.router.navigate(['/pages', p.id, 'edit']); },
        error: (e) => this.fail(e),
      });
    }
  }

  private fail(e: any): void {
    this.saving.set(false);
    this.error.set(e?.error?.message ?? tr('Não foi possível salvar.'));
  }
}
