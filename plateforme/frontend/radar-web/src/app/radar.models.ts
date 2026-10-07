/** Contrats de l'API radar-api (miroir des records Java). */

export type Ring = 'agir' | 'preparer' | 'explorer' | 'surveiller';
export type Nature = 'opportunite' | 'menace' | 'mixte';

/** Sujet du radar lu à travers le protocole : axe, KIQ, enjeu, tendance, action datée. */
export interface Assessment {
  label: string;
  axe: string;
  axeIntitule: string;
  quadrant: string;
  kiq: string;
  question: string;
  decision: string;
  proprietaire: string;
  horizonDecision: string;
  nature: Nature;
  opportunite: string;
  tendance: string;
  ring: Ring;
  ringLabel: string;
  ringHorizon: string;
  trl: number;
  action: string;
  revueAvant: string;
  recent: number;
  baseline: number;
  g2: number;
  diffusion: number;
  impact: number;
  novelty: number;
  disruptionIndex: number;
  sourceTypes: string[];
  evidenceUrls: string[];
}

export interface KiqCoverage {
  id: string;
  question: string;
  decision: string;
  proprietaire: string;
  horizon: string;
  signals: number;
  technologies: number;
  statut: string;
}

export interface AxisCoverage {
  id: string;
  intitule: string;
  quadrant: string;
  enjeu: string;
  technologies: number;
  signals: number;
  sourceTypes: string[];
  couverture: 'complète' | 'partielle' | 'insuffisante';
  sujetsParAnneau: Record<Ring, number>;
  sujetPrincipal: string;
  kiq: KiqCoverage[];
}

export interface RadarSnapshot {
  computedAt: string;
  asOf: string;
  protocolVersion: string;
  signalsInWindow: number;
  topics: Assessment[];
  coverage: AxisCoverage[];
}

export interface Protocol {
  version: string;
  finalite: string;
  commanditaire: string;
}

export interface SignalSummary {
  title: string;
  source: string;
  sourceType: string;
  url: string;
  publishedAt: string;
  engagement: number;
}

export interface Stats {
  total: number;
  bySource: Record<string, number>;
}

export const QUADRANTS = ['Modèles & Techniques', 'Agents & Applications', 'Infrastructure & Outillage', 'Confiance & Régulation'];
export const RINGS: { id: Ring; label: string; horizon: string }[] = [
  { id: 'agir', label: 'Agir', horizon: '0–6 mois' },
  { id: 'preparer', label: 'Préparer', horizon: '6–18 mois' },
  { id: 'explorer', label: 'Explorer', horizon: '18–36 mois' },
  { id: 'surveiller', label: 'Surveiller', horizon: '> 36 mois' },
];
export const SOURCE_LABELS: Record<string, string> = {
  academic: 'recherche', code: 'code ouvert', community: 'communauté', press: 'presse', regulatory: 'réglementation',
};
export const NATURE_LABELS: Record<Nature, string> = { opportunite: 'Opportunité', menace: 'Menace', mixte: 'Opportunité et menace' };
