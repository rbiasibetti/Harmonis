import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE } from './api';

export interface NotificationView {
  id: number;
  type: string;
  title: string;
  message: string | null;
  url: string | null;
  instanceId: number | null;
  readAt: string | null;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private http = inject(HttpClient);

  items = signal<NotificationView[]>([]);
  unread = signal(0);

  refresh(): void {
    this.http.get<{ items: NotificationView[]; unread: number }>(`${API_BASE}/notifications`).subscribe({
      next: (r) => { this.items.set(r.items); this.unread.set(r.unread); },
      error: () => { /* silencioso */ },
    });
  }

  markRead(id: number): void {
    this.http.post<void>(`${API_BASE}/notifications/${id}/read`, {}).subscribe({ next: () => this.refresh() });
  }

  markAllRead(): void {
    this.http.post<void>(`${API_BASE}/notifications/read-all`, {}).subscribe({ next: () => this.refresh() });
  }
}
