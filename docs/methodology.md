# Benchmark Methodology

This repo separates case orchestration from performance measurement.

## In-App FPS Runner

The Android and iOS apps run the same manual multi-instance FPS scenarios:

- x8, x12, x16, and x20 buttons.
- One selected engine at a time: AnimaX or Lottie.
- An AnimaX-only multi-thread checkbox that remains visible but disabled in Lottie mode and maps to `AnimaXContext.Builder(...).multiThreadAccelerate(...)` on Android and `AnimaXContext.enableMultiThreadAccelerate` on iOS.
- An AnimaX-only image mode checkbox that remains visible but disabled in Lottie mode and creates `AnimaXImageView` instead of `AnimaXView`.
- On Android, a Lottie-only async update checkbox that remains visible but disabled in AnimaX mode and maps to `LottieAnimationView.setAsyncUpdates(...)`.
- Local-only animation assets loaded from the app package or bundle.
- Autoplay and loop enabled for every instance.
- Every scene repeats `lotties/heavy_matte_mask.json` for each tile.
- Main-thread FPS sampled with `Choreographer` on Android and `CADisplayLink` on iOS.
- AnimaX GPU/offscreen FPS sampled through `AnimationListenerAdapter.onFPS` on Android and `AnimaXAnimationListener.onFps` on iOS after setting a 1000 ms FPS event interval.

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

The default manifest intentionally contains one generated stress case:

- `heavy_matte_mask`: a shape-only `ANIMAX` wordmark animation with 10 layer masks and 10 track matte pairs.
