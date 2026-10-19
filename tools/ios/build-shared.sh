#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$ROOT"

# Prefer an explicitly selected JDK, then the project-local Temurin installation.
if [ -z "${JAVA_HOME:-}" ]; then
    for candidate in "$ROOT"/.tools/jdk-21*/Contents/Home; do
        if [ -x "$candidate/bin/java" ]; then
            JAVA_HOME="$candidate"
            export JAVA_HOME
            break
        fi
    done
fi
if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 21)
    export JAVA_HOME
fi
GRADLE_USER_HOME=${GRADLE_USER_HOME:-"$ROOT/.tools/gradle"}
export GRADLE_USER_HOME

exec ./gradlew -PiosOnly=true :shared:embedAndSignAppleFrameworkForXcode --console=plain
