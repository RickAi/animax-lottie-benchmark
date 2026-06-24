package com.animax.benchmark;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.RenderMode;
import com.lynx.animax.ability.NativeAbility;
import com.lynx.animax.listener.AnimaXFPSParam;
import com.lynx.animax.listener.AnimationListenerAdapter;
import com.lynx.animax.ui.AnimaXContext;
import com.lynx.animax.ui.AnimaXImageView;
import com.lynx.animax.ui.AnimaXView;
import com.lynx.animax.ui.IAnimaXPlayer;
import com.lynx.animax.ui.IAnimaXView;
import com.lynx.animax.ui.ObjectFit;
import com.lynx.animax.util.UriUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class BenchmarkActivity extends Activity {
  private static final String ENGINE_ANIMAX = "animax";
  private static final String ENGINE_LOTTIE = "lottie";
  private static final String DEFAULT_MANIFEST = "manifest.json";
  private static final int MAX_COLUMNS = 4;
  private static final int MAX_ROWS = 5;
  private static final int MIN_UNIQUE_CASES = MAX_COLUMNS * MAX_ROWS;
  private static final long ANIMAX_FPS_INTERVAL_MS = 1000L;
  private static final BenchmarkCase[] BENCHMARK_CASES = {
      BenchmarkCase.mainThreadBusy("main-thread-30", "30% main thread", 20, 100, 30),
      BenchmarkCase.mainThreadBusy("main-thread-90", "90% main thread", 20, 100, 90),
      BenchmarkCase.renderCount("count-1", "x1", 1),
      BenchmarkCase.renderCount("count-10", "x10", 10),
      BenchmarkCase.renderCount("count-20", "x20", 20),
      BenchmarkCase.renderCount("count-60", "x60", 60)
  };

  private final MainThreadFpsMonitor mainThreadFpsMonitor = new MainThreadFpsMonitor();
  private final MainThreadBusySimulator mainThreadBusySimulator = new MainThreadBusySimulator();
  private final List<IAnimaXView> animaxViews = new ArrayList<>();
  private final List<AnimationListenerAdapter> animaxListeners = new ArrayList<>();
  private final List<LottieAnimationView> lottieViews = new ArrayList<>();
  private final List<Float> animaxGpuFpsValues = new ArrayList<>();
  private final List<String> caseAssetPaths = new ArrayList<>();

  private CheckBox animaxCheckBox;
  private CheckBox lottieCheckBox;
  private CheckBox animaxMultiThreadCheckBox;
  private CheckBox animaxImageModeCheckBox;
  private TextView fpsView;
  private FrameLayout stage;
  private String selectedEngine = ENGINE_ANIMAX;
  private String assetStatus = "";
  private String currentSceneEngine = ENGINE_ANIMAX;
  private BenchmarkCase currentBenchmarkCase = BENCHMARK_CASES[0];
  private boolean showingScene;
  private boolean animaxMultiThreadEnabled;
  private boolean animaxImageModeEnabled;
  private float mainThreadFps;
  private float animaxGpuFps;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    try {
      loadCaseAssets();
      assetStatus = "Loaded " + caseAssetPaths.size()
          + " local assets. High-count scenes repeat assets.";
    } catch (Exception e) {
      assetStatus = "Failed to load local assets: " + e.getMessage();
    }
    animaxMultiThreadEnabled = getIntent().getBooleanExtra("animaxMultiThread", false);
    animaxImageModeEnabled = getIntent().getBooleanExtra("animaxImageMode", false);
    showHome();
    if (getIntent().getBooleanExtra("autorun", false)) {
      String engine = getIntent().getStringExtra("engine");
      String caseId = getIntent().getStringExtra("caseId");
      int count = getIntent().getIntExtra("count", 1);
      if (ENGINE_LOTTIE.equalsIgnoreCase(engine)) {
        selectedEngine = ENGINE_LOTTIE;
      }
      showScene(selectedEngine, benchmarkCaseForLaunch(caseId, count));
    }
  }

  @Override
  protected void onDestroy() {
    releaseScene();
    super.onDestroy();
  }

  @Override
  public void onBackPressed() {
    if (showingScene) {
      releaseScene();
      showHome();
      return;
    }
    super.onBackPressed();
  }

  private void showHome() {
    showingScene = false;
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(20), dp(24), dp(20), dp(20));
    root.setBackgroundColor(0xfffafafa);

    TextView title = new TextView(this);
    title.setText("AnimaX vs Lottie FPS Benchmark");
    title.setTextSize(24);
    title.setTextColor(0xff444444);
    root.addView(title, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

    LinearLayout engineRow = new LinearLayout(this);
    engineRow.setOrientation(LinearLayout.HORIZONTAL);
    engineRow.setGravity(Gravity.CENTER_VERTICAL);
    root.addView(engineRow, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

    animaxCheckBox = new CheckBox(this);
    animaxCheckBox.setText("AnimaX");
    animaxCheckBox.setTextSize(16);
    animaxCheckBox.setChecked(ENGINE_ANIMAX.equals(selectedEngine));
    engineRow.addView(animaxCheckBox, new LinearLayout.LayoutParams(0, dp(48), 1f));

    lottieCheckBox = new CheckBox(this);
    lottieCheckBox.setText("Lottie");
    lottieCheckBox.setTextSize(16);
    lottieCheckBox.setChecked(ENGINE_LOTTIE.equals(selectedEngine));
    engineRow.addView(lottieCheckBox, new LinearLayout.LayoutParams(0, dp(48), 1f));

    animaxCheckBox.setOnClickListener(v -> selectEngine(ENGINE_ANIMAX));
    lottieCheckBox.setOnClickListener(v -> selectEngine(ENGINE_LOTTIE));

    animaxMultiThreadCheckBox = new CheckBox(this);
    animaxMultiThreadCheckBox.setText("Enable multi thread");
    animaxMultiThreadCheckBox.setTextSize(16);
    animaxMultiThreadCheckBox.setChecked(animaxMultiThreadEnabled);
    animaxMultiThreadCheckBox.setOnClickListener(v ->
        animaxMultiThreadEnabled = animaxMultiThreadCheckBox.isChecked());
    root.addView(animaxMultiThreadCheckBox, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

    animaxImageModeCheckBox = new CheckBox(this);
    animaxImageModeCheckBox.setText("Enable image mode");
    animaxImageModeCheckBox.setTextSize(16);
    animaxImageModeCheckBox.setChecked(animaxImageModeEnabled);
    animaxImageModeCheckBox.setOnClickListener(v ->
        animaxImageModeEnabled = animaxImageModeCheckBox.isChecked());
    root.addView(animaxImageModeCheckBox, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
    updateAnimaxOptionVisibility();

    LinearLayout buttonGrid = new LinearLayout(this);
    buttonGrid.setOrientation(LinearLayout.VERTICAL);
    buttonGrid.setGravity(Gravity.TOP);
    root.addView(buttonGrid, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    for (int i = 0; i < BENCHMARK_CASES.length; i += 2) {
      LinearLayout row = new LinearLayout(this);
      row.setOrientation(LinearLayout.HORIZONTAL);
      LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
      rowParams.setMargins(0, dp(6), 0, dp(6));
      buttonGrid.addView(row, rowParams);

      for (int j = i; j < i + 2 && j < BENCHMARK_CASES.length; j++) {
        final BenchmarkCase benchmarkCase = BENCHMARK_CASES[j];
        Button button = new Button(this);
        button.setText(benchmarkCase.buttonLabel);
        button.setTextSize(18);
        button.setAllCaps(false);
        button.setEnabled(!caseAssetPaths.isEmpty());
        button.setOnClickListener(v -> showScene(selectedEngine, benchmarkCase));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        row.addView(button, params);
      }
    }

    setContentView(root);
  }

  private void selectEngine(String engine) {
    selectedEngine = engine;
    if (animaxCheckBox != null) {
      animaxCheckBox.setChecked(ENGINE_ANIMAX.equals(engine));
    }
    if (lottieCheckBox != null) {
      lottieCheckBox.setChecked(ENGINE_LOTTIE.equals(engine));
    }
    updateAnimaxOptionVisibility();
  }

  private void showScene(String engine, BenchmarkCase benchmarkCase) {
    if (caseAssetPaths.isEmpty()) {
      assetStatus = "No local Lottie assets were loaded.";
      showHome();
      return;
    }
    releaseScene();
    showingScene = true;
    selectedEngine = engine;
    currentSceneEngine = engine;
    currentBenchmarkCase = benchmarkCase;
    mainThreadFps = 0f;
    animaxGpuFps = 0f;

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(0xff111111);

    LinearLayout header = new LinearLayout(this);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(dp(12), dp(8), dp(12), dp(8));
    root.addView(header, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

    Button back = new Button(this);
    back.setText("Back");
    back.setAllCaps(false);
    back.setOnClickListener(v -> onBackPressed());
    header.addView(back, new LinearLayout.LayoutParams(dp(92), dp(44)));

    TextView title = new TextView(this);
    title.setText(engineLabel(engine) + " " + benchmarkCase.titleLabel);
    title.setGravity(Gravity.CENTER_VERTICAL);
    title.setTextColor(0xffffffff);
    title.setTextSize(20);
    LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
    titleParams.setMargins(dp(12), 0, 0, 0);
    header.addView(title, titleParams);

    fpsView = new TextView(this);
    fpsView.setTextColor(0xffe6e6e6);
    fpsView.setTextSize(14);
    fpsView.setPadding(dp(16), dp(8), dp(16), dp(8));
    fpsView.setBackgroundColor(0xff222222);
    root.addView(fpsView, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        benchmarkCase.hasMainThreadBusyLoad() ? dp(188) : dp(156)));

    stage = new FrameLayout(this);
    stage.setBackgroundColor(0xfff4f4f4);
    root.addView(stage, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    setContentView(root);
    updateFpsText(engine, benchmarkCase);
    mainThreadFpsMonitor.start(fps -> {
      mainThreadFps = fps;
      updateFpsText(engine, benchmarkCase);
    });

    stage.post(() -> {
      if (!showingScene || currentBenchmarkCase != benchmarkCase
          || !currentSceneEngine.equals(engine)) {
        return;
      }
      populateStage(engine, benchmarkCase.animationCount);
      mainThreadBusySimulator.start(benchmarkCase.mainThreadBusyStrategy);
    });
  }

  private void populateStage(String engine, int count) {
    if (stage == null || stage.getWidth() == 0 || stage.getHeight() == 0) {
      return;
    }
    GridLayout grid = new GridLayout(this);
    GridSpec spec = gridSpecFor(count, stage.getWidth(), stage.getHeight());
    grid.setColumnCount(spec.columns);
    grid.setRowCount(spec.rows);
    grid.setClipChildren(true);
    grid.setClipToPadding(true);
    FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(
        spec.width,
        spec.height,
        Gravity.CENTER);
    stage.addView(grid, gridParams);

    for (int i = 0; i < count; i++) {
      String assetPath = caseAssetPaths.get(i % caseAssetPaths.size());
      View animationView = ENGINE_ANIMAX.equals(engine)
          ? createAnimaxView(i, assetPath)
          : createLottieView(assetPath);
      FrameLayout tile = new ClipFrameLayout(this);
      tile.setClipChildren(true);
      tile.setClipToPadding(true);
      tile.addView(animationView, new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
          Gravity.CENTER));
      GridLayout.LayoutParams params = new GridLayout.LayoutParams();
      params.width = spec.tileWidth;
      params.height = spec.tileHeight;
      params.setMargins(0, 0, 0, 0);
      grid.addView(tile, params);
    }
  }

  private View createAnimaxView(int index, String assetPath) {
    AnimaXContext animaxContext = new AnimaXContext.Builder(new NativeAbility(), this)
        .multiThreadAccelerate(animaxMultiThreadEnabled)
        .build();
    IAnimaXView view = animaxImageModeEnabled
        ? new AnimaXImageView(animaxContext)
        : new AnimaXView(animaxContext);
    IAnimaXPlayer player = view.getPlayer();
    player.setObjectFit(ObjectFit.CONTAIN);
    player.setAutoPlay(true);
    player.setLoop(true);
    player.setFpsEventInterval(ANIMAX_FPS_INTERVAL_MS);
    while (animaxGpuFpsValues.size() <= index) {
      animaxGpuFpsValues.add(0f);
    }
    AnimationListenerAdapter listener = new AnimationListenerAdapter() {
      @Override
      public void onFPS(AnimaXFPSParam param) {
        animaxGpuFpsValues.set(index, param.getFPS());
        animaxGpuFps = averagePositive(animaxGpuFpsValues);
        updateFpsText(currentSceneEngine, currentBenchmarkCase);
      }
    };
    player.addAnimationListener(listener);
    player.setSrc(UriUtil.fromLocalAsset(assetPath));
    animaxViews.add(view);
    animaxListeners.add(listener);
    return (View) view;
  }

  private View createLottieView(String assetPath) {
    LottieAnimationView view = new LottieAnimationView(this);
    view.setCacheComposition(false);
    view.setRenderMode(RenderMode.AUTOMATIC);
    view.setScaleType(ImageView.ScaleType.FIT_CENTER);
    view.setRepeatCount(ValueAnimator.INFINITE);
    view.setRepeatMode(ValueAnimator.RESTART);
    view.setAnimationFromJson(readAssetUnchecked(assetPath), null);
    view.playAnimation();
    lottieViews.add(view);
    return view;
  }

  private void updateFpsText(String engine, BenchmarkCase benchmarkCase) {
    if (fpsView == null) {
      return;
    }
    int count = benchmarkCase.animationCount;
    String mainFps = formatFps(mainThreadFps);
    if (ENGINE_ANIMAX.equals(engine)) {
      String text = "Engine: AnimaX  Case: " + benchmarkCase.titleLabel
          + "\nAnimations: x" + count;
      if (benchmarkCase.hasMainThreadBusyLoad()) {
        text += "\nMain-thread load: "
            + benchmarkCase.mainThreadBusyStrategy.displayDescription();
      }
      text += "\nMulti thread: " + (animaxMultiThreadEnabled ? "enabled" : "disabled")
          + "\nImage mode: " + (animaxImageModeEnabled ? "enabled" : "disabled")
          + "\nAssets: " + assetSummary(count)
          + "\nMain thread FPS: " + mainFps
          + "\nAnimaX GPU FPS: " + formatFps(animaxGpuFps);
      fpsView.setText(text);
    } else {
      String text = "Engine: Lottie  Case: " + benchmarkCase.titleLabel
          + "\nAnimations: x" + count;
      if (benchmarkCase.hasMainThreadBusyLoad()) {
        text += "\nMain-thread load: "
            + benchmarkCase.mainThreadBusyStrategy.displayDescription();
      }
      text += "\nAssets: " + assetSummary(count)
          + "\nMain thread FPS: " + mainFps;
      fpsView.setText(text);
    }
  }

  private void releaseScene() {
    mainThreadBusySimulator.stop();
    mainThreadFpsMonitor.stop();
    for (int i = 0; i < animaxViews.size(); i++) {
      IAnimaXView view = animaxViews.get(i);
      IAnimaXPlayer player = view.getPlayer();
      if (i < animaxListeners.size()) {
        player.removeAnimationListener(animaxListeners.get(i));
      }
      player.stop();
      view.release();
    }
    for (LottieAnimationView view : lottieViews) {
      view.cancelAnimation();
    }
    animaxViews.clear();
    animaxListeners.clear();
    lottieViews.clear();
    animaxGpuFpsValues.clear();
    if (stage != null) {
      stage.removeAllViews();
    }
    stage = null;
    fpsView = null;
  }

  private void loadCaseAssets() throws IOException, JSONException {
    String manifest = readAsset(DEFAULT_MANIFEST);
    JSONArray cases = new JSONObject(manifest).getJSONArray("cases");
    caseAssetPaths.clear();
    for (int i = 0; i < cases.length(); i++) {
      String file = cases.getJSONObject(i).getString("file");
      if (!caseAssetPaths.contains(file)) {
        caseAssetPaths.add(file);
      }
    }
    if (caseAssetPaths.size() < MIN_UNIQUE_CASES) {
      throw new IOException("manifest has fewer than " + MIN_UNIQUE_CASES
          + " unique case files");
    }
  }

  private String readAssetUnchecked(String name) {
    try {
      return readAsset(name);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read asset: " + name, e);
    }
  }

  private String readAsset(String name) throws IOException {
    try (InputStream input = getAssets().open(name);
         BufferedReader reader =
             new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      StringBuilder builder = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        builder.append(line).append('\n');
      }
      return builder.toString();
    }
  }

  private BenchmarkCase benchmarkCaseForLaunch(String caseId, int count) {
    BenchmarkCase byId = benchmarkCaseById(caseId);
    if (byId != null) {
      return byId;
    }
    return benchmarkCaseForCount(count);
  }

  private BenchmarkCase benchmarkCaseById(String caseId) {
    if (caseId == null || caseId.length() == 0) {
      return null;
    }
    for (BenchmarkCase benchmarkCase : BENCHMARK_CASES) {
      if (benchmarkCase.caseId.equals(caseId)) {
        return benchmarkCase;
      }
    }
    return null;
  }

  private BenchmarkCase benchmarkCaseForCount(int count) {
    for (BenchmarkCase benchmarkCase : BENCHMARK_CASES) {
      if (!benchmarkCase.hasMainThreadBusyLoad() && benchmarkCase.animationCount == count) {
        return benchmarkCase;
      }
    }
    return BENCHMARK_CASES[0];
  }

  private void updateAnimaxOptionVisibility() {
    boolean animaxSelected = ENGINE_ANIMAX.equals(selectedEngine);
    if (animaxMultiThreadCheckBox != null) {
      animaxMultiThreadCheckBox.setVisibility(animaxSelected ? View.VISIBLE : View.GONE);
      animaxMultiThreadCheckBox.setEnabled(animaxSelected);
    }
    if (animaxImageModeCheckBox != null) {
      animaxImageModeCheckBox.setVisibility(animaxSelected ? View.VISIBLE : View.GONE);
      animaxImageModeCheckBox.setEnabled(animaxSelected);
    }
  }

  private GridSpec gridSpecFor(int count, int stageWidth, int stageHeight) {
    if (count <= MIN_UNIQUE_CASES) {
      int columns = Math.min(MAX_COLUMNS, count);
      int rows = (int) Math.ceil(count / (float) columns);
      int tileSize = Math.max(dp(48), Math.min(
          stageWidth / MAX_COLUMNS,
          stageHeight / MAX_ROWS));
      return new GridSpec(columns, rows, columns * tileSize, rows * tileSize, tileSize, tileSize);
    }

    int bestColumns = 1;
    int bestRows = count;
    float bestScore = Float.NEGATIVE_INFINITY;
    for (int columns = 1; columns <= count; columns++) {
      int rows = (int) Math.ceil(count / (float) columns);
      int emptySlots = columns * rows - count;
      float tileWidth = stageWidth / (float) columns;
      float tileHeight = stageHeight / (float) rows;
      float score = Math.min(tileWidth, tileHeight) - emptySlots * 1000f;
      if (score > bestScore) {
        bestScore = score;
        bestColumns = columns;
        bestRows = rows;
      }
    }
    int tileWidth = Math.max(1, stageWidth / bestColumns);
    int tileHeight = Math.max(1, stageHeight / bestRows);
    return new GridSpec(
        bestColumns,
        bestRows,
        tileWidth * bestColumns,
        tileHeight * bestRows,
        tileWidth,
        tileHeight);
  }

  private String assetSummary(int count) {
    int localAssetCount = caseAssetPaths.size();
    if (localAssetCount == 0) {
      return "--";
    }
    if (count <= localAssetCount) {
      return count + " unique local JSON";
    }
    return localAssetCount + " local JSON, repeated to " + count;
  }

  private static String engineLabel(String engine) {
    return ENGINE_LOTTIE.equals(engine) ? "Lottie" : "AnimaX";
  }

  private static float averagePositive(List<Float> values) {
    float sum = 0f;
    int count = 0;
    for (Float value : values) {
      if (value != null && value > 0f) {
        sum += value;
        count++;
      }
    }
    return count == 0 ? 0f : sum / count;
  }

  private static String formatFps(float fps) {
    if (fps <= 0f) {
      return "--";
    }
    return String.format(Locale.US, "%.1f", fps);
  }

  private int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
  }

  private static final class GridSpec {
    final int columns;
    final int rows;
    final int width;
    final int height;
    final int tileWidth;
    final int tileHeight;

    GridSpec(int columns, int rows, int width, int height, int tileWidth, int tileHeight) {
      this.columns = columns;
      this.rows = rows;
      this.width = width;
      this.height = height;
      this.tileWidth = tileWidth;
      this.tileHeight = tileHeight;
    }
  }

  private static final class BenchmarkCase {
    final String caseId;
    final String buttonLabel;
    final String titleLabel;
    final int animationCount;
    final MainThreadBusyStrategy mainThreadBusyStrategy;

    private BenchmarkCase(
        String caseId,
        String buttonLabel,
        String titleLabel,
        int animationCount,
        MainThreadBusyStrategy mainThreadBusyStrategy) {
      this.caseId = caseId;
      this.buttonLabel = buttonLabel;
      this.titleLabel = titleLabel;
      this.animationCount = animationCount;
      this.mainThreadBusyStrategy = mainThreadBusyStrategy;
    }

    static BenchmarkCase renderCount(String caseId, String label, int animationCount) {
      return new BenchmarkCase(caseId, label, label, animationCount, null);
    }

    static BenchmarkCase mainThreadBusy(
        String caseId,
        String label,
        int animationCount,
        int intervalMs,
        int blockMs) {
      return new BenchmarkCase(
          caseId,
          label,
          label,
          animationCount,
          new MainThreadBusyStrategy(intervalMs, blockMs));
    }

    boolean hasMainThreadBusyLoad() {
      return mainThreadBusyStrategy != null;
    }
  }

  private static final class MainThreadBusyStrategy {
    final int intervalMs;
    final int blockMs;

    MainThreadBusyStrategy(int intervalMs, int blockMs) {
      this.intervalMs = intervalMs;
      this.blockMs = blockMs;
    }

    String displayDescription() {
      int dutyCycle = Math.round(blockMs * 100f / intervalMs);
      return "busy-spin block " + blockMs + " ms every " + intervalMs
          + " ms on UI thread (~" + dutyCycle + "% duty)";
    }
  }

  private static final class MainThreadBusySimulator {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable busyTask = new Runnable() {
      @Override
      public void run() {
        if (!running || strategy == null) {
          return;
        }
        blockMainThread(strategy.blockMs);
        nextRunUptimeMs += strategy.intervalMs;
        long now = SystemClock.uptimeMillis();
        if (nextRunUptimeMs <= now) {
          nextRunUptimeMs = now + strategy.intervalMs;
        }
        handler.postAtTime(this, nextRunUptimeMs);
      }
    };

    private boolean running;
    private long nextRunUptimeMs;
    private long spinSink;
    private MainThreadBusyStrategy strategy;

    void start(MainThreadBusyStrategy strategy) {
      stop();
      if (strategy == null || strategy.intervalMs <= 0 || strategy.blockMs <= 0) {
        return;
      }
      this.strategy = strategy;
      running = true;
      nextRunUptimeMs = SystemClock.uptimeMillis() + strategy.intervalMs;
      handler.postAtTime(busyTask, nextRunUptimeMs);
    }

    void stop() {
      running = false;
      strategy = null;
      nextRunUptimeMs = 0L;
      handler.removeCallbacks(busyTask);
    }

    private void blockMainThread(int blockMs) {
      long deadlineMs = SystemClock.uptimeMillis() + blockMs;
      long localSink = spinSink;
      while (SystemClock.uptimeMillis() < deadlineMs) {
        localSink += SystemClock.elapsedRealtimeNanos();
      }
      spinSink = localSink;
    }
  }

  private static final class MainThreadFpsMonitor implements Choreographer.FrameCallback {
    private static final long FPS_WINDOW_NS = 1_000_000_000L;

    private boolean running;
    private long windowStartNs;
    private int frames;
    private Callback callback;

    void start(Callback callback) {
      stop();
      this.callback = callback;
      running = true;
      windowStartNs = 0L;
      frames = 0;
      Choreographer.getInstance().postFrameCallback(this);
    }

    void stop() {
      if (running) {
        Choreographer.getInstance().removeFrameCallback(this);
      }
      running = false;
      callback = null;
      windowStartNs = 0L;
      frames = 0;
    }

    @Override
    public void doFrame(long frameTimeNanos) {
      if (!running) {
        return;
      }
      if (windowStartNs == 0L) {
        windowStartNs = frameTimeNanos;
      }
      frames++;
      long elapsed = frameTimeNanos - windowStartNs;
      if (elapsed >= FPS_WINDOW_NS) {
        if (callback != null) {
          callback.onFps(frames * (FPS_WINDOW_NS / (float) elapsed));
        }
        frames = 0;
        windowStartNs = frameTimeNanos;
      }
      Choreographer.getInstance().postFrameCallback(this);
    }

    interface Callback {
      void onFps(float fps);
    }
  }

  private static final class ClipFrameLayout extends FrameLayout {
    ClipFrameLayout(Activity activity) {
      super(activity);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
      int saveCount = canvas.save();
      canvas.clipRect(0, 0, getWidth(), getHeight());
      boolean result = super.drawChild(canvas, child, drawingTime);
      canvas.restoreToCount(saveCount);
      return result;
    }
  }
}
