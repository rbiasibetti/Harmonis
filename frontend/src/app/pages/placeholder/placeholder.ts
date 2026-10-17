import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { TPipe } from '../../core/i18n/i18n';
import { tr } from '../../core/i18n/i18n';

@Component({
  imports: [TPipe],
  selector: 'app-placeholder',
  templateUrl: './placeholder.html',
  styleUrl: './placeholder.scss',
})
export class Placeholder {
  private route = inject(ActivatedRoute);

  protected readonly info = toSignal(
    this.route.data.pipe(
      map((d) => ({
        title: (d['title'] as string) ?? tr('Módulo'),
        desc: (d['desc'] as string) ?? '',
        milestone: (d['milestone'] as string) ?? '',
      }))
    ),
    { initialValue: { title: tr('Módulo'), desc: '', milestone: '' } }
  );
}
