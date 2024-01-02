run:
	docker compose up --detach postgis geoserver
	./gradlew bootRun

# In spatial-service-config-yml you need to change dataSource.url to 'postgis'
# and geoserver.url to 'geoserver:8079' for this to work
run-docker:
	./gradlew war
	docker compose build --no-cache
	docker compose up --detach

release:
	../sbdi-install/utils/make-release.sh
