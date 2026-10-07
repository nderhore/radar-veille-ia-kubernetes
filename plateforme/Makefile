# Code source du radar : tests et construction. Le lancement se fait depuis ../demo (Docker Compose).
SHELL := /bin/bash
.DEFAULT_GOAL := help

.PHONY: help test images

help: ## Affiche cette aide
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-10s\033[0m %s\n", $$1, $$2}'

test: ## Tests des services Java (Maven) et construction du front (Angular)
	cd services && mvn -B -q verify
	cd frontend/radar-web && npm ci --no-audit --no-fund && npx ng build

images: ## Construit les images Docker (version : RADAR_VERSION, 1.0.0 par défaut)
	cd ../demo && docker compose build
