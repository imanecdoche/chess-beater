#!/usr/bin/env bash
GRADLE_BIN="/home/fatihfarhat/.gradle/wrapper/dists/gradle-8.11.1-all/2qik7nd48slq1ooc2496ixf4i/gradle-8.11.1/bin/gradle"
if [ -x "$GRADLE_BIN" ]; then
    exec "$GRADLE_BIN" --no-daemon "$@"
else
    echo "Gradle binary not found at $GRADLE_BIN"
    exit 1
fi
