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
RUN apt-get update && apt-get install -y curl unzip gettext-base gdal-bin  && rm -rf /var/lib/apt/lists/*

#https://nexus.ala.org.au/repository/releases/au/org/ala/spatial/ala-aloc/1.0/ala-aloc-1.0-distribution.jar

# Download aloc.jar
RUN curl -L -o /data/spatial-data/modelling/aloc/aloc.jar "https://nexus.ala.org.au/repository/releases/au/org/ala/spatial/ala-aloc/1.0/ala-aloc-1.0-distribution.jar" && \
    chmod 755 /data/spatial-data/modelling/aloc/aloc.jar

# Add local copy of maxent.zip
ADD maxent/maxent.zip /tmp/maxent.zip

RUN unzip /tmp/maxent.zip -d /tmp/ && \
    mv /tmp/maxent/maxent.jar /data/spatial-data/modelling/maxent/maxent.jar && \
    rm -rf /tmp/maxent /tmp/maxent.zip

RUN mkdir -p \
  /data/ala/data/alaspatial \
  /data/ala/runtime/output/maxent \
  /data/ala/runtime/files \
  /data/ala/runtime/output \
  /data/ala/data/layers/analysis

RUN chmod 755 /data/spatial-data/modelling/maxent/maxent.jar

COPY build/libs/spatial-service-*.war $CATALINA_HOME/webapps/ws.war

ENV DOCKERIZE_VERSION=v0.9.2

RUN apt-get update \
    && apt-get install -y wget \
    && wget -O - https://github.com/jwilder/dockerize/releases/download/$DOCKERIZE_VERSION/dockerize-linux-amd64-$DOCKERIZE_VERSION.tar.gz | tar xzf - -C /usr/local/bin \
    && apt-get autoremove -yqq --purge wget && rm -rf /var/lib/apt/lists/*

# Create non-root user
ARG USER_NAME=ubuntu
ARG USER_ID=1000
ARG GROUP_ID=1000

# Create non-root user and group if they don't exist
RUN getent group ${USER_NAME} || groupadd -g ${GROUP_ID} ${USER_NAME} && \
    id -u ${USER_NAME} || useradd -u ${USER_ID} -g ${GROUP_ID} -m -d /home/${USER_NAME} -s /bin/bash ${USER_NAME} && \
    chown -R ${USER_NAME}:${USER_NAME} /usr/local/tomcat /data

# Switch to non-root user
USER ${USER_NAME}
