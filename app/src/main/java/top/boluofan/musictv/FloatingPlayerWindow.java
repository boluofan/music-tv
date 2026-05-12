package top.boluofan.musictv;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

public class FloatingPlayerWindow {
    private static final String TAG = "FloatingPlayerWindow";

    private static final int FADE_DURATION = 300;
    private static final int AUTO_FADE_DELAY = 3000;
    private static final int PLAYER_MARGIN_TOP = 0;

    private final Activity activity;
    private final Context context;
    private final View floatingView;
    private final CardView cvCover;
    private final ImageView ivCover;
    private final TextView tvTitle;
    private final View container;

    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private Player.Listener playerListener;
    private ObjectAnimator rotateAnim;
    private ObjectAnimator fadeAnim;
    private ValueAnimator scaleAnim;
    private Handler fadeHandler;
    private Runnable fadeOutRunnable;
    private boolean isPlaying = false;
    private boolean isConnected = false;
    private boolean isFadedOut = false;
    private boolean isFocused = false;
    private int collapsedWidth = 96;
    private int expandedWidth = 300;

    private static final String PREF_NAME = "floating_player_pos";
    private static final String KEY_POS_X = "pos_x";
    private static final String KEY_POS_Y = "pos_y";
    private static final int DEFAULT_POS_X = -1;
    private static final int DEFAULT_POS_Y = 0;

    private boolean isDragging = false;
    private int mDragStartX;
    private int mDragStartY;
    private int mPosX = DEFAULT_POS_X;
    private int mPosY = DEFAULT_POS_Y;

    public FloatingPlayerWindow(Activity activity) {
        this.activity = activity;
        this.context = activity.getApplicationContext();

        LayoutInflater inflater = LayoutInflater.from(activity);
        floatingView = inflater.inflate(R.layout.layout_floating_player, null);

        container = floatingView.findViewById(R.id.floatingPlayerContainer);
        cvCover = floatingView.findViewById(R.id.cvFloatingCover);
        ivCover = floatingView.findViewById(R.id.ivFloatingCover);
        tvTitle = floatingView.findViewById(R.id.tvFloatingTitle);

        setupContainer();
        setupListeners();
    }

    private void setupContainer() {
        ViewGroup rootView = (ViewGroup) activity.getWindow().getDecorView();

        View existingContainer = rootView.findViewById(R.id.floatingPlayerContainer);
        if (existingContainer != null && existingContainer.getParent() != null) {
            ((ViewGroup) existingContainer.getParent()).removeView(existingContainer);
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        mPosX = prefs.getInt(KEY_POS_X, DEFAULT_POS_X);
        mPosY = prefs.getInt(KEY_POS_Y, DEFAULT_POS_Y);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                collapsedWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int defaultX = (screenWidth - collapsedWidth) / 2;

        if (mPosX == DEFAULT_POS_X) {
            mPosX = defaultX;
        }
        params.setMargins(mPosX, mPosY, 0, 0);

        container.setLayoutParams(params);
        
        if (container.getParent() == null) {
            rootView.addView(container);
        }
        
        container.setFocusable(true);
        
        cvCover.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        rotateAnim = ObjectAnimator.ofFloat(cvCover, "rotation", 0f, 360f);
        rotateAnim.setDuration(10000);
        rotateAnim.setInterpolator(new LinearInterpolator());
        rotateAnim.setRepeatCount(ObjectAnimator.INFINITE);
        rotateAnim.setRepeatMode(ObjectAnimator.RESTART);

        fadeHandler = new Handler(Looper.getMainLooper());
        fadeOutRunnable = this::fadeOut;
    }

    private void setupListeners() {
        container.setOnClickListener(v -> {
            if (!isDragging) {
                openPlayer();
            }
        });

        container.setOnFocusChangeListener((v, hasFocus) -> {
            isFocused = hasFocus;
            if (hasFocus) {
                container.setBackgroundResource(R.drawable.bg_floating_player_focused);
                tvTitle.setSelected(true);
                expandPlayer();
            } else {
                container.setBackgroundResource(R.drawable.bg_floating_player);
                tvTitle.setSelected(false);
                collapsePlayer();
            }
        });

        container.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                    openPlayer();
                    return true;
                }
            }
            return false;
        });

        container.setOnTouchListener((v, event) -> {
            if (!isFocused) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mDragStartX = (int) event.getRawX();
                        mDragStartY = (int) event.getRawY();
                        isDragging = false;
                        v.setPressed(true);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - mDragStartX);
                        int deltaY = (int) (event.getRawY() - mDragStartY);

                        if (!isDragging && (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10)) {
                            isDragging = true;
                            v.setPressed(false);
                        }

                        if (isDragging) {
                            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) v.getLayoutParams();
                            int newX = params.leftMargin + deltaX;
                            int newY = params.topMargin + deltaY;

                            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                            int maxX = screenWidth - v.getWidth();
                            int maxY = context.getResources().getDisplayMetrics().heightPixels - v.getHeight();

                            newX = Math.max(0, Math.min(newX, maxX));
                            newY = Math.max(0, Math.min(newY, maxY));

                            params.setMargins(newX, newY, 0, 0);
                            v.setLayoutParams(params);

                            mDragStartX = (int) event.getRawX();
                            mDragStartY = (int) event.getRawY();
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        if (isDragging) {
                            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) v.getLayoutParams();
                            mPosX = params.leftMargin;
                            mPosY = params.topMargin;
                            savePosition();
                        }
                        isDragging = false;
                        return true;
                }
            }
            return false;
        });
    }

    private void openPlayer() {
        context.startActivity(new Intent(context, PlayerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void expandPlayer() {
        if (scaleAnim != null && scaleAnim.isRunning()) {
            scaleAnim.cancel();
        }

        fadeHandler.removeCallbacks(fadeOutRunnable);

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
        if (params.width != expandedWidth) {
            final FrameLayout.LayoutParams finalParams = params;
            scaleAnim = ObjectAnimator.ofInt(params.width, expandedWidth);
            scaleAnim.setDuration(FADE_DURATION);
            scaleAnim.setEvaluator(new android.animation.IntEvaluator());
            scaleAnim.addUpdateListener(animation -> {
                int width = (int) animation.getAnimatedValue();
                finalParams.width = width;
                container.setLayoutParams(finalParams);
            });
            scaleAnim.start();
        }
    }

    private void collapsePlayer() {
        if (scaleAnim != null && scaleAnim.isRunning()) {
            scaleAnim.cancel();
        }

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
        if (params.width != collapsedWidth) {
            final FrameLayout.LayoutParams finalParams = params;
            scaleAnim = ObjectAnimator.ofInt(params.width, collapsedWidth);
            scaleAnim.setDuration(FADE_DURATION);
            scaleAnim.setEvaluator(new android.animation.IntEvaluator());
            scaleAnim.addUpdateListener(animation -> {
                int width = (int) animation.getAnimatedValue();
                finalParams.width = width;
                container.setLayoutParams(finalParams);
            });
            scaleAnim.start();
        }
    }

    private static class WidthEvaluator implements java.util.concurrent.Callable<Integer> {
        private int width;

        public void setWidth(int width) {
            this.width = width;
        }

        public int getWidth() {
            return width;
        }

        @Override
        public Integer call() {
            return width;
        }
    }

    private void fadeIn() {
        if (fadeAnim != null && fadeAnim.isRunning()) {
            fadeAnim.cancel();
        }
        
        fadeHandler.removeCallbacks(fadeOutRunnable);
        
        if (!isFadedOut && container.getAlpha() >= 1.0f) {
            return;
        }
        
        isFadedOut = false;
        
        container.setVisibility(View.VISIBLE);
        fadeAnim = ObjectAnimator.ofFloat(container, "alpha", container.getAlpha(), 1.0f);
        fadeAnim.setDuration(FADE_DURATION);
        fadeAnim.start();
    }

    private void fadeOut() {
        if (fadeAnim != null && fadeAnim.isRunning()) {
            fadeAnim.cancel();
        }
        
        if (isFadedOut || container.getAlpha() <= 0.0f) {
            return;
        }
        
        isFadedOut = true;
        
        fadeAnim = ObjectAnimator.ofFloat(container, "alpha", container.getAlpha(), 0.0f);
        fadeAnim.setDuration(FADE_DURATION);
        fadeAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (isFadedOut) {
                    container.setVisibility(View.GONE);
                }
            }
        });
        fadeAnim.start();
    }

    public void connectToService() {
        if (isConnected) return;
        
        SessionToken sessionToken = new SessionToken(context, 
                new ComponentName(context, MusicService.class));
        
        controllerFuture = new MediaController.Builder(context, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                player = controllerFuture.get();
                isConnected = true;
                playerListener = new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean playing) {
                        isPlaying = playing;
                        updatePlayPauseButton();
                    }

                    @Override
                    public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                        updateUI();
                    }

                    @Override
                    public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_READY) {
                            updateUI();
                        }
                    }
                };
                player.addListener(playerListener);
                updateUI();
            } catch (Exception e) {
                Log.e(TAG, "Failed to get MediaController: " + e.getMessage());
            }
        }, MoreExecutors.directExecutor());
    }

    private void updatePlayPauseButton() {
        activity.runOnUiThread(() -> {
            if (rotateAnim != null) {
                if (isPlaying) {
                    if (rotateAnim.isPaused()) rotateAnim.resume();
                    else if (!rotateAnim.isRunning()) rotateAnim.start();
                } else {
                    rotateAnim.pause();
                }
            }
        });
    }

    public void showIfPlaying() {
        if (player == null || player.getMediaItemCount() == 0) {
            hide();
            return;
        }

        MediaItem currentItem = player.getCurrentMediaItem();
        if (currentItem == null) {
            hide();
            return;
        }

        updateUI();
    }

    public void updateUI() {
        if (player == null || player.getMediaItemCount() == 0) {
            hide();
            return;
        }

        MediaItem currentItem = player.getCurrentMediaItem();
        if (currentItem == null) {
            hide();
            return;
        }

        activity.runOnUiThread(() -> {
            isPlaying = player.isPlaying();
            
            if (rotateAnim != null) {
                if (isPlaying) {
                    if (rotateAnim.isPaused()) rotateAnim.resume();
                    else if (!rotateAnim.isRunning()) rotateAnim.start();
                } else {
                    rotateAnim.pause();
                }
            }

            MediaMetadata metadata = currentItem.mediaMetadata;
            if (metadata != null) {
                CharSequence title = metadata.title;
                tvTitle.setText(title != null ? title.toString() : "");

                Uri artworkUri = metadata.artworkUri;
                if (artworkUri != null) {
                    Glide.with(context)
                            .load(artworkUri)
                            .placeholder(R.drawable.ic_album_placeholder)
                            .centerCrop()
                            .into(ivCover);
                } else {
                    ivCover.setImageResource(R.drawable.ic_album_placeholder);
                }
            }

            container.setVisibility(View.VISIBLE);
            container.setAlpha(1.0f);
            isFadedOut = false;
        });
    }

    public void hide() {
        if (container != null) {
            activity.runOnUiThread(() -> {
                fadeHandler.removeCallbacks(fadeOutRunnable);
                if (fadeAnim != null) {
                    fadeAnim.cancel();
                }
                container.setVisibility(View.GONE);
                container.setAlpha(1.0f);
                isFadedOut = false;
            });
        }
    }

    public void release() {
        isFocused = false;
        if (scaleAnim != null) {
            scaleAnim.cancel();
            scaleAnim = null;
        }
        if (rotateAnim != null) {
            rotateAnim.cancel();
            rotateAnim = null;
        }
        if (fadeAnim != null) {
            fadeAnim.cancel();
            fadeAnim = null;
        }
        if (fadeHandler != null) {
            fadeHandler.removeCallbacks(fadeOutRunnable);
            fadeHandler = null;
        }
        if (player != null && playerListener != null) {
            player.removeListener(playerListener);
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
        
        if (container != null) {
            container.setOnFocusChangeListener(null);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
            if (params != null) {
                params.width = collapsedWidth;
                container.setLayoutParams(params);
            }
            container.setBackgroundResource(R.drawable.bg_floating_player);
            tvTitle.setSelected(false);
            
            ViewGroup parent = (ViewGroup) container.getParent();
            if (parent != null) {
                parent.removeView(container);
            }
        }
        
        isConnected = false;
    }
    
    public View getContainer() {
        return container;
    }
    
    public boolean requestFocus() {
        if (container != null && container.getVisibility() == View.VISIBLE) {
            return container.requestFocus();
        }
        return false;
    }
    
    public boolean handleLeftKey(View currentFocus) {
        if (container == null || container.getVisibility() != View.VISIBLE) {
            return false;
        }

        if (currentFocus == null) {
            return requestFocus();
        }

        int[] location = new int[2];
        currentFocus.getLocationOnScreen(location);

        if (location[0] <= 60) {
            return requestFocus();
        }

        return false;
    }

    private void savePosition() {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_POS_X, mPosX)
                .putInt(KEY_POS_Y, mPosY)
                .apply();
    }
}
