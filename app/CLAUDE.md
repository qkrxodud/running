# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> 저장소 루트는 상위 폴더(`running/`)다. 개발 하네스(에이전트 팀·스킬)와 트리거 규칙은 루트 `CLAUDE.md`를 따르며, 제품 계획은 `docs/러닝크루_앱_계획서.md`가 진실이다.

## Project state

This is the **Flutter mobile client** (`running/app`) for a running app. It sits alongside a sibling `running/backend` directory (currently empty). The Dart code is still the default Flutter counter scaffold in `lib/main.dart` — no domain features have been built yet. Treat `main.dart` as a starting point to replace, not an example to imitate.

The Dart SDK constraint is `^3.11.5`. There are no third-party dependencies beyond `cupertino_icons`; state is managed with plain `setState` and there is no routing, networking, or persistence layer yet.

## Commands

```bash
flutter pub get              # install dependencies (run after editing pubspec.yaml)
flutter run                  # run on a connected device/emulator (hot reload with `r`)
flutter analyze              # static analysis / lint (uses analysis_options.yaml)
flutter test                 # run all tests
flutter test test/widget_test.dart   # run a single test file
flutter build apk            # Android release build
flutter build ios            # iOS release build
```

Lints come from `package:flutter_lints/flutter.yaml` via `analysis_options.yaml`. Note that `test/widget_test.dart` is the default scaffold test and will break once `main.dart`'s counter UI is replaced — update it alongside UI changes.

## Platform layout

`android/` and `ios/` are standard Flutter platform host projects. The Android application id / package is `com.example.running` (`android/app/src/main/java/com/example/running/MainActivity.java`) and should be changed before any real release.
