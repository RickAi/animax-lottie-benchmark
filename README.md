# AnimaX Lottie Benchmark

Native case runner for comparing [AnimaX](https://github.com/lynx-family/animax) with Airbnb Lottie on Android and iOS under platform profiling tools.

The repository is intentionally client-only:

- Android uses `AnimaXView` and `LottieAnimationView` in the same native View host.
- iOS uses `AnimaXView` and `LottieAnimationView` in the same UIKit host.
- AnimaX is integrated through published Android Maven artifacts and iOS CocoaPods, not through an in-repository source checkout.
- All Lottie JSON cases live under [assets/lotties](assets/lotties); no test path downloads animation data at runtime.

## What The App Does

The checked-in Android and iOS apps focus on deterministic case construction and startup:

- Load the local manifest and Lottie JSON assets.
- Construct either `AnimaXView` or `LottieAnimationView` for each case.
- Start playback and keep the case on screen for a configurable duration.
- Export a small JSON run log with case status, composition-ready state, first-frame state, and errors.
- Emit platform markers for external tooling: Android `Trace` sections and iOS `os_signpost` events.

The app no longer collects FPS, memory, CPU, frame interval, or latency metrics internally. Collect those from PC-side tooling such as Android Studio Profiler, Perfetto, Jetpack Macrobenchmark, Xcode Instruments, and XCTest metrics. For release-quality numbers, run on physical devices, release builds, thermal state stable, airplane mode enabled, and fixed display refresh rate when possible. Simulator/emulator runs are useful for smoke tests only.

## Cases

The default case manifest is [assets/manifest.json](assets/manifest.json). It currently uses Apache-2.0 sample files from the official Airbnb Lottie Android/iOS repositories:

- `hamburger_arrow`: small path morph.
- `lottie_logo_2`: complex logo, many layers.
- `twitter_heart`: shape-heavy icon animation.
- `pin_jump`: character/icon motion.
- `track_mattes`: matte feature coverage.
- `gradient_fill_blur`: gradient and blur.
- `base64_image`: embedded image asset.
- `text_animated_properties`: text animation.
- `shadow_effect_animated`: effect/shadow coverage.
- `motion_corpse`: large-canvas motion sample.

See [assets/README.md](assets/README.md) for provenance and license notes.

## Android

The Android benchmark project uses Gradle 8.11.1, Android Gradle Plugin 8.9.1, compile SDK 35, and JDK 17 or newer for command-line builds.

AnimaX is consumed from Maven Central-compatible artifacts:

- `org.lynxsdk.lynx:animax-sdk:1.0.0`
- `org.lynxsdk.lynx:animax-textra:1.0.0`

Build and install the Android app:

```sh
cd android
./gradlew :app:assembleNoasanDebug
adb install -r app/build/outputs/apk/noasan/debug/app-noasan-debug.apk
```

Run all cases:

```sh
../scripts/android_run.sh --iterations 5 --case-duration-ms 10000
```

The Android Lottie dependency defaults to `com.airbnb.android:lottie:6.7.1`, verified from Maven Central.

## iOS

The iOS app uses the published `AnimaX` CocoaPod with the same subspec shape as the AnimaX binary demo, plus `lottie-ios`.

```sh
cd ios/AnimaXBenchmark
./bundle_install.sh
xcodebuild -workspace AnimaXExample.xcworkspace -scheme AnimaXExample -configuration Release -destination 'platform=iOS Simulator,name=iPhone 16' build
```

The default `lottie-ios` version is `4.6.1`, matching the current GitHub/CocoaPods release metadata checked while scaffolding this repo.

Run manually from Xcode, or pass launch arguments:

```text
--autorun --iterations=5 --engine=all --case-duration-ms=10000
```

Results are written to the app documents directory under `results/`.

## Result Summary

After pulling one or more result JSON files:

```sh
python3 scripts/summarize_results.py path/to/results/*.json
```

This prints grouped case launch counts, first-frame status, composition-ready status, and errors. Performance metrics should come from the PC-side profiler output captured during the same run.
