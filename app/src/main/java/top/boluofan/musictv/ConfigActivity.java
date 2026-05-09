package top.boluofan.musictv;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.LoginResponse;
import top.boluofan.musictv.api.model.MiAuthTokenResponse;
import top.boluofan.musictv.ui.LibraryActivity;
import top.boluofan.musictv.util.DialogHelper;

public class ConfigActivity extends AppCompatActivity {
    private static final String TAG = "ConfigActivity";
    private LoginWebServer webServer;
    private static final int SERVER_PORT = 8088;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isQrMode = false;
    
    private EditText etUrl;
    private EditText etUsername;
    private EditText etPassword;
    private EditText etToken;
    private Button btnConnect;
    private View layoutManual;
    private View layoutQr;
    private Button btnToggleMode;
    private RadioGroup rgApiType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        etUrl = findViewById(R.id.etUrl);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etToken = findViewById(R.id.etToken);
        btnConnect = findViewById(R.id.btnConnect);
        ImageView ivQrCode = findViewById(R.id.ivQrCode);
        TextView tvIpAddress = findViewById(R.id.tvIpAddress);
        layoutManual = findViewById(R.id.layoutManual);
        layoutQr = findViewById(R.id.layoutQr);
        btnToggleMode = findViewById(R.id.btnToggleMode);
        rgApiType = findViewById(R.id.rgApiType);

        String savedApiType = LxRetrofitClient.getApiType(this);
        if (LxRetrofitClient.API_TYPE_MiMusic.equals(savedApiType)) {
            etUrl.setText("http://localhost:58091/api/v1");
        } else {
            etUrl.setText("http://localhost:9527");
        }

        View.OnFocusChangeListener focusLogger = (v, hasFocus) -> {
            Log.d(TAG, "Focus changed: " + v.getClass().getSimpleName() + " id=" + v.getId() + " hasFocus=" + hasFocus);
        };
        findViewById(R.id.rbLxserver).setOnFocusChangeListener(focusLogger);
        findViewById(R.id.rbMiMusic).setOnFocusChangeListener(focusLogger);
        etUrl.setOnFocusChangeListener(focusLogger);
        etUsername.setOnFocusChangeListener(focusLogger);
        etPassword.setOnFocusChangeListener(focusLogger);
        etToken.setOnFocusChangeListener(focusLogger);
        btnConnect.setOnFocusChangeListener(focusLogger);
        btnToggleMode.setOnFocusChangeListener(focusLogger);
        
        String serverUrlFromSettings = getIntent().getStringExtra("server_url");
        String usernameFromSettings = getIntent().getStringExtra("username");
        
        if (serverUrlFromSettings != null && !serverUrlFromSettings.isEmpty()) {
            etUrl.setText(serverUrlFromSettings);
        }
        
        if (usernameFromSettings != null && !usernameFromSettings.isEmpty()) {
            etUsername.setText(usernameFromSettings);
        }
        
        if (LxRetrofitClient.isLoggedIn(this)) {
            String savedPassword = LxRetrofitClient.getPassword(this);
            if (!savedPassword.isEmpty()) {
                etPassword.setText(savedPassword);
            }
        }

        String savedToken = LxRetrofitClient.getToken(this);
        if (!savedToken.isEmpty()) {
            etToken.setText(savedToken);
        }

        if (LxRetrofitClient.API_TYPE_MiMusic.equals(savedApiType)) {
            rgApiType.check(R.id.rbMiMusic);
        } else {
            rgApiType.check(R.id.rbLxserver);
        }

        btnToggleMode.setOnClickListener(v -> {
            isQrMode = !isQrMode;
            if (isQrMode) {
                btnToggleMode.setText("返回手动输入");
                layoutManual.setVisibility(View.GONE);
                layoutQr.setVisibility(View.VISIBLE);

                etUrl.setFocusable(false);
                etUsername.setFocusable(false);
                etPassword.setFocusable(false);
                etToken.setFocusable(false);
                btnConnect.setFocusable(false);

                showQrCodeDialog();
            } else {
                btnToggleMode.setText("扫码配置");
                layoutManual.setVisibility(View.VISIBLE);
                layoutQr.setVisibility(View.GONE);

                etUrl.setFocusable(true);
                etUrl.setFocusableInTouchMode(true);
                etUsername.setFocusable(true);
                etUsername.setFocusableInTouchMode(true);
                etPassword.setFocusable(true);
                etPassword.setFocusableInTouchMode(true);
                etToken.setFocusable(true);
                etToken.setFocusableInTouchMode(true);
                btnConnect.setFocusable(true);

                if (webServer != null) {
                    webServer.stop();
                    webServer = null;
                }
            }
        });

        View.OnClickListener clickToShowKeyboard = v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(v, InputMethodManager.SHOW_FORCED);
            }
        };

        etUrl.setOnClickListener(clickToShowKeyboard);
        etUsername.setOnClickListener(clickToShowKeyboard);
        etPassword.setOnClickListener(clickToShowKeyboard);
        etToken.setOnClickListener(clickToShowKeyboard);

        btnConnect.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

            String urlRaw = etUrl.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String token = etToken.getText().toString().trim();

            if (urlRaw.isEmpty()) {
                Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
                return;
            }

            // 校验 URL 格式是否合法（使用 OkHttp HttpUrl 严格校验）
            String testUrl = urlRaw;
            if (!testUrl.startsWith("http")) {
                testUrl = "https://" + testUrl;
            }
            try {
                okhttp3.HttpUrl httpUrl = okhttp3.HttpUrl.parse(testUrl);
                if (httpUrl == null || httpUrl.host() == null || httpUrl.host().isEmpty()) {
                    Toast.makeText(this, "服务器地址格式错误", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (Exception e) {
                Toast.makeText(this, "服务器地址格式错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }

            // 校验 baseUrl 必须以 /api/v* 结尾（仅 MiMusic 模式）
            if (rgApiType.getCheckedRadioButtonId() == R.id.rbMiMusic
                    && !urlRaw.matches(".*/api/v\\d+/?$")) {
                Toast.makeText(this, "MiMusic 模式服务器地址必须以 /api/v* 结尾", Toast.LENGTH_SHORT).show();
                return;
            }

            // MiMusic 模式必须输入用户名密码
            if (rgApiType.getCheckedRadioButtonId() == R.id.rbMiMusic
                    && (username.isEmpty() || password.isEmpty())) {
                Toast.makeText(this, "MiMusic 模式必须输入用户名和密码", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!urlRaw.startsWith("http")) {
                urlRaw = "https://" + urlRaw;
            }
            String finalUrl = urlRaw.endsWith("/") ? urlRaw : urlRaw + "/";

            String apiType = rgApiType.getCheckedRadioButtonId() == R.id.rbMiMusic
                    ? LxRetrofitClient.API_TYPE_MiMusic
                    : LxRetrofitClient.API_TYPE_LXserver;

            btnConnect.setEnabled(false);
            btnConnect.setText("连接中...");

            LxRetrofitClient.saveConfig(this, finalUrl, username, password, token, apiType);
            LxRetrofitClient.resetClient();

            if (username.isEmpty() || password.isEmpty()) {
                btnConnect.setEnabled(true);
                btnConnect.setText("连　接");
                Toast.makeText(this, "配置已保存，将使用公共功能", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(ConfigActivity.this, top.boluofan.musictv.ui.MainActivity.class));
                finish();
                return;
            }

            LxApiService apiService = LxRetrofitClient.getApiService(this);
            java.util.HashMap<String, String> body = new java.util.HashMap<>();
            body.put("username", username);
            body.put("password", password);

            if (LxRetrofitClient.API_TYPE_MiMusic.equals(apiType)) {
                LxApiService authService = LxRetrofitClient.getMiMusicAuthService(this);
                if (authService != null) {
                    authService.miMusicLogin(body).enqueue(new retrofit2.Callback<MiAuthTokenResponse>() {
                        @Override
                        public void onResponse(retrofit2.Call<MiAuthTokenResponse> call, retrofit2.Response<MiAuthTokenResponse> response) {
                            btnConnect.setEnabled(true);
                            btnConnect.setText("连　接");

                            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                                Toast.makeText(ConfigActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                                LxRetrofitClient.saveMiMusicToken(ConfigActivity.this,
                                        response.body().getAccessToken(), response.body().getRefreshToken());
                            } else {
                                Toast.makeText(ConfigActivity.this, "用户名或密码错误，将以游客身份使用", Toast.LENGTH_LONG).show();
                            }
                            startActivity(new Intent(ConfigActivity.this, top.boluofan.musictv.ui.MainActivity.class));
                            finish();
                        }

                        @Override
                        public void onFailure(retrofit2.Call<MiAuthTokenResponse> call, Throwable t) {
                            btnConnect.setEnabled(true);
                            btnConnect.setText("连　接");
                            Toast.makeText(ConfigActivity.this, "连接超时，将以游客身份使用", Toast.LENGTH_LONG).show();
                            startActivity(new Intent(ConfigActivity.this, top.boluofan.musictv.ui.MainActivity.class));
                            finish();
                        }
                    });
                    return;
                }
            }

            apiService.verifyUser(body).enqueue(new retrofit2.Callback<LoginResponse>() {
                @Override
                public void onResponse(retrofit2.Call<LoginResponse> call, retrofit2.Response<LoginResponse> response) {
                    btnConnect.setEnabled(true);
                    btnConnect.setText("连　接");

                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        Toast.makeText(ConfigActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ConfigActivity.this, "用户名或密码错误，将以游客身份使用", Toast.LENGTH_LONG).show();
                    }
                    startActivity(new Intent(ConfigActivity.this, top.boluofan.musictv.ui.MainActivity.class));
                    finish();
                }

                @Override
                public void onFailure(retrofit2.Call<LoginResponse> call, Throwable t) {
                    btnConnect.setEnabled(true);
                    btnConnect.setText("连　接");
                    Toast.makeText(ConfigActivity.this, "连接超时，将以游客身份使用", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(ConfigActivity.this, top.boluofan.musictv.ui.MainActivity.class));
                    finish();
                }
            });
        });
    }

    private void showQrCodeDialog() {
        String ipAddress = getIPAddress();
        if (ipAddress == null) {
            Toast.makeText(this, "无法获取局域网地址，请检查网络", Toast.LENGTH_SHORT).show();
            return;
        }

        String loginUrl = "http://" + ipAddress + ":" + SERVER_PORT;
        
        AlertDialog qrDialog = DialogHelper.showQrCodeDialog(
            this,
            "扫码配置服务器",
            "在手机浏览器访问地址后填写配置信息",
            loginUrl,
            "访问管理: " + loginUrl
        );
        
        qrDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "关闭", (d, which) -> {
            if (webServer != null) {
                webServer.stop();
                webServer = null;
            }
            isQrMode = false;
            btnToggleMode.setText("扫码配置");
            layoutManual.setVisibility(View.VISIBLE);
            layoutQr.setVisibility(View.GONE);
            
            etUrl.setFocusable(true);
            etUsername.setFocusable(true);
            etPassword.setFocusable(true);
            btnConnect.setFocusable(true);
        });
        
        String savedUrl = LxRetrofitClient.getServerUrl(this);
        String savedUsername = LxRetrofitClient.getUsername(this);
        String savedPassword = LxRetrofitClient.getPassword(this);
        String savedToken = LxRetrofitClient.getToken(this);

        webServer = new LoginWebServer(this, SERVER_PORT, (url, username, password, token) -> {
            mainHandler.post(() -> {
                qrDialog.dismiss();
                // 合并：如果推送的值为空，保留原有值
                String mergedUrl = (url != null && !url.isEmpty()) ? url : savedUrl;
                String mergedUsername = (username != null && !username.isEmpty()) ? username : savedUsername;
                String mergedPassword = (password != null && !password.isEmpty()) ? password : savedPassword;
                String mergedToken = (token != null && !token.isEmpty()) ? token : savedToken;

                etUrl.setText(mergedUrl);
                etUsername.setText(mergedUsername);
                etPassword.setText(mergedPassword);
                etToken.setText(mergedToken);
                Toast.makeText(this, "收到推送信息，正在登录...", Toast.LENGTH_SHORT).show();
                btnConnect.performClick();
            });
        }, savedUrl, savedUsername, savedPassword, savedToken);

        try {
            webServer.start();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "服务启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        
        qrDialog.setOnDismissListener(d -> {
            if (webServer != null) {
                webServer.stop();
                webServer = null;
            }
        });
        
        qrDialog.show();
    }

    private void startLoginWebServer(TextView tvIp, ImageView ivQr, EditText etUrl, EditText etUsername, EditText etPassword, Button btnConnect) {
        String ipAddress = getIPAddress();
        if (ipAddress == null) {
            tvIp.setText("无法获取局域网地址，请检查网络");
            return;
        }

        String loginUrl = "http://" + ipAddress + ":" + SERVER_PORT;
        tvIp.setText("访问管理: " + loginUrl);

        generateQrCode(loginUrl, ivQr);

        webServer = new LoginWebServer(this, SERVER_PORT, (url, username, password, token) -> {
            mainHandler.post(() -> {
                etUrl.setText(url);
                etUsername.setText(username);
                etPassword.setText(password);
                etToken.setText(token);
                Toast.makeText(this, "收到推送信息，正在登录...", Toast.LENGTH_SHORT).show();
                btnConnect.performClick();
            });
        });

        try {
            webServer.start();
        } catch (IOException e) {
            e.printStackTrace();
            tvIp.setText("服务启动失败: " + e.getMessage());
        }
    }

    private void generateQrCode(String text, ImageView imageView) {
        QRCodeWriter writer = new QRCodeWriter();
        try {
            BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            imageView.setImageBitmap(bmp);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    private String getIPAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        if (isIPv4) return sAddr;
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webServer != null) {
            webServer.stop();
        }
    }
}
