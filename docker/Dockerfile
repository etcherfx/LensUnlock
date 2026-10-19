# Build environment: JDK 21 plus the Android SDK. Nothing Android-related is installed on the host.
#
#   docker build -t lensunlock-build docker
#   docker run --rm -v "$PWD:/work" -v lensunlock-gradle:/root/.gradle lensunlock-build ./gradlew check assembleDebug assembleRelease
FROM eclipse-temurin:21-jdk

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

ARG CMDLINE_TOOLS_ZIP=commandlinetools-linux-16111833_latest.zip
ARG CMDLINE_TOOLS_SHA256=0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/build-tools/37.0.0

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /tmp/cmdline-tools.zip "https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}" \
    && echo "${CMDLINE_TOOLS_SHA256}  /tmp/cmdline-tools.zip" | sha256sum -c - \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools \
    && mv /tmp/cmdline-tools/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm -rf /tmp/cmdline-tools /tmp/cmdline-tools.zip

RUN (yes || true) | sdkmanager --licenses > /dev/null \
    && sdkmanager --install "platforms;android-37.0" "build-tools;37.0.0" "platform-tools" \
    && chmod -R a+rwX "${ANDROID_HOME}"

WORKDIR /work
