import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { PortalItem, PortalService } from '../../core/portal.service';
import { TPipe } from '../../core/i18n/i18n';

@Component({
  selector: 'app-portal',
  imports: [TPipe, RouterLink, DatePipe],
  templateUrl: './portal.html',
  styleUrl: './portal.scss',
})
export class Portal {
  protected auth = inject(AuthService);
  private api = inject(PortalService);

  items = signal<PortalItem[]>([]);
  loading = signal(true);

  constructor() {
    this.api.listPortal().subscribe({
      next: (i) => { this.items.set(i); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}
