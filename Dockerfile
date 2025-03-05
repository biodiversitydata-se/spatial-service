FROM tomcat:9.0-jdk11-temurin

ENV TZ=Europe/Stockholm

RUN mkdir -p  \
    /data/spatial-service/config \
    /data/spatial-data \
    /data/spatial-data/uploads \
    /data/spatial-data/public \
    /data/spatial-data/private \
    /data/ala/data/runtime/files \
    /data/spatial-data/modelling/aloc \
    /data/spatial-data/modelling/maxent

# Download and install dependencies
RUN apt-get update && apt-get install -y curl unzip && rm -rf /var/lib/apt/lists/*

#https://nexus.ala.org.au/repository/releases/au/org/ala/spatial/ala-aloc/1.0/ala-aloc-1.0-distribution.jar

# Download aloc.jar
RUN curl -L -o /data/spatial-data/modelling/aloc/aloc.jar "https://nexus.ala.org.au/repository/releases/au/org/ala/spatial/ala-aloc/1.0/ala-aloc-1.0-distribution.jar" && \
    chmod 755 /data/spatial-data/modelling/aloc/aloc.jar

# Add local copy of maxent.zip
ADD maxent/maxent.zip /tmp/maxent.zip

RUN unzip /tmp/maxent.zip -d /tmp/ && \
    mv /tmp/maxent/maxent.jar /data/spatial-data/modelling/maxent/maxent.jar && \
    rm -rf /tmp/maxent /tmp/maxent.zip

RUN chmod 755 /data/spatial-data/modelling/maxent/maxent.jar

COPY build/libs/spatial-service-*.war $CATALINA_HOME/webapps/ws.war

ENV DOCKERIZE_VERSION=v0.9.2

RUN apt-get update \
    && apt-get install -y wget \
    && wget -O - https://github.com/jwilder/dockerize/releases/download/$DOCKERIZE_VERSION/dockerize-linux-amd64-$DOCKERIZE_VERSION.tar.gz | tar xzf - -C /usr/local/bin \
    && apt-get autoremove -yqq --purge wget && rm -rf /var/lib/apt/lists/*
