#!/bin/sh

# gamedir from lutris
GAMEDIR="Games"

# fully qualified java path
java="/usr/lib/jvm/openjdk-bin-11/bin/java"

# fully qualified lutris install path
path="${HOME}/${GAMEDIR}/jagex-launcher/drive_c/Program Files (x86)/Jagex Launcher/Games/RuneLite"

class="${path}/RuneLite.jar"
main="net.runelite.client.RuneLite"

"${java}" -cp "${class}" "${main}"
