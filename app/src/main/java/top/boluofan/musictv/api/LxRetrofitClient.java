package top.boluofan.musictv.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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

    // 缓存 Key
    private static final String CACHE_LX_MUSIC = "lx_music";
    private static final String CACHE_LX_USER = "lx_user";
    private static final String CACHE_MIMUSIC_API = "mimusic_api";
    private static final String CACHE_MIMUSIC_AUTH = "mimusic_auth";

    private static final Map<String, Retrofit> RETROFIT_CACHE = new HashMap<>();
    private static final Map<String, String> BASE_URL_CACHE = new HashMap<>();
    private static OkHttpClient miMusicOkHttpClientNoTimeout;

    public static final String API_TYPE_LXserver = "music";
    public static final String API_TYPE_MiMusic = "tv";
    private static final String PATH_PREFIX_TV = "lxmusic-api/";
    private static final String PATH_PREFIX_PLUGIN = "jsplugin/";
    private static final String PATH_LX_MUSIC = "api/music/";
    private static final String PATH_LX_USER = "api/user/";

    public static final String QUALITY_FLAC = "flac";
    public static final String QUALITY_320K = "320k";
    public static final String QUALITY_128K = "128k";

    static class AuthInterceptor implements Interceptor {
        private final Context mContext;

        AuthInterceptor(Context context) {
            this.mContext = context.getApplicationContext();
        }

        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            okhttp3.Request request = chain.request();
            if (mContext == null) {
                return chain.proceed(request);
            }
            String apiType = getApiType(mContext);
            if (API_TYPE_MiMusic.equals(apiType)) {
                String token = getMiAccessToken(mContext);
                if (token != null && !token.isEmpty()) {
                    request = request.newBuilder()
                            .header("Authorization", "Bearer " + token)
                            .build();
                }
            }
            return chain.proceed(request);
        }
    }

    private static String normalizeBaseUrl(String baseUrl, String defaultUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = defaultUrl;
        }
        if (!baseUrl.startsWith("http")) {
            baseUrl = "http://" + baseUrl;
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return baseUrl;
    }

    private static OkHttpClient buildOkHttpClient(Context context) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor(context))
                .addInterceptor(logging)
                .build();
    }

    private static Retrofit getOrCreateRetrofit(Context context, String cacheKey, String baseUrl) {
        Retrofit cachedRetrofit = RETROFIT_CACHE.get(cacheKey);
        String cachedBaseUrl = BASE_URL_CACHE.get(cacheKey);
        if (cachedRetrofit != null && cachedBaseUrl != null && cachedBaseUrl.equals(baseUrl)) {
            return cachedRetrofit;
        }
        OkHttpClient client = buildOkHttpClient(context);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();
        RETROFIT_CACHE.put(cacheKey, retrofit);
        BASE_URL_CACHE.put(cacheKey, baseUrl);
        return retrofit;
    }

    public static Retrofit getClient(Context context, boolean isAuth) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String baseUrl = normalizeBaseUrl(prefs.getString(KEY_SERVER_URL, ""), "http://localhost:9527/");
        String path = isAuth ? PATH_LX_USER : PATH_LX_MUSIC;
        String fullUrl = baseUrl + path;
        String cacheKey = isAuth ? CACHE_LX_USER : CACHE_LX_MUSIC;
        return getOrCreateRetrofit(context, cacheKey, fullUrl);
    }

    public static LxApiService getApiService(Context context) {
        if (isMiMusicApi(context)) {
            return getMiMusicApiService(context);
        } else {
            return getClient(context, false).create(LxApiService.class);
        }
    }

    public static LxApiService getLxAuthService(Context context) {
        return getClient(context, true).create(LxApiService.class);
    }

    public static Retrofit getMiMusicAuthClient(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String baseUrl = normalizeBaseUrl(prefs.getString(KEY_SERVER_URL, ""), "http://localhost:58091/api/v1");
        if (!API_TYPE_MiMusic.equals(getApiType(context))) {
            return null;
        }
        return getOrCreateRetrofit(context, CACHE_MIMUSIC_AUTH, baseUrl);
    }

    public static LxApiService getMiMusicAuthService(Context context) {
        Retrofit client = getMiMusicAuthClient(context);
        return client != null ? client.create(LxApiService.class) : null;
    }

    public static Retrofit getMiMusicApiClient(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String baseUrl = normalizeBaseUrl(prefs.getString(KEY_SERVER_URL, ""), "http://localhost:58091/api/v1");
        if (!API_TYPE_MiMusic.equals(getApiType(context))) {
            return null;
        }
        String fullUrl = baseUrl + PATH_PREFIX_PLUGIN + PATH_PREFIX_TV;
        return getOrCreateRetrofit(context, CACHE_MIMUSIC_API, fullUrl);
    }

    public static LxApiService getMiMusicApiService(Context context) {
        Retrofit client = getMiMusicApiClient(context);
        return client != null ? client.create(LxApiService.class) : null;
    }

    public static LxApiService getMiMusicApiServiceNoTimeout(Context context) {
        if (!API_TYPE_MiMusic.equals(getApiType(context))) {
            return null;
        }
        if (miMusicOkHttpClientNoTimeout == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            miMusicOkHttpClientNoTimeout = new OkHttpClient.Builder()
                    .connectTimeout(0, TimeUnit.MILLISECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .writeTimeout(0, TimeUnit.MILLISECONDS)
                    .addInterceptor(new AuthInterceptor(context))
                    .addInterceptor(logging)
                    .build();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String baseUrl = normalizeBaseUrl(prefs.getString(KEY_SERVER_URL, ""), "http://localhost:58091/api/v1");
        String fullUrl = baseUrl + PATH_PREFIX_PLUGIN + PATH_PREFIX_TV;
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(fullUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(miMusicOkHttpClientNoTimeout)
                .build();
        return retrofit.create(LxApiService.class);
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

    public static String getPureServerUrl(Context context) {
        String baseUrl = getServerUrl(context);
        if (baseUrl.isEmpty()) {
            return "http://localhost:58091";
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        int apiIndex = baseUrl.indexOf("/api/v1");
        if (apiIndex > 0) {
            baseUrl = baseUrl.substring(0, apiIndex);
        }
        int pluginIndex = baseUrl.indexOf("/plugin/");
        if (pluginIndex > 0) {
            baseUrl = baseUrl.substring(0, pluginIndex);
        }
        return baseUrl;
    }

    public static String getApiType(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_API_TYPE, API_TYPE_LXserver);
    }

    public static boolean isMiMusicApi(Context context) {
        return API_TYPE_MiMusic.equals(getApiType(context));
    }

    public static boolean isLXServerApi(Context context) {
        return API_TYPE_LXserver.equals(getApiType(context));
    }

    public static void setApiType(Context context, String apiType) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_API_TYPE, apiType).apply();
        resetClient();
    }

    public static String getPathPrefix(Context context) {
        if (isMiMusicApi(context)) {
            return PATH_PREFIX_TV;
        }
        return PATH_LX_MUSIC;
    }

    public static void saveConfig(Context context, String serverUrl, String username, String password, String token) {
        saveConfig(context, serverUrl, username, password, token, null);
    }

    public static void saveConfig(Context context, String serverUrl, String username, String password, String token, String apiType) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String oldApiType = prefs.getString(KEY_API_TYPE, API_TYPE_LXserver);

        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_SERVER_URL, serverUrl)
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .putString(KEY_TOKEN, token);

        // 强制更新 apiType，确保全局标识同步
        if (apiType != null) {
            editor.putString(KEY_API_TYPE, apiType);
        } else {
            // apiType 为 null 时，默认使用 LXServer
            apiType = API_TYPE_LXserver;
            editor.putString(KEY_API_TYPE, apiType);
        }

        // 切换 API 类型时，清除另一个类型的 token，避免混用
        if (!apiType.equals(oldApiType)) {
            if (API_TYPE_MiMusic.equals(oldApiType)) {
                // 从 MiMusic 切换走，清除 MiMusic token
                editor.remove(KEY_MI_ACCESS_TOKEN);
                editor.remove(KEY_MI_REFRESH_TOKEN);
            } else {
                // 从 LXServer 切换走，清除 LXServer token
                editor.remove(KEY_TOKEN);
            }
        }

        editor.apply();
        resetClient();
    }

    public static void clearConfig(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String apiType = prefs.getString(KEY_API_TYPE, API_TYPE_LXserver);
        prefs.edit().clear().putString(KEY_API_TYPE, apiType).apply();
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
        RETROFIT_CACHE.clear();
        BASE_URL_CACHE.clear();
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
