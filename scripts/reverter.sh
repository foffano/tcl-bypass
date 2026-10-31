#!/usr/bin/env bash
# Volta o HDR ao modo Automático (padrão de fábrica).
# Uso: ./scripts/reverter.sh [IP:PORTA]
set -euo pipefail

TV="${1:?uso: $0 IP_DA_TV:5555}"
A=(adb -s "$TV")
adb connect "$TV" >/dev/null

"${A[@]}" shell settings put global are_user_disabled_hdr_formats_allowed 1
"${A[@]}" shell settings delete global user_disabled_hdr_formats >/dev/null

echo "Configuração revertida. Para garantir que o sistema aplique, abra o menu e marque 'Automático':"
echo "  adb -s $TV shell am start -n com.android.tv.settings/.device.displaysound.DisplaySoundActivity"
echo "  -> Configurações de tela avançadas -> Seleção de formato -> Automático"
