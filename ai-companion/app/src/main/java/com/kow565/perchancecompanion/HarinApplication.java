package com.kow565.perchancecompanion;

import android.app.Application;
import android.content.Context;

public class HarinApplication extends Application {
    private static Context appContext;

    @Override public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
        CompanionJobService.schedule(this);
    }

    public static Context context() {
        if (appContext == null) throw new IllegalStateException("Application context not initialized");
        return appContext;
    }
}
