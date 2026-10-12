import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PortalService, RenderedPage } from '../../core/portal.service';

@Component({
  selector: 'app-portal-page',
  imports: [RouterLink, DatePipe],
  templateUrl: './portal-page.html',
  styleUrl: './portal.scss',
})
export class PortalPage {
  private api = inject(PortalService);
  private route = inject(ActivatedRoute);

  page = signal<RenderedPage | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);

  constructor() {
    this.route.paramMap.subscribe((p) => {
      const slug = p.get('slug')!;
      this.loading.set(true);
      this.api.getPage(slug).subscribe({
        next: (pg) => { this.page.set(pg); this.loading.set(false); },
        error: () => { this.error.set('Página não encontrada.'); this.loading.set(false); },
      });
    });
  }
}
