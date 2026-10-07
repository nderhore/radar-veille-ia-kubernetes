import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';

import { Assessment, NATURE_LABELS, SOURCE_LABELS } from './radar.models';

/**
 * Opportunités et actions : chaque sujet est présenté comme un élément de réponse à une question
 * décisionnelle, avec l'enjeu pour l'organisation, la tendance, l'action recommandée, son
 * responsable et sa date de revue. Les verdicts des analystes alimentent la boucle de rétroaction.
 */
@Component({
  selector: 'app-opportunity-list',
  imports: [DatePipe, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './opportunity-list.html',
  styleUrl: './opportunity-list.css',
})
export class OpportunityList {
  readonly topics = input.required<Assessment[]>();
  readonly verdict = output<{ term: string; verdict: 'pertinent' | 'bruit' }>();

  protected nature(n: Assessment['nature']): string {
    return NATURE_LABELS[n] ?? n;
  }

  protected sources(types: string[]): string {
    return types.map((t) => SOURCE_LABELS[t] ?? t).join(', ');
  }
}
