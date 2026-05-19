package top.boluofan.musictv;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.MusicUrlResponse;

public class MusicService extends MediaSessionService {
    private static final String TAG = "MusicService";
    private static final String PREFS_NAME = "LxMusicPrefs";
    private static final String RESOLVE_SCHEME = "lxmusic";
    private static final String RESOLVE_HOST = "resolve";
    private static final int RESOLVE_TIMEOUT_SECONDS = 30;

    private MediaSession mediaSession;
    private ExoPlayer player;
    private LxApiService apiService;
    private Handler mainHandler;
    private String quality;

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onCreate() {
        super.onCreate();

        mainHandler = new Handler(Looper.getMainLooper());

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

                        resolvedUrl = fixUrlFormat(resolvedUrl);

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
                .setLoadControl(new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                        .setBufferDurationsMs(15000, 30000, 1500, 2000)
                        .build())
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

    private String resolveMusicUrlSync(String source, String songmid, String name) {
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> songInfo = new HashMap<>();
        songInfo.put("source", source);
        songInfo.put("songmid", songmid);
        if (name != null) {
            songInfo.put("name", name);
        }
        body.put("songInfo", songInfo);
        body.put("quality", quality);

        AtomicReference<String> resultUrl = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        apiService.getMusicUrl(body).enqueue(new Callback<MusicUrlResponse>() {
            @Override
            public void onResponse(Call<MusicUrlResponse> call, Response<MusicUrlResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    MusicUrlResponse urlResponse = response.body();
                    if (urlResponse.isValid()) {
                        String url = urlResponse.getUrl();
                        Log.d(TAG, "API returned URL: " + url);
                        resultUrl.set(url);
                    } else {
                        Log.e(TAG, "Invalid URL response: " + urlResponse);
                    }
                } else {
                    Log.e(TAG, "API call failed: " + response.code());
                }
                latch.countDown();
            }

            @Override
            public void onFailure(Call<MusicUrlResponse> call, Throwable t) {
                Log.e(TAG, "Failed to resolve URL: " + t.getMessage());
                latch.countDown();
            }
        });

        try {
            if (!latch.await(RESOLVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Log.e(TAG, "URL resolution timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "URL resolution interrupted");
        }

        return resultUrl.get();
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
