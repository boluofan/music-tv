package top.boluofan.musictv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import android.content.ComponentName;
import top.boluofan.musictv.ConfigActivity;
import top.boluofan.musictv.MusicService;
import top.boluofan.musictv.R;
import top.boluofan.musictv.FloatingPlayerWindow;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.util.DialogHelper;

public class SettingsActivity extends AppCompatActivity {
    private FloatingPlayerWindow floatingPlayerWindow;
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        floatingPlayerWindow = new FloatingPlayerWindow(this);
        floatingPlayerWindow.connectToService();
        
        initViews();
    }

    private static final String EXTRA_SERVER_URL = "server_url";
    private static final String EXTRA_USERNAME = "username";
    
    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
        
        String serverUrl = LxRetrofitClient.getServerUrl(this);
        String username = LxRetrofitClient.getUsername(this);

        TextView tvVersion = findViewById(R.id.tvVersion);
        tvVersion.setText(getVersionName());

        LinearLayout layoutAbout = findViewById(R.id.layoutAbout);
        layoutAbout.setOnClickListener(v -> showAboutDialog());
        
        LinearLayout layoutServerConfig = findViewById(R.id.layoutServerConfig);
        layoutServerConfig.setOnClickListener(v -> {
            Intent intent = new Intent(this, ConfigActivity.class);
            intent.putExtra(EXTRA_SERVER_URL, serverUrl);
            intent.putExtra(EXTRA_USERNAME, username);
            startActivity(intent);
        });

        LinearLayout layoutUserInfo = findViewById(R.id.layoutUserInfo);
        layoutUserInfo.setOnClickListener(v -> {
            if (LxRetrofitClient.isLoggedIn(this)) {
                Intent intent = new Intent(this, LibraryActivity.class);
                startActivity(intent);
            } else {
                Intent intent = new Intent(this, ConfigActivity.class);
                intent.putExtra(EXTRA_SERVER_URL, serverUrl);
                startActivity(intent);
            }
        });
        
        LinearLayout layoutLogout = findViewById(R.id.layoutLogout);
        layoutLogout.setOnClickListener(v -> clearConfigAndLogout());
        
        TextView tvServerUrl = findViewById(R.id.tvServerUrl);
        tvServerUrl.setText(serverUrl);
        
        TextView tvUsername = findViewById(R.id.tvUsername);
        tvUsername.setText(username.isEmpty() ? "未登录" : username);
        
        ImageButton btnBackgroundPlay = findViewById(R.id.btnBackgroundPlay);
        LinearLayout layoutBackgroundPlay = findViewById(R.id.layoutBackgroundPlay);
        updateBackgroundPlayButton(btnBackgroundPlay);
        
        layoutBackgroundPlay.setOnClickListener(v -> {
            boolean newState = !LxRetrofitClient.getBackgroundPlay(this);
            LxRetrofitClient.setBackgroundPlay(this, newState);
            Toast.makeText(this, "后台播放: " + (newState ? "开启" : "关闭"), Toast.LENGTH_SHORT).show();
            updateBackgroundPlayButton(btnBackgroundPlay);
        });
    }
    
    private void updateBackgroundPlayButton(ImageButton btn) {
        boolean isEnabled = LxRetrofitClient.getBackgroundPlay(this);
        btn.setBackgroundResource(isEnabled ? R.drawable.toggle_on_new : R.drawable.toggle_off_new);
    }

    private String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "未知";
        }
    }

    private void showAboutDialog() {
        DialogHelper.showAboutDialog(this);
    }

    private void clearConfigAndLogout() {
        boolean backgroundPlay = LxRetrofitClient.getBackgroundPlay(this);
        
        if (!backgroundPlay && player != null) {
            player.stop();
            player.clearMediaItems();
        }
        if (!backgroundPlay && floatingPlayerWindow != null) {
            floatingPlayerWindow.release();
            floatingPlayerWindow = null;
        }
        LxRetrofitClient.clearConfig(this);
        Toast.makeText(this, "配置已清除", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, ConfigActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, MusicService.class));
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                player = controllerFuture.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (floatingPlayerWindow != null) {
            floatingPlayerWindow.release();
            floatingPlayerWindow = null;
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            View currentFocus = getCurrentFocus();
            if (currentFocus != null && floatingPlayerWindow != null) {
                if (floatingPlayerWindow.handleLeftKey(currentFocus)) {
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
