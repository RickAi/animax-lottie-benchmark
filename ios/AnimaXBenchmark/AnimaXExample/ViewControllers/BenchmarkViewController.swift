import AnimaX
import Darwin
import Foundation
import Lottie
import QuartzCore
import UIKit

@objc(BenchmarkViewController)
final class BenchmarkViewController: UIViewController, AnimaXAnimationListener {
  private struct CaseSpec {
    let id: String
    let file: String
    let category: String
    let features: [String]
  }

  private final class MutableSample {
    let engine: String
    let spec: CaseSpec
    let iteration: Int
    let loadStart: CFTimeInterval
    let cpuStartMs: Double
    let memoryStart: MemorySnapshot
    var compositionMs: Double = -1
    var firstFrameMs: Double = -1
    var cpuEndMs: Double = 0
    var memoryEnd: MemorySnapshot = .capture()
    var memoryPeakBytes: UInt64 = 0
    var frameStats = FrameStats()
    var engineFps: [Double] = []
    var engineMemoryBytes: Int64 = 0
    var error: String?

    init(engine: String, spec: CaseSpec, iteration: Int) {
      self.engine = engine
      self.spec = spec
      self.iteration = iteration
      loadStart = CACurrentMediaTime()
      cpuStartMs = BenchmarkViewController.processCpuMs()
      memoryStart = .capture()
    }

    func asDictionary() -> [String: Any] {
      var dictionary: [String: Any] = [
        "engine": engine,
        "caseId": spec.id,
        "file": spec.file,
        "category": spec.category,
        "features": spec.features,
        "iteration": iteration,
        "compositionMs": compositionMs,
        "firstFrameMs": firstFrameMs,
        "processCpuMs": cpuEndMs - cpuStartMs,
        "engineFpsMean": engineFps.isEmpty ? -1 : engineFps.reduce(0, +) / Double(engineFps.count),
        "engineMemoryBytes": engineMemoryBytes,
        "memoryStart": memoryStart.asDictionary(),
        "memoryEnd": memoryEnd.asDictionary(),
        "memoryPeakBytes": memoryPeakBytes,
        "frames": frameStats.asDictionary()
      ]
      if let error {
        dictionary["error"] = error
      }
      return dictionary
    }
  }

  private struct MemorySnapshot {
    let residentBytes: UInt64
    let physicalFootprintBytes: UInt64

    static func capture() -> MemorySnapshot {
      var info = task_vm_info_data_t()
      var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size)
      let result = withUnsafeMutablePointer(to: &info) { pointer in
        pointer.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
          task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
        }
      }
      guard result == KERN_SUCCESS else {
        return MemorySnapshot(residentBytes: 0, physicalFootprintBytes: 0)
      }
      return MemorySnapshot(
        residentBytes: UInt64(info.resident_size),
        physicalFootprintBytes: UInt64(info.phys_footprint)
      )
    }

    func asDictionary() -> [String: Any] {
      [
        "residentBytes": residentBytes,
        "physicalFootprintBytes": physicalFootprintBytes
      ]
    }
  }

  private struct FrameStats {
    var frameCount = 0
    var averageFps: Double = 0
    var p50Ms: Double = 0
    var p90Ms: Double = 0
    var p95Ms: Double = 0
    var p99Ms: Double = 0
    var jankPercent: Double = 0
    var droppedFrames = 0

    func asDictionary() -> [String: Any] {
      [
        "frameCount": frameCount,
        "averageFps": averageFps,
        "p50Ms": p50Ms,
        "p90Ms": p90Ms,
        "p95Ms": p95Ms,
        "p99Ms": p99Ms,
        "jankPercent": jankPercent,
        "droppedFrames": droppedFrames
      ]
    }
  }

  private final class DisplayLinkSampler {
    private let refreshPeriod: CFTimeInterval
    private var link: CADisplayLink?
    private var previousTimestamp: CFTimeInterval?
    private var firstTimestamp: CFTimeInterval?
    private var lastTimestamp: CFTimeInterval?
    private var intervals: [CFTimeInterval] = []

    init(refreshRate: Double) {
      refreshPeriod = 1.0 / max(1.0, refreshRate)
    }

    func start() {
      intervals.removeAll()
      previousTimestamp = nil
      firstTimestamp = nil
      lastTimestamp = nil
      link = CADisplayLink(target: self, selector: #selector(tick(_:)))
      link?.add(to: .main, forMode: .common)
    }

    func stop() -> FrameStats {
      link?.invalidate()
      link = nil
      guard let firstTimestamp, let lastTimestamp, lastTimestamp > firstTimestamp, !intervals.isEmpty else {
        return FrameStats()
      }

      let sorted = intervals.map { $0 * 1000.0 }.sorted()
      var jank = 0
      var dropped = 0
      for interval in intervals {
        if interval > refreshPeriod * 1.5 {
          jank += 1
        }
        dropped += max(0, Int(round(interval / refreshPeriod)) - 1)
      }

      let duration = lastTimestamp - firstTimestamp
      return FrameStats(
        frameCount: intervals.count + 1,
        averageFps: Double(intervals.count + 1) / duration,
        p50Ms: BenchmarkViewController.percentile(sorted, 50),
        p90Ms: BenchmarkViewController.percentile(sorted, 90),
        p95Ms: BenchmarkViewController.percentile(sorted, 95),
        p99Ms: BenchmarkViewController.percentile(sorted, 99),
        jankPercent: 100.0 * Double(jank) / Double(intervals.count),
        droppedFrames: dropped
      )
    }

    @objc private func tick(_ link: CADisplayLink) {
      if firstTimestamp == nil {
        firstTimestamp = link.timestamp
      }
      if let previousTimestamp {
        intervals.append(link.timestamp - previousTimestamp)
      }
      previousTimestamp = link.timestamp
      lastTimestamp = link.timestamp
    }
  }

  private final class MemorySampler {
    private var timer: Timer?
    private(set) var peakBytes: UInt64 = 0

    func start() {
      peakBytes = MemorySnapshot.capture().physicalFootprintBytes
      timer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
        self?.peakBytes = max(self?.peakBytes ?? 0, MemorySnapshot.capture().physicalFootprintBytes)
      }
      if let timer {
        RunLoop.main.add(timer, forMode: .common)
      }
    }

    func stop() {
      timer?.invalidate()
      timer = nil
    }
  }

  private let stage = UIView()
  private let statusView = UITextView()
  private let runButton = UIButton(type: .system)
  private var cases: [CaseSpec] = []
  private var results: [[String: Any]] = []
  private var runId = ""
  private var iterations = 3
  private var warmupMs: UInt64 = 1000
  private var measureMs: UInt64 = 10_000
  private var engineFilter = "all"
  private var currentAnimaxView: AnimaXView?
  private var currentSample: MutableSample?
  private var firstFrameSeen = false

  override func viewDidLoad() {
    super.viewDidLoad()
    title = "AnimaX Benchmark"
    view.backgroundColor = .systemBackground
    parseArguments()
    buildUI()
    do {
      cases = try loadCases()
      appendStatus("Loaded \(cases.count) cases")
    } catch {
      appendStatus("Failed to load cases: \(error.localizedDescription)")
    }
    if ProcessInfo.processInfo.arguments.contains("--autorun") {
      Task { await runBenchmark() }
    }
  }

  private func parseArguments() {
    for argument in ProcessInfo.processInfo.arguments {
      if argument.hasPrefix("--iterations=") {
        iterations = Int(argument.replacingOccurrences(of: "--iterations=", with: "")) ?? iterations
      } else if argument.hasPrefix("--warmup-ms=") {
        warmupMs = UInt64(argument.replacingOccurrences(of: "--warmup-ms=", with: "")) ?? warmupMs
      } else if argument.hasPrefix("--measure-ms=") {
        measureMs = UInt64(argument.replacingOccurrences(of: "--measure-ms=", with: "")) ?? measureMs
      } else if argument.hasPrefix("--engine=") {
        engineFilter = argument.replacingOccurrences(of: "--engine=", with: "")
      }
    }
  }

  private func buildUI() {
    let stack = UIStackView()
    stack.axis = .vertical
    stack.spacing = 12
    stack.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(stack)

    stage.backgroundColor = UIColor(white: 0.96, alpha: 1.0)
    stage.translatesAutoresizingMaskIntoConstraints = false
    stack.addArrangedSubview(stage)

    runButton.setTitle("Run benchmark", for: .normal)
    runButton.addTarget(self, action: #selector(runTapped), for: .touchUpInside)
    stack.addArrangedSubview(runButton)

    statusView.isEditable = false
    statusView.font = .monospacedSystemFont(ofSize: 12, weight: .regular)
    stack.addArrangedSubview(statusView)

    NSLayoutConstraint.activate([
      stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
      stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
      stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
      stack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -16),
      stage.heightAnchor.constraint(equalTo: stack.heightAnchor, multiplier: 0.58),
      runButton.heightAnchor.constraint(equalToConstant: 44),
      statusView.heightAnchor.constraint(greaterThanOrEqualToConstant: 140)
    ])
  }

  @objc private func runTapped() {
    Task { await runBenchmark() }
  }

  private func runBenchmark() async {
    guard !cases.isEmpty else { return }
    runButton.isEnabled = false
    results.removeAll()
    runId = Self.timestamp()
    appendStatus("Run \(runId) started iterations=\(iterations) engine=\(engineFilter)")

    for engine in engines() {
      for spec in cases {
        for iteration in 0..<iterations {
          appendStatus("Running \(engine) / \(spec.id) \(iteration + 1)/\(iterations)")
          let sample = await runSample(engine: engine, spec: spec, iteration: iteration)
          results.append(sample.asDictionary())
          writeResults(final: false)
          appendStatus("\(engine) / \(spec.id) fps=\(Self.round2(sample.frameStats.averageFps)) p95=\(Self.round2(sample.frameStats.p95Ms))ms")
          try? await Task.sleep(nanoseconds: 500_000_000)
        }
      }
    }

    writeResults(final: true)
    appendStatus("Run complete")
    runButton.isEnabled = true
  }

  private func runSample(engine: String, spec: CaseSpec, iteration: Int) async -> MutableSample {
    clearStage()
    let sample = MutableSample(engine: engine, spec: spec, iteration: iteration)
    currentSample = sample
    firstFrameSeen = false

    do {
      let url = try urlForCase(spec)
      let data = try Data(contentsOf: url)
      let json = String(decoding: data, as: UTF8.self)

      if engine == "animax" {
        runAnimax(json: json)
      } else {
        try runLottie(data: data, sample: sample)
      }

      for _ in 0..<150 where !firstFrameSeen {
        try? await Task.sleep(nanoseconds: 100_000_000)
      }
      if !firstFrameSeen {
        sample.error = "timeout waiting for first frame"
      }
    } catch {
      sample.error = error.localizedDescription
    }

    if sample.firstFrameMs >= 0 {
      try? await Task.sleep(nanoseconds: warmupMs * 1_000_000)
      let frameSampler = DisplayLinkSampler(refreshRate: Double(UIScreen.main.maximumFramesPerSecond))
      let memorySampler = MemorySampler()
      frameSampler.start()
      memorySampler.start()
      try? await Task.sleep(nanoseconds: measureMs * 1_000_000)
      sample.frameStats = frameSampler.stop()
      memorySampler.stop()
      sample.memoryPeakBytes = memorySampler.peakBytes
    }

    sample.cpuEndMs = Self.processCpuMs()
    sample.memoryEnd = .capture()
    if let animaxView = currentAnimaxView {
      sample.engineMemoryBytes = animaxView.memoryUsageBytes()
      animaxView.stop()
      animaxView.removeAnimationEventListener(self)
    }
    clearStage()
    currentAnimaxView = nil
    currentSample = nil
    return sample
  }

  private func runAnimax(json: String) {
    let context = AnimaXContext(ability: BaseAnimaXAbility())
    let animaxView = AnimaXView(context: context)
    animaxView.translatesAutoresizingMaskIntoConstraints = false
    animaxView.setLoop(true)
    animaxView.setAutoplay(false)
    animaxView.setFPSEventInterval(1000)
    animaxView.addAnimationEventListener(self)
    addToStage(animaxView)
    currentAnimaxView = animaxView
    animaxView.setJson(json)
  }

  private func runLottie(data: Data, sample: MutableSample) throws {
    let animation = try LottieAnimation.from(data: data)
    sample.compositionMs = Self.elapsedMs(sample.loadStart)
    let lottieView = LottieAnimationView(
      animation: animation,
      configuration: LottieConfiguration(renderingEngine: .automatic)
    )
    lottieView.translatesAutoresizingMaskIntoConstraints = false
    lottieView.contentMode = .scaleAspectFit
    addToStage(lottieView)
    lottieView.loopMode = .loop
    lottieView.play()
    DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { [weak self, weak sample] in
      guard let sample else { return }
      if sample.firstFrameMs < 0 {
        sample.firstFrameMs = BenchmarkViewController.elapsedMs(sample.loadStart)
      }
      self?.firstFrameSeen = true
    }
  }

  @objc(onReady:)
  func onReady(_ params: NSDictionary) {
    guard let sample = currentSample else { return }
    if sample.compositionMs < 0 {
      sample.compositionMs = Self.elapsedMs(sample.loadStart)
    }
    currentAnimaxView?.play()
  }

  @objc(onCompositionReady:)
  func onCompositionReady(_ params: NSDictionary) {
    guard let sample = currentSample else { return }
    if sample.compositionMs < 0 {
      sample.compositionMs = Self.elapsedMs(sample.loadStart)
    }
  }

  @objc(onFirstFrame:)
  func onFirstFrame(_ params: NSDictionary) {
    guard let sample = currentSample else { return }
    if sample.firstFrameMs < 0 {
      sample.firstFrameMs = Self.elapsedMs(sample.loadStart)
    }
    firstFrameSeen = true
  }

  @objc(onFps:)
  func onFps(_ params: NSDictionary) {
    guard let sample = currentSample else { return }
    if let fps = params["fps"] as? NSNumber {
      sample.engineFps.append(fps.doubleValue)
    }
  }

  @objc(onError:)
  func onError(_ params: NSDictionary) {
    currentSample?.error = params.description
  }

  private func addToStage(_ child: UIView) {
    stage.addSubview(child)
    NSLayoutConstraint.activate([
      child.leadingAnchor.constraint(equalTo: stage.leadingAnchor),
      child.trailingAnchor.constraint(equalTo: stage.trailingAnchor),
      child.topAnchor.constraint(equalTo: stage.topAnchor),
      child.bottomAnchor.constraint(equalTo: stage.bottomAnchor)
    ])
  }

  private func clearStage() {
    for subview in stage.subviews {
      subview.removeFromSuperview()
    }
  }

  private func engines() -> [String] {
    if engineFilter == "animax" { return ["animax"] }
    if engineFilter == "lottie" { return ["lottie"] }
    return ["animax", "lottie"]
  }

  private func loadCases() throws -> [CaseSpec] {
    guard let url = Bundle.main.url(forResource: "manifest", withExtension: "json", subdirectory: "export_output") else {
      throw NSError(domain: "Benchmark", code: 1, userInfo: [NSLocalizedDescriptionKey: "manifest not found"])
    }
    let object = try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any]
    guard let rawCases = object?["cases"] as? [[String: Any]] else {
      throw NSError(domain: "Benchmark", code: 2, userInfo: [NSLocalizedDescriptionKey: "cases missing"])
    }
    return rawCases.compactMap { item in
      guard let id = item["id"] as? String, let file = item["file"] as? String else { return nil }
      return CaseSpec(
        id: id,
        file: file,
        category: item["category"] as? String ?? "",
        features: item["features"] as? [String] ?? []
      )
    }
  }

  private func urlForCase(_ spec: CaseSpec) throws -> URL {
    let path = "export_output/" + spec.file
    let name = (path as NSString).deletingPathExtension
    let ext = (path as NSString).pathExtension
    if let url = Bundle.main.url(forResource: name, withExtension: ext) {
      return url
    }
    throw NSError(domain: "Benchmark", code: 3, userInfo: [NSLocalizedDescriptionKey: "case not found: \(spec.file)"])
  }

  private func writeResults(final: Bool) {
    let root: [String: Any] = [
      "schemaVersion": 1,
      "runId": runId,
      "final": final,
      "platform": "ios",
      "engineFilter": engineFilter,
      "iterations": iterations,
      "warmupMs": warmupMs,
      "measureMs": measureMs,
      "device": [
        "model": UIDevice.current.model,
        "systemName": UIDevice.current.systemName,
        "systemVersion": UIDevice.current.systemVersion,
        "maximumFramesPerSecond": UIScreen.main.maximumFramesPerSecond
      ],
      "samples": results
    ]
    do {
      let data = try JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted, .sortedKeys])
      let dir = try resultDirectory()
      let url = dir.appendingPathComponent("animax-lottie-ios-\(runId).json")
      try data.write(to: url)
    } catch {
      appendStatus("Failed to write results: \(error.localizedDescription)")
    }
  }

  private func resultDirectory() throws -> URL {
    let base = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    let dir = base.appendingPathComponent("results", isDirectory: true)
    try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    return dir
  }

  private func appendStatus(_ text: String) {
    let line = "\(Self.timestamp()) \(text)\n"
    statusView.text.append(line)
    print("[AnimaXBench] \(line)", terminator: "")
  }

  private static func elapsedMs(_ start: CFTimeInterval) -> Double {
    (CACurrentMediaTime() - start) * 1000.0
  }

  private static func percentile(_ sorted: [Double], _ percentile: Double) -> Double {
    guard !sorted.isEmpty else { return 0 }
    let rank = (percentile / 100.0) * Double(sorted.count - 1)
    let low = Int(floor(rank))
    let high = Int(ceil(rank))
    if low == high { return sorted[low] }
    let fraction = rank - Double(low)
    return sorted[low] * (1 - fraction) + sorted[high] * fraction
  }

  private static func processCpuMs() -> Double {
    var usage = rusage()
    getrusage(RUSAGE_SELF, &usage)
    let user = Double(usage.ru_utime.tv_sec) * 1000.0 + Double(usage.ru_utime.tv_usec) / 1000.0
    let system = Double(usage.ru_stime.tv_sec) * 1000.0 + Double(usage.ru_stime.tv_usec) / 1000.0
    return user + system
  }

  private static func timestamp() -> String {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyyMMdd-HHmmss"
    return formatter.string(from: Date())
  }

  private static func round2(_ value: Double) -> Double {
    Darwin.round(value * 100) / 100
  }
}
