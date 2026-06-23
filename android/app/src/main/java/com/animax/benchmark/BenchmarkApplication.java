package com.animax.benchmark;

import android.app.Application;
import com.lynx.animax.util.AnimaX;

public final class BenchmarkApplication extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    AnimaX.inst().init(this);
  }
}
