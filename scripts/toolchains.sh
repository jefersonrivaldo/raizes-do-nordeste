#!/usr/bin/env bash
set -euo pipefail
# Instalação opcional da nuvem sem modificar pacotes do sistema.
raizes_tools=${RAIZES_TOOLCHAINS:-/workspace/toolchains}
mkdir -p "$raizes_tools/jdk" "$raizes_tools/maven"
if [[ ! -x "$raizes_tools/jdk/jdk-21.0.8+9/bin/javac" ]]; then
  raizes_jdk_url='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.8_9.tar.gz'
  curl -fsSL "$raizes_jdk_url" -o "$raizes_tools/jdk/temurin21.tar.gz"
  curl -fsSL "$raizes_jdk_url.sha256.txt" -o "$raizes_tools/jdk/temurin21.sha256"
  raizes_jdk_hash=$(cut -d ' ' -f1 "$raizes_tools/jdk/temurin21.sha256")
  printf '%s  %s\n' "$raizes_jdk_hash" "$raizes_tools/jdk/temurin21.tar.gz" | sha256sum --check --status
  tar -xzf "$raizes_tools/jdk/temurin21.tar.gz" -C "$raizes_tools/jdk"
fi
if [[ ! -x "$raizes_tools/maven/apache-maven-3.9.11/bin/mvn" ]]; then
  raizes_maven_url='https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.tar.gz'
  curl -fsSL "$raizes_maven_url" -o "$raizes_tools/maven/apache-maven-3.9.11-bin.tar.gz"
  curl -fsSL "$raizes_maven_url.sha512" -o "$raizes_tools/maven/apache-maven-3.9.11-bin.tar.gz.sha512"
  raizes_maven_hash=$(cut -d ' ' -f1 "$raizes_tools/maven/apache-maven-3.9.11-bin.tar.gz.sha512")
  printf '%s  %s\n' "$raizes_maven_hash" "$raizes_tools/maven/apache-maven-3.9.11-bin.tar.gz" | sha512sum --check --status
  tar -xzf "$raizes_tools/maven/apache-maven-3.9.11-bin.tar.gz" -C "$raizes_tools/maven"
fi
printf 'JDK 21 e Maven preparados.\n'
