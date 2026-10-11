#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi
raizes_java=${JAVA_HOME:-/workspace/toolchains/jdk/jdk-21.0.8+9}/bin/java
if [[ ! -x "$raizes_java" ]]; then raizes_java=$(command -v java); fi
exec "$raizes_java" -jar target/raizes-do-nordeste-1.0.0.jar
