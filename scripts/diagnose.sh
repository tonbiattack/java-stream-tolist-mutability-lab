#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT_DIR/out-diagnostic"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

javac --release 21 -d "$OUT_DIR" \
  "$ROOT_DIR/src/main/java/jp/example/recipients/RecipientPlanner.java" \
  "$ROOT_DIR/src/test/java/jp/example/recipients/RecipientPlannerDiagnostic.java"

java -cp "$OUT_DIR" jp.example.recipients.RecipientPlannerDiagnostic
