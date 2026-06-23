# Benchmark Methodology

This repo has two layers of measurement.

## Portable In-App Harness

The checked-in Android and iOS apps run the same local Lottie JSON cases and export the same result schema. This is the fast path for day-to-day regression checks.

Android uses:

- `Choreographer` frame callbacks for frame interval distribution.
- `Debug.MemoryInfo`, native heap, Java heap, and `Process.getElapsedCpuTime()`.
- AnimaX lifecycle/FPS callbacks when exposed by the engine.
- `Trace` sections for `bench_read_asset`, `bench_set_animation`, and `bench_first_frame`, so Perfetto and Macrobenchmark can align app-internal phases.

iOS uses:

- `CADisplayLink` for frame interval distribution.
- Mach `task_info(TASK_VM_INFO)` for resident and physical footprint memory.
- `getrusage(RUSAGE_SELF)` for process CPU time.
- AnimaX lifecycle/FPS callbacks and `memoryUsageBytes` when exposed.

The in-app harness is intentionally simple and self-contained. It is good for smoke testing, relative local iteration, and catching large regressions.

## Publication-Grade Runs

Use platform tooling as the primary source when publishing numbers.

Android:

- Use Jetpack Macrobenchmark with `StartupTimingMetric`, `FrameTimingMetric`, `TraceSectionMetric`, and `MemoryUsageMetric`.
- Capture Perfetto traces with FrameTimeline enabled for root-cause analysis.
- Prefer frame time percentiles and jank/overrun over average FPS.
- Keep the benchmark app release-like: non-debuggable release build, R8 choice fixed, same ABI, same display refresh rate, same thermal conditions.

iOS:

- Use XCTest performance tests with `XCTApplicationLaunchMetric`, `XCTOSSignpostMetric`, `XCTCPUMetric`, `XCTMemoryMetric`, and `XCTClockMetric`.
- Use Instruments Animation Hitches, Time Profiler, and Allocations for root-cause analysis.
- Record Lottie rendering engine (`automatic`, `coreAnimation`, `mainThread`) because it materially changes CPU and frame behavior.

## Required Result Metadata

Every result file should include:

- Device model, OS, API level or iOS version, refresh rate, and ABI.
- Build type, commit SHA, AnimaX version/commit, Lottie version, renderer mode, cache policy, and case manifest version.
- Per-case JSON size, category, feature tags, frame count, layer count, canvas size, and whether external image/font assets are required.
- Cold and warm-cache modes when measuring parse and first-frame costs.

## Case Selection

The default manifest covers small/complex/icon/character/matte/gradient/image/text/effect/large-canvas cases. Future additions should come from license-clear corpora such as:

- LottieFiles `test-files/data` CC0 corpus.
- Official Airbnb Lottie Android snapshot tests.
- Official Airbnb Lottie iOS tests outside the `Tests/Samples/LottieFiles` subdirectory unless the Lottie Simple License is acceptable for that specific asset.
- Lottie Animation Community tests.
