# Démonstration « cours » du radar industrialisé (Docker Compose). `make help` liste les cibles.
SHELL := /bin/bash
.DEFAULT_GOAL := help
.PHONY: help up up-complet down charge panne-api retour-api panne-collecteur retour-collecteur erreurs liens

help: ## Affiche cette aide
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

up: ## Application seule : PostgreSQL, API, collecteur, front
	docker compose up --build -d
	@$(MAKE) --no-print-directory liens

up-complet: ## Application + observabilité (Prometheus, Alertmanager, Grafana, Loki, Vector)
	docker compose --profile observabilite up --build -d
	@$(MAKE) --no-print-directory liens

down: ## Arrête tout et supprime les données
	docker compose --profile observabilite down -v

charge: ## Génère du trafic sur l'API pendant 2 minutes
	@end=$$((SECONDS + 120)); n=0; while [ $$SECONDS -lt $$end ]; do \
	  curl -s -o /dev/null http://localhost:8080/api/radar; curl -s -o /dev/null http://localhost:8080/api/signals; \
	  n=$$((n + 2)); [ $$((n % 100)) -eq 0 ] && echo "  $$n requêtes"; sleep 0.2; done

erreurs: ## Provoque des requêtes invalides (erreurs 4xx visibles dans le tableau de bord)
	@for i in $$(seq 1 30); do curl -s -o /dev/null -X POST -H 'Content-Type: application/json' \
	  -d '{"term":"x","verdict":"peut-etre"}' http://localhost:8080/api/feedback; done; echo "30 requêtes refusées (400)"

panne-api: ## Scénario : arrêt de l'API, alerte critique RadarApiDown (routée vers l'astreinte)
	docker compose stop radar-api
	@echo "Observer : le front affiche « API indisponible » ; Alertmanager (http://localhost:9093) reçoit RadarApiDown en ~1 min."

retour-api: ## Rétablit l'API
	docker compose start radar-api

panne-collecteur: ## Scénario : arrêt du collecteur, l'API continue de servir (isolation des pannes, D1)
	docker compose stop signal-collector
	@echo "Observer : le radar reste consultable ; RadarCollectorDown (avertissement, canal d'exploitation) en ~1 min."

retour-collecteur: ## Rétablit le collecteur
	docker compose start signal-collector

liens: ## Affiche les adresses utiles
	@echo ""
	@echo "  Radar (front)        http://localhost:8088"
	@echo "  API                  http://localhost:8080/api/radar"
	@echo "  Métriques            http://localhost:8081/actuator/prometheus"
	@echo "  Grafana              http://localhost:3000   (profil complet ; admin / radar-demo)"
	@echo "  Prometheus           http://localhost:9090/alerts"
	@echo "  Alertmanager         http://localhost:9093"
