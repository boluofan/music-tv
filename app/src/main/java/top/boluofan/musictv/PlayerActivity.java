package top.boluofan.musictv;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

import java.util.Collections;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import android.content.SharedPreferences;
import com.google.gson.JsonObject;
import top.boluofan.musictv.player.MiMusicPlayerHelper;

public class PlayerActivity extends AppCompatActivity {
    private static final String TAG = "PlayerActivity";
    private ImageView ivBigCover;
    private ImageView ivBlurBackground;
    private TextView tvBigTitle;
    private TextView tvBigArtist;
    private RecyclerView rvLyrics;
    private TextView tvNoLyrics; 
    private SeekBar progressBar; // Changed to SeekBar
    private TextView tvCurrentTime, tvDuration;
    private TextView tvTooltip, tvSeekProgress, tvPlaylistName, tvPlaylistCount;
    private android.widget.LinearLayout layoutTooltip, layoutSeekProgress;
    private ImageButton btnRepeat, btnPrev, btnPlayPause, btnNext, btnScrape, btnQueue;
    private android.view.View layoutDrawer;
    private android.view.ViewGroup layoutMainContent;
    private RecyclerView rvDrawerSongs;
    private DrawerSongAdapter drawerSongAdapter;
    private android.view.View layoutOptionMenu;
    private RecyclerView rvOptions;
    private PlayerOptionAdapter optionAdapter;
    private String scrapedPicUrl = ""; // 用于保存刮削到的封面地址
    private String scrapedArtist = ""; // 用于保存刮削到的歌手名

    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private LyricAdapter lyricAdapter;
    private top.boluofan.musictv.api.LxApiService apiService;
    private String baseUrl;
    private SongScraper songScraper;
    private String currentScrapingId = "";
    private String currentSongSource = "mg";
    private String currentSongMid = "";
    private boolean hasArtwork = false;
    private final Runnable scrapeTimeoutRunnable = () -> {
        Log.e(TAG, "Scrape Timeout!");
        runOnUiThread(() -> {
            Toast.makeText(PlayerActivity.this, "刮削超时", Toast.LENGTH_SHORT).show();
            stopScrapeAnimation();
        });
        if (lyricAdapter.getItemCount() <= 0) {
            showNoLyrics("暂无歌词 (请求超时)");
        }
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 1000);
        }
    };

    // Auto-hide bottom controls logic
    private android.view.View layoutBottomControls;
    private static final int CONTROLS_HIDE_DELAY_MS = 10000;
    private final Runnable hideControlsRunnable = this::hideBottomControls;

    // Remote capture long press / double click
    private long lastLeftClickTime = 0;
    private long lastRightClickTime = 0;
    private static final long DOUBLE_CLICK_INTERVAL = 300;
    private boolean isSeeking = false;
    private final Runnable seekForwardRunnable = new Runnable() {
        @Override public void run() {
            if (player != null) {
                player.seekForward();
                updateTooltip(true);
                handler.postDelayed(this, 300);
            }
        }
    };
    private final Runnable seekBackwardRunnable = new Runnable() {
        @Override public void run() {
            if (player != null) {
                player.seekBack();
                updateTooltip(false);
                handler.postDelayed(this, 300);
            }
        }
    };

    private void updateTooltip(boolean isForward) {
        if (player == null) return;
        handler.removeCallbacks(showShortcutHintRunnable); // Cancel hint if seeking starts
        handler.removeCallbacks(hideSeekHintRunnable);
        long current = player.getCurrentPosition();
        long duration = player.getDuration();
        String prefix = isForward ? "▶▶ " : "◀◀ ";
        tvSeekProgress.setText(prefix + formatTime(current) + " / " + formatTime(duration));
        layoutSeekProgress.setVisibility(View.VISIBLE);
    }

    private final Runnable hideSeekHintRunnable = () -> {
        layoutSeekProgress.setVisibility(View.GONE);
    };

    private final Runnable showShortcutHintRunnable = () -> {
        if (!isSeeking) { // Only show if not currently seeking
            tvSeekProgress.setText("长按「左」「右」键快进快退，双击「左」「右」键切换歌曲");
            layoutSeekProgress.setVisibility(View.VISIBLE);
            handler.postDelayed(hideSeekHintRunnable, 2000);
        }
    };

    private void showShortcutHint() {
        handler.removeCallbacks(showShortcutHintRunnable);
        handler.removeCallbacks(hideSeekHintRunnable);
        // Increase delay to 500ms to ensure long press (which starts around 300-500ms) 
        // has enough time to cancel this hint.
        handler.postDelayed(showShortcutHintRunnable, 500);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        ivBigCover = findViewById(R.id.ivBigCover);
        ivBlurBackground = findViewById(R.id.ivBlurBackground);
        tvBigTitle = findViewById(R.id.tvBigTitle);
        tvBigArtist = findViewById(R.id.tvBigArtist);
        rvLyrics = findViewById(R.id.rvLyrics);
        tvNoLyrics = findViewById(R.id.tvNoLyrics); 
        progressBar = findViewById(R.id.progressBar);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvDuration = findViewById(R.id.tvDuration);
        tvTooltip = findViewById(R.id.tvTooltip);
        tvSeekProgress = findViewById(R.id.tvSeekProgress);
        tvPlaylistName = findViewById(R.id.tvPlaylistName);
        tvPlaylistCount = findViewById(R.id.tvPlaylistCount);
        layoutTooltip = findViewById(R.id.layoutTooltip);
        layoutSeekProgress = findViewById(R.id.layoutSeekProgress);

        btnRepeat = findViewById(R.id.btnRepeat);
        btnPrev = findViewById(R.id.btnPrev);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);
        btnScrape = findViewById(R.id.btnScrape);
        btnQueue = findViewById(R.id.btnQueue);
        layoutDrawer = findViewById(R.id.layoutDrawer);
        layoutMainContent = findViewById(R.id.layoutMainContent);
        layoutBottomControls = findViewById(R.id.layoutBottomControls);
        layoutOptionMenu = findViewById(R.id.layoutOptionMenu);
        rvOptions = findViewById(R.id.rvOptions);
        
        rvDrawerSongs = findViewById(R.id.rvDrawerSongs);
        
        rvLyrics.setLayoutManager(new LinearLayoutManager(this));
        rvLyrics.setItemAnimator(null); // Disable animations to prevent marquee reset
        lyricAdapter = new LyricAdapter();
        rvLyrics.setAdapter(lyricAdapter);

        // Option Menu Setup
        rvOptions.setLayoutManager(new LinearLayoutManager(this));
        List<String> options = new ArrayList<>();
        options.add("修改歌手名");
        options.add("重新刮削歌词");
        options.add("保存当前歌词");
        
        optionAdapter = new PlayerOptionAdapter(options, (option, position) -> {
            handleOptionClick(option);
        });
        optionAdapter.setOptionDisabled("保存当前歌词", true);
        rvOptions.setAdapter(optionAdapter);
        layoutOptionMenu.findViewById(R.id.viewOptionMenuDim).setOnClickListener(v -> toggleOptionMenu(false));

        // Sidebar Drawer Setup
        rvDrawerSongs.setLayoutManager(new LinearLayoutManager(this));
        drawerSongAdapter = new DrawerSongAdapter(this);
        rvDrawerSongs.setAdapter(drawerSongAdapter);
        drawerSongAdapter.setOnItemClickListener((song, position) -> {
            if (player != null) {
                player.seekToDefaultPosition(position);
                player.play();
                toggleDrawer(false);
            }
        });
        
        // Optimize Marquee performance and isolation
        // Removed Hardware Layer to prevent artifacts during layout updates

        SharedPreferences settings = getSharedPreferences("LxMusicPrefs", 0);
        baseUrl = settings.getString("server_url", "");
        apiService = top.boluofan.musictv.api.LxRetrofitClient.getApiService(this);

        // Enable marquee
        tvBigTitle.setSelected(true);
        tvBigArtist.setSelected(true);

        progressBar.setOnFocusChangeListener((v, hasFocus) -> {
            layoutTooltip.setVisibility(hasFocus ? View.VISIBLE : View.INVISIBLE);
            if (hasFocus) {
                updateProgress();
                resetControlsTimer();
            }
        });

        btnPlayPause.post(() -> btnPlayPause.requestFocus());
        setupControls();
        setupSeekBarListener();

        // Keep screen on while this activity is in foreground
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        songScraper = new SongScraper();
        resetControlsTimer();
    }

    @Override
    protected void onStart() {
        super.onStart();
        SessionToken token = new SessionToken(this, new android.content.ComponentName(this, MusicService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                player = controllerFuture.get();
                setupPlayer();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(this));
    }

    private void setupControls() {
        btnPlayPause.setOnClickListener(v -> {
            if (player == null) return;
            if (player.isPlaying()) player.pause();
            else player.play();
            resetControlsTimer();
        });

        btnPrev.setOnClickListener(v -> {
            skipToPrevious();
            resetControlsTimer();
        });

        btnNext.setOnClickListener(v -> {
            if (player != null) {
                player.seekToNext();
                resetControlsTimer();
            }
        });

        btnScrape.setOnClickListener(v -> {
            String songName = tvBigTitle.getText().toString();
            if (!songName.isEmpty() && !songName.equals("Song Title")) {
                startScrapeAnimation();
                startExternalScrapeFlow(songName);
            }
            resetControlsTimer();
        });

        btnQueue.setOnClickListener(v -> {
            toggleDrawer(true);
            resetControlsTimer();
        });
        
        layoutDrawer.setOnClickListener(v -> toggleDrawer(false));

        btnRepeat.setOnClickListener(v -> {
            if (player == null) return;
            
            // Current State
            int repeatMode = player.getRepeatMode();
            boolean isShuffle = player.getShuffleModeEnabled();

            // Next State Logic: All(Loop) -> Shuffle(All+S) -> One(1) -> All(Loop)...
            if (isShuffle) {
                // Currently Shuffle -> Switch to Single Loop (One)
                player.setShuffleModeEnabled(false);
                player.setRepeatMode(Player.REPEAT_MODE_ONE);
                Toast.makeText(this, "已切换为单曲循环", Toast.LENGTH_SHORT).show();
            } else if (repeatMode == Player.REPEAT_MODE_ONE) {
                // Currently Single Loop -> Switch to List Loop (All)
                player.setRepeatMode(Player.REPEAT_MODE_ALL);
                Toast.makeText(this, "已切换为列表循环", Toast.LENGTH_SHORT).show();
            } else {
                // Currently List Loop (or somehow Off) -> Switch to Shuffle
                player.setRepeatMode(Player.REPEAT_MODE_ALL); 
                player.setShuffleModeEnabled(true);
                Toast.makeText(this, "已切换为随机播放", Toast.LENGTH_SHORT).show();
            }
            updateControlsUI();
            resetControlsTimer();
        });
    }

    private void toggleDrawer(boolean show) {
        if (show) {
            layoutDrawer.setVisibility(View.VISIBLE);
            // Lock focus to the drawer by blocking background interaction
            if (layoutMainContent != null) {
                layoutMainContent.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            }
            // Prepare data from current player queue
            if (player != null) {
                List<top.boluofan.musictv.DrawerSongAdapter.DrawerSongItem> queue = new ArrayList<>();
                for (int i = 0; i < player.getMediaItemCount(); i++) {
                    androidx.media3.common.MediaItem item = player.getMediaItemAt(i);
                    String title = item.mediaMetadata.title != null ? item.mediaMetadata.title.toString() : "";
                    String artist = item.mediaMetadata.artist != null ? item.mediaMetadata.artist.toString() : "";
                    String coverUrl = item.mediaMetadata.artworkUri != null ? item.mediaMetadata.artworkUri.toString() : "";
                    
                    queue.add(new top.boluofan.musictv.DrawerSongAdapter.DrawerSongItem(title, artist, coverUrl, "", ""));
                }
                drawerSongAdapter.setSongs(queue);
                drawerSongAdapter.setPlayingIndex(player.getCurrentMediaItemIndex());
                drawerSongAdapter.setPlayerPlaying(player.isPlaying());
            }

            // Show current playlist name from prefs
        SharedPreferences prefs = getSharedPreferences("LxMusicPrefs", 0);
            String playlistName = prefs.getString("last_playlist_name", "");
            if (tvPlaylistName != null) {
                tvPlaylistName.setText(playlistName.isEmpty() ? "" : "[" + playlistName + "]");
            }
            if (tvPlaylistCount != null && player != null) {
                tvPlaylistCount.setText(player.getMediaItemCount() + "首");
            }

            rvDrawerSongs.post(() -> {
                if (player != null) {
                    int currentIndex = player.getCurrentMediaItemIndex();
                    rvDrawerSongs.scrollToPosition(currentIndex);
                    
                    // Delay slightly to ensure RecyclerView has bound the view for requestFocus to succeed
                    rvDrawerSongs.postDelayed(() -> {
                        RecyclerView.ViewHolder vh = rvDrawerSongs.findViewHolderForAdapterPosition(currentIndex);
                        if (vh != null && vh.itemView != null) {
                            vh.itemView.requestFocus();
                        } else {
                            rvDrawerSongs.requestFocus();
                        }
                    }, 50);
                }
            });
        } else {
            layoutDrawer.setVisibility(View.GONE);
            // Restore background interaction when drawer is closed
            if (layoutMainContent != null) {
                layoutMainContent.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
            btnQueue.requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        if (layoutOptionMenu.getVisibility() == View.VISIBLE) {
            toggleOptionMenu(false);
        } else if (layoutDrawer.getVisibility() == View.VISIBLE) {
            toggleDrawer(false);
            resetControlsTimer();
        } else if (isControlsVisible()) {
            hideBottomControls();
        } else {
            super.onBackPressed();
        }
    }

    private boolean isControlsVisible() {
        return layoutBottomControls.getVisibility() == View.VISIBLE && layoutBottomControls.getAlpha() > 0;
    }

    private void showBottomControls() {
        if (layoutBottomControls.getVisibility() == View.VISIBLE && layoutBottomControls.getAlpha() == 1f) return;
        
        layoutBottomControls.setVisibility(View.VISIBLE);
        ObjectAnimator.ofFloat(layoutBottomControls, "alpha", layoutBottomControls.getAlpha(), 1f)
                .setDuration(300)
                .start();
        
        btnPlayPause.requestFocus();
        resetControlsTimer();
    }

    private void hideBottomControls() {
        if (layoutBottomControls.getVisibility() == View.GONE) return;

        ObjectAnimator animator = ObjectAnimator.ofFloat(layoutBottomControls, "alpha", layoutBottomControls.getAlpha(), 0f);
        animator.setDuration(300);
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                layoutBottomControls.setVisibility(View.GONE);
                layoutTooltip.setVisibility(View.INVISIBLE);
            }
        });
        animator.start();
    }

    private void resetControlsTimer() {
        handler.removeCallbacks(hideControlsRunnable);
        // If controls are visible or currently animating into visibility
        if (layoutBottomControls != null && layoutBottomControls.getVisibility() == View.VISIBLE) {
            handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS);
        }
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (layoutDrawer.getVisibility() == View.VISIBLE || layoutOptionMenu.getVisibility() == View.VISIBLE) {
            return super.dispatchKeyEvent(event);
        }

        if (action == android.view.KeyEvent.ACTION_DOWN) {
            if (keyCode == android.view.KeyEvent.KEYCODE_MENU) {
                toggleOptionMenu(true);
                return true;
            }
            resetControlsTimer();
            if (!isControlsVisible()) {
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    showBottomControls();
                    return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (event.getRepeatCount() == 0) {
                        long now = System.currentTimeMillis();
                        if (now - lastLeftClickTime < DOUBLE_CLICK_INTERVAL) {
                            handler.removeCallbacks(showShortcutHintRunnable);
                            handler.removeCallbacks(hideSeekHintRunnable);
                            layoutSeekProgress.setVisibility(View.GONE);
                            skipToPrevious();
                            lastLeftClickTime = 0;
                        } else {
                            lastLeftClickTime = now;
                            showShortcutHint();
                        }
                    } else {
                        // Any repeat means the user is holding the key
                        handler.removeCallbacks(showShortcutHintRunnable);
                        handler.removeCallbacks(hideSeekHintRunnable);
                        if (!isSeeking) {
                            layoutSeekProgress.setVisibility(View.GONE);
                        }
                        if (event.getRepeatCount() > 2) {
                            if (!isSeeking) {
                                isSeeking = true;
                                handler.post(seekBackwardRunnable);
                            }
                        }
                    }
                    return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.getRepeatCount() == 0) {
                        long now = System.currentTimeMillis();
                        if (now - lastRightClickTime < DOUBLE_CLICK_INTERVAL) {
                            handler.removeCallbacks(showShortcutHintRunnable);
                            handler.removeCallbacks(hideSeekHintRunnable);
                            layoutSeekProgress.setVisibility(View.GONE);
                            if (player != null) player.seekToNext();
                            lastRightClickTime = 0;
                        } else {
                            lastRightClickTime = now;
                            showShortcutHint();
                        }
                    } else {
                        // Any repeat means the user is holding the key
                        handler.removeCallbacks(showShortcutHintRunnable);
                        handler.removeCallbacks(hideSeekHintRunnable);
                        if (!isSeeking) {
                            layoutSeekProgress.setVisibility(View.GONE);
                        }
                        if (event.getRepeatCount() > 2) {
                            if (!isSeeking) {
                                isSeeking = true;
                                handler.post(seekForwardRunnable);
                            }
                        }
                    }
                    return true;
                }
            } else {
                // Controls visible
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    // If focus is already on bottom row, hide
                    View focused = getCurrentFocus();
                    if (focused == btnPlayPause || focused == btnPrev || focused == btnNext || focused == btnScrape || focused == btnRepeat || focused == btnQueue) {
                        hideBottomControls();
                        return true;
                    }
                }
            }
        } else if (action == android.view.KeyEvent.ACTION_UP) {
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT || keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (isSeeking) {
                    handler.removeCallbacks(showShortcutHintRunnable);
                    isSeeking = false;
                    handler.removeCallbacks(seekForwardRunnable);
                    handler.removeCallbacks(seekBackwardRunnable);
                    layoutSeekProgress.setVisibility(View.GONE);
                }
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private void skipToPrevious() {
        if (player == null) return;
        // If the track has played for a while, seekToPrevious normally just restarts the current song.
        // We want to force it to ALWAYS skip to the previous song.
        // So if we are past the threshold (typically 3s), we seek to 0 first, then call seekToPrevious.
        if (player.getCurrentPosition() > player.getMaxSeekToPreviousPosition()) {
            player.seekTo(0);
        }
        player.seekToPrevious();
    }
    
    // This was originally inside setupControls(), moved out as per instruction's implied structure
    // to accommodate toggleDrawer and onBackPressed.
    private void setupSeekBarListener() {
        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    long duration = player.getDuration();
                    if (duration > 0) {
                         long newPos = (duration * progress) / 100;
                         tvCurrentTime.setText(formatTime(newPos));
                         player.seekTo(newPos);
                         
                         // Instantly trace tooltip changes
                         if (layoutTooltip.getVisibility() == View.VISIBLE) {
                             tvTooltip.setText(formatTime(newPos) + "/" + formatTime(duration));
                             int availableWidth = seekBar.getWidth() - seekBar.getPaddingLeft() - seekBar.getPaddingRight();
                             float thumbX = seekBar.getPaddingLeft() + (availableWidth * (float) progress / 100f);
                             layoutTooltip.setTranslationX(thumbX - (layoutTooltip.getWidth() / 2f));
                         }
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                 stopProgressUpdater();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                 startProgressUpdater();
            }
        });
    }

    private void updateControlsUI() {
        if (player == null) return;
        
        // Play/Pause
        btnPlayPause.setImageResource(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play); 

        // Mode Display
        int repeatMode = player.getRepeatMode();
        boolean isShuffle = player.getShuffleModeEnabled();

        if (isShuffle) {
            btnRepeat.setImageResource(R.drawable.ic_shuffle);
            btnRepeat.setAlpha(1.0f);
        } else if (repeatMode == Player.REPEAT_MODE_ONE) {
            btnRepeat.setImageResource(R.drawable.ic_repeat_one);
            btnRepeat.setAlpha(1.0f);
        } else if (repeatMode == Player.REPEAT_MODE_ALL) {
            btnRepeat.setImageResource(R.drawable.ic_repeat);
            btnRepeat.setAlpha(1.0f);
        } else {
            // Fallback (Off) visually mapped to List Loop as it's the enforced default
            btnRepeat.setImageResource(R.drawable.ic_repeat);
            btnRepeat.setAlpha(1.0f);
        }
        
        if (layoutDrawer.getVisibility() == View.VISIBLE) {
            drawerSongAdapter.setPlayingIndex(player.getCurrentMediaItemIndex());
            drawerSongAdapter.setPlayerPlaying(player.isPlaying());
        }
    }

    private void setupPlayer() {
        player.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                updateMetadata(mediaItem);
            }
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) startProgressUpdater();
                else stopProgressUpdater();
                updateControlsUI();
            }
            @Override
            public void onRepeatModeChanged(int repeatMode) {
                updateControlsUI();
            }
            @Override
            public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
                updateControlsUI();
            }
            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                String errorMsg = error.getErrorCodeName() + " - " + error.getMessage();
                Throwable cause = error.getCause();
                if (cause != null) {
                    if (cause.getMessage() != null) {
                        errorMsg += "\nCause: " + cause.getMessage();
                    } else {
                        errorMsg += "\nCause: " + cause.toString();
                    }
                }
                Log.e(TAG, "Player Error: " + errorMsg);
                Toast.makeText(PlayerActivity.this, "播放失败: " + errorMsg, Toast.LENGTH_LONG).show();
            }
        });
        
        // Enforce Default Mode: List Loop (REPEAT_MODE_ALL)
        if (player.getRepeatMode() == Player.REPEAT_MODE_OFF && !player.getShuffleModeEnabled()) {
            player.setRepeatMode(Player.REPEAT_MODE_ALL);
        }
        
        if (player.getCurrentMediaItem() != null) {
            updateMetadata(player.getCurrentMediaItem());
        }
        if (player.isPlaying()) startProgressUpdater();
        updateControlsUI();
    }

    private void updateMetadata(MediaItem mediaItem) {
        if (mediaItem == null) return;
        
        CharSequence titleSeq = mediaItem.mediaMetadata.title;
        String title = titleSeq != null ? titleSeq.toString() : null;
        String originalName = null;
        String lyrics = null;

        if (mediaItem.mediaMetadata.extras != null) {
            originalName = mediaItem.mediaMetadata.extras.getString("original_name");
            lyrics = mediaItem.mediaMetadata.extras.getString("lyrics");
            currentSongSource = mediaItem.mediaMetadata.extras.getString("source");
            currentSongMid = mediaItem.mediaMetadata.extras.getString("songmid");
            if (currentSongSource == null) currentSongSource = "mg";
            if (currentSongMid == null) currentSongMid = "";
        }

        // 获取 MiMusic 歌词 URL
        String lyricUrl = null;
        if (mediaItem.mediaMetadata.extras != null) {
            lyricUrl = mediaItem.mediaMetadata.extras.getString("lyric_url");
        }
        
        SharedPreferences prefs = getSharedPreferences("XiaoMusicPrefs", 0);
        String savedName = prefs.getString("current_song_name", null);
        
        Log.d(TAG, "updateMetadata: title=" + title + ", lrc=" + (lyrics != null ? "yes" : "no"));
        
        String queryName = originalName != null ? originalName : (savedName != null ? savedName : title);
        
        if (queryName != null && !queryName.equals(savedName)) {
            prefs.edit().putString("current_song_name", queryName).apply();
        }
        
        if (title != null) {
            tvBigTitle.setText(title);
            tvBigArtist.setText(mediaItem.mediaMetadata.artist != null ? mediaItem.mediaMetadata.artist : "");
        } else if (queryName != null) {
            tvBigTitle.setText(queryName);
            tvBigArtist.setText("加载中...");
        }

        // 1. 处理歌词状态
        boolean isCurrentlyScraping = mediaItem.mediaId.equals(currentScrapingId);
        if (lyricUrl != null && !lyricUrl.isEmpty()) {
            // 有 lyricUrl，从后端端点获取歌词
            fetchMiMusicLyric(lyricUrl);
        } else if (lyrics != null && !lyrics.isEmpty()) {
            // 内嵌歌词，直接使用
            String lyricArtist = extractArtistFromLyrics(lyrics);
            if (lyricArtist != null && !lyricArtist.isEmpty()) {
                tvBigArtist.setText(lyricArtist);
            }
            parseLyrics(lyrics);
        } else if (!isCurrentlyScraping) {
            showNoLyrics("加载歌词...");
        }

        // 2. 处理封面状态
        hasArtwork = false;
        if (mediaItem.mediaMetadata.artworkData != null) {
            byte[] data = mediaItem.mediaMetadata.artworkData;
            Glide.with(this).load(data)
                .placeholder(R.drawable.ic_cover_placeholder)
                .error(R.drawable.ic_cover_placeholder)
                .transform(new RoundedCorners(80)).into(ivBigCover);
            Glide.with(this).load(data)
                .transform(new jp.wasabeef.glide.transformations.BlurTransformation(20, 3))
                .into(ivBlurBackground);
            hasArtwork = true;
        } else if (mediaItem.mediaMetadata.artworkUri != null) {
            String uri = mediaItem.mediaMetadata.artworkUri.toString();
            Log.d(TAG, "Loading artwork from metadata: " + uri);
            Glide.with(this).load(uri)
                .placeholder(R.drawable.ic_cover_placeholder)
                .error(R.drawable.ic_cover_placeholder)
                .transform(new RoundedCorners(80)).into(ivBigCover);
            Glide.with(this).load(uri)
                .transform(new jp.wasabeef.glide.transformations.BlurTransformation(20, 3))
                .into(ivBlurBackground);
            hasArtwork = true;
        } else {
            // 如果当前在抓取中，不要轻易重置图片，避免闪烁
            if (!isCurrentlyScraping) {
                ivBigCover.setImageResource(R.drawable.ic_cover_placeholder);
                ivBlurBackground.setImageResource(android.R.color.black);
            }
        }

        // 3. 触发抓取补全逻辑：只要缺歌词，就去尝试获取
        // 封面如果已存在，则不刮削
        if (lyrics == null || lyrics.isEmpty()) {
            // 只有当歌曲 ID 变化时（或者之前没记录到 ID）才重新触发
            if (!isCurrentlyScraping) {
                fetchMusicInfoForScraping(queryName, false);
            }
        } else {
            // 如果歌词已获取到，重置状态
            currentScrapingId = "";
        }
    }

    private void fetchMusicInfoForScraping(String songName, boolean forceScrape) {
        if (songName == null || songName.isEmpty()) return;
        
        MediaItem current = player != null ? player.getCurrentMediaItem() : null;
        if (current != null) {
            String targetId = current.mediaId + (forceScrape ? "_forced" : "");
            if (targetId.equals(currentScrapingId)) return; // 已经在刮削了
            currentScrapingId = targetId;
        }
        
        // 每次重新开始刮削前，禁用保存功能
        if (optionAdapter != null) optionAdapter.setOptionDisabled("保存当前歌词", true);
        scrapedPicUrl = "";
        scrapedArtist = "";
        
        Log.d(TAG, "Triggering initial scraper check for: " + songName + " (force=" + forceScrape + ")");
        showNoLyrics("正在获取歌词...");
        
        if (forceScrape) {
            startExternalScrapeFlow(songName);
            return;
        }
        
        // 1. 尝试从 lxserver 获取歌词
        String source = currentSongSource != null ? currentSongSource : "mg";
        String songmid = currentSongMid != null ? currentSongMid : "";

        if (songmid.isEmpty()) {
            startExternalScrapeFlow(songName);
            return;
        }

        String quality = top.boluofan.musictv.api.LxRetrofitClient.getQuality(this);

        apiService.getLyric(source, songmid, quality).enqueue(new retrofit2.Callback<top.boluofan.musictv.api.model.LyricInfo>() {
            @Override
            public void onResponse(retrofit2.Call<top.boluofan.musictv.api.model.LyricInfo> call,
                                   retrofit2.Response<top.boluofan.musictv.api.model.LyricInfo> response) {
                if (response.isSuccessful() && response.body() != null) {
                    top.boluofan.musictv.api.model.LyricInfo lyricInfo = response.body();
                    String lyrics = lyricInfo.getLyric();
                    String tlyrics = lyricInfo.getTlyric();

                    if ((lyrics == null || lyrics.isEmpty()) && (tlyrics == null || tlyrics.isEmpty())) {
                        startExternalScrapeFlow(songName);
                        return;
                    }

                    if (lyrics != null && !lyrics.isEmpty()) {
                        parseLyrics(lyrics);
                    } else if (tlyrics != null && !tlyrics.isEmpty()) {
                        parseLyrics(tlyrics);
                    }
                } else {
                    startExternalScrapeFlow(songName);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<top.boluofan.musictv.api.model.LyricInfo> call, Throwable t) {
                Log.e(TAG, "获取歌词失败: " + t.getMessage());
                startExternalScrapeFlow(songName);
            }
        });
    }

    private void startExternalScrapeFlow(String songName) {
        if (songScraper == null) return;
        
        // 获取当前的歌手信息作为提示
        String artistHint = tvBigArtist.getText().toString();
        if ("加载中...".equals(artistHint) || "未知艺术家".equals(artistHint)) {
            artistHint = "";
        }

        Log.d(TAG, "开始第三方刮削流程 | 歌曲名: " + songName + " | 歌手提示: " + artistHint);
        
        // 增加 15 秒超时保护
        handler.removeCallbacks(scrapeTimeoutRunnable);
        handler.postDelayed(scrapeTimeoutRunnable, 15000);

        final String finalArtistHint = artistHint;
        songScraper.scrape(songName, finalArtistHint, new SongScraper.ScrapeCallback() {
            @Override
            public void onSuccess(String artist, String picUrl, String lyrics) {
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) {
                        return;
                    }
                    handler.removeCallbacks(scrapeTimeoutRunnable);
                    Log.d(TAG, "第三方刮削成功，正在更新 UI。图片地址: " + picUrl);
                    
                    boolean hasCover = picUrl != null && !picUrl.isEmpty();
                    boolean hasLyrics = lyrics != null && !lyrics.isEmpty();
                    String resultMsg = "";
                    if (hasCover && hasLyrics) {
                        resultMsg = "刮削成功：封面+歌词";
                    } else if (hasCover) {
                        resultMsg = "刮削成功：封面";
                    } else if (hasLyrics) {
                        resultMsg = "刮削成功：歌词";
                    } else {
                        resultMsg = "刮削结果：无有效信息";
                    }
                    Toast.makeText(PlayerActivity.this, resultMsg, Toast.LENGTH_SHORT).show();
                    stopScrapeAnimation();
                    
                    // 更新歌手
                    if (artist != null && !artist.isEmpty()) {
                        tvBigArtist.setText(artist);
                    }
                    
                    // 更新封面：只有当封面不存在时才更新
                    if (!hasArtwork && picUrl != null && !picUrl.isEmpty()) {
                        Glide.with(PlayerActivity.this)
                            .load(picUrl)
                            .apply(new RequestOptions()
                                .placeholder(R.drawable.ic_cover_placeholder)
                                .error(R.drawable.ic_cover_placeholder)
                                .transform(new RoundedCorners(80)))
                            .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                                @Override
                                public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                                    Log.e(TAG, "Glide 加载封面失败: " + picUrl, e);
                                    return false;
                                }
                                @Override
                                public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                                    Log.d(TAG, "Glide 加载封面成功: " + picUrl);
                                    return false;
                                }
                            })
                            .into(ivBigCover);
                            
                        Glide.with(PlayerActivity.this)
                            .load(picUrl)
                            .apply(new RequestOptions()
                                .transform(new jp.wasabeef.glide.transformations.BlurTransformation(20, 3)))
                            .into(ivBlurBackground);
                    }
                    
                    // 更新歌词
                    if (lyrics != null && !lyrics.isEmpty()) {
                        parseLyrics(lyrics);
                        // 刮削到歌词或封面，启用保存功能
                        if (!hasArtwork && picUrl != null && !picUrl.isEmpty()) {
                            scrapedPicUrl = picUrl;
                            scrapedArtist = artist;
                            if (optionAdapter != null) optionAdapter.setOptionDisabled("保存当前歌词", false);
                        }
                    } else {
                        showNoLyrics("暂无当前歌曲歌词");
                    }
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    handler.removeCallbacks(scrapeTimeoutRunnable);
                    Log.e(TAG, "第三方刮削出错: " + msg);
                    Toast.makeText(PlayerActivity.this, "刮削失败：" + msg, Toast.LENGTH_SHORT).show();
                    stopScrapeAnimation();
                    showNoLyrics("暂无歌词");
                });
            }
        });
    }
    
    private void startScrapeAnimation() {
        if (btnScrape != null) {
            android.animation.ObjectAnimator animator = android.animation.ObjectAnimator.ofFloat(btnScrape, "rotation", 0f, 360f);
            animator.setDuration(1000);
            animator.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
            animator.setRepeatMode(android.animation.ObjectAnimator.RESTART);
            animator.start();
            btnScrape.setTag(animator);
        }
    }
    
    private void stopScrapeAnimation() {
        if (btnScrape != null) {
            ObjectAnimator animator = (ObjectAnimator) btnScrape.getTag();
            if (animator != null) {
                animator.cancel();
                animator.end();
                btnScrape.setRotation(0f);
                btnScrape.setTag(null);
            }
        }
    }


    private void showNoLyrics(String msg) {
        rvLyrics.setVisibility(View.GONE);
        tvNoLyrics.setVisibility(View.VISIBLE);
        tvNoLyrics.setText(msg);
        // Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); // Optional toast
    }

    private void parseLyrics(String lrc) {
        Log.d(TAG, "Parsing lyrics length: " + lrc.length());
        List<LyricAdapter.LyricLine> lines = new ArrayList<>();
        
        // Try to parse timestamps
        try (BufferedReader reader = new BufferedReader(new StringReader(lrc))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int closing = line.indexOf(']');
                if (closing > 0 && line.startsWith("[")) {
                     String timePart = line.substring(1, closing);
                     String textPart = line.substring(closing + 1).trim();
                     
                     try {
                         String[] parts = timePart.split(":");
                         if (parts.length >= 2) {
                             long min = Long.parseLong(parts[0].trim());
                             double sec = Double.parseDouble(parts[1].trim());
                             long timeMs = (long) ((min * 60 + sec) * 1000);
                             if (!textPart.isEmpty()) { 
                                 lines.add(new LyricAdapter.LyricLine(timeMs, textPart, line));
                             }
                         }
                     } catch (Exception e) {
                         // ignore invalid line
                     }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        Log.d(TAG, "Parsed lines: " + lines.size());
        
        if (lines.isEmpty()) {
             if (!lrc.trim().isEmpty()) {
                 showNoLyrics(lrc); 
                 tvNoLyrics.setMaxLines(20);
                 tvNoLyrics.setEllipsize(android.text.TextUtils.TruncateAt.END);
             } else {
                 showNoLyrics("没有歌词");
             }
             lyricAdapter.setLyrics(new ArrayList<>());
        } else {
             rvLyrics.setVisibility(View.VISIBLE);
             tvNoLyrics.setVisibility(View.GONE);
             lyricAdapter.setCurrentIndex(-1);
             rvLyrics.scrollToPosition(0);
             lyricAdapter.setLyrics(lines);
             // Toast.makeText(this, "Loaded " + lines.size() + " lines", Toast.LENGTH_SHORT).show();
        }
    }

    private String extractArtistFromLyrics(String lyrics) {
        if (lyrics == null || lyrics.isEmpty()) return null;
        try (BufferedReader reader = new BufferedReader(new StringReader(lyrics))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.toLowerCase().startsWith("[ar:")) {
                    int closing = line.indexOf(']');
                    if (closing > 4) {
                        return line.substring(4, closing).trim();
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    private void startProgressUpdater() {
        handler.removeCallbacks(progressUpdater);
        handler.post(progressUpdater);
    }
    
    private void stopProgressUpdater() {
        handler.removeCallbacks(progressUpdater);
    }

    private void updateProgress() {
        if (player == null) return;
        long current = player.getCurrentPosition();
        long duration = player.getDuration();
        
        tvCurrentTime.setText(formatTime(current));
        tvDuration.setText(formatTime(duration));
        if (duration > 0) {
            int prog = (int) ((current * 100) / duration);
            progressBar.setProgress(prog);
            
            // Update Tooltip
            if (layoutTooltip.getVisibility() == View.VISIBLE) {
                tvTooltip.setText(formatTime(current) + "/" + formatTime(duration));
                int availableWidth = progressBar.getWidth() - progressBar.getPaddingLeft() - progressBar.getPaddingRight();
                float thumbX = progressBar.getPaddingLeft() + (availableWidth * (float) prog / 100f);
                layoutTooltip.setTranslationX(thumbX - (layoutTooltip.getWidth() / 2f));
            }
        }

        // Sync Lyric
        updateLyric(current);
    }

    private void updateLyric(long currentPos) {
        if (lyricAdapter.getItemCount() == 0) return;
        
        // 注意：因为 LyricAdapter 内部在 setLyrics 时在开头加了一个空行，
        // 这里的原始数据索引需要对应调整。
        List<LyricAdapter.LyricLine> rawLyrics = lyricAdapter.getLyrics();
        if (rawLyrics.size() < 3) return; // 只有空行，没有实际歌词

        int activeIdx = -1;
        // 注意：i 从 1 开始，避开前置空行；i 到 size-1 结束，避开后置空行
        for (int i = 1; i < rawLyrics.size() - 1; i++) {
            if (currentPos >= rawLyrics.get(i).timeMs) {
                activeIdx = i;
            } else {
                break;
            }
        }
        
        if (activeIdx != -1 && activeIdx != lyricAdapter.getCurrentIndex()) {
            lyricAdapter.setCurrentIndex(activeIdx);
            
            androidx.recyclerview.widget.LinearSmoothScroller smoothScroller = new androidx.recyclerview.widget.LinearSmoothScroller(this) {
                @Override
                public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                    return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2);
                }
                @Override
                protected float calculateSpeedPerPixel(android.util.DisplayMetrics displayMetrics) {
                    return 200f / displayMetrics.densityDpi; // Slower smooth scroll
                }
            };
            
            smoothScroller.setTargetPosition(activeIdx);
            if (rvLyrics.getLayoutManager() != null) {
                rvLyrics.getLayoutManager().startSmoothScroll(smoothScroller);
            }
        }
    }

    private String formatTime(long ms) {
        long sec = ms / 1000;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    private void toggleOptionMenu(boolean show) {
        if (show) {
            layoutOptionMenu.setVisibility(View.VISIBLE);
            if (layoutMainContent != null) {
                layoutMainContent.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            }
            rvOptions.postDelayed(() -> {
                if (rvOptions.getChildCount() > 0) {
                    View firstItem = rvOptions.getChildAt(0);
                    if (firstItem != null) firstItem.requestFocus();
                } else {
                    rvOptions.requestFocus();
                }
            }, 100);
        } else {
            layoutOptionMenu.setVisibility(View.GONE);
            if (layoutMainContent != null) {
                layoutMainContent.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
        }
    }

    private void handleOptionClick(String option) {
        toggleOptionMenu(false);
        MediaItem current = player != null ? player.getCurrentMediaItem() : null;
        if (current == null) return;
        
        final String mediaId = current.mediaId; // 原始文件名
        String songTitle = current.mediaMetadata.title != null ? current.mediaMetadata.title.toString() : mediaId;

        switch (option) {
            case "修改歌手名":
                String currentArtist = tvBigArtist.getText().toString();
                if (currentArtist.equals("加载中...") || currentArtist.equals("未知艺术家")) currentArtist = "";
                showEditDialog("修改歌手名", currentArtist, newArtist -> updateSongTag(mediaId, "artist", newArtist));
                break;
            case "重新刮削歌词":
                currentScrapingId = ""; 
                fetchMusicInfoForScraping(songTitle, true);
                break;
            case "保存当前歌词":
                // 拼合歌词
                StringBuilder sb = new StringBuilder();
                List<LyricAdapter.LyricLine> lyricLines = lyricAdapter.getLyrics();
                for (LyricAdapter.LyricLine l : lyricLines) {
                    if (l.rawLine != null && !l.rawLine.isEmpty()) {
                        sb.append(l.rawLine).append("\n");
                    }
                }
                String fullLyrics = sb.toString().trim();
                
                java.util.Map<String, String> updates = new java.util.HashMap<>();
                if (!fullLyrics.isEmpty()) updates.put("lyrics", fullLyrics);
                // 如果刮削到了歌手，也可以作为备选存入（在 updateSongTagsBundle 内部判断是否覆盖）
                if (scrapedArtist != null && !scrapedArtist.isEmpty()) {
                    updates.put("scraped_artist", scrapedArtist);
                }
                
                if (scrapedPicUrl != null && !scrapedPicUrl.isEmpty() && scrapedPicUrl.startsWith("http")) {
                    Toast.makeText(this, "正在处理并保存封面，请稍后...", Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        try {
                            java.net.URL url = new java.net.URL(scrapedPicUrl);
                            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                            conn.setConnectTimeout(5000);
                            conn.setReadTimeout(5000);
                            java.io.InputStream is = conn.getInputStream();
                            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
                            if (bitmap != null) {
                                int maxDim = 800;
                                int width = bitmap.getWidth();
                                int height = bitmap.getHeight();
                                if (width > maxDim || height > maxDim) {
                                    float ratio = Math.min((float) maxDim / width, (float) maxDim / height);
                                    width = Math.round(width * ratio);
                                    height = Math.round(height * ratio);
                                    bitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, width, height, true);
                                }
                                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, baos);
                                byte[] imageBytes = baos.toByteArray();
                                String base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP);
                                // 参考提供的代码，只传裸的 base64 字符串，不带 "data:image/jpeg;base64," 前缀
                                updates.put("picture", base64);
                            } else {
                                updates.put("picture", scrapedPicUrl);
                            }
                        } catch (Exception e) {
                            android.util.Log.e(TAG, "Failed to download image: " + e.getMessage());
                            updates.put("picture", scrapedPicUrl);
                        }
                        
                        runOnUiThread(() -> {
                            if (!updates.isEmpty()) {
                                updateSongTagsBundle(mediaId, updates);
                            } else {
                                Toast.makeText(PlayerActivity.this, "无可保存的内容", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }).start();
                } else {
                    if (scrapedPicUrl != null && !scrapedPicUrl.isEmpty()) {
                        updates.put("picture", scrapedPicUrl);
                    }
                    if (!updates.isEmpty()) {
                        updateSongTagsBundle(mediaId, updates);
                    } else {
                        Toast.makeText(this, "无可保存的内容", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
        }
    }

    private interface OnSubmitListener {
        void onSubmit(String value);
    }

    private void showEditDialog(String title, String defaultValue, OnSubmitListener listener) {
        android.widget.EditText editText = new android.widget.EditText(this);
        editText.setText(defaultValue);
        if (defaultValue != null) editText.setSelection(defaultValue.length());

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setView(editText)
                .setPositiveButton("保存", (dialog, which) -> {
                    String value = editText.getText().toString().trim();
                    if (!value.isEmpty()) {
                        listener.onSubmit(value);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateSongTag(String fileName, String key, String value) {
        Toast.makeText(PlayerActivity.this, "lxserver 不支持此功能", Toast.LENGTH_SHORT).show();
    }

    private void updateSongTagsBundle(String fileName, java.util.Map<String, String> updates) {
        Toast.makeText(PlayerActivity.this, "歌词已加载，请在播放器中查看", Toast.LENGTH_SHORT).show();
        if (optionAdapter != null) {
            optionAdapter.setOptionDisabled("保存当前歌词", true);
        }
    }

    /**
     * 获取 MiMusic 歌词
     * 新架构(2026): lyricUrl 永远是 /api/v1/songs/{id}/lyric 端点或空字符串
     */
    private void fetchMiMusicLyric(String lyricUrl) {
        Log.d(TAG, "Fetching MiMusic lyric from URL: " + lyricUrl);
        showNoLyrics("加载歌词...");

        // 使用 MiMusicPlayerHelper 构建完整歌词 URL
        String fullLyricUrl = MiMusicPlayerHelper.buildLyricUrl(this, lyricUrl, null);
        if (fullLyricUrl == null || fullLyricUrl.isEmpty()) {
            Log.w(TAG, "Failed to build lyric URL, falling back to scrape");
            startExternalScrapeFlow(tvBigTitle.getText().toString());
            return;
        }

        // 发起 HTTP 请求获取歌词
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(fullLyricUrl)
                .get()
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "Failed to fetch lyric: " + e.getMessage());
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    startExternalScrapeFlow(tvBigTitle.getText().toString());
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Lyric request failed, code: " + response.code());
                    runOnUiThread(() -> {
                        if (isDestroyed() || isFinishing()) return;
                        startExternalScrapeFlow(tvBigTitle.getText().toString());
                    });
                    return;
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "Got lyric response, length: " + responseBody.length());

                // 解析 JSON: {"code":0,"data":{"lyric":"..."}}
                String lyricContent = null;
                try {
                    com.google.gson.JsonObject json = new com.google.gson.JsonParser().parse(responseBody).getAsJsonObject();
                    int code = json.get("code").getAsInt();
                    if (code == 0 && json.has("data")) {
                        com.google.gson.JsonObject data = json.getAsJsonObject("data");
                        if (data.has("lyric") && !data.get("lyric").isJsonNull()) {
                            lyricContent = data.get("lyric").getAsString();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse lyric JSON: " + e.getMessage());
                }

                final String finalLyricContent = lyricContent;
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    if (finalLyricContent != null && !finalLyricContent.isEmpty()) {
                        parseLyrics(finalLyricContent);
                    } else {
                        startExternalScrapeFlow(tvBigTitle.getText().toString());
                    }
                });
            }
        });
    }

    private String getStringOrEmpty(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
        stopProgressUpdater();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (btnPlayPause != null) {
            btnPlayPause.post(() -> btnPlayPause.requestFocus());
        }
    }
}
