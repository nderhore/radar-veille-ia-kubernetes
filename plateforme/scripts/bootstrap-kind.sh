#!/usr/bin/env bash
# Installe la plateforme complète sur un cluster kind local :
# Istio (CNI, istiod, passerelle) · CloudNativePG · Prometheus/Alertmanager/Grafana · Loki · Tempo · Vector · application.
# Prérequis : docker (8 Go de RAM alloués recommandés), kind, kubectl, helm.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEPLOY="$ROOT/deploy"
CLUSTER=radar

# Versions figées : une montée de version est une décision tracée (pull request), pas un effet de bord
ISTIO_VERSION=1.30.5
CNPG_CHART=0.29.1
KPS_CHART=92.1.0
LOKI_CHART=7.3.0
TEMPO_CHART=1.24.4
VECTOR_CHART=0.59.0

step() { printf '\n\033[1;34m%s\033[0m\n' "$*"; }

for bin in docker kind kubectl helm; do
  command -v "$bin" >/dev/null || { echo "outil manquant : $bin" >&2; exit 1; }
done

step "Cluster kind « $CLUSTER »"
if ! kind get clusters | grep -qx "$CLUSTER"; then
  kind create cluster --config "$DEPLOY/kind/cluster.yaml"
fi
kubectl config use-context "kind-$CLUSTER" >/dev/null

step "Dépôts Helm"
helm repo add istio https://istio-release.storage.googleapis.com/charts --force-update >/dev/null
helm repo add cnpg https://cloudnative-pg.github.io/charts --force-update >/dev/null
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts --force-update >/dev/null
helm repo add grafana https://grafana.github.io/helm-charts --force-update >/dev/null
helm repo add vector https://helm.vector.dev --force-update >/dev/null
helm repo update >/dev/null

for ns in istio-system observability cnpg-system; do
  kubectl create namespace "$ns" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
done

step "Observabilité : Prometheus, Alertmanager, Grafana (fournit aussi les CRD ServiceMonitor/PodMonitor)"
# Webhooks fictifs pour la démonstration ; en production : External Secrets (décision D12)
kubectl -n observability create secret generic alertmanager-webhooks \
  --from-literal=slack-ops=https://hooks.slack.com/services/DEMO/DEMO/ops \
  --from-literal=slack-veille=https://hooks.slack.com/services/DEMO/DEMO/veille \
  --from-literal=astreinte=http://localhost:9/astreinte \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
helm upgrade --install kps prometheus-community/kube-prometheus-stack --version "$KPS_CHART" \
  -n observability -f "$DEPLOY/platform/observability/kube-prometheus-stack.values.yaml" --wait --timeout 10m

step "Istio $ISTIO_VERSION : base (CRD), CNI, istiod, passerelle d'entrée"
helm upgrade --install istio-base istio/base --version "$ISTIO_VERSION" -n istio-system --wait
helm upgrade --install istio-cni istio/cni --version "$ISTIO_VERSION" -n istio-system \
  -f "$DEPLOY/platform/istio/cni.values.yaml" --wait
helm upgrade --install istiod istio/istiod --version "$ISTIO_VERSION" -n istio-system \
  -f "$DEPLOY/platform/istio/istiod.values.yaml" --wait
helm upgrade --install istio-ingressgateway istio/gateway --version "$ISTIO_VERSION" -n istio-system \
  -f "$DEPLOY/platform/istio/gateway.values.yaml" --wait
kubectl apply -f "$DEPLOY/platform/istio/podmonitor-gateway.yaml" >/dev/null

step "Opérateur PostgreSQL CloudNativePG"
helm upgrade --install cnpg cnpg/cloudnative-pg --version "$CNPG_CHART" -n cnpg-system \
  -f "$DEPLOY/platform/cnpg/operator.values.yaml" --wait

step "Journaux et traces : Loki, Tempo, Vector"
helm upgrade --install loki grafana/loki --version "$LOKI_CHART" -n observability \
  -f "$DEPLOY/platform/observability/loki.values.yaml" --wait --timeout 10m
helm upgrade --install tempo grafana/tempo --version "$TEMPO_CHART" -n observability \
  -f "$DEPLOY/platform/observability/tempo.values.yaml" --wait
helm upgrade --install vector vector/vector --version "$VECTOR_CHART" -n observability \
  -f "$DEPLOY/platform/observability/vector.values.yaml" --wait

step "Images applicatives : construction et chargement dans kind"
docker build -t radar/radar-api:1.0.0 "$ROOT/services/radar-api"
docker build -t radar/signal-collector:1.0.0 "$ROOT/services/signal-collector"
docker build -t radar/radar-web:1.0.0 "$ROOT/frontend/radar-web"
kind load docker-image --name "$CLUSTER" radar/radar-api:1.0.0 radar/signal-collector:1.0.0 radar/radar-web:1.0.0

step "Application (overlay dev)"
kubectl apply -k "$DEPLOY/app/overlays/dev"

step "Attente du cluster PostgreSQL (3 instances) puis des services"
kubectl -n radar wait cluster/radar-db --for=condition=Ready --timeout=10m
kubectl -n radar rollout status deployment/radar-db-pooler-rw --timeout=5m
for d in radar-api signal-collector radar-web; do
  kubectl -n radar rollout status "deployment/$d" --timeout=10m
done

cat <<EOF

Plateforme opérationnelle
  Radar (front + API via la passerelle Istio) : http://localhost:8080
  Grafana (admin / radar-demo)                : http://localhost:3000 (tableau « Radar d'innovation : plateforme »)
  Prometheus                                  : kubectl -n observability port-forward svc/kps-prometheus 9090
  Alertmanager                                : kubectl -n observability port-forward svc/kps-alertmanager 9093

Démonstrations : make demo-load | demo-failover | demo-canary | demo-fault
EOF
