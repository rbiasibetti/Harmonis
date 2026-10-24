import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from './api';
import { AttachmentRef, FieldType, FormDetail, FormSummary, SaveFormRequest, SignatureValue, SubmissionView } from './models';
import { HookRef } from './integrations.service';

export interface FlowView {
  nodes: any[];
  fields: import('./models').FormField[];
  hooks: Record<string, HookRef[]>;
}

@Injectable({ providedIn: 'root' })
export class FormsService {
  private http = inject(HttpClient);

  fieldTypes(): Observable<FieldType[]> {
    return this.http.get<FieldType[]>(`${API_BASE}/forms/field-types`);
  }

  list(): Observable<FormSummary[]> {
    return this.http.get<FormSummary[]>(`${API_BASE}/forms`);
  }

  get(id: number): Observable<FormDetail> {
    return this.http.get<FormDetail>(`${API_BASE}/forms/${id}`);
  }

  create(req: SaveFormRequest): Observable<FormDetail> {
    return this.http.post<FormDetail>(`${API_BASE}/forms`, req);
  }

  update(id: number, req: SaveFormRequest): Observable<FormDetail> {
    return this.http.put<FormDetail>(`${API_BASE}/forms/${id}`, req);
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/forms/${id}`);
  }

  submit(id: number, data: Record<string, unknown>): Observable<{ id: number }> {
    return this.http.post<{ id: number }>(`${API_BASE}/forms/${id}/submissions`, { data });
  }

  submissions(id: number): Observable<SubmissionView[]> {
    return this.http.get<SubmissionView[]>(`${API_BASE}/forms/${id}/submissions`);
  }

  downloadSubmissionPdf(formId: number, submissionId: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/forms/${formId}/submissions/${submissionId}/pdf`, { responseType: 'blob' });
  }

  downloadCsv(id: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/forms/${id}/submissions.csv`, { responseType: 'blob' });
  }

  uploadAttachment(formId: number, fieldKey: string, file: File): Observable<AttachmentRef> {
    const form = new FormData();
    form.append('fieldKey', fieldKey);
    form.append('file', file);
    return this.http.post<AttachmentRef>(`${API_BASE}/forms/${formId}/attachments`, form);
  }

  downloadAttachment(attachmentId: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/forms/attachments/${attachmentId}/download`, { responseType: 'blob' });
  }

  deleteAttachment(attachmentId: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/forms/attachments/${attachmentId}`);
  }

  /** Envia a imagem da assinatura + metadados do cliente; o servidor completa os confiáveis. */
  captureSignature(
    formId: number, fieldKey: string, style: 'drawn' | 'selfie', image: Blob,
    geo: { status: string; lat: number | null; lon: number | null; accuracy: number | null },
  ): Observable<SignatureValue> {
    const form = new FormData();
    form.append('fieldKey', fieldKey);
    form.append('style', style);
    form.append('image', image, style === 'selfie' ? 'selfie.jpg' : 'assinatura.png');
    form.append('geoStatus', geo.status);
    if (geo.lat != null) form.append('geoLat', String(geo.lat));
    if (geo.lon != null) form.append('geoLon', String(geo.lon));
    if (geo.accuracy != null) form.append('geoAccuracy', String(geo.accuracy));
    return this.http.post<SignatureValue>(`${API_BASE}/forms/${formId}/signatures`, form);
  }

  signatureImage(signatureId: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/forms/signatures/${signatureId}/image`, { responseType: 'blob' });
  }

  getFlow(id: number): Observable<FlowView> {
    return this.http.get<FlowView>(`${API_BASE}/forms/${id}/flow`);
  }

  /** hooks: evento -> ids de integrações (omitido = não altera os vínculos). */
  saveFlow(id: number, nodes: any[], hooks?: Record<string, number[]>): Observable<FlowView> {
    return this.http.put<FlowView>(`${API_BASE}/forms/${id}/flow`, { nodes, hooks });
  }
}
