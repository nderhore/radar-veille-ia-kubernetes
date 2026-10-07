# Radar d'innovation : plateforme Kubernetes. `make help` liste les cibles.
SHELL := /bin/bash
.DEFAULT_GOAL := help

.PHONY: help test build local local-down kind-up kind-down validate \
        demo-load demo-failover demo-canary demo-promote demo-rollback demo-fault demo-fault-off demo-egress

help: ## Affiche cette aide
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

test: ## Tests des services Java (Maven) et construction du front (Angular)
	cd services && mvn -B -q verify
	cd frontend/radar-web && npm ci --no-audit --no-fund && npx ng build

local: ## Lance la pile sans Kubernetes (docker compose) : http://localhost:8088
	docker compose up --build -d

local-down: ## Arrête la pile docker compose
	docker compose down -v

kind-up: ## Installe toute la plateforme sur un cluster kind local
	./scripts/bootstrap-kind.sh

kind-down: ## Supprime le cluster kind
	kind delete cluster --name radar

validate: ## Contrôles statiques : Kustomize, schémas Kubernetes et CRD, règles Prometheus
	@for o in dev dev-canary prod; do \
	  kubectl kustomize deploy/app/overlays/$$o | kubeconform -strict -summary \
	    -schema-location default \
	    -schema-location 'https://raw.githubusercontent.com/datreeio/CRDs-catalog/main/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json' || exit 1; \
	done
	@yq '{"groups": .spec.groups}' deploy/app/monitoring/prometheus-rules.yaml > /tmp/radar-rules.yaml
	docker run --rm --entrypoint promtool -v /tmp/radar-rules.yaml:/rules.yaml:ro prom/prometheus:v3.15.0 check rules /rules.yaml

demo-load: ## Génère du trafic sur l'API (5 min)
	./scripts/demo.sh load 300
demo-failover: ## Perte du primaire PostgreSQL et bascule automatique
	./scripts/demo.sh failover
demo-canary: ## Déploie la v2 de radar-api avec 10 % du trafic
	./scripts/demo.sh canary
demo-promote: ## Promotion du canari (100 % sur v2)
	./scripts/demo.sh promote
demo-rollback: ## Retour arrière (100 % sur v1)
	./scripts/demo.sh rollback
demo-fault: ## Injecte 30 % d'erreurs 503 (déclenche l'alerte SLO)
	./scripts/demo.sh fault
demo-fault-off: ## Retire l'injection d'erreurs
	./scripts/demo.sh fault-off
demo-egress: ## Montre le blocage d'un hôte externe non déclaré
	./scripts/demo.sh egress
