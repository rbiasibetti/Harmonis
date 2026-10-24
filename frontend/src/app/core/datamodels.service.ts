import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from './api';

export type ColumnType = 'text' | 'number' | 'date';
export type SourceType = 'static' | 'query';

export interface ColumnSpec { key: string; label: string; type: ColumnType; }
export interface FilterSpec { key: string; label: string; type: ColumnType; }

export interface DataModelSummary {
  id: number;
  name: string;
  description: string | null;
  sourceType: SourceType;
  status: 'draft' | 'published';
  columns: number;
  rows: number; // -1 quando source=query
}

export interface DataModelView {
  id: number;
  name: string;
  description: string | null;
  sourceType: SourceType;
  querySql: string | null;
  columns: ColumnSpec[];
  filters: FilterSpec[];
  labelKey: string | null;
  status: 'draft' | 'published';
  rows: number;
}

export interface DataModelOption {
  id: number;
  name: string;
  columns: ColumnSpec[];
  filters: FilterSpec[];
  labelKey: string | null;
}

export interface SaveDataModelRequest {
  name: string;
  description: string | null;
  sourceType: SourceType;
  querySql: string | null;
  columns: ColumnSpec[];
  filters: FilterSpec[];
  labelKey: string | null;
}

export interface QueryResult {
  rows: Record<string, unknown>[];
  page: number;
  size: number;
  total: number;
  capped: boolean;
}

@Injectable({ providedIn: 'root' })
export class DataModelsService {
  private http = inject(HttpClient);

  list(): Observable<DataModelSummary[]> { return this.http.get<DataModelSummary[]>(`${API_BASE}/datamodels`); }
  get(id: number): Observable<DataModelView> { return this.http.get<DataModelView>(`${API_BASE}/datamodels/${id}`); }
  create(req: SaveDataModelRequest): Observable<DataModelView> { return this.http.post<DataModelView>(`${API_BASE}/datamodels`, req); }
  update(id: number, req: SaveDataModelRequest): Observable<DataModelView> { return this.http.put<DataModelView>(`${API_BASE}/datamodels/${id}`, req); }
  remove(id: number): Observable<void> { return this.http.delete<void>(`${API_BASE}/datamodels/${id}`); }
  publish(id: number): Observable<DataModelView> { return this.http.post<DataModelView>(`${API_BASE}/datamodels/${id}/publish`, null); }
  unpublish(id: number): Observable<DataModelView> { return this.http.post<DataModelView>(`${API_BASE}/datamodels/${id}/unpublish`, null); }

  rows(id: number): Observable<Record<string, unknown>[]> { return this.http.get<Record<string, unknown>[]>(`${API_BASE}/datamodels/${id}/rows`); }
  saveRows(id: number, rows: Record<string, unknown>[]): Observable<void> { return this.http.put<void>(`${API_BASE}/datamodels/${id}/rows`, { rows }); }
  importCsv(id: number, file: File): Observable<{ imported: number }> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<{ imported: number }>(`${API_BASE}/datamodels/${id}/import`, form);
  }

  options(): Observable<DataModelOption[]> { return this.http.get<DataModelOption[]>(`${API_BASE}/datamodels/options`); }
  option(id: number): Observable<DataModelOption> { return this.http.get<DataModelOption>(`${API_BASE}/datamodels/${id}/option`); }
  query(id: number, filters: Record<string, unknown>, page = 0, size = 10): Observable<QueryResult> {
    return this.http.post<QueryResult>(`${API_BASE}/datamodels/${id}/query`, { filters, page, size });
  }
}
