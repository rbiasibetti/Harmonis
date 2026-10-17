import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import {
  BrowseView, DocumentDetail, DocumentsService, SearchHit,
} from '../../core/documents.service';
import { TPipe } from '../../core/i18n/i18n';
import { tr } from '../../core/i18n/i18n';

interface MetaRow { key: string; value: string; }

@Component({
  selector: 'app-documents',
  imports: [TPipe, FormsModule, DatePipe, RouterLink],
  templateUrl: './documents.html',
  styleUrl: './documents.scss',
})
export class Documents {
  protected auth = inject(AuthService);
  private api = inject(DocumentsService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  folderId = signal<number | null>(null);
  view = signal<BrowseView | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);

  // busca
  searchTerm = '';
  searchResults = signal<SearchHit[] | null>(null);

  // criar pasta
  showFolderModal = signal(false);
  folderName = '';

  // upload
  uploading = signal(false);

  // detalhe
  detail = signal<DocumentDetail | null>(null);
  metaRows = signal<MetaRow[]>([]);
  savingDetail = signal(false);

  constructor() {
    this.route.paramMap.subscribe((p) => {
      const id = p.get('id');
      this.folderId.set(id ? Number(id) : null);
      this.searchResults.set(null);
      this.load();
    });
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.browse(this.folderId()).subscribe({
      next: (v) => { this.view.set(v); this.loading.set(false); },
      error: () => { this.error.set(tr('Não foi possível carregar os documentos.')); this.loading.set(false); },
    });
  }

  openFolder(id: number): void { this.router.navigate(['/documents/folder', id]); }

  // ----- busca -----
  runSearch(): void {
    const q = this.searchTerm.trim();
    if (!q) { this.searchResults.set(null); return; }
    this.api.search(q).subscribe({ next: (r) => this.searchResults.set(r) });
  }
  clearSearch(): void { this.searchTerm = ''; this.searchResults.set(null); }

  // ----- pastas -----
  openFolderModal(): void { this.folderName = ''; this.showFolderModal.set(true); }
  createFolder(): void {
    if (!this.folderName.trim()) return;
    this.api.createFolder(this.folderName.trim(), this.folderId()).subscribe({
      next: () => { this.showFolderModal.set(false); this.load(); },
      error: (e) => alert(e?.error?.message ?? tr('Não foi possível criar a pasta.')),
    });
  }
  renameFolder(id: number, currentName: string): void {
    const name = prompt(tr('Novo nome da pasta:'), currentName);
    if (!name || !name.trim()) return;
    this.api.renameFolder(id, name.trim()).subscribe({ next: () => this.load() });
  }
  deleteFolder(id: number, name: string): void {
    if (!confirm(tr('Excluir a pasta "{name}" e todo o conteúdo?', { name }))) return;
    this.api.deleteFolder(id).subscribe({ next: () => this.load(), error: (e) => alert(e?.error?.message ?? tr('Erro.')) });
  }

  // ----- upload -----
  onUpload(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.uploading.set(true);
    this.api.upload(file, this.folderId()).subscribe({
      next: () => { this.uploading.set(false); input.value = ''; this.load(); },
      error: (e) => { this.uploading.set(false); input.value = ''; alert(e?.error?.message ?? tr('Falha no upload.')); },
    });
  }

  // ----- detalhe -----
  open(id: number): void {
    this.api.detail(id).subscribe({ next: (d) => { this.detail.set(d); this.syncMeta(d); } });
  }
  private syncMeta(d: DocumentDetail): void {
    this.metaRows.set(Object.entries(d.metadata || {}).map(([key, value]) => ({ key, value })));
  }
  addMeta(): void { this.metaRows.set([...this.metaRows(), { key: '', value: '' }]); }
  removeMeta(i: number): void { const a = [...this.metaRows()]; a.splice(i, 1); this.metaRows.set(a); }
  closeDetail(): void { this.detail.set(null); }

  saveDetail(): void {
    const d = this.detail();
    if (!d) return;
    this.savingDetail.set(true);
    const meta: Record<string, string> = {};
    this.metaRows().forEach((r) => { if (r.key.trim()) meta[r.key.trim()] = r.value; });
    this.api.update(d.id, d.name, d.description, meta).subscribe({
      next: (upd) => { this.savingDetail.set(false); this.detail.set(upd); this.syncMeta(upd); this.load(); },
      error: (e) => { this.savingDetail.set(false); alert(e?.error?.message ?? tr('Não foi possível salvar.')); },
    });
  }

  onAddVersion(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    const d = this.detail();
    if (!file || !d) return;
    this.api.addVersion(d.id, file).subscribe({
      next: (upd) => { input.value = ''; this.detail.set(upd); this.syncMeta(upd); this.load(); },
      error: (e) => { input.value = ''; alert(e?.error?.message ?? tr('Falha ao enviar versão.')); },
    });
  }

  deleteDocument(): void {
    const d = this.detail();
    if (!d || !confirm(tr('Excluir o documento "{name}" e todas as versões?', { name: d.name }))) return;
    this.api.remove(d.id).subscribe({ next: () => { this.closeDetail(); this.load(); } });
  }

  // ----- downloads -----
  downloadDoc(id: number, filename: string): void {
    this.api.downloadDoc(id).subscribe({ next: (b) => this.saveBlob(b, filename) });
  }
  downloadVersion(versionId: number, filename: string): void {
    this.api.downloadVersion(versionId).subscribe({ next: (b) => this.saveBlob(b, filename) });
  }
  private saveBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = filename; a.click();
    URL.revokeObjectURL(url);
  }

  metaKeys(m: Record<string, string>): string[] { return Object.keys(m || {}); }
  humanSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1024 / 1024).toFixed(1) + ' MB';
  }
}
