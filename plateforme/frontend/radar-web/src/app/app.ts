import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { DatePipe, KeyValuePipe } from '@angular/common';

import { CoverageGrid } from './coverage-grid';
import { OpportunityList } from './opportunity-list';
import { RadarApiService } from './radar-api.service';
import { RadarChart } from './radar-chart';
import { RINGS } from './radar.models';

@Component({
  selector: 'app-root',
  imports: [RadarChart, CoverageGrid, OpportunityList, DatePipe, KeyValuePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {
  protected readonly api = inject(RadarApiService);
  protected readonly topics = computed(() => this.api.radar()?.topics ?? []);
  protected readonly ringSummary = computed(() =>
    RINGS.map((r) => ({ ...r, count: this.topics().filter((t) => t.ring === r.id).length })),
  );
  protected readonly blindSpots = computed(
    () => (this.api.radar()?.coverage ?? []).flatMap((c) => c.kiq).filter((k) => k.statut === 'angle mort').length,
  );

  ngOnInit(): void {
    this.api.refresh();
  }
}
