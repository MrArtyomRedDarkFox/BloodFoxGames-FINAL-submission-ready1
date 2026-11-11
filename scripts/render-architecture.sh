#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
command -v dot >/dev/null 2>&1 || { echo 'Graphviz dot is required (Ubuntu: sudo apt install graphviz)' >&2; exit 2; }
command -v magick >/dev/null 2>&1 || command -v convert >/dev/null 2>&1 || { echo 'ImageMagick is required to optimize the PNG (Ubuntu: sudo apt install imagemagick)' >&2; exit 2; }
TMP="$ROOT/docs/.architecture-full.png"
dot -Tpng -Gdpi=150 "$ROOT/docs/architecture.dot" -o "$TMP"
dot -Tpdf "$ROOT/docs/architecture.dot" -o "$ROOT/docs/architecture.pdf"
dot -Tsvg "$ROOT/docs/architecture.dot" -o "$ROOT/docs/architecture.svg"
if command -v magick >/dev/null 2>&1; then
  magick "$TMP" -resize 1250x1250 -colors 64 -define png:compression-level=9 -strip "$ROOT/docs/architecture.png"
else
  convert "$TMP" -resize 1250x1250 -colors 64 -define png:compression-level=9 -strip "$ROOT/docs/architecture.png"
fi
rm -f "$TMP"
echo 'Architecture exports updated: docs/architecture.png, .pdf, .svg'
