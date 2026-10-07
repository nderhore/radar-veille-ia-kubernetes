import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { AxisCoverage, RINGS, SOURCE_LABELS } from './radar.models';

/**
 * Couverture stratégique du protocole : pour chaque axe (KIT), volume de signaux récents,
 * diversité des sources et état de chaque question décisionnelle (KIQ). Une KIQ en « angle mort »
 * signale qu'aucune donnée ne permet d'y répondre : le plan de sourcing doit être revu.
 */
@Component({
  selector: 'app-coverage-grid',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './coverage-grid.html',
  styleUrl: './coverage-grid.css',
})
export class CoverageGrid {
  readonly coverage = input.required<AxisCoverage[]>();
  protected readonly rings = RINGS;
  protected readonly allSources = ['academic', 'code', 'community'];

  protected label(t: string): string {
    return SOURCE_LABELS[t] ?? t;
  }
}
