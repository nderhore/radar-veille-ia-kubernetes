# Plateforme radar : Kubernetes, Istio, PostgreSQL HA, observabilité

Démonstration de l'étude de cas « Industrialiser le radar d'innovation » (`../01_etude-de-cas.md`). Le radar de la démo 1 y devient un service en production : backend Java, front Angular, maillage Istio, cluster PostgreSQL haute disponibilité, alertes Prometheus, journaux collectés par Vector.

```
plateforme/
├── services/                    Backend Java 21 / Spring Boot 4.1 (Maven)
│   ├── radar-api/               API publique + ingestion interne + indice de rupture + métriques
│   └── signal-collector/        Collecte planifiée : Hacker News, arXiv, GitHub
├── frontend/radar-web/          Angular 22, servi par nginx non privilégié
├── deploy/
│   ├── kind/                    Cluster local (1 plan de contrôle + 2 nœuds)
│   ├── platform/                Valeurs Helm : Istio, CloudNativePG, kube-prometheus-stack, Loki, Tempo, Vector
│   ├── app/
│   │   ├── base/                Workloads, Services, HPA, PDB, ServiceMonitor, NetworkPolicy
│   │   ├── postgres/            Cluster CloudNativePG (3 instances), Pooler PgBouncer, PodMonitor
│   │   ├── mesh/                Gateway, VirtualService, DestinationRule, mTLS, AuthorizationPolicy, egress, télémétrie
│   │   ├── monitoring/          PrometheusRule (SLO, collecte, PostgreSQL, journaux, métier) + tableau de bord Grafana
│   │   ├── components/canary/   Version v2 de radar-api à 10 % du trafic
│   │   └── overlays/            dev (kind), dev-canary, prod (TLS, sauvegardes, Pod Security restricted)
│   └── argocd/                  GitOps : AppProject + Applications (plateforme et application)
├── scripts/                     bootstrap-kind.sh (installation complète), demo.sh (scénarios)
├── docs/runbooks.md             Un runbook par alerte
├── docker-compose.yml           Exécution sans Kubernetes
└── Makefile                     Point d'entrée : `make help`
```

L'intégration continue (tests, validation, images, Trivy, cosign, mise à jour GitOps) est à la racine du dépôt : `.github/workflows/ci.yml`.

## Trois façons de lancer la démonstration

| Mode | Commande | Prérequis | Ce que l'on montre |
|---|---|---|---|
| Sans Kubernetes | `make local` puis http://localhost:8088 | Docker | Application complète : collecte réelle, API, front, métriques |
| Kubernetes local complet | `make kind-up` | Docker avec **8 Go de RAM** et **≥ 25 Go de disque libre**, kind, kubectl, helm | Tout : maillage, PostgreSQL HA, alertes, journaux, traces, scénarios d'incident |
| Validation seule | `make validate` | kubectl, kubeconform, yq, Docker | Rendu Kustomize, schémas Kubernetes et CRD, règles Prometheus |

**Ressources.** La pile complète (Istio, kube-prometheus-stack, Loki, Tempo, Vector, 3 instances PostgreSQL et l'application) télécharge plusieurs gigaoctets d'images. Sur un poste dont le disque est presque plein, l'installation échoue en cours de route. C'est d'ailleurs une illustration du § 8 de l'étude de cas : la plateforme pèse plus lourd que l'application.

## Scénarios (après `make kind-up`)

| Commande | Scénario de l'étude de cas |
|---|---|
| `make demo-load` | Trafic continu (alimente SLO, tableaux de bord, traces) |
| `make demo-failover` | S1 : perte du primaire PostgreSQL, bascule automatique chronométrée |
| `make demo-canary`, puis `demo-promote` ou `demo-rollback` | Livraison progressive à 10 % |
| `make demo-fault` (+ `demo-load`), puis `demo-fault-off` | S2 : erreurs 503 injectées, alerte de consommation du budget d'erreur |
| `make demo-egress` | S5 : un hôte externe non déclaré est bloqué |

## État des vérifications

| Contrôle | Résultat |
|---|---|
| `mvn verify` (2 services) | 9 tests : calcul G² (valeur de référence du live), émergence, dédoublonnage, validation RFC 9457, analyse arXiv, protection XXE, mappage des sources |
| `ng build` | 53 Ko transférés |
| Docker Compose de bout en bout | collecte réelle (arXiv, GitHub, Hacker News), ingestion, radar, front, métriques Prometheus |
| Kustomize + kubeconform (dev, dev-canary, prod) | 42, 43 et 45 objets valides, CRD comprises |
| `promtool check rules` | 18 règles |
| `vector validate` (configuration produite par Helm) | Oui |
| `helm template` des 8 charts avec nos valeurs | Oui |
| Installation kind complète | Partiel : tous les charts installés ; test interrompu à l'attente du cluster PostgreSQL par la saturation du disque du poste de préparation |
