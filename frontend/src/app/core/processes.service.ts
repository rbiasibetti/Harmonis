import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from './api';
import { FormField } from './models';


export interface InboxItem {
  taskId: number;
  instanceId: number;
  formName: string;
  stepName: string;
  requestedBy: string | null;
  createdAt: string;
}

export interface MineItem {
  instanceId: number;
  formName: string;
  status: string;
  currentStep: string | null;
  createdAt: string;
}

export interface TaskView {
  id: number;
  stepIndex: number;
  stepName: string;
  approverType: string;
  role: string | null;
  assignedUser: string | null;
  status: string;
  decidedBy: string | null;
  comment: string | null;
  decidedAt: string | null;
}

export interface ProcessDetail {
  instanceId: number;
  formId: number;
  formName: string;
  status: string;
  requestedBy: string | null;
  createdAt: string;
  fields: FormField[];
  data: Record<string, unknown>;
  timeline: TaskView[];
  actionableTaskId: number | null;
  fillFields: FormField[];
}

@Injectable({ providedIn: 'root' })
export class ProcessesService {
  private http = inject(HttpClient);

  inbox(): Observable<InboxItem[]> {
    return this.http.get<InboxItem[]>(`${API_BASE}/processes/inbox`);
  }

  mine(): Observable<MineItem[]> {
    return this.http.get<MineItem[]>(`${API_BASE}/processes/mine`);
  }

  pendingCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${API_BASE}/processes/pending-count`);
  }

  detail(instanceId: number): Observable<ProcessDetail> {
    return this.http.get<ProcessDetail>(`${API_BASE}/processes/${instanceId}`);
  }

  downloadPdf(instanceId: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/processes/${instanceId}/pdf`, { responseType: 'blob' });
  }

  decide(taskId: number, approved: boolean, comment: string, fieldValues?: Record<string, unknown>): Observable<void> {
    return this.http.post<void>(`${API_BASE}/processes/tasks/${taskId}/decide`, { approved, comment, fieldValues });
  }
}
