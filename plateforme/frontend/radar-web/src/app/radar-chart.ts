import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { Assessment, QUADRANTS, RINGS } from './radar.models';

interface Blip {
  n: number;
  x: number;
  y: number;
  color: string;
  topic: Assessment;
}

const SIZE = 520;
const CENTER = SIZE / 2;
const RADII = [70, 130, 185, 235];
const COLORS = ['#2f6fdf', '#1a9e75', '#d9822b', '#b84a9e'];

/** Radar SVG : quadrant = axe de veille, anneau = horizon de décision. Placement déterministe. */
@Component({
  selector: 'app-radar-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './radar-chart.html',
  styleUrl: './radar-chart.css',
})
export class RadarChart {
  readonly topics = input.required<Assessment[]>();

  protected readonly size = SIZE;
  protected readonly center = CENTER;
  protected readonly rings = RINGS.map((r, i) => ({ label: r.label, radius: RADII[i] }));
  protected readonly quadrants = [
    { name: QUADRANTS[0], x: SIZE - 6, y: 16, anchor: 'end', color: COLORS[0] },
    { name: QUADRANTS[1], x: 6, y: 16, anchor: 'start', color: COLORS[1] },
    { name: QUADRANTS[2], x: 6, y: SIZE - 8, anchor: 'start', color: COLORS[2] },
    { name: QUADRANTS[3], x: SIZE - 6, y: SIZE - 8, anchor: 'end', color: COLORS[3] },
  ];

  protected readonly blips = computed<Blip[]>(() =>
    this.topics().map((topic, i) => {
      const q = Math.max(0, QUADRANTS.indexOf(topic.quadrant));
      const r = Math.max(0, RINGS.findIndex((x) => x.id === topic.ring));
      const seed = hash(topic.label);
      const angle = ((q * 90 + 10 + (seed % 70)) * Math.PI) / 180;
      const inner = (r === 0 ? 0 : RADII[r - 1]) + 14;
      const radius = inner + ((seed >> 8) % Math.max(RADII[r] - inner - 12, 1));
      return { n: i + 1, x: CENTER + radius * Math.cos(angle), y: CENTER - radius * Math.sin(angle), color: COLORS[q], topic };
    }),
  );
}

function hash(s: string): number {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) {
    h = Math.imul(h ^ s.charCodeAt(i), 16777619);
  }
  return h >>> 0;
}
