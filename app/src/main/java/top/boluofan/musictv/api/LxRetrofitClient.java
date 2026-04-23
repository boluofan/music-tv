package top.boluofan.musictv.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LxRetrofitClient {
    private static final String TAG = "LxRetrofitClient";
    private static final String PREFS_NAME = "LxMusicPrefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_TOKEN = "x-user-token";
    private static final String KEY_MI_ACCESS_TOKEN = "mi_access_token";
    private static final String KEY_MI_REFRESH_TOKEN = "mi_refresh_token";
    private static final String KEY_QUALITY = "quality";
    private static final String KEY_ADMIN_PASSWORD = "admin_password";
    private static final String KEY_BACKGROUND_PLAY = "background_play";
    private static final String KEY_API_TYPE = "api_type";

    private static Retrofit retrofit = null;
    private static String currentBaseUrl = null;

    static class AuthInterceptor implements Interceptor {
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            okhttp3.Request request = chain.request();
            String apiType = getApiType(top.boluofan.musictv.MusicTvApp.getInstance());
            if (API_TYPE_MiMusic.equals(apiType)) {
                String token = getMiAccessToken(top.boluofan.musictv.MusicTvApp.getInstance());
                if (token != null && !token.isEmpty()) {
                    request = request.newBuilder()
                            .header("Authorization", "Bearer " + token)
                            .build();
                }
            }
            return chain.proceed(request);
        }
    }

    public static final String API_TYPE_LXserver = "music";
    public static final String API_TYPE_MiMusic = "tv";
    private static final String PATH_PREFIX_TV = "api/tv/";
    private static final String PATH_PREFIX_PLUGIN = "plugin/lxmusic/";
    private static final String PATH_PREFIX_MUSIC = "api/music/";

    public static final String QUALITY_FLAC = "flac";
    public static final String QUALITY_320K = "320k";
    public static final String QUALITY_128K = "128k";

    public static Retrofit getClient(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String baseUrl = prefs.getString(KEY_SERVER_URL, "");

        if (baseUrl.isEmpty()) {
            baseUrl = "http://localhost:9527/";
        }

        if (!baseUrl.startsWith("http")) {
            baseUrl = "http://" + baseUrl;
        }

        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        String apiType = prefs.getString(KEY_API_TYPE, API_TYPE_LXserver);
        if (API_TYPE_MiMusic.equals(apiType)) {
            baseUrl = baseUrl + PATH_PREFIX_PLUGIN + PATH_PREFIX_TV;
        } else {
            baseUrl = baseUrl + PATH_PREFIX_MUSIC;
        }

        if (retrofit != null && baseUrl.equals(currentBaseUrl)) {
            return retrofit;
        }

        currentBaseUrl = baseUrl;

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor())
                .addInterceptor(logging);

        retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(builder.build())
                .build();

        return retrofit;
    }

    public static LxApiService getApiService(Context context) {
        return getClient(context).create(LxApiService.class);
    }

    public static Retrofit getMiMusicAuthClient(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String baseUrl = prefs.getString(KEY_SERVER_URL, "");

        if (baseUrl.isEmpty()) {
            baseUrl = "http://localhost:9527/";
        }

        if (!baseUrl.startsWith("http")) {
            baseUrl = "http://" + baseUrl;
        }

        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        String apiType = prefs.getString(KEY_API_TYPE, API_TYPE_LXserver);
        if (!API_TYPE_MiMusic.equals(apiType)) {
            return null;
        }

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor())
                .addInterceptor(logging);

        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(builder.build())
                .build();
    }

    public static LxApiService getMiMusicAuthService(Context context) {
        Retrofit client = getMiMusicAuthClient(context);
        return client != null ? client.create(LxApiService.class) : null;
    }

    public static String getUsername(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USERNAME, "");
    }

    public static String getPassword(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PASSWORD, "");
    }

    public static String getToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_TOKEN, "");
    }

    public static String getMiAccessToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_MI_ACCESS_TOKEN, "");
    }

    public static String getMiRefreshToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_MI_REFRESH_TOKEN, "");
    }

    public static void saveMiMusicToken(Context context, String accessToken, String refreshToken) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_MI_ACCESS_TOKEN, accessToken)
                .putString(KEY_MI_REFRESH_TOKEN, refreshToken)
                .apply();
    }

    public static String getServerUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SERVER_URL, "");
    }

    public static String getApiType(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_API_TYPE, API_TYPE_LXserver);
    }

    public static void setApiType(Context context, String apiType) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_API_TYPE, apiType).apply();
        resetClient();
    }

    public static String getPathPrefix(Context context) {
        String apiType = getApiType(context);
        if (API_TYPE_MiMusic.equals(apiType)) {
            return PATH_PREFIX_TV;
        }
        return PATH_PREFIX_MUSIC;
    }

    public static void saveConfig(Context context, String serverUrl, String username, String password, String token) {
        saveConfig(context, serverUrl, username, password, token, null);
    }

    public static void saveConfig(Context context, String serverUrl, String username, String password, String token, String apiType) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_SERVER_URL, serverUrl)
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .putString(KEY_TOKEN, token);
        if (apiType != null) {
            editor.putString(KEY_API_TYPE, apiType);
        }
        editor.apply();
        resetClient();
    }

    public static void clearConfig(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String apiType = prefs.getString(KEY_API_TYPE, API_TYPE_LXserver);
        prefs.edit().clear().apply();
        prefs.edit().putString(KEY_API_TYPE, apiType).apply();
        resetClient();
    }

    public static void clearUserInfo(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply();
    }

    public static boolean isLoggedIn(Context context) {
        String username = getUsername(context);
        String password = getPassword(context);
        return !username.isEmpty() && !password.isEmpty();
    }

    public static String getQuality(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_QUALITY, QUALITY_320K);
    }

    public static void setQuality(Context context, String quality) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_QUALITY, quality).apply();
    }

    public static boolean getBackgroundPlay(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_BACKGROUND_PLAY, true);
    }

    public static void setBackgroundPlay(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_BACKGROUND_PLAY, enabled).apply();
    }

    public static void resetClient() {
        retrofit = null;
        currentBaseUrl = null;
    }

    public static String getBasicAuthHeader() {
        Context context = top.boluofan.musictv.MusicTvApp.getInstance();
        if (context == null) return null;
        String username = getUsername(context);
        String password = getPassword(context);
        if (username.isEmpty() || password.isEmpty()) return null;
        String credentials = username + ":" + password;
        return "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
    }

    public static String getAdminAuth(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_ADMIN_PASSWORD, null);
    }

    public static void setAdminPassword(Context context, String password) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_ADMIN_PASSWORD, password).apply();
    }
}
