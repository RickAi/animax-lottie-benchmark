package com.animax.benchmark;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.os.Trace;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.RenderMode;
import com.lynx.animax.listener.AnimaXFPSParam;
import com.lynx.animax.listener.AnimaXParam;
import com.lynx.animax.listener.AnimationListenerAdapter;
import com.lynx.animax.ui.AnimaXView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class BenchmarkActivity extends Activity {
  private static final String TAG = "AnimaXBench";

  private final Handler handler = new Handler(Looper.getMainLooper());
  private final List<CaseSpec> cases = new ArrayList<>();
  private JSONArray samples = new JSONArray();

  private FrameLayout stage;
  private TextView status;
  private Button runButton;
  private String runId;
  private int iterations;
  private long warmupMs;
  private long measureMs;
  private String requestedEngine;
  private File latestJson;
  private boolean running;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    readConfig(getIntent());
    setupUi();
    try {
      loadCases();
      setStatus("Loaded " + cases.size() + " cases. Tap Run or start with --ez autorun true.");
    } catch (Exception e) {
      setStatus("Failed to load cases: " + e.getMessage());
    }
    if (getIntent().getBooleanExtra("autorun", false)) {
      handler.postDelayed(this::startBenchmark, 600);
    }
  }

  private void readConfig(Intent intent) {
    iterations = intent.getIntExtra("iterations", 3);
    warmupMs = intent.getLongExtra("warmupMs", 1000L);
    measureMs = intent.getLongExtra("measureMs", 10000L);
    requestedEngine = intent.getStringExtra("engine");
    if (requestedEngine == null || requestedEngine.length() == 0) {
      requestedEngine = "all";
    }
    runId = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
  }

  private void setupUi() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(16), dp(16), dp(16), dp(16));

    TextView title = new TextView(this);
    title.setText("AnimaX vs Lottie Benchmark");
    title.setTextSize(20);
    title.setGravity(Gravity.CENTER_VERTICAL);
    root.addView(title, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

    stage = new FrameLayout(this);
    stage.setBackgroundColor(0xfff5f5f5);
    root.addView(stage, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    runButton = new Button(this);
    runButton.setText("Run benchmark");
    runButton.setOnClickListener(v -> startBenchmark());
    root.addView(runButton, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

    ScrollView scroll = new ScrollView(this);
    status = new TextView(this);
    status.setTextSize(12);
    status.setTextIsSelectable(true);
    scroll.addView(status);
    root.addView(scroll, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(160)));

    setContentView(root);
  }

  private void loadCases() throws IOException, JSONException {
    String manifest = readAsset("manifest.json");
    JSONArray array = new JSONObject(manifest).getJSONArray("cases");
    cases.clear();
    String onlyCase = getIntent().getStringExtra("case");
    for (int i = 0; i < array.length(); i++) {
      JSONObject item = array.getJSONObject(i);
      CaseSpec spec = new CaseSpec(
          item.getString("id"),
          item.getString("file"),
          item.optString("category", ""),
          item.optJSONArray("features"));
      if (onlyCase == null || onlyCase.equals(spec.id)) {
        cases.add(spec);
      }
    }
    if (cases.isEmpty()) {
      throw new IOException("No benchmark cases matched");
    }
  }

  private void startBenchmark() {
    if (running) {
      return;
    }
    running = true;
    runButton.setEnabled(false);
    samples = new JSONArray();
    setStatus("Run " + runId + " started. iterations=" + iterations
        + " warmupMs=" + warmupMs + " measureMs=" + measureMs
        + " engine=" + requestedEngine);
    runNext(0, 0, 0);
  }

  private void runNext(int engineIndex, int caseIndex, int iteration) {
    List<String> engines = engines();
    if (engineIndex >= engines.size()) {
      finishRun();
      return;
    }
    if (caseIndex >= cases.size()) {
      runNext(engineIndex + 1, 0, 0);
      return;
    }
    if (iteration >= iterations) {
      runNext(engineIndex, caseIndex + 1, 0);
      return;
    }

    String engine = engines.get(engineIndex);
    CaseSpec spec = cases.get(caseIndex);
    setStatus("Running " + engine + " / " + spec.id + " iteration "
        + (iteration + 1) + "/" + iterations);
    handler.postDelayed(() -> runSample(engine, spec, iteration, () ->
        handler.postDelayed(() -> runNext(engineIndex, caseIndex, iteration + 1), 500)), 250);
  }

  private List<String> engines() {
    if ("animax".equalsIgnoreCase(requestedEngine)) {
      return Collections.singletonList("animax");
    }
    if ("lottie".equalsIgnoreCase(requestedEngine)) {
      return Collections.singletonList("lottie");
    }
    List<String> list = new ArrayList<>();
    list.add("animax");
    list.add("lottie");
    return list;
  }

  private void runSample(String engine, CaseSpec spec, int iteration, Runnable done) {
    System.gc();
    stage.removeAllViews();

    Sample sample = new Sample(engine, spec, iteration);
    sample.loadStartNs = SystemClock.elapsedRealtimeNanos();
    sample.cpuStartMs = Process.getElapsedCpuTime();
    sample.memoryStart = MemorySnapshot.capture();

    EngineHarness harness = "animax".equals(engine)
        ? new AnimaxHarness()
        : new LottieHarness();
    FrameSampler frameSampler = new FrameSampler(displayRefreshRate());
    MemorySampler memorySampler = new MemorySampler();

    EngineCallback callback = new EngineCallback() {
      boolean measurementStarted;
      boolean completed;

      @Override
      public void onCompositionReady() {
        if (sample.compositionMs < 0) {
          sample.compositionMs = elapsedMs(sample.loadStartNs);
        }
      }

      @Override
      public void onFirstFrame() {
        if (sample.firstFrameMs < 0) {
          Trace.beginSection("bench_first_frame");
          sample.firstFrameMs = elapsedMs(sample.loadStartNs);
          Trace.endSection();
        }
        if (measurementStarted) {
          return;
        }
        measurementStarted = true;
        handler.postDelayed(() -> {
          frameSampler.start();
          memorySampler.start();
          handler.postDelayed(() -> finish(null), measureMs);
        }, warmupMs);
      }

      @Override
      public void onEngineFps(float fps) {
        sample.engineFps.add(fps);
      }

      @Override
      public void onError(String message) {
        finish(message);
      }

      private void finish(String error) {
        if (completed) {
          return;
        }
        completed = true;
        frameSampler.stop();
        memorySampler.stop();
        sample.error = error;
        sample.cpuEndMs = Process.getElapsedCpuTime();
        sample.memoryEnd = MemorySnapshot.capture();
        sample.memoryPeakPssKb = memorySampler.peakPssKb;
        sample.frameStats = frameSampler.stats();
        sample.engineMemoryBytes = harness.engineMemoryBytes();
        harness.release();
        stage.removeAllViews();
        appendSample(sample);
        done.run();
      }
    };

    handler.postDelayed(() -> {
      if (sample.firstFrameMs < 0) {
        callback.onError("timeout waiting for first frame");
      }
    }, 15000);

    try {
      View view = harness.create(this, callback);
      stage.addView(view, new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      String json;
      Trace.beginSection("bench_read_asset");
      try {
        json = readAsset(spec.file);
      } finally {
        Trace.endSection();
      }
      Trace.beginSection("bench_set_animation");
      try {
        harness.load(json, spec.file);
      } finally {
        Trace.endSection();
      }
    } catch (Exception e) {
      callback.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private void appendSample(Sample sample) {
    try {
      samples.put(sample.toJson());
      writeResults(false);
      FrameStats stats = sample.frameStats;
      setStatus(sample.engine + " / " + sample.spec.id
          + " done: fps=" + round(stats.averageFps)
          + " p95=" + round(stats.p95Ms) + "ms"
          + " jank=" + round(stats.jankPercent) + "%"
          + (sample.error == null ? "" : " error=" + sample.error));
    } catch (Exception e) {
      setStatus("Failed to append sample: " + e.getMessage());
    }
  }

  private void finishRun() {
    try {
      writeResults(true);
      setStatus("Run complete. Result: " + latestJson.getAbsolutePath()
          + "\nPull with: adb exec-out run-as com.animax.benchmark cat files/results/"
          + latestJson.getName() + " > " + latestJson.getName());
    } catch (Exception e) {
      setStatus("Run complete but failed to write result: " + e.getMessage());
    }
    running = false;
    runButton.setEnabled(true);
  }

  private void writeResults(boolean finalWrite) throws IOException, JSONException {
    JSONObject root = new JSONObject();
    root.put("schemaVersion", 1);
    root.put("runId", runId);
    root.put("final", finalWrite);
    root.put("platform", "android");
    root.put("engineFilter", requestedEngine);
    root.put("iterations", iterations);
    root.put("warmupMs", warmupMs);
    root.put("measureMs", measureMs);
    root.put("device", deviceJson());
    root.put("samples", samples);

    File dir = new File(getFilesDir(), "results");
    if (!dir.exists() && !dir.mkdirs()) {
      throw new IOException("Unable to create " + dir);
    }
    latestJson = new File(dir, "animax-lottie-android-" + runId + ".json");
    try (FileOutputStream out = new FileOutputStream(latestJson)) {
      out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
    }
  }

  private JSONObject deviceJson() throws JSONException {
    JSONObject device = new JSONObject();
    device.put("manufacturer", Build.MANUFACTURER);
    device.put("model", Build.MODEL);
    device.put("sdkInt", Build.VERSION.SDK_INT);
    device.put("abi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "");
    device.put("refreshRate", displayRefreshRate());
    return device;
  }

  private float displayRefreshRate() {
    if (Build.VERSION.SDK_INT >= 30 && getDisplay() != null) {
      return getDisplay().getRefreshRate();
    }
    if (stage != null && stage.getDisplay() != null) {
      return stage.getDisplay().getRefreshRate();
    }
    return 60f;
  }

  private String readAsset(String name) throws IOException {
    try (InputStream input = getAssets().open(name);
         BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      StringBuilder builder = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        builder.append(line).append('\n');
      }
      return builder.toString();
    }
  }

  private void setStatus(String text) {
    String line = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + " " + text;
    if (status == null) {
      android.util.Log.i(TAG, line);
      return;
    }
    status.append(line + "\n");
    android.util.Log.i(TAG, line);
  }

  private int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
  }

  private static double elapsedMs(long startNs) {
    return (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000.0;
  }

  private static double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  private interface EngineCallback {
    void onCompositionReady();
    void onFirstFrame();
    void onEngineFps(float fps);
    void onError(String message);
  }

  private interface EngineHarness {
    View create(Activity activity, EngineCallback callback);
    void load(String json, String cacheKey);
    long engineMemoryBytes();
    void release();
  }

  private static final class AnimaxHarness implements EngineHarness {
    private AnimaXView view;
    private boolean firstFrame;

    @Override
    public View create(Activity activity, EngineCallback callback) {
      view = new AnimaXView(activity);
      view.setLoop(true);
      view.setAutoPlay(false);
      view.setFpsEventInterval(1000);
      view.addAnimationListener(new AnimationListenerAdapter() {
        @Override
        public void onReady(AnimaXParam param) {
          callback.onCompositionReady();
          view.play();
        }

        @Override
        public void onCompositionReady(AnimaXParam param) {
          callback.onCompositionReady();
        }

        @Override
        public void onFirstFrame(AnimaXParam param) {
          if (!firstFrame) {
            firstFrame = true;
            callback.onFirstFrame();
          }
        }

        @Override
        public void onFPS(AnimaXFPSParam param) {
          callback.onEngineFps(param.getFPS());
        }
      });
      return view;
    }

    @Override
    public void load(String json, String cacheKey) {
      view.setJson(json);
    }

    @Override
    public long engineMemoryBytes() {
      return 0L;
    }

    @Override
    public void release() {
      if (view != null) {
        view.stop();
        view.release();
        view = null;
      }
    }
  }

  private static final class LottieHarness implements EngineHarness {
    private LottieAnimationView view;
    private boolean firstFrame;

    @Override
    public View create(Activity activity, EngineCallback callback) {
      view = new LottieAnimationView(activity);
      view.setCacheComposition(false);
      view.setRenderMode(RenderMode.AUTOMATIC);
      view.setRepeatCount(ValueAnimator.INFINITE);
      view.addLottieOnCompositionLoadedListener(composition -> {
        callback.onCompositionReady();
        view.playAnimation();
      });
      view.addAnimatorUpdateListener(animation -> {
        if (!firstFrame) {
          firstFrame = true;
          callback.onFirstFrame();
        }
      });
      return view;
    }

    @Override
    public void load(String json, String cacheKey) {
      view.setAnimationFromJson(json, null);
    }

    @Override
    public long engineMemoryBytes() {
      return 0L;
    }

    @Override
    public void release() {
      if (view != null) {
        view.cancelAnimation();
        view.removeAllAnimatorListeners();
        view.removeAllUpdateListeners();
        view = null;
      }
    }
  }

  private static final class CaseSpec {
    final String id;
    final String file;
    final String category;
    final JSONArray features;

    CaseSpec(String id, String file, String category, JSONArray features) {
      this.id = id;
      this.file = file;
      this.category = category;
      this.features = features == null ? new JSONArray() : features;
    }
  }

  private static final class Sample {
    final String engine;
    final CaseSpec spec;
    final int iteration;
    final List<Float> engineFps = new ArrayList<>();
    long loadStartNs;
    long cpuStartMs;
    long cpuEndMs;
    double compositionMs = -1;
    double firstFrameMs = -1;
    MemorySnapshot memoryStart;
    MemorySnapshot memoryEnd;
    long memoryPeakPssKb;
    FrameStats frameStats = new FrameStats();
    long engineMemoryBytes;
    String error;

    Sample(String engine, CaseSpec spec, int iteration) {
      this.engine = engine;
      this.spec = spec;
      this.iteration = iteration;
    }

    JSONObject toJson() throws JSONException {
      JSONObject json = new JSONObject();
      json.put("engine", engine);
      json.put("caseId", spec.id);
      json.put("file", spec.file);
      json.put("category", spec.category);
      json.put("features", spec.features);
      json.put("iteration", iteration);
      json.put("compositionMs", compositionMs);
      json.put("firstFrameMs", firstFrameMs);
      json.put("processCpuMs", cpuEndMs - cpuStartMs);
      json.put("engineMemoryBytes", engineMemoryBytes);
      json.put("engineFpsMean", mean(engineFps));
      json.put("memoryStart", memoryStart.toJson());
      json.put("memoryEnd", memoryEnd.toJson());
      json.put("memoryPeakPssKb", memoryPeakPssKb);
      json.put("frames", frameStats.toJson());
      if (error != null) {
        json.put("error", error);
      }
      return json;
    }

    private static double mean(List<Float> values) {
      if (values.isEmpty()) {
        return -1;
      }
      double sum = 0;
      for (Float value : values) {
        sum += value;
      }
      return sum / values.size();
    }
  }

  private static final class MemorySnapshot {
    long totalPssKb;
    long nativePssKb;
    long dalvikPssKb;
    long otherPssKb;
    long nativeHeapAllocatedKb;
    long javaHeapUsedKb;

    static MemorySnapshot capture() {
      Debug.MemoryInfo info = new Debug.MemoryInfo();
      Debug.getMemoryInfo(info);
      Runtime runtime = Runtime.getRuntime();
      MemorySnapshot snapshot = new MemorySnapshot();
      snapshot.totalPssKb = info.getTotalPss();
      snapshot.nativePssKb = info.nativePss;
      snapshot.dalvikPssKb = info.dalvikPss;
      snapshot.otherPssKb = info.otherPss;
      snapshot.nativeHeapAllocatedKb = Debug.getNativeHeapAllocatedSize() / 1024;
      snapshot.javaHeapUsedKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024;
      return snapshot;
    }

    JSONObject toJson() throws JSONException {
      JSONObject json = new JSONObject();
      json.put("totalPssKb", totalPssKb);
      json.put("nativePssKb", nativePssKb);
      json.put("dalvikPssKb", dalvikPssKb);
      json.put("otherPssKb", otherPssKb);
      json.put("nativeHeapAllocatedKb", nativeHeapAllocatedKb);
      json.put("javaHeapUsedKb", javaHeapUsedKb);
      return json;
    }
  }

  private final class MemorySampler implements Runnable {
    long peakPssKb;
    boolean running;

    void start() {
      running = true;
      peakPssKb = MemorySnapshot.capture().totalPssKb;
      handler.postDelayed(this, 250);
    }

    void stop() {
      running = false;
      handler.removeCallbacks(this);
    }

    @Override
    public void run() {
      if (!running) {
        return;
      }
      peakPssKb = Math.max(peakPssKb, MemorySnapshot.capture().totalPssKb);
      handler.postDelayed(this, 250);
    }
  }

  private static final class FrameSampler implements Choreographer.FrameCallback {
    private final List<Long> intervalsNs = new ArrayList<>();
    private final double refreshPeriodNs;
    private boolean running;
    private long firstFrameNs;
    private long lastFrameNs;
    private long previousFrameNs;

    FrameSampler(float refreshRate) {
      refreshPeriodNs = 1_000_000_000.0 / Math.max(1.0, refreshRate);
    }

    void start() {
      running = true;
      Choreographer.getInstance().postFrameCallback(this);
    }

    void stop() {
      running = false;
      Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
      if (!running) {
        return;
      }
      if (firstFrameNs == 0) {
        firstFrameNs = frameTimeNanos;
      }
      if (previousFrameNs != 0) {
        intervalsNs.add(frameTimeNanos - previousFrameNs);
      }
      previousFrameNs = frameTimeNanos;
      lastFrameNs = frameTimeNanos;
      Choreographer.getInstance().postFrameCallback(this);
    }

    FrameStats stats() {
      FrameStats stats = new FrameStats();
      if (intervalsNs.isEmpty() || firstFrameNs == 0 || lastFrameNs <= firstFrameNs) {
        return stats;
      }
      List<Double> ms = new ArrayList<>(intervalsNs.size());
      int jank = 0;
      int dropped = 0;
      for (Long interval : intervalsNs) {
        double intervalMs = interval / 1_000_000.0;
        ms.add(intervalMs);
        if (interval > refreshPeriodNs * 1.5) {
          jank++;
        }
        dropped += Math.max(0, (int) Math.round(interval / refreshPeriodNs) - 1);
      }
      Collections.sort(ms);
      double durationSec = (lastFrameNs - firstFrameNs) / 1_000_000_000.0;
      stats.frameCount = intervalsNs.size() + 1;
      stats.averageFps = stats.frameCount / durationSec;
      stats.p50Ms = percentile(ms, 50);
      stats.p90Ms = percentile(ms, 90);
      stats.p95Ms = percentile(ms, 95);
      stats.p99Ms = percentile(ms, 99);
      stats.jankPercent = 100.0 * jank / intervalsNs.size();
      stats.droppedFrames = dropped;
      return stats;
    }
  }

  private static final class FrameStats {
    int frameCount;
    double averageFps;
    double p50Ms;
    double p90Ms;
    double p95Ms;
    double p99Ms;
    double jankPercent;
    int droppedFrames;

    JSONObject toJson() throws JSONException {
      JSONObject json = new JSONObject();
      json.put("frameCount", frameCount);
      json.put("averageFps", averageFps);
      json.put("p50Ms", p50Ms);
      json.put("p90Ms", p90Ms);
      json.put("p95Ms", p95Ms);
      json.put("p99Ms", p99Ms);
      json.put("jankPercent", jankPercent);
      json.put("droppedFrames", droppedFrames);
      return json;
    }
  }

  private static double percentile(List<Double> sorted, int percentile) {
    if (sorted.isEmpty()) {
      return 0;
    }
    double rank = (percentile / 100.0) * (sorted.size() - 1);
    int low = (int) Math.floor(rank);
    int high = (int) Math.ceil(rank);
    if (low == high) {
      return sorted.get(low);
    }
    double fraction = rank - low;
    return sorted.get(low) * (1 - fraction) + sorted.get(high) * fraction;
  }
}
