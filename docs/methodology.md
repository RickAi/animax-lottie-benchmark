# Benchmark Methodology

This repo separates case orchestration from performance measurement.

## Android In-App FPS Runner

The Android app runs a manual multi-instance FPS scenario:

- x1, x5, x10, x20, x40, and x60 render-count buttons.
- One selected engine at a time: AnimaX or Lottie.
- An AnimaX-only multi-thread checkbox that maps to `AnimaXContext.Builder(...).multiThreadAccelerate(...)`.
- An AnimaX-only image mode checkbox that creates `AnimaXImageView` instead of `AnimaXView`.
- Local-only animation assets loaded from the APK.
- Autoplay and loop enabled for every instance.
- x1/x5/x10/x20 use different local JSON files selected from the manifest.
- x40/x60 repeat local JSON files and shrink tiles with dynamic grids so the stage is filled without overflow.
- Main-thread FPS sampled with `Choreographer`.
- AnimaX GPU/offscreen FPS sampled through `AnimationListenerAdapter.onFPS` after `setFpsEventInterval(1000)`.

Memory is measured from host-side tooling, not in the app.

## iOS In-App FPS Runner

The iOS app mirrors the Android manual multi-instance FPS scenario:

- x1, x5, x10, x20, x40, and x60 render-count buttons.
- One selected engine at a time: AnimaX or Lottie.
- An AnimaX-only multi-thread checkbox that maps to `AnimaXContext.enableMultiThreadAccelerate`.
- An AnimaX-only image mode checkbox that creates `AnimaXImageView` instead of `AnimaXView`.
- Local-only animation assets loaded from the app bundle.
- Autoplay and loop enabled for every instance.
- x1/x5/x10/x20 use different local JSON files selected from the manifest.
- x40/x60 repeat local JSON files and shrink tiles with dynamic grids so the stage is filled without overflow.
- Main-thread FPS sampled with `CADisplayLink`.
- AnimaX GPU/offscreen FPS sampled through `AnimaXAnimationListener.onFps` after `setFPSEventInterval(1000)`.

The apps only show live FPS in-app. Frame intervals, jank, dropped frames, CPU time, memory, heap, resident size, and engine memory should come from platform tooling on the host machine.

## Android Measurement

Use platform tooling as the source of Android performance data:

- Use Jetpack Macrobenchmark with `StartupTimingMetric`, `FrameTimingMetric`, `TraceSectionMetric`, and `MemoryUsageMetric`.
- Capture Perfetto traces with FrameTimeline enabled for frame timing and root-cause analysis.
- Use Android Studio Profiler for interactive CPU, memory, and allocation inspection.
- Prefer frame time percentiles, jank, and frame overrun over average FPS.
- Keep the runner release-like: physical device, stable thermal state, fixed ABI, fixed display refresh rate, fixed renderer mode, and consistent build type.

## iOS Measurement

Use platform tooling as the source of iOS performance data:

- Use XCTest performance tests with `XCTApplicationLaunchMetric`, `XCTOSSignpostMetric`, `XCTCPUMetric`, `XCTMemoryMetric`, and `XCTClockMetric`.
- Use Instruments Animation Hitches, Time Profiler, Allocations, and VM Tracker for root-cause analysis.
- Record Lottie rendering engine (`automatic`, `coreAnimation`, `mainThread`) because it materially changes CPU and frame behavior.
- Keep the runner release-like: physical device, stable thermal state, fixed display refresh rate, and consistent build configuration.

## Required Result Metadata

Pair each profiler capture with enough metadata to make comparisons reproducible:

- Device model, OS, API level or iOS version, refresh rate, and ABI.
- Build type, commit SHA, AnimaX binary version, Lottie version, renderer mode, cache policy, and case manifest version.
- Per-case JSON size, category, feature tags, frame count, layer count, canvas size, and whether external image/font assets are required.
- Cold and warm-cache mode when measuring parse, startup, and first-frame behavior.
- Profiler tool and version, capture configuration, and any trace/signpost sections used for slicing.

## Case Selection

The default manifest covers small/complex/icon/character/matte/gradient/image/text/effect/large-canvas cases. Future additions should come from license-clear corpora such as:

- LottieFiles `test-files/data` CC0 corpus.
- Official Airbnb Lottie Android snapshot tests.
- Official Airbnb Lottie iOS tests outside the `Tests/Samples/LottieFiles` subdirectory unless the Lottie Simple License is acceptable for that specific asset.
- Lottie Animation Community tests.
