# AnimaX Lottie Benchmark

Native case runner for comparing [AnimaX](https://github.com/lynx-family/animax) with Airbnb Lottie on Android and iOS under platform profiling tools.

The repository is intentionally client-only:

- Android uses `AnimaXView` or `AnimaXImageView` and `LottieAnimationView` in the same native View host.
- iOS uses `AnimaXView` or `AnimaXImageView` and `LottieAnimationView` in the same UIKit host.
- AnimaX is integrated through published Android Maven artifacts and iOS CocoaPods, not through an in-repository source checkout.
- All Lottie JSON cases live under [assets/lotties](assets/lotties); no test path downloads animation data at runtime.

## What The App Does

The checked-in Android and iOS apps focus on steady-state multi-instance rendering:

- Show a home screen with x1, x5, x10, x20, x40, and x60 render-count buttons.
- Let the user choose AnimaX or Lottie with checkboxes.
- Show AnimaX-only "Enable multi thread" and "Enable image mode" checkboxes. Multi-thread maps to `AnimaXContext.Builder(...).multiThreadAccelerate(...)` on Android and `AnimaXContext.enableMultiThreadAccelerate` on iOS. Image mode creates `AnimaXImageView` instead of `AnimaXView`.
- Open a dedicated render page where all animations autoplay and loop.
- Keep x1/x5/x10/x20 on the fixed x20-derived grid, and shrink x40/x60 tiles with dynamic grids that fill the stage.
- Use different local Lottie JSON files for x1/x5/x10/x20, then repeat local files for x40/x60 pressure cases.
- Show main-thread FPS for both engines.
- Show AnimaX GPU/offscreen FPS from `AnimationListenerAdapter.onFPS` on Android and `AnimaXAnimationListener.onFps` on iOS after setting a 1000 ms FPS event interval.

Memory is intentionally measured from host-side tooling.

Collect memory, CPU, frame interval, and latency metrics from PC-side tooling such as Android Studio Profiler, Perfetto, Jetpack Macrobenchmark, Xcode Instruments, and XCTest metrics. For release-quality numbers, run on physical devices, release builds, thermal state stable, airplane mode enabled, and fixed display refresh rate when possible. Simulator/emulator runs are useful for smoke tests only.

## Cases

The default case manifest is [assets/manifest.json](assets/manifest.json). It currently uses 20 Apache-2.0 sample files from the official Airbnb Lottie Android/iOS repositories. The Android x1/x5/x10/x20 scenes use unique files from this manifest. The x40 and x60 scenes repeat the same local files to build higher instance-count pressure cases.

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
- `nine_squares_al_boardman`, `boat_loader`, `icon_transitions`, `shape_types`, `repeater`, `switch`, `trim_paths`, `laugh4`, `watermelon`, and `success`: additional official Lottie samples used to make the x20 multi-instance scene non-repeating and visually distinct.

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

Launch an Android scene from the command line:

```sh
../scripts/android_run.sh --engine animax --count 60 --animax-multithread --animax-image-mode
```

The Android Lottie dependency defaults to `com.airbnb.android:lottie:6.7.1`, verified from Maven Central.

## iOS

The iOS app uses the published `AnimaX` CocoaPod with the required rendering subspecs, plus `lottie-ios`.

```sh
cd ios/AnimaXBenchmark
./bundle_install.sh
xcodebuild -workspace AnimaXLottieBenchmark.xcworkspace -scheme AnimaXLottieBenchmark -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build
```

The default `lottie-ios` version is `4.6.1`.

Run manually from Xcode, or pass launch arguments:

```text
--autorun --engine=animax --count=20 --animax-multithread --animax-image-mode
```

Use `--engine=lottie` and any supported `--count=1|5|10|20|40|60` for Lottie scenes.

## Results

The app keeps FPS display in-app and leaves memory, CPU, frame intervals, hitches, and trace analysis to host-side profilers. Performance metrics should come from the PC-side profiler output captured during the same run.
