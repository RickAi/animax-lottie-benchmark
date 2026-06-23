import Foundation
import Lottie
import os
import UIKit

@objc(BenchmarkViewController)
final class BenchmarkViewController: UIViewController, AnimaXAnimationListener {
  private struct CaseSpec {
    let id: String
    let file: String
    let category: String
    let features: [String]
  }

  private final class CaseRun {
    let engine: String
    let spec: CaseSpec
    let iteration: Int
    var compositionReady = false
    var firstFrameSeen = false
    var error: String?

    init(engine: String, spec: CaseSpec, iteration: Int) {
      self.engine = engine
      self.spec = spec
      self.iteration = iteration
    }

    func asDictionary() -> [String: Any] {
      var dictionary: [String: Any] = [
        "engine": engine,
        "caseId": spec.id,
        "file": spec.file,
        "category": spec.category,
        "features": spec.features,
        "iteration": iteration,
        "status": status,
        "compositionReady": compositionReady,
        "firstFrameSeen": firstFrameSeen
      ]
      if let error {
        dictionary["error"] = error
      }
      return dictionary
    }

    private var status: String {
      if error != nil {
        return "error"
      }
      return firstFrameSeen ? "launched" : "started"
    }
  }

  private let stage = UIView()
  private let statusView = UITextView()
  private let runButton = UIButton(type: .system)
  private var cases: [CaseSpec] = []
  private var caseRuns: [[String: Any]] = []
  private var runId = ""
  private var iterations = 3
  private var caseDurationMs: UInt64 = 10_000
  private var engineFilter = "all"
  private var currentAnimaxView: AnimaXView?
  private var currentCaseRun: CaseRun?
  private var firstFrameSeen = false

  override func viewDidLoad() {
    super.viewDidLoad()
    title = "AnimaX Case Runner"
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
      } else if argument.hasPrefix("--case-duration-ms=") {
        caseDurationMs = UInt64(argument.replacingOccurrences(of: "--case-duration-ms=", with: "")) ?? caseDurationMs
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

    runButton.setTitle("Run cases", for: .normal)
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
    caseRuns.removeAll()
    runId = Self.timestamp()
    appendStatus("Run \(runId) started iterations=\(iterations) caseDurationMs=\(caseDurationMs) engine=\(engineFilter)")

    for engine in engines() {
      for spec in cases {
        for iteration in 0..<iterations {
          appendStatus("Running \(engine) / \(spec.id) \(iteration + 1)/\(iterations)")
          let caseRun = await runCase(engine: engine, spec: spec, iteration: iteration)
          caseRuns.append(caseRun.asDictionary())
          writeResults(final: false)
          appendStatus("\(engine) / \(spec.id) \(caseRun.error == nil ? "launched" : "error")")
          try? await Task.sleep(nanoseconds: 500_000_000)
        }
      }
    }

    writeResults(final: true)
    appendStatus("Run complete")
    runButton.isEnabled = true
  }

  private func runCase(engine: String, spec: CaseSpec, iteration: Int) async -> CaseRun {
    clearStage()
    let caseRun = CaseRun(engine: engine, spec: spec, iteration: iteration)
    currentCaseRun = caseRun
    firstFrameSeen = false
    let caseSignpost = OSSignpostID(log: Self.signpostLog)
    os_signpost(.begin, log: Self.signpostLog, name: "bench_case_run", signpostID: caseSignpost)
    defer {
      os_signpost(.end, log: Self.signpostLog, name: "bench_case_run", signpostID: caseSignpost)
    }

    do {
      let url = try urlForCase(spec)
      let data = try Self.signposted("bench_read_asset") {
        try Data(contentsOf: url)
      }
      let json = String(decoding: data, as: UTF8.self)

      try Self.signposted("bench_set_animation") {
        if engine == "animax" {
          runAnimax(json: json)
        } else {
          try runLottie(data: data)
        }
      }

      for _ in 0..<150 where !firstFrameSeen {
        try? await Task.sleep(nanoseconds: 100_000_000)
      }
      if !firstFrameSeen {
        caseRun.error = "timeout waiting for first frame"
      } else {
        try? await Task.sleep(nanoseconds: caseDurationMs * 1_000_000)
      }
    } catch {
      caseRun.error = error.localizedDescription
    }

    if let animaxView = currentAnimaxView {
      animaxView.stop()
      animaxView.removeAnimationEventListener(self)
    }
    clearStage()
    currentAnimaxView = nil
    currentCaseRun = nil
    return caseRun
  }

  private func runAnimax(json: String) {
    let context = AnimaXContext(ability: BaseAnimaXAbility())
    let animaxView = AnimaXView(context: context)
    animaxView.translatesAutoresizingMaskIntoConstraints = false
    animaxView.setLoop(true)
    animaxView.setAutoplay(false)
    animaxView.addAnimationEventListener(self)
    addToStage(animaxView)
    currentAnimaxView = animaxView
    animaxView.setJson(json)
  }

  private func runLottie(data: Data) throws {
    let animation = try LottieAnimation.from(data: data)
    markCompositionReady()
    let lottieView = LottieAnimationView(
      animation: animation,
      configuration: LottieConfiguration(renderingEngine: .automatic)
    )
    lottieView.translatesAutoresizingMaskIntoConstraints = false
    lottieView.contentMode = .scaleAspectFit
    addToStage(lottieView)
    lottieView.loopMode = .loop
    lottieView.play()
    DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { [weak self] in
      self?.currentCaseRun?.firstFrameSeen = true
      self?.firstFrameSeen = true
      Self.signpostEvent("bench_first_frame")
    }
  }

  func onReady(_ params: [AnyHashable: Any]) {
    markCompositionReady()
    currentAnimaxView?.play()
  }

  func onCompositionReady(_ params: [AnyHashable: Any]) {
    markCompositionReady()
  }

  func onFirstFrame(_ params: [AnyHashable: Any]) {
    guard let caseRun = currentCaseRun, !caseRun.firstFrameSeen else {
      return
    }
    caseRun.firstFrameSeen = true
    firstFrameSeen = true
    Self.signpostEvent("bench_first_frame")
  }

  func onError(_ params: [AnyHashable: Any]) {
    currentCaseRun?.error = String(describing: params)
  }

  private func markCompositionReady() {
    guard let caseRun = currentCaseRun, !caseRun.compositionReady else {
      return
    }
    caseRun.compositionReady = true
    Self.signpostEvent("bench_composition_ready")
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
      "schemaVersion": 2,
      "runnerMode": "case-launch",
      "runId": runId,
      "final": final,
      "platform": "ios",
      "engineFilter": engineFilter,
      "iterations": iterations,
      "caseDurationMs": caseDurationMs,
      "device": [
        "model": UIDevice.current.model,
        "systemName": UIDevice.current.systemName,
        "systemVersion": UIDevice.current.systemVersion,
        "maximumFramesPerSecond": UIScreen.main.maximumFramesPerSecond
      ],
      "caseRuns": caseRuns
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

  private static let signpostLog = OSLog(subsystem: "com.animax.benchmark", category: "CaseRunner")

  private static func signposted<T>(_ name: StaticString, _ work: () throws -> T) rethrows -> T {
    let signpostID = OSSignpostID(log: signpostLog)
    os_signpost(.begin, log: signpostLog, name: name, signpostID: signpostID)
    defer {
      os_signpost(.end, log: signpostLog, name: name, signpostID: signpostID)
    }
    return try work()
  }

  private static func signpostEvent(_ name: StaticString) {
    os_signpost(.event, log: signpostLog, name: name)
  }

  private static func timestamp() -> String {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyyMMdd-HHmmss"
    return formatter.string(from: Date())
  }
}
