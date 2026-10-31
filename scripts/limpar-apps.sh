#!/usr/bin/env bash
# Remove apps de fábrica desnecessários da TCL (só para o usuário 0, reversível).
# Uso: ./scripts/limpar-apps.sh IP_DA_TV:5555
# Para trazer um de volta: adb shell cmd package install-existing <pacote>
set -euo pipefail

TV="${1:?uso: $0 IP_DA_TV:5555}"
A=(adb -s "$TV")
adb connect "$TV" >/dev/null

APPS=(
  com.tcl.channelplus
  com.tcl.waterfall.overseas
  com.tcl.tv.tclhome_passive
  com.tcl.exhibit
  com.tcl.esticker
  com.tcl.ocean.instructions
  com.tcl.repairguide
  com.tcl.messagebox
  com.tcl.usercenter
  com.tcl.logkit
  com.tcl.gamebar
  com.tcl.smartalexa
  com.tcl.magiconnectfree
  com.google.android.play.games
  com.google.android.youtube.tvmusic
)

for p in "${APPS[@]}"; do
  printf "%-38s %s\n" "$p" "$("${A[@]}" shell pm uninstall -k --user 0 "$p" 2>&1 | tr -d '\r')"
done
