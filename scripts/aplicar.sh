#!/usr/bin/env bash
# Desativa HDR em TVs TCL (Android TV 12+) e força o sistema a aplicar.
# Uso: ./scripts/aplicar.sh [IP:PORTA]   (padrão: 192.168.1.16:5555)
set -euo pipefail

TV="${1:-192.168.1.16:5555}"
A=(adb -s "$TV")

tela_tem() { # retorna 0 se o texto aparece na tela atual
  "${A[@]}" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  "${A[@]}" shell cat /sdcard/ui.xml | grep -q "text=\"$1\""
}
tecla() { "${A[@]}" shell input keyevent "$@"; sleep 1; }

echo "> Conectando em $TV"
adb connect "$TV" >/dev/null

echo "> Gravando configurações (modo Manual + DV, HDR10, HLG, HDR10+ desativados)"
"${A[@]}" shell settings put global are_user_disabled_hdr_formats_allowed 0
"${A[@]}" shell settings put global user_disabled_hdr_formats 1,2,3,4

echo "> Abrindo o menu escondido 'Tela e som'"
"${A[@]}" shell am start -n com.android.tv.settings/.device.displaysound.DisplaySoundActivity >/dev/null
sleep 2

# Tela e som: Tamanho do texto, Frame rate, Resolução, Configurações de tela avançadas
tecla 20 20 20
tecla 23
sleep 1
if ! tela_tem "Seleção de formato"; then
  echo "! Não cheguei em 'Configurações de tela avançadas'. Faça o passo 3.3 do README na mão."
  exit 1
fi

# Configurações de tela avançadas: Permitir modo jogo, Seleção de formato
tecla 20
tecla 23
sleep 1
if ! tela_tem "Manual"; then
  echo "! Não cheguei em 'Seleção de formato'. Faça o passo 3.3 do README na mão."
  exit 1
fi

echo "> Alternando Automático -> Manual para o sistema aplicar"
tecla 23      # Automático (o foco começa nele)
tecla 20      # desce para Manual
tecla 23      # Manual
sleep 1

"$(dirname "$0")/verificar.sh" "$TV"
