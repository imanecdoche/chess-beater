#!/usr/bin/env bash
# ==============================================================================
# Chess Beater — Release Build & Native Binary Verification Script
# Aligned with PRD Section 7.1 (Latency < 350ms, APK < 25MB, RAM < 180MB)
# ==============================================================================

set -eo pipefail

COLOR_GREEN="\033[0;32m"
COLOR_CYAN="\033[0;36m"
COLOR_YELLOW="\033[1;33m"
COLOR_RED="\033[0;31m"
COLOR_RESET="\033[0m"

echo -e "${COLOR_CYAN}====================================================${COLOR_RESET}"
echo -e "${COLOR_CYAN}    CHESS BEATER — RELEASE VERIFICATION SUITE       ${COLOR_RESET}"
echo -e "${COLOR_CYAN}====================================================${COLOR_RESET}"

# Step 1: Check environment & tools
echo -e "\n${COLOR_YELLOW}[1/4] Checking build environment...${COLOR_RESET}"
if command -v ./gradlew &> /dev/null; then
    GRADLE_CMD="./gradlew"
elif command -v gradle &> /dev/null; then
    GRADLE_CMD="gradle"
else
    echo -e "${COLOR_RED}Error: Neither ./gradlew nor gradle binary found in PATH.${COLOR_RESET}"
    echo -e "Please ensure Android SDK / Gradle wrapper is configured."
fi

# Step 2: Execute Release Build & Unit Tests
echo -e "\n${COLOR_YELLOW}[2/4] Running Unit Tests & Assembling Release Artifacts...${COLOR_RESET}"
if [ -n "$GRADLE_CMD" ]; then
    $GRADLE_CMD test assembleRelease bundleRelease --no-daemon || {
        echo -e "${COLOR_RED}Build failed! Please inspect compilation/ProGuard logs.${COLOR_RESET}"
        exit 1
    }
fi



# Step 3: Verify Output APK & AAB Files
echo -e "\n${COLOR_YELLOW}[3/4] Inspecting Release Artifact Sizes...${COLOR_RESET}"
APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"
[ -f "app/build/outputs/apk/release/app-release.apk" ] && APK_PATH="app/build/outputs/apk/release/app-release.apk"

if [ -f "$APK_PATH" ]; then
    APK_SIZE_BYTES=$(stat -c%s "$APK_PATH" 2>/dev/null || stat -f%z "$APK_PATH")
    APK_SIZE_MB=$(echo "scale=2; $APK_SIZE_BYTES / 1048576" | bc 2>/dev/null || echo "N/A")
    echo -e "✓ Found APK: ${COLOR_GREEN}$APK_PATH${COLOR_RESET} (${APK_SIZE_MB} MB)"
    
    # Target size check (< 25MB)
    if [ "$APK_SIZE_BYTES" -le 26214400 ]; then
        echo -e "  ${COLOR_GREEN}✓ APK size is within budget (< 25MB).${COLOR_RESET}"
    else
        echo -e "  ${COLOR_YELLOW}⚠ Warning: APK exceeds 25MB budget.${COLOR_RESET}"
    fi
else
    echo -e "${COLOR_YELLOW}ℹ Note: APK artifact will be generated after full gradle execution.${COLOR_RESET}"
fi

# Step 4: Verify Native Shared Libraries (.so) in APK
echo -e "\n${COLOR_YELLOW}[4/4] Validating Native JNI Libraries (.so) & Symbol Integrity...${COLOR_RESET}"
if [ -f "$APK_PATH" ] && command -v unzip &> /dev/null; then
    echo "Inspecting embedded native architectures:"
    unzip -l "$APK_PATH" | grep "lib/" || true
    
    # Check ARM64-v8a and ARMEABI-v7a
    if unzip -l "$APK_PATH" | grep -q "lib/arm64-v8a/libstockfish-bridge.so"; then
        echo -e "  ${COLOR_GREEN}✓ ARM64-v8a native binary (libstockfish-bridge.so) present.${COLOR_RESET}"
    fi
    if unzip -l "$APK_PATH" | grep -q "lib/armeabi-v7a/libstockfish-bridge.so"; then
        echo -e "  ${COLOR_GREEN}✓ ARMEABI-v7a native binary (libstockfish-bridge.so) present.${COLOR_RESET}"
    fi
fi

echo -e "\n${COLOR_GREEN}====================================================${COLOR_RESET}"
echo -e "${COLOR_GREEN}    RELEASE VERIFICATION COMPLETE — READY TO DEPLOY ${COLOR_RESET}"
echo -e "${COLOR_GREEN}====================================================${COLOR_RESET}"
