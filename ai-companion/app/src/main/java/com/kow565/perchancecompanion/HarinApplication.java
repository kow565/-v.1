package com.kow565.perchancecompanion;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

public class HarinApplication extends Application {
    private static Context appContext;

    @Override public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
        PerchanceBrowserTransport.startHeadless(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {
                PerchanceBrowserTransport.bind(activity);
            }
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {
                PerchanceBrowserTransport.unbind(activity);
            }
        });
    }

    public static Context context() {
        if (appContext == null) throw new IllegalStateException("Application context not initialized");
        return appContext;
    }
}
