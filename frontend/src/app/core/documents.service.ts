import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from './api';

export interface FolderRef { id: number; name: string; }
export interface FolderView { id: number; name: string; createdAt: string; }
export interface DocumentView {
  id: number; name: string; description: string | null; currentVersion: number;
  metadata: Record<string, string>; createdAt: string; updatedAt: string;
}
export interface VersionView {
  id: number; version: number; originalName: string; mimeType: string | null;
  size: number; uploadedBy: string | null; createdAt: string;
}
export interface DocumentDetail {
  id: number; folderId: number | null; name: string; description: string | null;
  currentVersion: number; metadata: Record<string, string>; versions: VersionView[];
}
export interface BrowseView {
  current: FolderRef | null; breadcrumb: FolderRef[];
  folders: FolderView[]; documents: DocumentView[];
}
export interface SearchHit { document: DocumentView; folderId: number | null; folderName: string | null; }

@Injectable({ providedIn: 'root' })
export class DocumentsService {
  private http = inject(HttpClient);

  browse(folderId?: number | null): Observable<BrowseView> {
    let params = new HttpParams();
    if (folderId != null) params = params.set('folder', String(folderId));
    return this.http.get<BrowseView>(`${API_BASE}/documents`, { params });
  }

  search(q: string): Observable<SearchHit[]> {
    return this.http.get<SearchHit[]>(`${API_BASE}/documents/search`, { params: new HttpParams().set('q', q) });
  }

  detail(id: number): Observable<DocumentDetail> {
    return this.http.get<DocumentDetail>(`${API_BASE}/documents/${id}`);
  }

  createFolder(name: string, parentId: number | null): Observable<FolderView> {
    return this.http.post<FolderView>(`${API_BASE}/documents/folders`, { name, parentId });
  }

  renameFolder(id: number, name: string): Observable<FolderView> {
    return this.http.put<FolderView>(`${API_BASE}/documents/folders/${id}`, { name });
  }

  deleteFolder(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/documents/folders/${id}`);
  }

  upload(file: File, folderId: number | null, name?: string, description?: string): Observable<DocumentDetail> {
    const fd = new FormData();
    fd.append('file', file);
    if (folderId != null) fd.append('folderId', String(folderId));
    if (name) fd.append('name', name);
    if (description) fd.append('description', description);
    return this.http.post<DocumentDetail>(`${API_BASE}/documents`, fd);
  }

  addVersion(id: number, file: File): Observable<DocumentDetail> {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<DocumentDetail>(`${API_BASE}/documents/${id}/versions`, fd);
  }

  update(id: number, name: string, description: string | null, metadata: Record<string, string>): Observable<DocumentDetail> {
    return this.http.put<DocumentDetail>(`${API_BASE}/documents/${id}`, { name, description, metadata });
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/documents/${id}`);
  }

  downloadDoc(id: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/documents/${id}/download`, { responseType: 'blob' });
  }

  downloadVersion(versionId: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/documents/versions/${versionId}/download`, { responseType: 'blob' });
  }
}
