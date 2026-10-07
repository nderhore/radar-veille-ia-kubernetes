# Runbooks : alertes de la plateforme radar

Chaque alerte de `deploy/app/monitoring/prometheus-rules.yaml` renvoie ici via son annotation `runbook_url`. Un runbook répond à trois questions : **qu'est-ce que cela signifie pour l'utilisateur ?**, **comment confirmer ?**, **comment rétablir ?**

| Alerte | Sévérité | Destinataire |
|---|---|---|
| RadarApiErrorBudgetBurnFast | critical | Astreinte |
| RadarApiDown | critical | Astreinte |
| RadarPostgresNoSyncReplica | critical | Astreinte |
| RadarApiErrorBudgetBurnSlow, RadarApiLatencyHigh | warning | #radar-ops |
| RadarCollectorStale, RadarCollectorNeverSucceeded, RadarCollectorErrors | warning | #radar-ops |
| RadarPostgresInstanceMissing, RadarPostgresReplicationLag | warning | #radar-ops |
| RadarErrorLogsSpike | warning | #radar-ops |
| RadarDisruptionDetected | info | #veille-radar (équipe veille) |

---

## RadarApiErrorBudgetBurn

**Signification.** Plus de 7,2 % (alerte rapide) ou 3 % (alerte lente) des requêtes vers `radar-api` échouent en 5xx. À ce rythme, le budget d'erreur mensuel (0,5 %) sera épuisé en moins de 2 jours (rapide) ou 5 jours (lente).

**Confirmer.**
1. Tableau Grafana « Radar d'innovation : plateforme », ligne SLO : taux d'erreur par fenêtre, trafic par version.
2. Si une version v2 reçoit du trafic (canari), comparer les erreurs par `destination_version`.
3. Loki : `{namespace="radar", app="radar-api", level="error"}`.

**Rétablir.**
- Erreurs concentrées sur la v2 : `./scripts/demo.sh rollback` (100 % du trafic sur v1).
- Injection de pannes oubliée : `kubectl -n radar get virtualservice radar-edge -o yaml | grep -A3 fault` puis `./scripts/demo.sh fault-off`.
- Erreurs de base de données : voir [RadarPostgresNoSyncReplica](#radarpostgresnosyncreplica).
- Après rétablissement : geler les déploiements non urgents tant que le budget n'est pas reconstitué (politique de budget d'erreur).

## RadarApiLatencyHigh

**Signification.** Le 95ᵉ centile de latence dépasse 1 s pendant 10 min : le front devient lent.

**Confirmer.** `radar:api_latency_p95:5m` dans Prometheus ; traces lentes dans Grafana, Explore, Tempo (filtrer `service.name = radar-api.radar`). Durée de `radar_compute_duration_seconds` (recalcul du radar).

**Rétablir.** Vérifier la saturation CPU et le nombre de réplicas (`kubectl -n radar get hpa radar-api`), les requêtes lentes PostgreSQL (journaux `duration:` au-delà de 500 ms dans Loki), la saturation du pooler (`cnpg_pgbouncer_pools_cl_waiting`).

## RadarApiDown

**Signification.** Aucune instance de `radar-api` n'est collectée par Prometheus depuis 2 min : l'API est probablement indisponible.

**Confirmer.** `kubectl -n radar get pods -l app.kubernetes.io/name=radar-api` ; événements (`kubectl -n radar describe pod …`) ; échec de sonde de démarrage (migrations Flyway, base inaccessible).

**Rétablir.** Si la base est inaccessible : vérifier le cluster (`kubectl -n radar get cluster radar-db`) et le pooler. Si l'échec suit un déploiement : `kubectl -n radar rollout undo deployment/radar-api` (ou revert du commit, Argo CD réconcilie).

## RadarCollectorStale

**Signification.** Une source n'a pas été collectée avec succès depuis plus de 6 h. Le radar continue de fonctionner, mais **la veille n'est plus à jour sur cette source** : risque de manquer un signal.

**Confirmer.** Grafana : « Ancienneté de la dernière collecte réussie » ; Loki : `{app="signal-collector", level="error"}`.

**Causes fréquentes et remèdes.**

| Cause | Indice dans les journaux | Remède |
|---|---|---|
| Quota de l'API source atteint | `403` ou `429` | Ajouter un jeton (secret `radar-collector-secrets`), espacer la collecte |
| Source modifiée (format, URL) | erreur de désérialisation | Corriger le client de la source, ajouter un test de non-régression |
| Sortie bloquée par le maillage | `connection reset`, `503 UF` dans les journaux Envoy | Vérifier le `ServiceEntry` `veille-sources` |
| API interne refusée | `403 RBAC: access denied` | Vérifier l'AuthorizationPolicy `allow-collector-ingest` et le compte de service |

## RadarCollectorErrors

Erreurs répétées sans encore d'obsolescence : traiter comme RadarCollectorStale, avec moins d'urgence.

## RadarPostgresInstanceMissing

**Signification.** Le cluster `radar-db` compte moins de 3 instances saines. Le service continue (bascule automatique), mais la tolérance aux pannes est réduite.

**Confirmer.** `kubectl -n radar get cluster radar-db` ; `kubectl -n radar get pods -l cnpg.io/cluster=radar-db -L role` ; avec le greffon : `kubectl cnpg status radar-db -n radar`.

**Rétablir.** L'opérateur recrée l'instance automatiquement. Si le pod reste en attente : capacité des nœuds, volume (PVC) bloqué sur un nœud indisponible. Ne jamais supprimer un PVC de primaire sans avoir vérifié la promotion d'un réplica.

## RadarPostgresNoSyncReplica

**Signification.** Aucun réplica ne reçoit les WAL en flux. Avec la réplication **synchrone** (`synchronous.number: 1`), les validations de transaction attendent un réplica : **les écritures peuvent être bloquées**.

**Confirmer.** Panneau « Réplicas en flux » ; état du cluster CloudNativePG.

**Rétablir.** Rétablir au moins un réplica (voir ci-dessus). En dernier recours et **par décision explicite** (perte possible de transactions en cas de nouvelle panne), assouplir temporairement la durabilité (`dataDurability: preferred`) et tracer la décision.

## RadarPostgresReplicationLag

**Signification.** Un réplica accuse plus de 30 s de retard : en cas de bascule vers lui, les lectures sur réplicas seraient obsolètes.

**Rétablir.** Vérifier la charge d'écriture (ingestion massive après une panne de collecte), les ressources du réplica, le réseau entre nœuds.

## RadarErrorLogsSpike

**Signification.** Plus de 0,2 journal d'erreur par seconde pour une application, d'après les métriques produites par Vector. Cette alerte détecte des erreurs **qui ne se traduisent pas encore** en réponses 5xx (tâches de fond, collecte).

**Confirmer.** Grafana, Explore, Loki : `{namespace="radar", app="<app>", level="error"}`.

## RadarDisruptionDetected

**Alerte métier, pas un incident.** Un sujet de la liste de surveillance dépasse un indice de rupture de 0,5 depuis 30 min. Destinataire : l'équipe veille, sur son canal, **jamais l'astreinte**.

**Action.** Inscrire le sujet à l'ordre du jour de la revue hebdomadaire de triage ; rendre un verdict (`pertinent` ou `bruit`) dans l'interface du radar ; si pertinent, rédiger une fiche d'analyse.
