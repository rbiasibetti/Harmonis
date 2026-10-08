#!/usr/bin/env bash
# Wrapper de conveniencia: usa o JDK 21 e o Maven em cache, independente do PATH do shell.
set -e
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/jdks/jdk-21.0.12.1+1}"
MVN_BIN="$HOME/.m2/wrapper/dists/apache-maven-3.9.9/3477a4f1/bin/mvn"
if [ ! -x "$MVN_BIN" ]; then
  MVN_BIN="$(command -v mvn)"
fi
exec "$MVN_BIN" "$@"
