import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from './api';
import { CreateUserRequest, CreatedUser, UpdateUserRequest, UserView } from './models';

@Injectable({ providedIn: 'root' })
export class UsersService {
  private http = inject(HttpClient);

  list(query?: string): Observable<UserView[]> {
    let params = new HttpParams();
    if (query) params = params.set('q', query);
    return this.http.get<UserView[]>(`${API_BASE}/users`, { params });
  }

  create(req: CreateUserRequest): Observable<CreatedUser> {
    return this.http.post<CreatedUser>(`${API_BASE}/users`, req);
  }

  update(id: number, req: UpdateUserRequest): Observable<UserView> {
    return this.http.put<UserView>(`${API_BASE}/users/${id}`, req);
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/users/${id}`);
  }
}
