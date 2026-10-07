# Étude de cas : Industrialiser le radar d'innovation

**Docker Compose · Backend Java · Front Angular · PostgreSQL · Prometheus / Grafana · Vector**

*Démonstration distincte du live « Définition du protocole de veille stratégique » : le radar Python de la démo 1 devient un service en production, et chaque choix d'architecture fait l'objet d'une décision argumentée.*

---

## Contenu

| Fichier | Public | Contenu |
|---|---|---|
| `01_etude-de-cas.md` | Apprenants | **Angle technique et pannes** : Altéa Services et ses défis, exigences, architecture, 6 décisions clés, 5 pannes et les bonnes réactions. Variante : `01c_…` (angle détection des ruptures) |
| `03_slides-etude-de-cas.pptx` | Projection | Diaporama, notes de présentation incluses |
| `demo/` | Démonstration | Le service complet sous **Docker Compose** : application, alertes, journaux, tableau de bord, sauvegarde. Voir `demo/README.md` |
| `plateforme/` | Code source | Services Java, front Angular, runbooks. Voir `plateforme/README.md` |

`demo/` ne fonctionne pas seul : Docker Compose construit les images à partir des sources de `plateforme/services` et `plateforme/frontend`.

```bash
cd demo
make up-complet    # application + observabilité : radar http://localhost:8088, Grafana http://localhost:3000
```

## Les six décisions

| N° | Question | Décision retenue | Où la voir |
|---|---|---|---|
| D1 | Un ou plusieurs services ? | 2 services Java (API, collecteur) + front Angular | `make panne-collecteur` |
| D2 | Kubernetes ou Docker Compose ? | Docker Compose sur un serveur européen ; redémarrage automatique ; images versionnées | `demo/docker-compose.yml` |
| D3 | Comment sécuriser les échanges ? | Point d'entrée unique en HTTPS ; réseaux Docker séparés | `networks` dans `demo/docker-compose.yml` |
| D4 | Comment protéger les données ? | PostgreSQL sur volume persistant, sauvegarde chaque nuit, restauration testée | `make sauvegarde`, `make restauration` |
| D5 | Comment savoir que tout va bien ? | Prometheus, Alertmanager, Grafana ; Vector vers Loki, masquage RGPD | Grafana, http://localhost:3000 |
| D6 | Qui reçoit quelle alerte ? | Astreinte, exploitation, équipe veille | `demo/observabilite/alertmanager.yml` |

## Les cinq pannes

| Panne | Commande | Alerte et destinataire |
|---|---|---|
| Le collecteur s'arrête | `make panne-collecteur` | `RadarCollectorDown` : exploitation |
| L'API tombe | `make panne-api` | `RadarApiDown` : astreinte |
| Une nouvelle version est défectueuse | `RADAR_VERSION=1.0.0 docker compose up -d --no-build radar-api` (retour arrière) | `RadarApiErrorRate` : astreinte |
| La base de données s'arrête | `make panne-base` puis `make charge` | `RadarApiErrorRate` : astreinte |
| Un sujet dépasse le seuil de rupture | *(automatique)* | `RadarDisruptionDetected` : équipe veille |
