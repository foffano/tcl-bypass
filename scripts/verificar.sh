#!/usr/bin/env bash
# Mostra o estado do HDR na TV.
# Uso: ./scripts/verificar.sh [IP:PORTA]
set -euo pipefail

TV="${1:?uso: $0 IP_DA_TV:5555}"
A=(adb -s "$TV")
adb connect "$TV" >/dev/null

echo "are_user_disabled_hdr_formats_allowed = $("${A[@]}" shell settings get global are_user_disabled_hdr_formats_allowed)  (0 = Manual)"
echo "user_disabled_hdr_formats             = $("${A[@]}" shell settings get global user_disabled_hdr_formats)  (esperado: 1,2,3,4)"

BASE=$("${A[@]}" shell dumpsys display | grep -oE "userDisabledHdrTypes \[[^]]*\]" | head -1)
echo "display (mBaseDisplayInfo)            = $BASE"

if [[ "$BASE" == *"1, 2, 3, 4"* ]]; then
  echo "OK: HDR desativado e aplicado pelo sistema."
else
  echo "ATENÇÃO: o sistema ainda não aplicou. Rode scripts/aplicar.sh ou faça o passo 3.3 do README."
  exit 1
fi
