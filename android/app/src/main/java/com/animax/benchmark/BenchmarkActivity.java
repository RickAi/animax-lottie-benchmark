package com.animax.benchmark;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Trace;
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
import com.lynx.animax.listener.AnimaXParam;
import com.lynx.animax.listener.AnimationListenerAdapter;
import com.lynx.animax.ui.AnimaXView;
import com.lynx.animax.ui.ObjectFit;
import com.lynx.animax.util.UriUtil;
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
  private JSONArray caseRuns = new JSONArray();

  private FrameLayout stage;
  private TextView status;
  private Button runButton;
  private String runId;
  private int iterations;
  private long caseDurationMs;
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
    caseDurationMs = intent.getLongExtra("caseDurationMs", 10000L);
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
    title.setText("AnimaX vs Lottie Case Runner");
    title.setTextSize(20);
    title.setGravity(Gravity.CENTER_VERTICAL);
    root.addView(title, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

    stage = new FrameLayout(this);
    stage.setBackgroundColor(0xfff5f5f5);
    root.addView(stage, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    runButton = new Button(this);
    runButton.setText("Run cases");
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
    caseRuns = new JSONArray();
    setStatus("Run " + runId + " started. iterations=" + iterations
        + " caseDurationMs=" + caseDurationMs
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
    handler.postDelayed(() -> runCase(engine, spec, iteration, () ->
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

  private void runCase(String engine, CaseSpec spec, int iteration, Runnable done) {
    stage.removeAllViews();

    CaseRun caseRun = new CaseRun(engine, spec, iteration);

    EngineHarness harness = "animax".equals(engine)
        ? new AnimaxHarness()
        : new LottieHarness();

    EngineCallback callback = new EngineCallback() {
      boolean completed;

      @Override
      public void onCompositionReady() {
        if (caseRun.compositionReady) {
          return;
        }
        caseRun.compositionReady = true;
        traceEvent("bench_composition_ready");
      }

      @Override
      public void onFirstFrame() {
        if (caseRun.firstFrameSeen) {
          return;
        }
        caseRun.firstFrameSeen = true;
        traceEvent("bench_first_frame");
        handler.postDelayed(() -> finish(null), caseDurationMs);
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
        caseRun.error = error;
        harness.release();
        stage.removeAllViews();
        appendCaseRun(caseRun);
        done.run();
      }
    };

    handler.postDelayed(() -> {
      if (!caseRun.firstFrameSeen) {
        callback.onError("timeout waiting for first frame");
      }
    }, 15000);

    try {
      Trace.beginSection("bench_case_setup");
      try {
        View view;
        Trace.beginSection("bench_create_view");
        try {
          view = harness.create(this, callback);
        } finally {
          Trace.endSection();
        }
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
      } finally {
        Trace.endSection();
      }
    } catch (Exception e) {
      callback.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private void appendCaseRun(CaseRun caseRun) {
    try {
      caseRuns.put(caseRun.toJson());
      writeResults(false);
      setStatus(caseRun.engine + " / " + caseRun.spec.id
          + " " + caseRun.status()
          + (caseRun.error == null ? "" : " error=" + caseRun.error));
    } catch (Exception e) {
      setStatus("Failed to append case run: " + e.getMessage());
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
    root.put("schemaVersion", 2);
    root.put("runnerMode", "case-launch");
    root.put("runId", runId);
    root.put("final", finalWrite);
    root.put("platform", "android");
    root.put("engineFilter", requestedEngine);
    root.put("iterations", iterations);
    root.put("caseDurationMs", caseDurationMs);
    root.put("device", deviceJson());
    root.put("caseRuns", caseRuns);

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

  private static void traceEvent(String section) {
    Trace.beginSection(section);
    Trace.endSection();
  }

  private interface EngineCallback {
    void onCompositionReady();
    void onFirstFrame();
    void onError(String message);
  }

  private interface EngineHarness {
    View create(Activity activity, EngineCallback callback);
    void load(String json, String cacheKey);
    void release();
  }

  private static final class AnimaxHarness implements EngineHarness {
    private AnimaXView view;
    private boolean firstFrame;

    @Override
    public View create(Activity activity, EngineCallback callback) {
      view = new AnimaXView(activity);
      view.setObjectFit(ObjectFit.CONTAIN);
      view.setLoop(true);
      view.setAutoPlay(false);
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
      });
      return view;
    }

    @Override
    public void load(String json, String cacheKey) {
      view.setSrc(UriUtil.fromLocalAsset(cacheKey));
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

  private static final class CaseRun {
    final String engine;
    final CaseSpec spec;
    final int iteration;
    boolean compositionReady;
    boolean firstFrameSeen;
    String error;

    CaseRun(String engine, CaseSpec spec, int iteration) {
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
      json.put("status", status());
      json.put("compositionReady", compositionReady);
      json.put("firstFrameSeen", firstFrameSeen);
      if (error != null) {
        json.put("error", error);
      }
      return json;
    }

    String status() {
      if (error != null) {
        return "error";
      }
      return firstFrameSeen ? "launched" : "started";
    }
  }
}
