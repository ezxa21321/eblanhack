#!/usr/bin/env bash
set -euo pipefail

OLD_PKG_PATH="app/src/main/java/com/airware/loader"
NEW_PKG_PATH="app/src/main/java/com/eblanhack/loader"

# 1. Перенос исходников в новый пакет com.eblanhack.loader
if [ -d "$OLD_PKG_PATH" ]; then
    mkdir -p "$NEW_PKG_PATH"
    cp -r "$OLD_PKG_PATH/"* "$NEW_PKG_PATH/"
    rm -rf "app/src/main/java/com/airware"
fi

# 2. Точечная замена пакетов и JNI-сигнатур
find app/src/main -type f \( -name "*.kt" -o -name "*.xml" -o -name "*.cpp" -o -name "*.h" -o -name "CMakeLists.txt" \) \
    -exec sed -i 's/com\.airware\.loader/com.eblanhack.loader/g' {} +

find app/src/main -type f \( -name "*.kt" -o -name "*.xml" -o -name "*.cpp" -o -name "*.h" -o -name "CMakeLists.txt" \) \
    -exec sed -i 's/com\/airware\/loader/com\/eblanhack\/loader/g' {} +

find app/src/main -type f \( -name "*.kt" -o -name "*.xml" -o -name "*.cpp" -o -name "*.h" -o -name "CMakeLists.txt" \) \
    -exec sed -i 's/com_airware_loader/com_eblanhack_loader/g' {} +

# 3. Замена UI-названий AirWare / Airware -> EblanHack
find app/src/main -type f \( -name "*.kt" -o -name "*.xml" \) \
    -exec sed -i -E 's/AirWare|Airware/EblanHack/g' {} +

find app/src/main/res -type f -name "*.xml" \
    -exec sed -i 's/airware/eblanhack/g' {} +

# 4. Чистая вставка импорта getValue/setValue строго после строки package в ExpressiveShapes.kt
EXPRESSIVE_FILE="app/src/main/java/com/eblanhack/loader/ui/ExpressiveShapes.kt"
if [ -f "$EXPRESSIVE_FILE" ]; then
    if ! grep -q "import androidx.compose.runtime.getValue" "$EXPRESSIVE_FILE"; then
        sed -i '/^package com\.eblanhack\.loader\.ui/a import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.setValue' "$EXPRESSIVE_FILE"
    fi
fi

# 5. Патчим settings.gradle.kts
if [ -f "settings.gradle.kts" ]; then
    sed -i -E 's/rootProject\.name\s*=\s*".*"/rootProject.name = "EblanHack"/g' settings.gradle.kts
fi

# 6. Патчим app/build.gradle.kts (трогаем только release-блок и фильтр архитектур)
if [ -f "app/build.gradle.kts" ]; then
    sed -i 's/com\.airware\.loader/com.eblanhack.loader/g' app/build.gradle.kts
    sed -i '/release\s*{/,/}/ s/isMinifyEnabled = false/isMinifyEnabled = true\n            isShrinkResources = true\n            signingConfig = signingConfigs.getByName("debug")/' app/build.gradle.kts
    sed -i 's/"armeabi-v7a",\s*//g; s/,\s*"armeabi-v7a"//g; s/"x86_64",\s*//g; s/,\s*"x86_64"//g; s/"x86",\s*//g; s/,\s*"x86"//g' app/build.gradle.kts
fi

echo "[+] Ренейм и чистая вставка импортов завершены"
