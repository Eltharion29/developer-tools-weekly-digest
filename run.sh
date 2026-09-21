#!/bin/sh
set -eu

OUT="${TMPDIR:-/tmp}/developer-digest-cron-classes"
mkdir -p "$OUT"
find src/main/java src/test/java -name '*.java' -print | sort | xargs javac -d "$OUT"

case "${1:-test}" in
  test) java -cp "$OUT" dev.digest.service.DeveloperDigestServiceTest ;;
  register) java -cp "$OUT" dev.digest.DigestSchedulerApplication register ;;
  serve) java -cp "$OUT" dev.digest.DigestSchedulerApplication ;;
  *) echo "usage: ./run.sh [test|register|serve]" >&2; exit 2 ;;
esac
