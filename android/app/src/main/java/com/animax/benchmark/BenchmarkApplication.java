package com.animax.benchmark;

import android.app.Application;
import com.lynx.animax.util.AnimaX;

public final class BenchmarkApplication extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    if (System.getProperty("jacoco-agent.output") == null) {
      System.setProperty("jacoco-agent.output", "none");
    }
    AnimaX.inst().init(this);
  }
}
