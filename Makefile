help: ## Show this help
	@printf "\033[33m%s:\033[0m\n" 'Run: make <target> where <target> is one of the following'
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  \033[32m%-18s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

start: ## Run application containers (except frontend)
	@docker-compose -f development/docker-compose.yaml up --build -d

reset: ## Reset all data
	@docker-compose -f development/docker-compose.yaml down --volumes

stop: ## Stop all application containers
	@docker-compose -f development/docker-compose.yaml -f stop

create-migration: ## Generate Postgres migration file
	@touch app/src/main/resources/migrations/database/V`date '+%Y%m%d%H%M%S'`__new_migration.sql

create-migration-keycloak: ## Generate Keycloak migration file
	@touch app/src/main/resources/migrations/keycloak/V`date '+%Y%m%d%H%M%S'`__new_migration.yaml


run: ## Run api application
	./gradlew run

.DEFAULT_GOAL := help
