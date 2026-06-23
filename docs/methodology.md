# Benchmark Methodology

This repo separates case orchestration from performance measurement.

## In-App Case Runner

The checked-in Android and iOS apps run the same local Lottie JSON cases and export the same case-run schema. The apps are responsible for:

- Loading the manifest and bundled case assets.
- Constructing the requested engine view.
- Starting playback.
- Keeping the case visible for `caseDurationMs`.
- Recording only launch status, composition-ready status, first-frame status, and errors.

The apps do not collect FPS, frame intervals, jank, dropped frames, CPU time, memory, heap, resident size, or engine memory internally. Those values should come from platform tooling on the host machine.

## Profiler Alignment Markers

Android emits `Trace` sections/events so Perfetto, Android Studio Profiler, and Macrobenchmark can align profiler data with app phases:

- `bench_case_setup`
- `bench_create_view`
- `bench_read_asset`
- `bench_set_animation`
- `bench_composition_ready`
- `bench_first_frame`

iOS emits `os_signpost` markers for Instruments and XCTest signpost metrics:

- `bench_case_run`
- `bench_read_asset`
- `bench_set_animation`
- `bench_composition_ready`
- `bench_first_frame`

Treat these markers as phase boundaries, not as the source of final performance numbers.

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
