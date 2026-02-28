#!/usr/bin/env bash
cd "$(dirname "$0")" || exit
BASE_PATH=$(pwd)
BUILD_PATH=../all/build

# Make Repository
cd "$BASE_PATH" || exit
mkdir -p $BUILD_PATH/cocoapods/repository/debug
mkdir -p $BUILD_PATH/cocoapods/repository/release

# Copy Podspec
cd "$BASE_PATH" || exit
cd $BUILD_PATH/cocoapods/publish/debug || exit
cp knostr.podspec ../../repository/knostr-debug.podspec
cd ../../repository/ || exit
sed -i -e "s|'knostr'|'knostr-debug'|g" knostr-debug.podspec
sed -i -e "s|'knostr.xcframework'|'debug/knostr.xcframework'|g" knostr-debug.podspec
rm *.podspec-e
cd "$BASE_PATH" || exit
cd $BUILD_PATH/cocoapods/publish/release || exit
cp knostr.podspec ../../repository/knostr-release.podspec
cd ../../repository/ || exit
sed -i -e "s|'knostr'|'knostr-release'|g" knostr-release.podspec
sed -i -e "s|'knostr.xcframework'|'release/knostr.xcframework'|g" knostr-release.podspec
rm *.podspec-e

# Copy Framework
cd "$BASE_PATH" || exit
cd $BUILD_PATH/cocoapods/publish/debug || exit
cp -r knostr.xcframework ../../repository/debug/knostr.xcframework
cd "$BASE_PATH" || exit
cd $BUILD_PATH/cocoapods/publish/release || exit
cp -r knostr.xcframework ../../repository/release/knostr.xcframework

# Copy README
cd "$BASE_PATH" || exit
cd ../ || exit
cp ./LICENSE ./all/build/cocoapods/repository/LICENSE
cp ./docs/pods/README.md ./all/build/cocoapods/repository/README.md
cp ./docs/pods/README_ja.md ./all/build/cocoapods/repository/README_ja.md
