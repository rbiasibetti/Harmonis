import {
  AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild, inject, signal,
} from '@angular/core';
import { FormsService } from '../../core/forms.service';
import { SignatureValue } from '../../core/models';
import { TPipe, tr } from '../../core/i18n/i18n';

type Mode = 'pick' | 'drawn' | 'selfie';

/**
 * Captura de assinatura em modal: desenhada (canvas touch/mouse) ou selfie (câmera ao vivo).
 * Coleta a geolocalização (best-effort) e envia a imagem; o servidor registra os metadados
 * confiáveis (signatário, data/hora, IP, dispositivo).
 */
@Component({
  selector: 'app-signature-capture',
  imports: [TPipe],
  templateUrl: './signature-capture.html',
  styleUrl: './signature-capture.scss',
})
export class SignatureCapture implements AfterViewInit, OnDestroy {
  private api = inject(FormsService);

  @Input() formId!: number;
  @Input() fieldKey!: string;
  /** Estilo permitido pelo campo: drawn | selfie | both. */
  @Input() allowed: 'drawn' | 'selfie' | 'both' = 'both';
  @Output() captured = new EventEmitter<SignatureValue>();
  @Output() cancelled = new EventEmitter<void>();

  @ViewChild('pad') padRef?: ElementRef<HTMLCanvasElement>;
  @ViewChild('video') videoRef?: ElementRef<HTMLVideoElement>;

  mode = signal<Mode>('pick');
  saving = signal(false);
  error = signal<string | null>(null);
  cameraReady = signal(false);
  captured0 = signal(false); // selfie já capturada (mostra prévia)
  hasDrawing = signal(false);

  private stream: MediaStream | null = null;
  private ctx: CanvasRenderingContext2D | null = null;
  private drawing = false;
  private selfieBlob: Blob | null = null;

  ngAfterViewInit(): void {
    if (this.allowed !== 'both') this.start(this.allowed);
  }

  ngOnDestroy(): void { this.stopCamera(); }

  start(mode: 'drawn' | 'selfie'): void {
    this.error.set(null);
    this.mode.set(mode);
    if (mode === 'drawn') {
      setTimeout(() => this.initPad(), 0);
    } else {
      this.captured0.set(false);
      this.selfieBlob = null;
      setTimeout(() => this.startCamera(), 0);
    }
  }

  back(): void {
    this.stopCamera();
    this.selfieBlob = null;
    this.captured0.set(false);
    this.hasDrawing.set(false);
    if (this.allowed === 'both') this.mode.set('pick');
    else this.cancel();
  }

  cancel(): void { this.stopCamera(); this.cancelled.emit(); }

  // ---------------- desenho ----------------
  private initPad(): void {
    const c = this.padRef?.nativeElement;
    if (!c) return;
    const ratio = window.devicePixelRatio || 1;
    const rect = c.getBoundingClientRect();
    c.width = Math.round(rect.width * ratio);
    c.height = Math.round(rect.height * ratio);
    const ctx = c.getContext('2d');
    if (!ctx) return;
    ctx.scale(ratio, ratio);
    ctx.lineWidth = 2.2;
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    ctx.strokeStyle = '#111827';
    this.ctx = ctx;
    this.hasDrawing.set(false);
  }

  private pos(e: PointerEvent): [number, number] {
    const c = this.padRef!.nativeElement;
    const r = c.getBoundingClientRect();
    return [e.clientX - r.left, e.clientY - r.top];
  }

  onDown(e: PointerEvent): void {
    if (!this.ctx) return;
    e.preventDefault();
    this.padRef!.nativeElement.setPointerCapture(e.pointerId);
    this.drawing = true;
    const [x, y] = this.pos(e);
    this.ctx.beginPath();
    this.ctx.moveTo(x, y);
  }
  onMove(e: PointerEvent): void {
    if (!this.drawing || !this.ctx) return;
    e.preventDefault();
    const [x, y] = this.pos(e);
    this.ctx.lineTo(x, y);
    this.ctx.stroke();
    this.hasDrawing.set(true);
  }
  onUp(): void { this.drawing = false; }

  clearPad(): void {
    const c = this.padRef?.nativeElement;
    if (c && this.ctx) this.ctx.clearRect(0, 0, c.width, c.height);
    this.hasDrawing.set(false);
  }

  // ---------------- selfie ----------------
  private async startCamera(): Promise<void> {
    this.cameraReady.set(false);
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' }, audio: false });
      const v = this.videoRef?.nativeElement;
      if (v) {
        v.srcObject = this.stream;
        await v.play();
        this.cameraReady.set(true);
      }
    } catch {
      this.error.set(tr('Não foi possível acessar a câmera. Verifique a permissão do navegador.'));
    }
  }

  private stopCamera(): void {
    this.stream?.getTracks().forEach((t) => t.stop());
    this.stream = null;
    this.cameraReady.set(false);
  }

  takeSelfie(): void {
    const v = this.videoRef?.nativeElement;
    if (!v || !v.videoWidth) return;
    const canvas = document.createElement('canvas');
    canvas.width = v.videoWidth;
    canvas.height = v.videoHeight;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.drawImage(v, 0, 0);
    canvas.toBlob((b) => {
      if (!b) return;
      this.selfieBlob = b;
      this.captured0.set(true);
      this.stopCamera();
      const img = this.videoRef?.nativeElement;
      if (img) img.poster = '';
      this.previewUrl.set(URL.createObjectURL(b));
    }, 'image/jpeg', 0.9);
  }

  previewUrl = signal<string | null>(null);

  retakeSelfie(): void {
    this.captured0.set(false);
    this.selfieBlob = null;
    if (this.previewUrl()) { URL.revokeObjectURL(this.previewUrl()!); this.previewUrl.set(null); }
    this.startCamera();
  }

  // ---------------- confirmar ----------------
  confirm(): void {
    if (this.mode() === 'drawn') {
      const c = this.padRef?.nativeElement;
      if (!c || !this.hasDrawing()) { this.error.set(tr('Desenhe a assinatura antes de confirmar.')); return; }
      c.toBlob((b) => { if (b) this.send('drawn', b); }, 'image/png');
    } else {
      if (!this.selfieBlob) { this.error.set(tr('Capture a selfie antes de confirmar.')); return; }
      this.send('selfie', this.selfieBlob);
    }
  }

  private send(style: 'drawn' | 'selfie', blob: Blob): void {
    this.error.set(null);
    this.saving.set(true);
    this.collectGeo().then((geo) => {
      this.api.captureSignature(this.formId, this.fieldKey, style, blob, geo).subscribe({
        next: (value) => { this.saving.set(false); this.stopCamera(); this.captured.emit(value); },
        error: (e) => { this.saving.set(false); this.error.set(e?.error?.message ?? tr('Não foi possível registrar a assinatura.')); },
      });
    });
  }

  /** Geolocalização best-effort: nunca bloqueia; devolve o status e as coordenadas quando houver. */
  private collectGeo(): Promise<{ status: string; lat: number | null; lon: number | null; accuracy: number | null }> {
    return new Promise((resolve) => {
      if (!('geolocation' in navigator)) {
        resolve({ status: 'unavailable', lat: null, lon: null, accuracy: null });
        return;
      }
      navigator.geolocation.getCurrentPosition(
        (p) => resolve({ status: 'ok', lat: p.coords.latitude, lon: p.coords.longitude, accuracy: p.coords.accuracy }),
        (err) => resolve({
          status: err.code === err.PERMISSION_DENIED ? 'denied' : 'unavailable',
          lat: null, lon: null, accuracy: null,
        }),
        { enableHighAccuracy: true, timeout: 8000, maximumAge: 0 },
      );
    });
  }
}
