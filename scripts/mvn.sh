#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" && -x /workspace/toolchains/jdk/jdk-21.0.8+9/bin/javac ]]; then
  export JAVA_HOME=/workspace/toolchains/jdk/jdk-21.0.8+9
fi
if command -v mvn >/dev/null 2>&1; then
  raizes_maven=$(command -v mvn)
else
  raizes_maven=/workspace/toolchains/maven/apache-maven-3.9.11/bin/mvn
fi
if [[ -n "${JAVA_HOME:-}" ]]; then raizes_java="$JAVA_HOME/bin/java"; else raizes_java=$(command -v java); fi
"$raizes_java" scripts/MavenProxy.java
if [[ -f /etc/ssl/certs/java/cacerts ]]; then export MAVEN_OPTS="${MAVEN_OPTS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"; fi
exec "$raizes_maven" --batch-mode --strict-checksums --settings .m2/settings-proxy.xml -Dmaven.repo.local="${MAVEN_REPO_LOCAL:-$PWD/.m2/repository}" "$@"
