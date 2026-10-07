import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';

import { Protocol, RadarSnapshot, SignalSummary, Stats } from './radar.models';

/**
 * Accès à l'API. Les URL sont relatives (/api/...) : en Kubernetes, la passerelle Istio
 * route /api vers radar-api ; en local, nginx ou le serveur de développement font le relais.
 */
@Injectable({ providedIn: 'root' })
export class RadarApiService {
  private readonly http = inject(HttpClient);

  readonly radar = signal<RadarSnapshot | null>(null);
  readonly protocol = signal<Protocol | null>(null);
  readonly signals = signal<SignalSummary[]>([]);
  readonly stats = signal<Stats | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  refresh(): void {
    this.loading.set(true);
    forkJoin({
      radar: this.http.get<RadarSnapshot>('/api/radar'),
      protocol: this.http.get<Protocol>('/api/protocole'),
      signals: this.http.get<SignalSummary[]>('/api/signals', { params: { limit: 20 } }),
      stats: this.http.get<Stats>('/api/stats'),
    }).subscribe({
      next: ({ radar, protocol, signals, stats }) => {
        this.radar.set(radar);
        this.protocol.set(protocol);
        this.signals.set(signals);
        this.stats.set(stats);
        this.error.set(null);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(`API indisponible (${err.status || 'réseau'})`);
        this.loading.set(false);
      },
    });
  }

  sendFeedback(term: string, verdict: 'pertinent' | 'bruit'): void {
    this.http.post('/api/feedback', { term, verdict, analyst: 'interface web' }).subscribe({
      next: () => this.refresh(),
      error: () => this.error.set('Verdict non enregistré'),
    });
  }
}
