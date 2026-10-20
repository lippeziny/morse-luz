#!/bin/sh
# Small bootstrap launcher for this project. It downloads Gradle 8.13 on first use
# when neither the wrapper JAR nor a system Gradle installation is present.
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
VERSION=8.13
DIST_NAME="gradle-${VERSION}-bin"
EXPECTED_SHA256="20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"
GRADLE_USER_HOME=${GRADLE_USER_HOME:-"$HOME/.gradle"}
CACHE_DIR="$GRADLE_USER_HOME/wrapper/dists/morse-luz/$DIST_NAME"
GRADLE_HOME="$CACHE_DIR/gradle-$VERSION"

if [ -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  if [ -n "${JAVA_HOME:-}" ]; then JAVACMD="$JAVA_HOME/bin/java"; else JAVACMD="java"; fi
  exec "$JAVACMD" ${JAVA_OPTS:-} ${GRADLE_OPTS:-} -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi

if [ -x "$GRADLE_HOME/bin/gradle" ]; then
  exec "$GRADLE_HOME/bin/gradle" "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  SYSTEM_VERSION=$(gradle --version | awk '/^Gradle / { print $2; exit }')
  if [ "$SYSTEM_VERSION" = "$VERSION" ]; then
    exec gradle "$@"
  fi
fi

mkdir -p "$CACHE_DIR"
ZIP_FILE="$CACHE_DIR/gradle-${VERSION}-bin.zip"
if [ ! -f "$ZIP_FILE" ]; then
  echo "Baixando Gradle $VERSION para a primeira compilação..." >&2
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --retry 3 "https://services.gradle.org/distributions/gradle-${VERSION}-bin.zip" --output "$ZIP_FILE"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP_FILE" "https://services.gradle.org/distributions/gradle-${VERSION}-bin.zip"
  else
    echo "Instale curl ou wget para baixar o Gradle." >&2
    exit 1
  fi
fi

if [ -f "$ZIP_FILE" ]; then
  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL_SHA256=$(sha256sum "$ZIP_FILE" | awk '{print $1}')
  elif command -v shasum >/dev/null 2>&1; then
    ACTUAL_SHA256=$(shasum -a 256 "$ZIP_FILE" | awk '{print $1}')
  else
    echo "Não encontrei uma ferramenta SHA-256 para verificar o Gradle." >&2
    exit 1
  fi
  if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
    rm -f "$ZIP_FILE"
    echo "A verificação SHA-256 do Gradle falhou; o arquivo foi removido." >&2
    exit 1
  fi
fi

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  command -v unzip >/dev/null 2>&1 || { echo "Instale unzip para extrair o Gradle." >&2; exit 1; }
  TEMP_DIR="$CACHE_DIR/unpack-tmp"
  rm -rf "$TEMP_DIR"
  mkdir -p "$TEMP_DIR"
  unzip -q "$ZIP_FILE" -d "$TEMP_DIR"
  rm -rf "$GRADLE_HOME"
  mv "$TEMP_DIR/gradle-$VERSION" "$GRADLE_HOME"
  rm -rf "$TEMP_DIR"
fi
exec "$GRADLE_HOME/bin/gradle" "$@"
