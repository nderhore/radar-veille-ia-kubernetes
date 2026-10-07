# Runbooks : alertes du radar

Chaque alerte de `demo/observabilite/regles-alertes.yml` a son runbook ici. Un runbook répond à trois questions : **qu'est-ce que cela signifie pour l'utilisateur ?**, **comment confirmer ?**, **comment rétablir ?**

Les commandes se lancent depuis le dossier `demo/`, sur le serveur qui héberge le service.

| Alerte | Sévérité | Destinataire |
|---|---|---|
| RadarApiDown | critical | Astreinte |
| RadarApiErrorRate | critical | Astreinte |
| RadarCollectorDown, RadarCollectorStale | warning | Canal d'exploitation (ops) |
| RadarErrorLogsSpike | warning | Canal d'exploitation (ops) |
| RadarDisruptionDetected | info | Équipe veille, **jamais l'astreinte** |

**Principe commun : rétablir d'abord, comprendre ensuite.** Chaque minute de panne consomme le budget d'erreur (0,5 % de 30 jours, soit environ 3 h 36 par mois).

---

## RadarApiDown

**Signification.** Prometheus ne joint plus `radar-api` depuis 30 s : le front affiche « API indisponible ».

**Confirmer.**
```bash
docker compose ps radar-api                 # état et nombre de redémarrages
docker compose logs --tail 100 radar-api    # erreur au démarrage : migration Flyway, base inaccessible
```

**Rétablir.**
- Le conteneur est arrêté : `docker compose start radar-api`. Docker le redémarre seul après un plantage (`restart: unless-stopped`) ; s'il redémarre en boucle, lire les journaux.
- L'échec suit une mise en production : revenir à la version précédente, puis chercher la cause à froid.
  ```bash
  RADAR_VERSION=1.0.0 docker compose up -d --no-build radar-api
  ```
- La base est inaccessible : voir RadarApiErrorRate.

## RadarApiErrorRate

**Signification.** Plus de 5 % des requêtes de l'API échouent (erreurs 5xx) depuis 1 min. Les utilisateurs voient des erreurs.

**Confirmer.** Tableau Grafana « Radar d'innovation : démonstration » ; journaux : Grafana, Explore, Loki, `{app="radar-api", level="error"}`.

**Causes fréquentes et remèdes.**

| Cause | Indice | Remède |
|---|---|---|
| Base arrêtée | `Connection refused` ou `Connection is not available` dans les journaux | `docker compose start postgres` ; Docker la redémarre seul après un plantage |
| Données corrompues ou disque perdu | La base ne démarre plus | `make restauration` (dernière sauvegarde, environ 30 min de bout en bout) |
| Nouvelle version défectueuse | Les erreurs commencent juste après une mise en production | Retour à la version précédente (voir RadarApiDown) |

## RadarCollectorDown

**Signification.** Le collecteur est arrêté. Le radar reste consultable, mais **la veille ne se met plus à jour** : c'est un incident de veille, pas une urgence technique.

**Rétablir.** `docker compose start signal-collector`, dans la journée. Signaler l'interruption dans la prochaine note de veille.

## RadarCollectorStale

**Signification.** Une source n'a pas été collectée avec succès depuis plus de 6 h. Le radar fonctionne, mais **la veille n'est plus à jour sur cette source** : c'est la panne silencieuse du pilote, désormais détectée.

**Confirmer.** Grafana : « Ancienneté de la dernière collecte réussie » ; Loki : `{app="signal-collector", level="error"}`.

**Causes fréquentes et remèdes.**

| Cause | Indice dans les journaux | Remède |
|---|---|---|
| Quota de l'API source atteint | `403` ou `429` | Ajouter un jeton (`GITHUB_TOKEN` dans le fichier `.env`), espacer la collecte |
| Source modifiée (format, URL) | Erreur de désérialisation | Corriger le client de la source, ajouter un test de non-régression |
| API interne injoignable | `Connection refused` vers `radar-api` | Vérifier que l'API tourne et que les deux conteneurs sont sur le réseau `collecte` |

## RadarErrorLogsSpike

**Signification.** Plus de 0,05 journal d'erreur par seconde pour une application, d'après les métriques produites par Vector. Cette alerte détecte des erreurs **qui ne se traduisent pas encore** en réponses 5xx (tâches de fond, collecte).

**Confirmer.** Grafana, Explore, Loki : `{app="<app>", level="error"}`.

## RadarDisruptionDetected

**Alerte métier, pas un incident.** Un sujet de la liste de surveillance dépasse l'indice de rupture (0,5 en production, 0,3 en démonstration). Destinataire : l'équipe veille, sur son canal, **jamais l'astreinte**.

**Action.** Inscrire le sujet à l'ordre du jour de la revue hebdomadaire ; rendre un verdict (`pertinent` ou `bruit`) dans l'interface du radar ; si le sujet est pertinent, rédiger une fiche d'analyse.

---

## Sauvegarde et restauration

| Opération | Commande | Fréquence |
|---|---|---|
| Sauvegarde | `make sauvegarde` (fichier dans `demo/sauvegardes/`) | Chaque nuit (tâche planifiée du serveur), copie hors du serveur |
| Restauration | `make restauration` (dernière sauvegarde) | Exercice chaque mois : une sauvegarde jamais restaurée n'est pas une sauvegarde |

Perte maximale acceptée : 24 h. Les signaux se recollectent depuis les sources ; seuls les verdicts des analystes sont irremplaçables.
