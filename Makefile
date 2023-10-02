run:
	docker compose up --detach pgsql
	./gradlew bootRun

# In spatial-service-config-yml you need to change dataSource.url to 'pgsql' for this to work
run-docker:
	./gradlew war
	docker compose build --no-cache
	docker compose up --detach

release:
	@./sbdi/make-release.sh
