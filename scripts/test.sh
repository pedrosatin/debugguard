#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/test-classes
javac --release 17 -d build/test-classes \
  app/src/main/java/dev/satin/debugguard/Transaction.java \
  app/src/main/java/dev/satin/debugguard/SafeProfile.java \
  app/src/test/java/dev/satin/debugguard/TransactionTest.java
java -cp build/test-classes dev.satin.debugguard.TransactionTest
