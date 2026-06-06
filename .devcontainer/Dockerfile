FROM mcr.microsoft.com/devcontainers/java:25

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl jq unzip wget \
    && rm -rf /var/lib/apt/lists/*