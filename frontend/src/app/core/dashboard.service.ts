import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from './api';
import { InboxItem, MineItem } from './processes.service';

export interface Metrics {
  pendingApprovals: number | null;
  activeProcesses: number | null;
  publishedForms: number | null;
  documents: number | null;
  publishedPages: number | null;
  activeUsers: number | null;
}

export interface RecentSubmission {
  formName: string;
  submittedBy: string | null;
  createdAt: string;
}

export interface DashboardView {
  metrics: Metrics;
  pendingApprovals: InboxItem[];
  myRequests: MineItem[];
  recentSubmissions: RecentSubmission[];
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private http = inject(HttpClient);

  get(): Observable<DashboardView> {
    return this.http.get<DashboardView>(`${API_BASE}/dashboard`);
  }
}
