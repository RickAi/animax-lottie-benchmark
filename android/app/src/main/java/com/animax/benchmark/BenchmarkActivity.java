package com.animax.benchmark;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.RenderMode;
import com.lynx.animax.listener.AnimaXFPSParam;
import com.lynx.animax.listener.AnimationListenerAdapter;
import com.lynx.animax.ui.AnimaXView;
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

public final class BenchmarkActivity extends Activity {
  private static final String ENGINE_ANIMAX = "animax";
  private static final String ENGINE_LOTTIE = "lottie";
  private static final String DEFAULT_ASSET = "lotties/lottie_logo_2.json";
  private static final int[] COUNTS = {1, 5, 10, 20};
  private static final int MAX_COLUMNS = 4;
  private static final int MAX_ROWS = 5;
  private static final long ANIMAX_FPS_INTERVAL_MS = 1000L;

  private final MainThreadFpsMonitor mainThreadFpsMonitor = new MainThreadFpsMonitor();
  private final List<AnimaXView> animaxViews = new ArrayList<>();
  private final List<AnimationListenerAdapter> animaxListeners = new ArrayList<>();
  private final List<LottieAnimationView> lottieViews = new ArrayList<>();
  private final List<Float> animaxGpuFpsValues = new ArrayList<>();

  private CheckBox animaxCheckBox;
  private CheckBox lottieCheckBox;
  private TextView homeStatus;
  private TextView fpsView;
  private FrameLayout stage;
  private String selectedEngine = ENGINE_ANIMAX;
  private String assetPath = DEFAULT_ASSET;
  private String currentSceneEngine = ENGINE_ANIMAX;
  private int currentSceneCount = 1;
  private boolean showingScene;
  private float mainThreadFps;
  private float animaxGpuFps;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (getIntent().getStringExtra("asset") != null) {
      assetPath = getIntent().getStringExtra("asset");
    }
    showHome();
    if (getIntent().getBooleanExtra("autorun", false)) {
      String engine = getIntent().getStringExtra("engine");
      int count = getIntent().getIntExtra("count", 1);
      if (ENGINE_LOTTIE.equalsIgnoreCase(engine)) {
        selectedEngine = ENGINE_LOTTIE;
      }
      showScene(selectedEngine, normalizeCount(count));
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

    TextView subtitle = new TextView(this);
    subtitle.setText("Asset: " + assetPath + "\nChoose one engine, then choose a render count.");
    subtitle.setTextSize(14);
    subtitle.setTextColor(0xff666666);
    root.addView(subtitle, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

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

    LinearLayout buttonColumn = new LinearLayout(this);
    buttonColumn.setOrientation(LinearLayout.VERTICAL);
    buttonColumn.setGravity(Gravity.CENTER);
    root.addView(buttonColumn, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    for (int count : COUNTS) {
      Button button = new Button(this);
      button.setText("x" + count);
      button.setTextSize(18);
      button.setAllCaps(false);
      button.setOnClickListener(v -> showScene(selectedEngine, count));
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
      params.setMargins(0, dp(8), 0, dp(8));
      buttonColumn.addView(button, params);
    }

    homeStatus = new TextView(this);
    homeStatus.setTextSize(13);
    homeStatus.setTextColor(0xff777777);
    homeStatus.setText("Memory is intentionally measured from host-side tooling.");
    root.addView(homeStatus, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

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
  }

  private void showScene(String engine, int count) {
    releaseScene();
    showingScene = true;
    selectedEngine = engine;
    currentSceneEngine = engine;
    currentSceneCount = count;
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
    title.setText(engineLabel(engine) + " x" + count);
    title.setGravity(Gravity.CENTER_VERTICAL);
    title.setTextColor(0xffffffff);
    title.setTextSize(20);
    LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
    titleParams.setMargins(dp(12), 0, 0, 0);
    header.addView(title, titleParams);

    fpsView = new TextView(this);
    fpsView.setTextColor(0xffe6e6e6);
    fpsView.setTextSize(15);
    fpsView.setPadding(dp(16), dp(8), dp(16), dp(8));
    fpsView.setBackgroundColor(0xff222222);
    root.addView(fpsView, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));

    stage = new FrameLayout(this);
    stage.setBackgroundColor(0xfff4f4f4);
    root.addView(stage, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    setContentView(root);
    updateFpsText(engine, count);
    mainThreadFpsMonitor.start(fps -> {
      mainThreadFps = fps;
      updateFpsText(engine, count);
    });

    stage.post(() -> populateStage(engine, count));
  }

  private void populateStage(String engine, int count) {
    if (stage == null || stage.getWidth() == 0 || stage.getHeight() == 0) {
      return;
    }
    GridLayout grid = new GridLayout(this);
    int columns = Math.min(MAX_COLUMNS, count);
    int rows = (int) Math.ceil(count / (float) columns);
    int tileSize = Math.max(dp(48), Math.min(
        stage.getWidth() / MAX_COLUMNS,
        stage.getHeight() / MAX_ROWS));
    grid.setColumnCount(columns);
    grid.setRowCount(rows);
    FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(
        columns * tileSize,
        rows * tileSize,
        Gravity.CENTER);
    stage.addView(grid, gridParams);

    for (int i = 0; i < count; i++) {
      View animationView = ENGINE_ANIMAX.equals(engine)
          ? createAnimaxView(i)
          : createLottieView();
      GridLayout.LayoutParams params = new GridLayout.LayoutParams();
      params.width = tileSize;
      params.height = tileSize;
      params.setMargins(0, 0, 0, 0);
      grid.addView(animationView, params);
    }
  }

  private View createAnimaxView(int index) {
    AnimaXView view = new AnimaXView(this);
    view.setObjectFit(ObjectFit.CONTAIN);
    view.setAutoPlay(true);
    view.setLoop(true);
    view.setFpsEventInterval(ANIMAX_FPS_INTERVAL_MS);
    while (animaxGpuFpsValues.size() <= index) {
      animaxGpuFpsValues.add(0f);
    }
    AnimationListenerAdapter listener = new AnimationListenerAdapter() {
      @Override
      public void onFPS(AnimaXFPSParam param) {
        animaxGpuFpsValues.set(index, param.getFPS());
        animaxGpuFps = averagePositive(animaxGpuFpsValues);
        updateFpsText(currentSceneEngine, currentSceneCount);
      }
    };
    view.addAnimationListener(listener);
    view.setSrc(UriUtil.fromLocalAsset(assetPath));
    animaxViews.add(view);
    animaxListeners.add(listener);
    return view;
  }

  private View createLottieView() {
    LottieAnimationView view = new LottieAnimationView(this);
    view.setCacheComposition(false);
    view.setRenderMode(RenderMode.AUTOMATIC);
    view.setRepeatCount(ValueAnimator.INFINITE);
    view.setRepeatMode(ValueAnimator.RESTART);
    view.setAnimationFromJson(readAssetUnchecked(assetPath), null);
    view.playAnimation();
    lottieViews.add(view);
    return view;
  }

  private void updateFpsText(String engine, int count) {
    if (fpsView == null) {
      return;
    }
    String mainFps = formatFps(mainThreadFps);
    if (ENGINE_ANIMAX.equals(engine)) {
      fpsView.setText("Engine: AnimaX  Count: x" + count
          + "\nMain thread FPS: " + mainFps
          + "\nAnimaX GPU FPS: " + formatFps(animaxGpuFps));
    } else {
      fpsView.setText("Engine: Lottie  Count: x" + count
          + "\nMain thread FPS: " + mainFps);
    }
  }

  private void releaseScene() {
    mainThreadFpsMonitor.stop();
    for (int i = 0; i < animaxViews.size(); i++) {
      AnimaXView view = animaxViews.get(i);
      if (i < animaxListeners.size()) {
        view.removeAnimationListener(animaxListeners.get(i));
      }
      view.stop();
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

  private int normalizeCount(int count) {
    for (int value : COUNTS) {
      if (value == count) {
        return value;
      }
    }
    return 1;
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
}
