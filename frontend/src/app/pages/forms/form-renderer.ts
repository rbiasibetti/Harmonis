import { Component, Input, OnChanges, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { conditionPasses } from '../../core/condition';
import { AttachmentRef, FormField, LookupSelection, SignatureValue } from '../../core/models';
import { FormsService } from '../../core/forms.service';
import { downloadBlob } from '../../core/download';
import { TPipe } from '../../core/i18n/i18n';
import { SignatureCapture } from './signature-capture';
import { LookupSearch } from './lookup-search';
import { tr } from '../../core/i18n/i18n';

/**
 * Renderiza um formulario (layout responsivo em grid, blocos/secoes, todos os tipos
 * de campo, exibicao condicional). Usado no preenchimento e na previa do construtor.
 */
@Component({
  selector: 'app-form-renderer',
  imports: [TPipe, FormsModule, SignatureCapture, LookupSearch, DatePipe],
  templateUrl: './form-renderer.html',
  styleUrl: './form-renderer.scss',
})
export class FormRenderer implements OnChanges {
  private api = inject(FormsService);

  @Input() fields: FormField[] = [];
  @Input() values: Record<string, any> = {};
  @Input() errors: Record<string, string> = {};
  @Input() disabled = false;
  /** Necessário para enviar anexos; sem ele, campos de anexo ficam desabilitados. */
  @Input() formId: number | null = null;

  signingKey = signal<string | null>(null);
  lookupKey = signal<string | null>(null);
  private thumbs = signal<Record<number, string>>({});
  uploading = signal<Record<string, boolean>>({});
  uploadError = signal<Record<string, string>>({});

  ngOnChanges(): void {
    // Pré-carrega as miniaturas de assinaturas já existentes (ex.: visualização de resposta).
    for (const f of this.fields) {
      if (this.isSignature(f)) {
        const v = this.signatureValue(f);
        if (v) this.loadSignatureThumbPublic(v.signatureId);
      }
    }
  }

  private loadSignatureThumbPublic(id: number): void { this.loadSignatureThumb(id); }

  widthClass(f: FormField): string {
    return 'w-' + (f.width || 'full');
  }

  isSection(f: FormField): boolean { return f.type === 'section'; }
  isFile(f: FormField): boolean { return f.type === 'file' || f.type === 'multifile'; }
  isMultiFile(f: FormField): boolean { return f.type === 'multifile'; }
  isSignature(f: FormField): boolean { return f.type === 'signature'; }

  // ----- assinatura -----
  /** Estilo permitido do campo (drawn | selfie | both). */
  signatureStyle(f: FormField): 'drawn' | 'selfie' | 'both' {
    const d = f.display;
    return d === 'drawn' || d === 'selfie' ? d : 'both';
  }
  signatureValue(f: FormField): SignatureValue | null {
    const v = this.values[f.key!];
    return v && typeof v === 'object' && 'signatureId' in v ? (v as SignatureValue) : null;
  }
  signing(f: FormField): boolean { return this.signingKey() === f.key; }
  openSign(f: FormField): void {
    if (this.disabled || !this.formId) return;
    this.signingKey.set(f.key!);
  }
  onSigned(f: FormField, value: SignatureValue): void {
    this.values[f.key!] = value;
    this.signingKey.set(null);
    this.loadSignatureThumb(value.signatureId);
  }
  clearSignature(f: FormField): void {
    if (this.disabled) return;
    this.values[f.key!] = null;
  }
  signatureThumb(id: number): string | null { return this.thumbs()[id] ?? null; }
  private loadSignatureThumb(id: number): void {
    if (this.thumbs()[id]) return;
    this.api.signatureImage(id).subscribe({
      next: (blob) => this.thumbs.set({ ...this.thumbs(), [id]: URL.createObjectURL(blob) }),
      error: () => {},
    });
  }
  styleLabel(style: string): string { return style === 'selfie' ? tr('Selfie') : tr('Desenhada'); }
  geoLabel(v: SignatureValue): string {
    const g = v.geo;
    if (g.status === 'ok' && g.lat != null && g.lon != null) {
      return `${g.lat.toFixed(5)}, ${g.lon.toFixed(5)}`;
    }
    return g.status === 'denied' ? tr('permissão negada')
      : g.status === 'unavailable' ? tr('indisponível') : tr('não informada');
  }

  acceptAttr(f: FormField): string { return f.validation?.accept || ''; }

  // ----- anexos -----
  fileList(f: FormField): AttachmentRef[] {
    const v = this.values[f.key!];
    if (!v) return [];
    return Array.isArray(v) ? (v as AttachmentRef[]) : [v as AttachmentRef];
  }

  onFilePick(f: FormField, input: HTMLInputElement): void {
    const files = input.files;
    if (!files || files.length === 0) return;
    if (!this.formId) {
      this.setUploadError(f.key!, tr('Salve o formulário antes de anexar.'));
      return;
    }
    const picked = Array.from(files);
    input.value = ''; // permite reanexar o mesmo arquivo
    this.setUploadError(f.key!, '');
    this.setUploading(f.key!, true);
    let pending = picked.length;
    for (const file of picked) {
      this.api.uploadAttachment(this.formId, f.key!, file).subscribe({
        next: (ref) => {
          if (this.isMultiFile(f)) {
            const arr = this.fileList(f).slice();
            arr.push(ref);
            this.values[f.key!] = arr;
          } else {
            this.values[f.key!] = ref;
          }
          if (--pending === 0) this.setUploading(f.key!, false);
        },
        error: (e) => {
          this.setUploading(f.key!, false);
          this.setUploadError(f.key!, e?.error?.message ?? tr('Falha ao enviar o anexo.'));
        },
      });
      if (!this.isMultiFile(f)) break; // campo único: só o primeiro
    }
  }

  removeFile(f: FormField, ref: AttachmentRef): void {
    if (this.disabled) return;
    if (this.isMultiFile(f)) {
      this.values[f.key!] = this.fileList(f).filter((r) => r.id !== ref.id);
    } else {
      this.values[f.key!] = null;
    }
    // Remove o arquivo órfão do servidor (ainda não vinculado a uma resposta).
    this.api.deleteAttachment(ref.id).subscribe({ error: () => {} });
  }

  download(ref: AttachmentRef): void {
    this.api.downloadAttachment(ref.id).subscribe({
      next: (blob) => downloadBlob(blob, ref.name),
      error: () => this.setUploadError('', tr('Não foi possível baixar o anexo.')),
    });
  }

  humanSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + ' KB';
    return (bytes / 1024 / 1024).toFixed(1) + ' MB';
  }

  isUploading(key: string): boolean { return !!this.uploading()[key]; }
  fileError(key: string): string | null { return this.uploadError()[key] || null; }

  private setUploading(key: string, v: boolean): void { this.uploading.set({ ...this.uploading(), [key]: v }); }
  private setUploadError(key: string, v: string): void { this.uploadError.set({ ...this.uploadError(), [key]: v }); }

  // ----- lookup (modelo de dados) -----
  isLookup(f: FormField): boolean { return f.type === 'lookup'; }
  private lookupMultiple(f: FormField): boolean { return f.display === 'multiple'; }
  lookupSelections(f: FormField): LookupSelection[] {
    const v = this.values[f.key!];
    if (!v) return [];
    return Array.isArray(v) ? (v as LookupSelection[]) : [v as LookupSelection];
  }
  lookupText(f: FormField): string { return this.lookupSelections(f).map((s) => s.label).join('; '); }
  openLookup(f: FormField): void { if (!this.disabled && this.formId) this.lookupKey.set(f.key!); }
  onLookupPicked(f: FormField, sels: LookupSelection[]): void {
    this.values[f.key!] = this.lookupMultiple(f) ? sels : (sels[0] ?? null);
    this.lookupKey.set(null);
  }
  clearLookup(f: FormField): void { if (!this.disabled) this.values[f.key!] = this.lookupMultiple(f) ? [] : null; }

  /** Campo visivel conforme visibleWhen (secao sempre visivel). */
  isVisible(f: FormField): boolean {
    if (this.isSection(f)) return true;
    return conditionPasses(f.visibleWhen, this.values);
  }

  display(f: FormField): string {
    return f.display || 'combo';
  }

  err(key: string | null): string | null {
    return key ? (this.errors[key] ?? null) : null;
  }

  // ----- selecao multipla -----
  multiArray(key: string): string[] {
    const v = this.values[key];
    return Array.isArray(v) ? v : [];
  }
  isChecked(key: string, option: string): boolean {
    return this.multiArray(key).includes(option);
  }
  toggleMulti(key: string, option: string): void {
    if (this.disabled) return;
    const arr = [...this.multiArray(key)];
    const i = arr.indexOf(option);
    if (i >= 0) arr.splice(i, 1); else arr.push(option);
    this.values[key] = arr;
  }
}
