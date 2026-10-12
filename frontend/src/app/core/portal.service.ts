import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from './api';

export interface PortalItem { title: string; slug: string; excerpt: string | null; publishedAt: string | null; }
export interface RenderedPage { title: string; slug: string; html: string; publishedAt: string | null; }
export interface PageSummary {
  id: number; title: string; slug: string; excerpt: string | null;
  status: string; publishedAt: string | null; updatedAt: string;
}
export interface PageEdit {
  id: number; title: string; slug: string; excerpt: string | null;
  content: string | null; status: string; publishedAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class PortalService {
  private http = inject(HttpClient);

  // portal publico
  listPortal(): Observable<PortalItem[]> {
    return this.http.get<PortalItem[]>(`${API_BASE}/portal`);
  }
  getPage(slug: string): Observable<RenderedPage> {
    return this.http.get<RenderedPage>(`${API_BASE}/portal/${slug}`);
  }

  // gestao
  list(): Observable<PageSummary[]> {
    return this.http.get<PageSummary[]>(`${API_BASE}/pages`);
  }
  get(id: number): Observable<PageEdit> {
    return this.http.get<PageEdit>(`${API_BASE}/pages/${id}`);
  }
  create(body: { title: string; excerpt: string | null; content: string; status: string }): Observable<PageEdit> {
    return this.http.post<PageEdit>(`${API_BASE}/pages`, body);
  }
  update(id: number, body: { title: string; excerpt: string | null; content: string; status: string }): Observable<PageEdit> {
    return this.http.put<PageEdit>(`${API_BASE}/pages/${id}`, body);
  }
  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/pages/${id}`);
  }
  preview(content: string): Observable<{ html: string }> {
    return this.http.post<{ html: string }>(`${API_BASE}/pages/preview`, { content });
  }
}
