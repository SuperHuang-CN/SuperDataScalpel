#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUTPUT="$ROOT/data-scalpel-ui/src/modules/task/model/sparkJavaApiIndex.generated.json"

"$ROOT/mvnw" -q -pl data-scalpel-task-engine -am -DskipTests test-compile
"$ROOT/mvnw" -q -pl data-scalpel-task-engine \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=cn.superhuang.datascalpel.taskengine.tools.SparkJavaApiIndexGenerator \
  -Dexec.args="$OUTPUT" \
  exec:java

echo "Updated $OUTPUT"
