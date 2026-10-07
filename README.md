# Étude de cas : Industrialiser le radar d'innovation

**Kubernetes · Backend Java · Front Angular · Istio · PostgreSQL haute disponibilité · Prometheus / Grafana · Vector**

*Démonstration distincte du live « Définition du protocole de veille stratégique » : le radar Python de la démo 1 devient un service en production, et chaque choix d'architecture fait l'objet d'une décision argumentée.*

---

## Version « cours » : ce qu'il faut pour le live

| Fichier | Public | Contenu |
|---|---|---|
| `01_etude-de-cas.md` | Apprenants | **Version courte, angle technique et pannes** : Altéa Services et ses défis, exigences, architecture, 6 décisions clés, 5 pannes et les bonnes réactions. Variantes : `01b_…` (version détaillée, 13 décisions), `01c_…` (angle détection des ruptures) |
| `02_guide-de-demonstration.md` | Intervenant | Préparation, déroulé indicatif, commandes, questions attendues |
| `03_slides-etude-de-cas.pptx` | Projection | Diaporama de 24 diapositives, notes de présentation incluses |
| `demo/` | Démonstration | **Docker Compose uniquement** : application, alertes, journaux, tableau de bord. Voir `demo/README.md` |

```bash
cd demo
make up-complet    # application + observabilité : radar http://localhost:8088, Grafana http://localhost:3000
```

`demo/` ne fonctionne pas seul : Docker Compose construit les images à partir des sources de `plateforme/services` et `plateforme/frontend`.

## Annexe : la plateforme complète

`plateforme/` contient la version déployable sur Kubernetes : manifestes Istio, cluster PostgreSQL CloudNativePG à 3 instances, alertes SLO, Vector en DaemonSet, Argo CD, CI. Elle n'est **pas nécessaire au live**. Pendant la séance, on en ouvre quelques fichiers pour illustrer les décisions que Docker Compose ne permet pas de montrer :

| Décision | Fichier à ouvrir |
|---|---|
| D3 · Maillage Istio (mTLS, autorisations, egress) | `plateforme/deploy/app/mesh/security.yaml`, `egress.yaml` |
| D4 · D5 · Cluster PostgreSQL | `plateforme/deploy/app/postgres/cluster.yaml` |
| D6 · Alertes SLO multi-fenêtres | `plateforme/deploy/app/monitoring/prometheus-rules.yaml` |
| D10 · Canari | `plateforme/deploy/app/components/canary/kustomization.yaml` |

Son installation complète (`plateforme/`, `make kind-up`) demande 8 Go de RAM et au moins 25 Go de disque libre : voir `plateforme/README.md`.

## Les décisions instruites

| N° | Thème | Décision retenue |
|---|---|---|
| D1 | Découpage applicatif | 2 services Java + front Angular |
| D2 | Hébergement | Kubernetes managé, hébergeur UE, 3 zones |
| D3 | Maillage de services | Istio sidecar + CNI, base hors maillage |
| D4 | PostgreSQL | CloudNativePG : 3 instances, bascule automatique, PITR |
| D5 | Connexions et durabilité | PgBouncer en mode transaction ; réplication synchrone |
| D6 | Métriques et alertes | Prometheus, Alertmanager, Grafana ; SLO et alertes multi-fenêtres |
| D7 | Journaux | Vector vers Loki, masquage RGPD |
| D8 | Traces | Tempo, échantillonnage 10 % |
| D9 | Déploiement | GitOps avec Argo CD |
| D10 | Livraison progressive | Canari Istio |
| D11 | Front | Angular 22 |
| D12 | Secrets | External Secrets Operator + coffre |
| D13 | Sécurité | Défense en profondeur (6 mesures) |
