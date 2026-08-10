package top.boluofan.musictv;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Response;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.LoginResponse;
import top.boluofan.musictv.api.model.MusicUrlResponse;

public class MusicService extends MediaSessionService {
    private static final String TAG = "MusicService";
    private static final String PREFS_NAME = "LxMusicPrefs";
    private static final String RESOLVE_SCHEME = "lxmusic";
    private static final String RESOLVE_HOST = "resolve";

    private MediaSession mediaSession;
    private ExoPlayer player;
    private LxApiService apiService;
    private String quality;

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onCreate() {
        super.onCreate();

        android.content.SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        quality = settings.getString("quality", LxRetrofitClient.QUALITY_320K);

        apiService = LxRetrofitClient.getApiService(this);

        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        ResolvingDataSource.Factory resolvingFactory = new ResolvingDataSource.Factory(
                httpDataSourceFactory,
                dataSpec -> {
                    Uri uri = dataSpec.uri;
                    if (RESOLVE_SCHEME.equals(uri.getScheme()) && RESOLVE_HOST.equals(uri.getHost())) {
                        String source = uri.getQueryParameter("source");
                        String songmid = uri.getQueryParameter("songmid");
                        String name = uri.getQueryParameter("name");

                        if (source == null || songmid == null || songmid.isEmpty()) {
                            throw new IOException("Missing or empty source or songmid for URL resolution");
                        }

                        Log.d(TAG, "Resolving URL for: source=" + source + ", songmid=" + songmid + ", name=" + name);

                        String resolvedUrl = resolveMusicUrlSync(source, songmid, name);
                        if (resolvedUrl == null || resolvedUrl.isEmpty()) {
                            throw new IOException("Failed to resolve music URL");
                        }

                        Log.d(TAG, "Resolved URL: " + resolvedUrl);

                        resolvedUrl = preparePlaybackUrl(resolvedUrl);

                        return dataSpec.withUri(Uri.parse(resolvedUrl));
                    }
                    return dataSpec;
                }
        );

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(resolvingFactory))
                .setAudioAttributes(audioAttributes, true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();

        Intent intent = new Intent(this, top.boluofan.musictv.ui.LibraryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(pendingIntent)
                .setId("top.boluofan.musictv.session")
                .build();

        DefaultMediaNotificationProvider notificationProvider = new DefaultMediaNotificationProvider.Builder(this).build();
        setMediaNotificationProvider(notificationProvider);
    }

    private String fixUrlFormat(String url) {
        if (url == null) return url;
        
        if (url.contains("&redirect=1") && !url.contains("?")) {
            url = url.replace("&redirect=1", "?redirect=1");
            Log.d(TAG, "Fixed URL format: " + url);
        }
        
        return url;
    }

    private String preparePlaybackUrl(String url) throws IOException {
        String resolvedUrl = fixUrlFormat(url);
        String serverBaseUrl = LxRetrofitClient.getPureServerUrl(this);
        if (serverBaseUrl == null || serverBaseUrl.isEmpty()) {
            throw new IOException("Server address is empty");
        }

        Uri resolvedUri = Uri.parse(resolvedUrl);
        String scheme = resolvedUri.getScheme();
        if (scheme == null || scheme.isEmpty()) {
            String relativePath = resolvedUrl.startsWith("/") ? resolvedUrl : "/" + resolvedUrl;
            return serverBaseUrl + relativePath;
        }
        return resolvedUrl;
    }

    private String resolveMusicUrlSync(String source, String songmid, String name) throws IOException {
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> songInfo = new HashMap<>();
        songInfo.put("source", source);
        songInfo.put("songmid", songmid);
        if (name != null) {
            songInfo.put("name", name);
        }
        body.put("songInfo", songInfo);
        body.put("quality", quality);

        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);
        boolean isLxServer = LxRetrofitClient.isLXServerApi(this);

        try {
            if (isLxServer && (token == null || token.isEmpty())
                    && !username.isEmpty() && !password.isEmpty()) {
                token = loginAndSaveToken(username, password);
            }

            Response<MusicUrlResponse> response = apiService
                    .getMusicUrl(username, password, token, body)
                    .execute();

            if (isLxServer && response.code() == 401
                    && !username.isEmpty() && !password.isEmpty()) {
                token = loginAndSaveToken(username, password);
                if (token != null && !token.isEmpty()) {
                    response = apiService
                            .getMusicUrl(username, password, token, body)
                            .execute();
                }
            }

            if (response.isSuccessful() && response.body() != null) {
                MusicUrlResponse urlResponse = response.body();
                if (urlResponse.isValid()) {
                    String url = urlResponse.getUrl();
                    Log.d(TAG, "API returned URL: " + url);
                    return url;
                }
                throw new IOException("Server returned an empty music URL");
            }

            String detail = "";
            if (response.errorBody() != null) {
                detail = response.errorBody().string();
                if (detail.length() > 8000) {
                    detail = detail.substring(0, 8000) + "\n…(truncated after 8000 characters)";
                }
            }
            throw new IOException("Music URL request failed: HTTP "
                    + response.code() + (detail.isEmpty() ? "" : " - " + detail));
        } catch (IOException e) {
            Log.e(TAG, "Failed to resolve URL: " + e.getMessage());
            throw e;
        }
    }

    private String loginAndSaveToken(String username, String password) {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", username);
        credentials.put("password", password);

        try {
            Response<LoginResponse> response = LxRetrofitClient
                    .getLxAuthService(this)
                    .loginUser(credentials)
                    .execute();
            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                String token = response.body().getToken();
                if (token != null && !token.isEmpty()) {
                    LxRetrofitClient.saveToken(this, token);
                    return token;
                }
            }
            Log.e(TAG, "LXserver login failed while refreshing playback token: " + response.code());
        } catch (IOException e) {
            Log.e(TAG, "Failed to refresh playback token: " + e.getMessage());
        }
        return null;
    }

    public static Uri buildResolveUri(String source, String songmid, String name) {
        Uri.Builder builder = new Uri.Builder()
                .scheme(RESOLVE_SCHEME)
                .authority(RESOLVE_HOST)
                .appendQueryParameter("source", source)
                .appendQueryParameter("songmid", songmid);
        if (name != null) {
            builder.appendQueryParameter("name", name);
        }
        return builder.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (player != null && !player.getPlayWhenReady()) {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }
}
