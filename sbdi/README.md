# Spatial-service

## Setup

### Config and data directory
Create data directory at `/data/spatial-service` and populate as below (it is easiest to symlink the config files to the ones in this repo):
```
mats@xps-13:/data/spatial-service$ tree 
.
└── config
    └── spatial-service-config.yml -> /home/mats/src/biodiversitydata-se/spatial-service/sbdi/data/config/spatial-service-config.yml
```

You'll also need to create the directories `/data/spatial-data` and `/data/geoserver_data_dir`. 

### Database
An empty database will be created the first time the application starts. You can then export the database from production and import it.

### GeoServer
By default the development configuration will use a local GeoServer running at http://localhost:8079/geoserver. Alternatively you can use the production GeoServer (for read-only). Change `geoserver.url` to https://spatial.biodiversitydata.se/geoserver.

## Usage
Run locally:
```
make run
```

Build and run in Docker (using Tomcat). This requires a small change in the config file to work. See comment in Makefile.
```
make run-docker
```

Make a release. This will create a new tag and push it. A new Docker container will be built on Github.
```
mats@xps-13:~/src/biodiversitydata-se/spatial-service (master *)$ make release

Current version: 1.0.1. Enter the new version (or press Enter for 1.0.2): 
Updating to version 1.0.2
Tag 1.0.2 created and pushed.
```
