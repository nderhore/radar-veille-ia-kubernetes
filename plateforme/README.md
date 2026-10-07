# Code source du radar

Code des trois composants de l'étude de cas « Industrialiser le radar d'innovation » (`../01_etude-de-cas.md`). Le déploiement se fait avec Docker Compose, depuis le dossier `../demo`.

```
plateforme/
├── services/                    Backend Java 21 / Spring Boot 4.1 (Maven)
│   ├── radar-api/               API publique, ingestion interne, indice de rupture, protocole de veille, métriques
│   └── signal-collector/        Collecte planifiée : Hacker News, arXiv, GitHub
├── frontend/radar-web/          Angular 22, servi par nginx non privilégié (point d'entrée, relais /api)
├── docs/runbooks.md             Un runbook par alerte, sauvegarde et restauration
└── Makefile                     `make test`, `make images`
```

L'intégration continue (tests, validation de la configuration Docker Compose, images, Trivy, cosign) est à la racine du dépôt : `.github/workflows/ci.yml`.

## Lancer le service

```bash
cd ../demo
make up-complet    # radar sur http://localhost:8088, Grafana sur http://localhost:3000
```

Voir `../demo/README.md` pour les scénarios de panne, la sauvegarde et la restauration.

## Développer

| Composant | Commande | Prérequis |
|---|---|---|
| Services Java | `cd services && mvn verify` | Java 21, Maven |
| Front Angular | `cd frontend/radar-web && npm ci && npx ng serve` | Node.js 24 |
| Tout | `make test` | Les deux |

Les tests Java utilisent H2 en mode PostgreSQL : aucune base n'est nécessaire pour les lancer.

## Images

Chaque composant a son `Dockerfile` (construction en plusieurs étapes, exécution sans privilège : utilisateur 10001 pour les services Java, `nginx-unprivileged` pour le front). Les images sont versionnées par `RADAR_VERSION` : revenir à la version précédente se fait en une commande.

```bash
RADAR_VERSION=1.1.0 docker compose up -d --build radar-api     # mise en production
RADAR_VERSION=1.0.0 docker compose up -d --no-build radar-api  # retour arrière
```
