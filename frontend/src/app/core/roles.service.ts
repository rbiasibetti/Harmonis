import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from './api';
import { PermissionCatalog, RoleView, SaveRoleRequest } from './models';

@Injectable({ providedIn: 'root' })
export class RolesService {
  private http = inject(HttpClient);

  list(): Observable<RoleView[]> {
    return this.http.get<RoleView[]>(`${API_BASE}/roles`);
  }

  catalog(): Observable<PermissionCatalog> {
    return this.http.get<PermissionCatalog>(`${API_BASE}/permissions`);
  }

  create(req: SaveRoleRequest): Observable<RoleView> {
    return this.http.post<RoleView>(`${API_BASE}/roles`, req);
  }

  update(id: number, req: SaveRoleRequest): Observable<RoleView> {
    return this.http.put<RoleView>(`${API_BASE}/roles/${id}`, req);
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/roles/${id}`);
  }
}
