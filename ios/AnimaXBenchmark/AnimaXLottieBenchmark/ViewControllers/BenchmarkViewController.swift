import Foundation
import Lottie
import UIKit

@objc(BenchmarkViewController)
final class BenchmarkViewController: UIViewController {
  private enum Engine: String {
    case animax
    case lottie

    var title: String {
      switch self {
      case .animax:
        return "AnimaX"
      case .lottie:
        return "Lottie"
      }
    }
  }

  private struct CaseSpec {
    let file: String
  }

  private struct GridSpec {
    let columns: Int
    let rows: Int
    let width: CGFloat
    let height: CGFloat
    let tileWidth: CGFloat
    let tileHeight: CGFloat
  }

  private struct BenchmarkCase {
    let caseId: String
    let buttonLabel: String
    let titleLabel: String
    let animationCount: Int

    static func renderCount(_ caseId: String, _ label: String, _ animationCount: Int) -> BenchmarkCase {
      BenchmarkCase(
        caseId: caseId,
        buttonLabel: label,
        titleLabel: label,
        animationCount: animationCount
      )
    }
  }

  private static let benchmarkCases = [
    BenchmarkCase.renderCount("count-1", "x1", 1),
    BenchmarkCase.renderCount("count-4", "x4", 4),
    BenchmarkCase.renderCount("count-8", "x8", 8),
    BenchmarkCase.renderCount("count-12", "x12", 12)
  ]
  private static let maxColumns = 4
  private static let maxRows = 5
  private static let fixedGridCapacity = maxColumns * maxRows

  private let mainThreadFpsMonitor = MainThreadFpsMonitor()
  private let stage = UIView()

  private var caseSpecs: [CaseSpec] = []
  private var animaxViews: [UIView & AnimaXPlayerProtocol] = []
  private var lottieViews: [LottieAnimationView] = []

  private var animaxButton: UIButton?
  private var lottieButton: UIButton?
  private var animaxMultiThreadButton: UIButton?
  private var animaxImageModeButton: UIButton?
  private var fpsLabel: UILabel?

  private var selectedEngine: Engine = .animax
  private var launchCount = BenchmarkViewController.benchmarkCases[0].animationCount
  private var launchCaseId: String?
  private var showingScene = false
  private var currentSceneEngine: Engine = .animax
  private var currentBenchmarkCase = BenchmarkViewController.benchmarkCases[0]
  private var currentSceneCount = BenchmarkViewController.benchmarkCases[0].animationCount
  private var animaxMultiThreadEnabled = false
  private var animaxImageModeEnabled = false
  private var mainThreadFps = 0.0
  private var needsStagePopulation = false
  private var lastStageSize = CGSize.zero

  override func viewDidLoad() {
    super.viewDidLoad()
    view.backgroundColor = UIColor(white: 0.98, alpha: 1.0)
    navigationController?.setNavigationBarHidden(true, animated: false)
    parseLaunchArguments()
    loadCaseSpecs()
    showHome()

    if ProcessInfo.processInfo.arguments.contains("--autorun") {
      showScene(engine: selectedEngine, benchmarkCase: benchmarkCaseForLaunch())
    }
  }

  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    navigationController?.setNavigationBarHidden(true, animated: animated)
  }

  override func viewDidLayoutSubviews() {
    super.viewDidLayoutSubviews()
    guard showingScene else {
      return
    }
    let size = stage.bounds.size
    guard size.width > 0, size.height > 0 else {
      return
    }
    if needsStagePopulation || size != lastStageSize {
      populateStage(engine: currentSceneEngine, count: currentBenchmarkCase.animationCount)
      needsStagePopulation = false
      lastStageSize = size
    }
  }

  deinit {
    releaseScene()
  }

  private func parseLaunchArguments() {
    for argument in ProcessInfo.processInfo.arguments {
      if argument.hasPrefix("--engine=") {
        let value = argument.replacingOccurrences(of: "--engine=", with: "")
        selectedEngine = Engine(rawValue: value.lowercased()) ?? selectedEngine
      } else if argument.hasPrefix("--count=") {
        let value = argument.replacingOccurrences(of: "--count=", with: "")
        launchCount = normalizeCount(Int(value) ?? launchCount)
      } else if argument.hasPrefix("--case=") {
        launchCaseId = argument.replacingOccurrences(of: "--case=", with: "")
      } else if argument.hasPrefix("--caseId=") {
        launchCaseId = argument.replacingOccurrences(of: "--caseId=", with: "")
      } else if argument == "--animax-multithread"
          || argument == "--animax-multithread=true" {
        animaxMultiThreadEnabled = true
      } else if argument == "--animax-image-mode"
          || argument == "--animax-image-mode=true" {
        animaxImageModeEnabled = true
      }
    }
  }

  private func showHome() {
    releaseScene()
    showingScene = false
    view.subviews.forEach { $0.removeFromSuperview() }
    view.backgroundColor = UIColor(white: 0.98, alpha: 1.0)

    let stack = UIStackView()
    stack.axis = .vertical
    stack.spacing = 12
    stack.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(stack)

    let titleLabel = UILabel()
    titleLabel.text = "AnimaX vs Lottie FPS Benchmark"
    titleLabel.textColor = UIColor(white: 0.27, alpha: 1.0)
    titleLabel.font = .systemFont(ofSize: 24, weight: .regular)
    titleLabel.adjustsFontSizeToFitWidth = true
    titleLabel.minimumScaleFactor = 0.72
    stack.addArrangedSubview(titleLabel)
    titleLabel.heightAnchor.constraint(equalToConstant: 44).isActive = true

    let engineRow = UIStackView()
    engineRow.axis = .horizontal
    engineRow.spacing = 24
    engineRow.distribution = .fillEqually
    stack.addArrangedSubview(engineRow)
    engineRow.heightAnchor.constraint(equalToConstant: 56).isActive = true

    animaxButton = makeCheckButton(title: "AnimaX", action: #selector(animaxSelected))
    lottieButton = makeCheckButton(title: "Lottie", action: #selector(lottieSelected))
    engineRow.addArrangedSubview(animaxButton!)
    engineRow.addArrangedSubview(lottieButton!)

    animaxMultiThreadButton = makeCheckButton(
      title: "Enable multi thread",
      action: #selector(animaxMultiThreadToggled)
    )
    stack.addArrangedSubview(animaxMultiThreadButton!)
    animaxMultiThreadButton!.heightAnchor.constraint(equalToConstant: 48).isActive = true

    animaxImageModeButton = makeCheckButton(
      title: "Enable image mode",
      action: #selector(animaxImageModeToggled)
    )
    stack.addArrangedSubview(animaxImageModeButton!)
    animaxImageModeButton!.heightAnchor.constraint(equalToConstant: 48).isActive = true

    let buttonGrid = UIStackView()
    buttonGrid.axis = .vertical
    buttonGrid.spacing = 12
    buttonGrid.distribution = .fillEqually
    stack.addArrangedSubview(buttonGrid)

    for rowIndex in stride(from: 0, to: Self.benchmarkCases.count, by: 2) {
      let row = UIStackView()
      row.axis = .horizontal
      row.spacing = 12
      row.distribution = .fillEqually
      buttonGrid.addArrangedSubview(row)

      for index in rowIndex..<min(rowIndex + 2, Self.benchmarkCases.count) {
        let benchmarkCase = Self.benchmarkCases[index]
        let button = UIButton(type: .system)
        button.setTitle(benchmarkCase.buttonLabel, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 20, weight: .regular)
        button.titleLabel?.adjustsFontSizeToFitWidth = true
        button.titleLabel?.minimumScaleFactor = 0.72
        button.setTitleColor(UIColor(white: 0.12, alpha: 1.0), for: .normal)
        button.backgroundColor = UIColor(white: 0.84, alpha: 1.0)
        button.layer.cornerRadius = 4
        button.isEnabled = !caseSpecs.isEmpty
        button.tag = index
        button.addTarget(self, action: #selector(caseTapped(_:)), for: .touchUpInside)
        row.addArrangedSubview(button)
      }
    }

    NSLayoutConstraint.activate([
      stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 24),
      stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
      stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
      stack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -20)
    ])

    updateHomeControls()
  }

  private func showScene(engine: Engine, benchmarkCase: BenchmarkCase) {
    guard !caseSpecs.isEmpty else {
      return
    }

    releaseScene()
    showingScene = true
    selectedEngine = engine
    currentSceneEngine = engine
    currentBenchmarkCase = benchmarkCase
    currentSceneCount = benchmarkCase.animationCount
    mainThreadFps = 0.0
    needsStagePopulation = true
    lastStageSize = .zero
    view.subviews.forEach { $0.removeFromSuperview() }
    view.backgroundColor = UIColor(white: 0.07, alpha: 1.0)

    let stack = UIStackView()
    stack.axis = .vertical
    stack.spacing = 0
    stack.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(stack)

    let header = UIView()
    header.backgroundColor = UIColor(white: 0.07, alpha: 1.0)
    stack.addArrangedSubview(header)
    header.heightAnchor.constraint(equalToConstant: 60).isActive = true

    let backButton = UIButton(type: .system)
    backButton.setTitle("Back", for: .normal)
    backButton.titleLabel?.font = .systemFont(ofSize: 18, weight: .regular)
    backButton.setTitleColor(UIColor(white: 0.12, alpha: 1.0), for: .normal)
    backButton.backgroundColor = UIColor(white: 0.88, alpha: 1.0)
    backButton.layer.cornerRadius = 4
    backButton.translatesAutoresizingMaskIntoConstraints = false
    backButton.addTarget(self, action: #selector(backTapped), for: .touchUpInside)
    header.addSubview(backButton)

    let titleLabel = UILabel()
    titleLabel.text = "\(engine.title) \(benchmarkCase.titleLabel)"
    titleLabel.textColor = .white
    titleLabel.font = .systemFont(ofSize: 24, weight: .regular)
    titleLabel.adjustsFontSizeToFitWidth = true
    titleLabel.minimumScaleFactor = 0.70
    titleLabel.translatesAutoresizingMaskIntoConstraints = false
    header.addSubview(titleLabel)

    NSLayoutConstraint.activate([
      backButton.leadingAnchor.constraint(equalTo: header.leadingAnchor, constant: 12),
      backButton.centerYAnchor.constraint(equalTo: header.centerYAnchor),
      backButton.widthAnchor.constraint(equalToConstant: 92),
      backButton.heightAnchor.constraint(equalToConstant: 44),
      titleLabel.leadingAnchor.constraint(equalTo: backButton.trailingAnchor, constant: 12),
      titleLabel.trailingAnchor.constraint(equalTo: header.trailingAnchor, constant: -12),
      titleLabel.centerYAnchor.constraint(equalTo: header.centerYAnchor)
    ])

    let fpsLabel = InsetLabel(textInsets: UIEdgeInsets(top: 8, left: 16, bottom: 8, right: 16))
    fpsLabel.textColor = UIColor(white: 0.90, alpha: 1.0)
    fpsLabel.font = .systemFont(ofSize: 15, weight: .regular)
    fpsLabel.numberOfLines = 0
    fpsLabel.backgroundColor = UIColor(white: 0.13, alpha: 1.0)
    fpsLabel.translatesAutoresizingMaskIntoConstraints = false
    self.fpsLabel = fpsLabel
    stack.addArrangedSubview(fpsLabel)
    fpsLabel.heightAnchor.constraint(equalToConstant: 156).isActive = true

    stage.backgroundColor = UIColor(white: 0.96, alpha: 1.0)
    stage.clipsToBounds = true
    stack.addArrangedSubview(stage)

    NSLayoutConstraint.activate([
      stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
      stack.leadingAnchor.constraint(equalTo: view.leadingAnchor),
      stack.trailingAnchor.constraint(equalTo: view.trailingAnchor),
      stack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor)
    ])

    updateFpsText()
    mainThreadFpsMonitor.start { [weak self] fps in
      self?.mainThreadFps = fps
      self?.updateFpsText()
    }
    view.setNeedsLayout()
  }

  private func populateStage(engine: Engine, count: Int) {
    releaseAnimationViews()
    stage.subviews.forEach { $0.removeFromSuperview() }
    let spec = gridSpec(for: count, stageSize: stage.bounds.size)
    let grid = UIView(frame: CGRect(
      x: (stage.bounds.width - spec.width) / 2.0,
      y: (stage.bounds.height - spec.height) / 2.0,
      width: spec.width,
      height: spec.height
    ))
    grid.clipsToBounds = true
    stage.addSubview(grid)

    for index in 0..<count {
      let column = index % spec.columns
      let row = index / spec.columns
      let tile = UIView(frame: CGRect(
        x: CGFloat(column) * spec.tileWidth,
        y: CGFloat(row) * spec.tileHeight,
        width: spec.tileWidth,
        height: spec.tileHeight
      ))
      tile.clipsToBounds = true
      grid.addSubview(tile)

      do {
        let assetPath = caseSpecs[0].file
        let animationView = try engine == .animax
          ? createAnimaxView(assetPath: assetPath)
          : createLottieView(assetPath: assetPath)
        animationView.translatesAutoresizingMaskIntoConstraints = false
        tile.addSubview(animationView)
        NSLayoutConstraint.activate([
          animationView.leadingAnchor.constraint(equalTo: tile.leadingAnchor),
          animationView.trailingAnchor.constraint(equalTo: tile.trailingAnchor),
          animationView.topAnchor.constraint(equalTo: tile.topAnchor),
          animationView.bottomAnchor.constraint(equalTo: tile.bottomAnchor)
        ])
      } catch {
        let errorLabel = UILabel(frame: tile.bounds)
        errorLabel.text = "Load error"
        errorLabel.textColor = .systemRed
        errorLabel.font = .systemFont(ofSize: 10, weight: .regular)
        errorLabel.textAlignment = .center
        tile.addSubview(errorLabel)
      }
    }
  }

  private func createAnimaxView(assetPath: String) throws -> UIView {
    let context = AnimaXContext(ability: BaseAnimaXAbility())
    context.enableMultiThreadAccelerate = animaxMultiThreadEnabled

    let animaxView: UIView & AnimaXPlayerProtocol
    if animaxImageModeEnabled {
      animaxView = AnimaXImageView(context: context)
    } else {
      animaxView = AnimaXView(context: context)
    }
    animaxView.setLoop(true)
    animaxView.setAutoplay(true)
    animaxView.setObjectfit("contain")
    animaxView.setSrc(try srcForAsset(assetPath), in: Bundle.main)
    animaxViews.append(animaxView)
    return animaxView
  }

  private func createLottieView(assetPath: String) throws -> UIView {
    let data = try dataForAsset(assetPath)
    let animation = try LottieAnimation.from(data: data)
    let lottieView = LottieAnimationView(
      animation: animation,
      configuration: LottieConfiguration(renderingEngine: .mainThread)
    )
    lottieView.contentMode = .scaleAspectFit
    lottieView.loopMode = .loop
    lottieView.play()
    lottieViews.append(lottieView)
    return lottieView
  }

  private func releaseScene() {
    mainThreadFpsMonitor.stop()
    releaseAnimationViews()
    stage.subviews.forEach { $0.removeFromSuperview() }
    fpsLabel = nil
    needsStagePopulation = false
    lastStageSize = .zero
  }

  private func releaseAnimationViews() {
    for view in animaxViews {
      view.stop()
    }
    for view in lottieViews {
      view.stop()
    }
    animaxViews.removeAll()
    lottieViews.removeAll()
  }

  private func updateFpsText() {
    guard let fpsLabel else {
      return
    }
    let text: String
    if currentSceneEngine == .animax {
      var lines = [
        "Engine: AnimaX  Case: \(currentBenchmarkCase.titleLabel)",
        "Animations: x\(currentSceneCount)"
      ]
      lines.append("Multi thread: \(animaxMultiThreadEnabled ? "enabled" : "disabled")")
      lines.append("Image mode: \(animaxImageModeEnabled ? "enabled" : "disabled")")
      lines.append("Assets: \(assetSummary(for: currentSceneCount))")
      lines.append("Main thread FPS: \(formatFps(mainThreadFps))")
      text = lines.joined(separator: "\n")
    } else {
      var lines = [
        "Engine: Lottie  Case: \(currentBenchmarkCase.titleLabel)",
        "Animations: x\(currentSceneCount)"
      ]
      lines.append("Rendering engine: mainThread")
      lines.append("Assets: \(assetSummary(for: currentSceneCount))")
      lines.append("Main thread FPS: \(formatFps(mainThreadFps))")
      text = lines.joined(separator: "\n")
    }
    fpsLabel.text = text
  }

  private func updateHomeControls() {
    updateCheckButton(animaxButton, selected: selectedEngine == .animax)
    updateCheckButton(lottieButton, selected: selectedEngine == .lottie)
    updateCheckButton(animaxMultiThreadButton, selected: animaxMultiThreadEnabled)
    updateCheckButton(animaxImageModeButton, selected: animaxImageModeEnabled)
    setOptionButton(animaxMultiThreadButton, enabled: selectedEngine == .animax)
    setOptionButton(animaxImageModeButton, enabled: selectedEngine == .animax)
  }

  private func setOptionButton(_ button: UIButton?, enabled: Bool) {
    button?.isHidden = false
    button?.isEnabled = enabled
    button?.alpha = enabled ? 1.0 : 0.45
  }

  private func loadCaseSpecs() {
    do {
      guard let url = Bundle.main.url(
        forResource: "manifest",
        withExtension: "json",
        subdirectory: "export_output"
      ) else {
        throw benchmarkError("manifest not found")
      }
      let data = try Data(contentsOf: url)
      let object = try JSONSerialization.jsonObject(with: data) as? [String: Any]
      guard let rawCases = object?["cases"] as? [[String: Any]] else {
        throw benchmarkError("cases missing")
      }
      var files: [String] = []
      for item in rawCases {
        guard let file = item["file"] as? String, !files.contains(file) else {
          continue
        }
        files.append(file)
      }
      guard !files.isEmpty else {
        throw benchmarkError("manifest has no case files")
      }
      caseSpecs = files.map { CaseSpec(file: $0) }
    } catch {
      caseSpecs = []
      print("[AnimaXBench] Failed to load cases: \(error.localizedDescription)")
    }
  }

  private func dataForAsset(_ assetPath: String) throws -> Data {
    guard let url = urlForAsset(assetPath) else {
      throw benchmarkError("case not found: \(assetPath)")
    }
    return try Data(contentsOf: url)
  }

  private func srcForAsset(_ assetPath: String) throws -> String {
    guard let url = urlForAsset(assetPath) else {
      throw benchmarkError("case not found: \(assetPath)")
    }
    return url.path
  }

  private func urlForAsset(_ assetPath: String) -> URL? {
    let bundlePath = "export_output/" + assetPath
    let name = (bundlePath as NSString).deletingPathExtension
    let ext = (bundlePath as NSString).pathExtension
    return Bundle.main.url(forResource: name, withExtension: ext)
  }

  private func gridSpec(for count: Int, stageSize: CGSize) -> GridSpec {
    if count <= Self.fixedGridCapacity {
      let columns = min(Self.maxColumns, count)
      let rows = Int(ceil(Double(count) / Double(columns)))
      let tileSize = max(CGFloat(48), min(
        stageSize.width / CGFloat(Self.maxColumns),
        stageSize.height / CGFloat(Self.maxRows)
      ))
      return GridSpec(
        columns: columns,
        rows: rows,
        width: CGFloat(columns) * tileSize,
        height: CGFloat(rows) * tileSize,
        tileWidth: tileSize,
        tileHeight: tileSize
      )
    }

    var bestColumns = 1
    var bestRows = count
    var bestScore = -CGFloat.greatestFiniteMagnitude
    for columns in 1...count {
      let rows = Int(ceil(Double(count) / Double(columns)))
      let emptySlots = columns * rows - count
      let tileWidth = stageSize.width / CGFloat(columns)
      let tileHeight = stageSize.height / CGFloat(rows)
      let score = min(tileWidth, tileHeight) - CGFloat(emptySlots) * 1000
      if score > bestScore {
        bestScore = score
        bestColumns = columns
        bestRows = rows
      }
    }

    let tileWidth = max(CGFloat(1), floor(stageSize.width / CGFloat(bestColumns)))
    let tileHeight = max(CGFloat(1), floor(stageSize.height / CGFloat(bestRows)))
    return GridSpec(
      columns: bestColumns,
      rows: bestRows,
      width: tileWidth * CGFloat(bestColumns),
      height: tileHeight * CGFloat(bestRows),
      tileWidth: tileWidth,
      tileHeight: tileHeight
    )
  }

  private func assetSummary(for count: Int) -> String {
    if caseSpecs.isEmpty {
      return "--"
    }
    return "\(caseSpecs[0].file) repeated to \(count)"
  }

  private func normalizeCount(_ count: Int) -> Int {
    benchmarkCaseForCount(count).animationCount
  }

  private func benchmarkCaseForLaunch() -> BenchmarkCase {
    if let launchCaseId, let benchmarkCase = benchmarkCaseById(launchCaseId) {
      return benchmarkCase
    }
    return benchmarkCaseForCount(launchCount)
  }

  private func benchmarkCaseById(_ caseId: String) -> BenchmarkCase? {
    Self.benchmarkCases.first { $0.caseId == caseId }
  }

  private func benchmarkCaseForCount(_ count: Int) -> BenchmarkCase {
    Self.benchmarkCases.first { $0.animationCount == count } ?? Self.benchmarkCases[0]
  }

  private func formatFps(_ fps: Double) -> String {
    fps > 0 ? String(format: "%.1f", fps) : "--"
  }

  private func makeCheckButton(title: String, action: Selector) -> UIButton {
    let button = UIButton(type: .system)
    button.setTitle("  \(title)", for: .normal)
    button.setTitleColor(.label, for: .normal)
    button.setTitleColor(.secondaryLabel, for: .disabled)
    button.titleLabel?.font = .systemFont(ofSize: 20, weight: .regular)
    button.contentHorizontalAlignment = .leading
    button.addTarget(self, action: action, for: .touchUpInside)
    return button
  }

  private func updateCheckButton(_ button: UIButton?, selected: Bool) {
    let imageName = selected ? "checkmark.square.fill" : "square"
    button?.setImage(UIImage(systemName: imageName), for: .normal)
    button?.tintColor = selected ? UIColor(red: 0.0, green: 0.52, blue: 0.45, alpha: 1.0) : .systemGray
  }

  private func benchmarkError(_ message: String) -> NSError {
    NSError(domain: "Benchmark", code: 1, userInfo: [NSLocalizedDescriptionKey: message])
  }

  @objc private func animaxSelected() {
    selectedEngine = .animax
    updateHomeControls()
  }

  @objc private func lottieSelected() {
    selectedEngine = .lottie
    updateHomeControls()
  }

  @objc private func animaxMultiThreadToggled() {
    animaxMultiThreadEnabled.toggle()
    updateHomeControls()
  }

  @objc private func animaxImageModeToggled() {
    animaxImageModeEnabled.toggle()
    updateHomeControls()
  }

  @objc private func caseTapped(_ sender: UIButton) {
    guard Self.benchmarkCases.indices.contains(sender.tag) else {
      return
    }
    showScene(engine: selectedEngine, benchmarkCase: Self.benchmarkCases[sender.tag])
  }

  @objc private func backTapped() {
    showHome()
  }
}

private final class MainThreadFpsMonitor {
  private var displayLink: CADisplayLink?
  private var lastTimestamp: CFTimeInterval = 0
  private var frameCount = 0
  private var onUpdate: ((Double) -> Void)?

  func start(_ onUpdate: @escaping (Double) -> Void) {
    stop()
    self.onUpdate = onUpdate
    lastTimestamp = 0
    frameCount = 0
    let displayLink = CADisplayLink(target: self, selector: #selector(tick(_:)))
    displayLink.add(to: .main, forMode: .common)
    self.displayLink = displayLink
  }

  func stop() {
    displayLink?.invalidate()
    displayLink = nil
    onUpdate = nil
    lastTimestamp = 0
    frameCount = 0
  }

  @objc private func tick(_ displayLink: CADisplayLink) {
    if lastTimestamp == 0 {
      lastTimestamp = displayLink.timestamp
      return
    }
    frameCount += 1
    let elapsed = displayLink.timestamp - lastTimestamp
    guard elapsed >= 1.0 else {
      return
    }
    onUpdate?(Double(frameCount) / elapsed)
    frameCount = 0
    lastTimestamp = displayLink.timestamp
  }
}

private final class InsetLabel: UILabel {
  private let textInsets: UIEdgeInsets

  init(textInsets: UIEdgeInsets) {
    self.textInsets = textInsets
    super.init(frame: .zero)
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    nil
  }

  override func drawText(in rect: CGRect) {
    super.drawText(in: rect.inset(by: textInsets))
  }

  override var intrinsicContentSize: CGSize {
    let size = super.intrinsicContentSize
    return CGSize(
      width: size.width + textInsets.left + textInsets.right,
      height: size.height + textInsets.top + textInsets.bottom
    )
  }
}
