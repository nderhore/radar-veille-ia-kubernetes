# Le radar industrialisé sous Docker Compose. `make help` liste les cibles.
SHELL := /bin/bash
.DEFAULT_GOAL := help
.PHONY: help up up-complet down charge panne-api retour-api panne-collecteur retour-collecteur \
        panne-base retour-base sauvegarde restauration erreurs liens

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
	  curl -s -o /dev/null http://localhost:8088/api/radar; curl -s -o /dev/null http://localhost:8088/api/signals; \
	  n=$$((n + 2)); [ $$((n % 100)) -eq 0 ] && echo "  $$n requêtes"; sleep 0.2; done

erreurs: ## Provoque des requêtes invalides (erreurs 4xx visibles dans le tableau de bord)
	@for i in $$(seq 1 30); do curl -s -o /dev/null -X POST -H 'Content-Type: application/json' \
	  -d '{"term":"x","verdict":"peut-etre"}' http://localhost:8088/api/feedback; done; echo "30 requêtes refusées (400)"

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

panne-base: ## Scénario : arrêt de PostgreSQL ; lancer `make charge` pour voir RadarApiErrorRate (astreinte)
	docker compose stop postgres
	@echo "Observer : l'API répond en erreur ; avec du trafic (make charge), RadarApiErrorRate (critique) en ~2 min."

retour-base: ## Redémarre PostgreSQL (les données du volume sont intactes)
	docker compose start postgres

sauvegarde: ## Sauvegarde immédiate de la base dans sauvegardes/ (en production : chaque nuit, copiée hors du serveur)
	@mkdir -p sauvegardes; f=sauvegardes/radar-$$(date +%Y%m%d-%H%M%S).dump; \
	docker compose exec -T postgres pg_dump -U radar -d radar -Fc > "$$f" && echo "Sauvegarde : $$f ($$(du -h "$$f" | cut -f1))"

restauration: ## Restaure la dernière sauvegarde (API et collecteur arrêtés pendant l'opération)
	@f=$$(ls -t sauvegardes/*.dump 2>/dev/null | head -1); [ -n "$$f" ] || { echo "Aucune sauvegarde dans sauvegardes/"; exit 1; }; \
	echo "Restauration de $$f"; \
	docker compose stop radar-api signal-collector && \
	docker compose exec -T postgres pg_restore -U radar -d radar --clean --if-exists --no-owner < "$$f" && \
	docker compose start radar-api signal-collector && echo "Restauration terminée."

liens: ## Affiche les adresses utiles
	@echo ""
	@echo "  Radar (front)        http://localhost:8088"
	@echo "  API (via le front)   http://localhost:8088/api/radar"
	@echo "  Métriques            http://localhost:8081/actuator/prometheus"
	@echo "  Grafana              http://localhost:3000   (profil complet ; admin / radar-demo)"
	@echo "  Prometheus           http://localhost:9090/alerts"
	@echo "  Alertmanager         http://localhost:9093"
