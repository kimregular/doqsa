FROM mcr.microsoft.com/devcontainers/java:25

ARG GRADLE_VERSION=8.13

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl jq unzip wget \
    && rm -rf /var/lib/apt/lists/*

RUN wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -P /tmp \
    && unzip -q /tmp/gradle-${GRADLE_VERSION}-bin.zip -d /opt \
    && ln -s /opt/gradle-${GRADLE_VERSION}/bin/gradle /usr/local/bin/gradle \
    && rm /tmp/gradle-${GRADLE_VERSION}-bin.zip
