#!/usr/bin/env bash
# Scénarios de démonstration de l'étude de cas.
#   demo.sh load [secondes]       trafic continu sur l'API (alimente métriques, SLO, traces)
#   demo.sh failover              perte du primaire PostgreSQL : bascule automatique
#   demo.sh canary                déploiement de la v2 de radar-api avec 10 % du trafic
#   demo.sh promote | rollback    poids 0/100 (promotion) ou 100/0 (retour arrière) du canari
#   demo.sh fault | fault-off     injection de 30 % d'erreurs 503 : alerte de consommation du budget d'erreur
#   demo.sh egress                preuve du contrôle de sortie : un hôte non déclaré est bloqué
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NS=radar
URL=${RADAR_URL:-http://localhost:8080}

route_weights() { # $1 = poids v1, $2 = poids v2
  for vs in radar-edge:1 radar-api-mesh:0; do
    name=${vs%%:*}; idx=${vs##*:}
    kubectl -n "$NS" patch virtualservice "$name" --type=json -p "[{\"op\":\"replace\",\"path\":\"/spec/http/$idx/route\",\"value\":[
      {\"destination\":{\"host\":\"radar-api\",\"subset\":\"v1\",\"port\":{\"number\":8080}},\"weight\":$1},
      {\"destination\":{\"host\":\"radar-api\",\"subset\":\"v2\",\"port\":{\"number\":8080}},\"weight\":$2}]}]"
  done
}

case "${1:-}" in
  load)
    duration=${2:-300}; end=$((SECONDS + duration)); n=0; errors=0
    echo "Trafic sur $URL/api/radar pendant ${duration}s (Ctrl+C pour arrêter)…"
    while [ $SECONDS -lt $end ]; do
      code=$(curl -s -o /dev/null -w '%{http_code}' "$URL/api/radar" || echo 000)
      n=$((n + 1)); [ "$code" -ge 500 ] && errors=$((errors + 1))
      [ $((n % 50)) -eq 0 ] && echo "  $n requêtes, $errors erreurs 5xx"
      sleep 0.2
    done ;;

  failover)
    primary=$(kubectl -n "$NS" get cluster radar-db -o jsonpath='{.status.currentPrimary}')
    echo "Primaire actuel : $primary. Suppression du pod (simulation de panne de nœud)."
    kubectl -n "$NS" delete pod "$primary" --wait=false
    start=$SECONDS
    until new=$(kubectl -n "$NS" get cluster radar-db -o jsonpath='{.status.currentPrimary}') && [ "$new" != "$primary" ]; do sleep 1; done
    echo "Nouveau primaire : $new (bascule en $((SECONDS - start)) s)"
    kubectl -n "$NS" get pods -l cnpg.io/cluster=radar-db -L role
    echo "Contrôle applicatif :"; curl -s "$URL/api/stats"; echo ;;

  canary)
    kubectl apply -k "$ROOT/deploy/app/overlays/dev-canary"
    kubectl -n "$NS" rollout status deployment/radar-api-v2 --timeout=5m
    echo "v2 déployée : 10 % du trafic. Observer le panneau Grafana « Trafic par version », puis lancer demo.sh promote ou rollback" ;;

  promote)  route_weights 0 100;  echo "Promotion : 100 % du trafic sur v2" ;;
  rollback) route_weights 100 0;  echo "Retour arrière : 100 % du trafic sur v1" ;;

  fault)
    kubectl -n "$NS" patch virtualservice radar-edge --type=json -p '[{"op":"add","path":"/spec/http/1/fault",
      "value":{"abort":{"percentage":{"value":30},"httpStatus":503}}}]'
    echo "30 % d'erreurs 503 injectées sur /api. Lancer « demo.sh load » : RadarApiErrorBudgetBurnFast se déclenche en quelques minutes." ;;

  fault-off)
    kubectl -n "$NS" patch virtualservice radar-edge --type=json -p '[{"op":"remove","path":"/spec/http/1/fault"}]'
    echo "Injection de pannes retirée." ;;

  egress)
    echo "Depuis le collecteur, vers une source déclarée (api.github.com) puis non déclarée (example.com) :"
    kubectl -n "$NS" exec deploy/signal-collector -c istio-proxy -- \
      sh -c 'curl -s -o /dev/null -w "api.github.com : %{http_code}\n" https://api.github.com/zen; curl -s -o /dev/null -w "example.com : %{http_code} (bloqué)\n" https://example.com || echo "example.com : connexion refusée (bloqué)"' ;;

  *)
    sed -n '2,10p' "$0"; exit 1 ;;
esac
