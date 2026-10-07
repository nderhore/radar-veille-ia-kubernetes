# Démonstration « cours » : le radar industrialisé sous Docker Compose

Version allégée de la plateforme de l'étude de cas, conçue pour un live : **aucun Kubernetes**, un seul prérequis (Docker), démarrage en quelques minutes. Elle réutilise les mêmes services Java, le même front Angular et la même chaîne d'observabilité que la plateforme complète (`../plateforme`, en annexe).

## Prérequis

| Élément | Minimum |
|---|---|
| Docker Desktop | 4 Go de RAM alloués |
| Espace disque libre | 4 Go (application seule) · 6 Go (avec observabilité) |
| Réseau | accès à arXiv, Hacker News et GitHub (collecte réelle) |

## Lancement

```bash
make up            # application seule, sur http://localhost:8088
make up-complet    # avec Prometheus, Alertmanager, Grafana, Loki et Vector, sur http://localhost:3000
make liens         # rappel des adresses
make down          # arrêt et suppression des données
```

La première collecte prend environ deux minutes : le radar se remplit ensuite avec des signaux réels.

## Ce que l'on montre, décision par décision

| Décision de l'étude de cas | Montré en direct | Comment |
|---|---|---|
| D1 · Découpage en deux services | Oui | `make panne-collecteur` : le radar reste consultable, seule la veille s'arrête |
| D4 · D5 · PostgreSQL | Partiel | Une seule instance PostgreSQL ici ; le cluster à 3 instances se présente avec `../plateforme/deploy/app/postgres/cluster.yaml` |
| D3 · D10 · Istio, canari | Lecture commentée | `../plateforme/deploy/app/mesh/` et `components/canary/` |
| D6 · Métriques, alertes, routage | Oui | Prometheus (http://localhost:9090/alerts), Alertmanager (http://localhost:9093) |
| D7 · Vector, Loki, masquage RGPD | Oui | Tableau Grafana « Radar d'innovation : démonstration » et vue Explore de Loki |
| D11 · Front Angular | Oui | http://localhost:8088, verdicts « pertinent / bruit » |
| Boucle de rétroaction (live 1) | Oui | Un verdict « bruit » retire le sujet au recalcul suivant |

## Scénarios

| Commande | Ce qui se passe | Alerte et destinataire |
|---|---|---|
| `make charge` | Trafic sur l'API pendant 2 min ; courbes du tableau de bord | - |
| `make panne-collecteur` puis `make retour-collecteur` | Le front fonctionne toujours ; la fraîcheur de la veille se dégrade | `RadarCollectorDown` : **ops** (avertissement) |
| `make panne-api` puis `make retour-api` | Le front affiche « API indisponible » | `RadarApiDown` : **astreinte** (critique) |
| *(automatique)* | Un sujet dépasse l'indice de rupture de démonstration (0,3) | `RadarDisruptionDetected` : **veille** (métier) |
| `make erreurs` | 30 requêtes invalides refusées (400) : la validation est visible dans les métriques | - |

Les trois destinataires (`ops`, `astreinte`, `veille`) illustrent la mise en situation S4 : **une alerte métier ne réveille jamais l'astreinte**. Le routage se vérifie hors démonstration avec `amtool config routes test --config.file=observabilite/alertmanager.yml team=veille`.

## Différences avec la plateforme complète

| Plateforme complète (`../plateforme`) | Démonstration « cours » |
|---|---|
| Kubernetes (kind ou cluster managé) | Docker Compose |
| Istio : mTLS, autorisations, canari, egress | Pas de maillage ; l'API interne n'est pas protégée |
| PostgreSQL CloudNativePG, 3 instances, PgBouncer | PostgreSQL, 1 instance |
| SLO sur les métriques Envoy | Taux d'erreur sur les métriques Spring Boot |
| Vector en DaemonSet (fichiers des nœuds) | Vector via le démon Docker (lecture seule) |
| Seuil d'alerte métier 0,5 ; délais réalistes | Seuil 0,3 ; délais raccourcis pour le live |

## Vérifications effectuées

- `docker compose config` : 9 services valides ;
- `promtool check rules` et `check config` : 6 règles, configuration valide ;
- `amtool check-config` et `amtool config routes test` : routage `veille` / `astreinte` / `ops` conforme ;
- `vector validate` : configuration valide ;
- application (PostgreSQL, API, collecteur, front) testée de bout en bout sous Docker Compose avec collecte réelle ;
- **à faire lors de la répétition** : exécution complète du profil `observabilite` (configurations validées, mais Docker était indisponible lors de la préparation).
