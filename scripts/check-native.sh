#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
check_binary=$(mktemp "${TMPDIR:-/tmp}/sva-native-check.XXXXXX")
trap 'rm -f "$check_binary"' EXIT HUP INT TERM
"${CXX:-c++}" -std=c++17 -Wall -Wextra -Werror app/src/test/native/native_checks.cpp -o "$check_binary"
"$check_binary"
echo "Native timing and pixel-format checks passed."
