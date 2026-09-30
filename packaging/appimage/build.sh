#!/usr/bin/env bash
# Builds build/appimage/WideShare-x86_64.AppImage. Run on x86_64 Linux (or WSL), with JDK 17 on PATH/JAVA_HOME.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"

out="build/appimage"
appdir="$out/WideShare.AppDir"

./gradlew createDistributable --console=plain

rm -rf "$appdir"
mkdir -p "$appdir/usr"
# jpackage distribution: WideShare/{bin,lib} with the embedded JRE.
cp -r build/compose/binaries/main/app/WideShare "$appdir/usr/WideShare"

cp packaging/icon.png "$appdir/wideshare.png"
cp packaging/icon.png "$appdir/.DirIcon"

cat > "$appdir/wideshare.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=WideShare
Comment=Share one mouse and keyboard across computers
Comment[pt]=Compartilhe mouse e teclado entre computadores
Comment[es]=Comparte ratón y teclado entre ordenadores
Exec=WideShare
Icon=wideshare
Categories=Utility;Network;
Terminal=false
EOF

cat > "$appdir/AppRun" <<'EOF'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/WideShare/bin/WideShare" "$@"
EOF
chmod +x "$appdir/AppRun"

tool="$out/appimagetool-x86_64.AppImage"
if [ ! -x "$tool" ]; then
    curl -fsSL -o "$tool" https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage
    chmod +x "$tool"
fi

# --appimage-extract-and-run avoids depending on FUSE (missing on WSL and in containers).
ARCH=x86_64 "$tool" --appimage-extract-and-run "$appdir" "$out/WideShare-x86_64.AppImage"
echo "Generated: $out/WideShare-x86_64.AppImage"
