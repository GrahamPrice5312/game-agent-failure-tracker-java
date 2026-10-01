#!/usr/bin/env sh
set -eu

classes="${TMPDIR:-/tmp}/game-agent-errors-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java -name '*.java')
java -cp "$classes" dev.learninggame.errors.ModerationLoopExample
