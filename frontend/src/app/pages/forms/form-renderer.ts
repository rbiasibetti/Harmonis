import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { conditionPasses } from '../../core/condition';
import { FormField } from '../../core/models';

/**
 * Renderiza um formulario (layout responsivo em grid, blocos/secoes, todos os tipos
 * de campo, exibicao condicional). Usado no preenchimento e na previa do construtor.
 */
@Component({
  selector: 'app-form-renderer',
  imports: [FormsModule],
  templateUrl: './form-renderer.html',
  styleUrl: './form-renderer.scss',
})
export class FormRenderer {
  @Input() fields: FormField[] = [];
  @Input() values: Record<string, any> = {};
  @Input() errors: Record<string, string> = {};
  @Input() disabled = false;

  widthClass(f: FormField): string {
    return 'w-' + (f.width || 'full');
  }

  isSection(f: FormField): boolean { return f.type === 'section'; }

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
