# AnimaX Lottie Benchmark

Native benchmark harness for comparing [AnimaX](https://github.com/lynx-family/animax) with Airbnb Lottie on Android and iOS.

The repository is intentionally client-only:

- Android uses `AnimaXView` and `LottieAnimationView` in the same native View host.
- iOS uses `AnimaXView` and `LottieAnimationView` in the same UIKit host.
- All Lottie JSON cases live under [assets/lotties](assets/lotties); no test path downloads animation data at runtime.

## What Is Measured

Each sample exports a JSON row with:

- Load/composition latency and first-frame latency.
- Host FPS from `Choreographer` on Android and `CADisplayLink` on iOS.
- Frame interval percentiles, jank percentage, and estimated dropped frames.
- Process memory snapshots and peak sampled memory.
- Process CPU elapsed time during the measured window.
- Engine-specific values when exposed by the engine, such as AnimaX FPS callbacks and iOS `memoryUsageBytes`.

For release-quality numbers, run on physical devices, release builds, thermal state stable, airplane mode enabled, and fixed display refresh rate when possible. Simulator/emulator runs are useful for smoke tests only.

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

1. Initialize the in-repository AnimaX submodule and generated dependencies:

   ```sh
   git submodule update --init --recursive
   scripts/bootstrap_deps.sh
   ```

   Use JDK 11 for Android builds. AnimaX currently uses Gradle 6.7.1 and Android Gradle Plugin 4.1.0, which do not run under JDK 17.

2. Build and install the Android app:

   ```sh
   cd android
   ./gradlew :app:assembleNoasanDebug
   adb install -r app/build/outputs/apk/noasan/debug/app-noasan-debug.apk
   ```

3. Run all cases:

   ```sh
   ../scripts/android_run.sh --iterations 5
   ```

The Android Lottie dependency defaults to `com.airbnb.android:lottie:6.7.1`, verified from Maven Central.

## iOS

The iOS app reuses the AnimaX example CocoaPods integration and adds `lottie-ios`.

```sh
git submodule update --init --recursive
scripts/bootstrap_deps.sh
cd ios/AnimaXBenchmark
./bundle_install.sh
xcodebuild -workspace AnimaXExample.xcworkspace -scheme AnimaXExample -configuration Release -destination 'platform=iOS Simulator,name=iPhone 16' build
```

The default `lottie-ios` version is `4.6.1`, matching the current GitHub/CocoaPods release metadata checked while scaffolding this repo.

Run manually from Xcode, or pass launch arguments:

```text
--autorun --iterations=5 --engine=all --warmup-ms=1000 --measure-ms=10000
```

Results are written to the app documents directory under `results/`.

## Result Summary

After pulling one or more result JSON files:

```sh
python3 scripts/summarize_results.py path/to/results/*.json
```

This prints grouped medians for FPS, p95 frame time, jank percentage, PSS delta, CPU time, load time, and first-frame time.
