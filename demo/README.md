# Le radar industrialisé sous Docker Compose

Le service complet de l'étude de cas : deux services Java, un front Angular, PostgreSQL, et la chaîne d'observabilité. Un seul prérequis (Docker), démarrage en quelques minutes. C'est la même architecture qu'en production, à l'exception du HTTPS, assuré en production devant le point d'entrée.

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

| Décision de l'étude de cas | Comment |
|---|---|
| D1 · Deux services | `make panne-collecteur` : le radar reste consultable, seule la veille s'arrête |
| D2 · Docker Compose | `restart: unless-stopped` (redémarrage automatique) et images versionnées par `RADAR_VERSION` |
| D3 · Point d'entrée et réseaux séparés | Seul `radar-web` relaie `/api` ; la base est sur le réseau interne `donnees`, le collecteur sur le réseau `collecte` |
| D4 · Sauvegarde et restauration | `make sauvegarde`, puis `make restauration` |
| D5 · Métriques, alertes, journaux | Prometheus (http://localhost:9090/alerts), tableau Grafana « Radar d'innovation : démonstration », vue Explore de Loki |
| D6 · Routage des alertes | Alertmanager (http://localhost:9093) : trois destinataires |
| Boucle de rétroaction (live 1) | Un verdict « bruit » dans le front retire le sujet au recalcul suivant |

## Les cinq pannes

| Commande | Ce qui se passe | Alerte et destinataire |
|---|---|---|
| `make panne-collecteur` puis `make retour-collecteur` | Le front fonctionne toujours ; la fraîcheur de la veille se dégrade | `RadarCollectorDown` : **ops** (avertissement) |
| `make panne-api` puis `make retour-api` | Le front affiche « API indisponible » | `RadarApiDown` : **astreinte** (critique) |
| `RADAR_VERSION=1.0.0 docker compose up -d --no-build radar-api` | Retour à la version précédente après une mise en production défectueuse | `RadarApiErrorRate` : **astreinte** (critique) |
| `make panne-base`, `make charge`, puis `make retour-base` | L'API répond en erreur tant que la base est arrêtée | `RadarApiErrorRate` : **astreinte** (critique) |
| *(automatique)* | Un sujet dépasse l'indice de rupture de démonstration (0,3) | `RadarDisruptionDetected` : **veille** (métier) |

Autres commandes : `make charge` (trafic pendant 2 min), `make erreurs` (30 requêtes refusées, visibles dans les métriques).

Les trois destinataires (`ops`, `astreinte`, `veille`) illustrent la décision D6 : **une alerte métier ne réveille jamais l'astreinte**. Le routage se vérifie hors démonstration avec `amtool config routes test --config.file=observabilite/alertmanager.yml team=veille`.

## Démonstration et production

| Production | Démonstration |
|---|---|
| Seul le point d'entrée est publié, en HTTPS (Caddy ou Traefik, certificat Let's Encrypt) | Ports publiés pour montrer chaque outil (8081, 8082, 3000, 9090, 9093) |
| Mots de passe dans un fichier `.env` non versionné | Mots de passe de démonstration dans `docker-compose.yml` |
| Sauvegarde chaque nuit par une tâche planifiée du serveur, copiée hors du serveur | `make sauvegarde` à la demande |
| Seuil d'alerte métier 0,5 ; délais réalistes | Seuil 0,3 ; délais raccourcis pour le live |

## Vérifications effectuées

- `docker compose config` : 9 services, 4 réseaux, configuration valide ;
- `promtool check rules` : 6 règles valides ;
- `amtool check-config` : configuration valide ; routage `veille` / `astreinte` / `ops` vérifié ;
- `vector validate` : configuration valide ;
- **à faire lors de la répétition** : exécution complète de bout en bout, y compris les réseaux séparés, `make sauvegarde` et `make restauration` (Docker était indisponible lors de la préparation de cette version).
