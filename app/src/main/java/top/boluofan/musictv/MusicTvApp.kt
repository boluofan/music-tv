package top.boluofan.musictv;

import android.app.Application;
import android.content.Context;

public class MusicTvApp extends Application {
    private static MusicTvApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static MusicTvApp getInstance() {
        return instance;
    }

    public static Context getAppContext() {
        return instance;
    }
}
