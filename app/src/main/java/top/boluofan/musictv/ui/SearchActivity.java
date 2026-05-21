package top.boluofan.musictv.ui;

import android.content.Intent;
import android.content.DialogInterface;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.MusicService;
import okhttp3.ResponseBody;
import top.boluofan.musictv.R;
import top.boluofan.musictv.SearchWebServer;
import top.boluofan.musictv.FloatingPlayerWindow;
import top.boluofan.musictv.PlayerActivity;
import android.view.KeyEvent;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.ui.adapter.LxMusicAdapter;
import top.boluofan.musictv.util.DialogHelper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class SearchActivity extends AppCompatActivity {
    private static final String TAG = "SearchActivity";
    
    private EditText etSearch;
    private Button btnSearch;
    private ImageButton btnClear;
    private RecyclerView rvSourceList;
    private RecyclerView rvHotSearch;
    private RecyclerView rvSearchResults;
    private LxMusicAdapter songAdapter;
    private ProgressBar loadingProgress;
    private TextView tvNoResults;
    private TextView tvResultCount;
    private TextView tvHotSearchTitle;
    
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private FloatingPlayerWindow floatingPlayerWindow;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable positionUpdater;
    
    private String currentSource = "all";
    private int currentSourceIndex = 0;
    private int currentPage = 1;
    private boolean hasMore = true;
    private String lastKeyword = "";
    private List<MusicInfo> allResults = new ArrayList<>();
    private List<String> hotSearchWords = new ArrayList<>();
    
    private final String[] SOURCES = {"all", "kw", "kg", "tx", "wy", "mg"};
    private final String[] SOURCE_NAMES = {"聚合搜索", "小窝", "小枸", "小秋", "小芸", "小蜜"};
    
    private final String[] ALL_SOURCES = {"kw", "kg", "tx", "wy", "mg"};
    private final String[] ALL_SOURCE_NAMES = {"小窝", "小枸", "小秋", "小芸", "小蜜"};
    
    private static final int SEARCH_SERVER_PORT = 8089;
    private SearchWebServer searchWebServer;
    private ImageButton btnScan;
    private RecyclerView.Adapter<?> hotSearchAdapter;

    private CustomKeyboardPopup customKeyboardPopup;
    private boolean isKeyboardVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        initViews();
        setupRecyclerViews();
        setupListeners();
        setupCustomKeyboard();
        updateResults();
    }

    private void initViews() {
        etSearch = findViewById(R.id.etSearch);
        btnSearch = findViewById(R.id.btnSearch);
        btnClear = findViewById(R.id.btnClear);
        btnScan = findViewById(R.id.btnScan);
        rvSourceList = findViewById(R.id.rvSourceList);
        rvHotSearch = findViewById(R.id.rvHotSearch);
        rvSearchResults = findViewById(R.id.rvSearchResults);
        loadingProgress = findViewById(R.id.loadingProgress);
        tvNoResults = findViewById(R.id.tvNoResults);
        tvResultCount = findViewById(R.id.tvResultCount);
        tvHotSearchTitle = findViewById(R.id.tvHotSearchTitle);
        
        songAdapter = new LxMusicAdapter();
        rvSearchResults.setAdapter(songAdapter);
        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            if (isKeyboardVisible) {
                hideCustomKeyboard();
            } else {
                finish();
            }
        });

        // 禁用系统键盘弹出，由自定义键盘处理输入
        etSearch.setShowSoftInputOnFocus(false);
    }

    private void setupRecyclerViews() {
        rvSourceList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        
        rvSourceList.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<SourceViewHolder>() {
            @NonNull
            @Override
            public SourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_source, parent, false);
                return new SourceViewHolder(view);
            }
            
            @Override
            public void onBindViewHolder(@NonNull SourceViewHolder holder, int position) {
                holder.tvSourceName.setText(SOURCE_NAMES[position]);
                holder.ivRadio.setImageResource(position == currentSourceIndex ? R.drawable.radio_checked : R.drawable.radio_unchecked);
                
                holder.itemView.setOnClickListener(v -> selectSource(position));
            }
            
            @Override
            public int getItemCount() {
                return SOURCES.length;
            }
        });
        
        rvHotSearch.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvHotSearch.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                outRect.left = 2;
                outRect.right = 2;
            }
        });
        hotSearchAdapter = new androidx.recyclerview.widget.RecyclerView.Adapter<HotSearchViewHolder>() {
            @NonNull
            @Override
            public HotSearchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                TextView tv = new TextView(parent.getContext());
                tv.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                tv.setPadding(18, 6, 18, 6);
                tv.setTextSize(12);
                tv.setTextColor(getResources().getColorStateList(R.color.white));
                tv.setBackgroundResource(R.drawable.bg_tab_selected);
                tv.setFocusable(true);
                tv.setClickable(true);
                return new HotSearchViewHolder(tv);
            }
            
            @Override
            public void onBindViewHolder(@NonNull HotSearchViewHolder holder, int position) {
                String hotWord = hotSearchWords.size() > position ? hotSearchWords.get(position) : "";
                holder.tv.setText(hotWord);
                holder.tv.setOnClickListener(v -> {
                    hideCustomKeyboard();
                    etSearch.setText(hotWord);
                    search(hotWord);
                });
            }
            
            @Override
            public int getItemCount() {
                return Math.min(hotSearchWords.size(), 10);
            }
        };
        rvHotSearch.setAdapter(hotSearchAdapter);
        
        rvSourceList.post(() -> {
            if (rvSourceList.getChildCount() > 0) {
                rvSourceList.getChildAt(0).requestFocus();
            }
        });
        
        songAdapter.setOnItemClickListener((song, position) -> {
            playSong(song);
        });
        
        songAdapter.setOnPlayClickListener((song, position) -> {
            playSong(song);
        });
        
        songAdapter.setOnFullscreenClickListener((song, position) -> {
            playSong(song);
            startActivity(new Intent(this, top.boluofan.musictv.PlayerActivity.class));
        });

        songAdapter.setOnFavClickListener((song, position) -> {
            collectSingleSong(song);
        });
    }

    private void selectSource(int position) {
        if (position < 0 || position >= SOURCES.length) return;

        currentSourceIndex = position;
        currentSource = SOURCES[position];

        if (rvSourceList.getAdapter() != null) {
            rvSourceList.getAdapter().notifyDataSetChanged();
        }

        rvSourceList.post(() -> {
            if (rvSourceList.getChildCount() > position) {
                View itemView = rvSourceList.getChildAt(position);
                if (itemView != null) {
                    itemView.requestFocus();
                }
            }
        });

        if (!lastKeyword.isEmpty()) {
            search(lastKeyword);
        }

        loadHotSearch();
    }

    private void collectSingleSong(MusicInfo song) {
        if (!LxRetrofitClient.isLoggedIn(this)) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, top.boluofan.musictv.ConfigActivity.class);
            intent.putExtra("server_url", LxRetrofitClient.getServerUrl(this));
            startActivity(intent);
            return;
        }

        if (LxRetrofitClient.API_TYPE_MiMusic.equals(LxRetrofitClient.getApiType(this))) {
            collectSingleSongMiMusic(song);
        } else {
            collectSingleSongLxMusic(song);
        }
    }

    private void collectSingleSongLxMusic(MusicInfo song) {
        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);
        LxApiService apiService = LxRetrofitClient.getLxAuthService(this);

        apiService.getUserList(username, password, token).enqueue(new Callback<top.boluofan.musictv.api.model.ListData>() {
            @Override
            public void onResponse(Call<top.boluofan.musictv.api.model.ListData> call, Response<top.boluofan.musictv.api.model.ListData> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(SearchActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                top.boluofan.musictv.api.model.ListData listData = response.body();
                List<top.boluofan.musictv.api.model.Playlist> userPlaylists = listData.getUserList();
                if (userPlaylists == null || userPlaylists.isEmpty()) {
                    Toast.makeText(SearchActivity.this, "暂无歌单，请先在歌单库创建歌单", Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] playlistNames = new String[userPlaylists.size()];
                for (int i = 0; i < userPlaylists.size(); i++) {
                    playlistNames[i] = userPlaylists.get(i).getName();
                }

                final MusicInfo finalSong = song;
                DialogHelper.showPlaylistPickerDialog(SearchActivity.this, "选择歌单", playlistNames, (android.content.DialogInterface dialog, int which) -> {
                    fetchAndAddSongToPlaylist(userPlaylists.get(which).getName(), finalSong);
                });
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.ListData> call, Throwable t) {
                Toast.makeText(SearchActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchAndAddSongToPlaylist(String playlistName, MusicInfo song) {
        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);
        LxApiService apiService = LxRetrofitClient.getLxAuthService(this);

        apiService.getUserList(username, password, token).enqueue(new Callback<top.boluofan.musictv.api.model.ListData>() {
            @Override
            public void onResponse(Call<top.boluofan.musictv.api.model.ListData> call, Response<top.boluofan.musictv.api.model.ListData> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(SearchActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                top.boluofan.musictv.api.model.ListData listData = response.body();
                List<top.boluofan.musictv.api.model.Playlist> userPlaylists = listData.getUserList();
                if (userPlaylists == null) {
                    Toast.makeText(SearchActivity.this, "歌单不存在", Toast.LENGTH_SHORT).show();
                    return;
                }

                top.boluofan.musictv.api.model.Playlist targetPlaylist = null;
                for (top.boluofan.musictv.api.model.Playlist p : userPlaylists) {
                    if (playlistName.equals(p.getName())) {
                        targetPlaylist = p;
                        break;
                    }
                }

                if (targetPlaylist == null) {
                    Toast.makeText(SearchActivity.this, "歌单不存在", Toast.LENGTH_SHORT).show();
                    return;
                }

                addSongToPlaylist(listData, targetPlaylist, song);
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.ListData> call, Throwable t) {
                Toast.makeText(SearchActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addSongToPlaylist(top.boluofan.musictv.api.model.ListData listData, top.boluofan.musictv.api.model.Playlist playlist, MusicInfo song) {
        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);
        LxApiService apiService = LxRetrofitClient.getLxAuthService(this);

        List<MusicInfo> songList = playlist.getSongs();
        if (songList == null) {
            songList = new ArrayList<>();
        }

        for (MusicInfo m : songList) {
            if (m.getName().equals(song.getName()) && m.getSource().equals(song.getSource())) {
                Toast.makeText(this, "歌曲已存在于此歌单", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        songList.add(0, song);
        playlist.setSongs(songList);
        playlist.setSongCount(songList.size());

        apiService.updateUserList(username, password, token, listData).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(SearchActivity.this, "已添加到「" + playlist.getName() + "」", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(SearchActivity.this, "添加失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                Toast.makeText(SearchActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadHotSearch() {
        String source = currentSource;
        if ("all".equals(source)) {
            hotSearchWords.clear();
            if (hotSearchAdapter != null) {
                hotSearchAdapter.notifyDataSetChanged();
            }
            runOnUiThread(() -> updateResults());
            return;
        }
        
        LxApiService apiService = LxRetrofitClient.getApiService(this);
        apiService.getHotSearch(source).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String bodyStr = response.body().string();
                        com.google.gson.JsonObject obj = new com.google.gson.Gson().fromJson(bodyStr, com.google.gson.JsonObject.class);
                        com.google.gson.JsonArray arr = obj.getAsJsonArray("list");
                        hotSearchWords.clear();
                        if (arr != null) {
                            for (int i = 0; i < arr.size() && i < 10; i++) {
                                hotSearchWords.add(arr.get(i).getAsString());
                            }
                        }
                        if (hotSearchAdapter != null) {
                            hotSearchAdapter.notifyDataSetChanged();
                        }
                        runOnUiThread(() -> updateResults());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            
            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                t.printStackTrace();
            }
        });
    }
    
    private void setupListeners() {
        btnSearch.setOnClickListener(v -> {
            String keyword = etSearch.getText().toString().trim();
            if (keyword.isEmpty()) {
                Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show();
                return;
            }
            hideCustomKeyboard();
            search(keyword);
        });
        
        btnClear.setOnClickListener(v -> {
            etSearch.setText("");
            lastKeyword = "";
            allResults.clear();
            updateResults();
        });
        
        etSearch.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (!isKeyboardVisible) {
                        showCustomKeyboard();
                        return true;
                    }
                }
            }
            return false;
        });
        
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (!isKeyboardVisible) {
                    showCustomKeyboard();
                } else {
                    String keyword = etSearch.getText().toString().trim();
                    if (!keyword.isEmpty()) {
                        hideCustomKeyboard();
                        search(keyword);
                    }
                }
                return true;
            }
            return false;
        });
        
        btnScan.setOnClickListener(v -> showScanSearchDialog());
    }
    
    private void setupCustomKeyboard() {
        customKeyboardPopup = new CustomKeyboardPopup(this);
        customKeyboardPopup.setSource(currentSource);
        customKeyboardPopup.setOnSearchListener(new CustomKeyboardPopup.OnSearchListener() {
            @Override
            public void onSearch(String keyword) {
                search(keyword);
            }
            
            @Override
            public void onInputChanged(String text) {
                etSearch.setText(text);
                etSearch.setSelection(text.length());
            }
        });
    }
    
    private void showCustomKeyboard() {
        if (customKeyboardPopup == null) {
            customKeyboardPopup = new CustomKeyboardPopup(this);
            customKeyboardPopup.setOnSearchListener(new CustomKeyboardPopup.OnSearchListener() {
                @Override
                public void onSearch(String keyword) {
                    search(keyword);
                }

                @Override
                public void onInputChanged(String text) {
                    etSearch.setText(text);
                    etSearch.setSelection(text.length());
                }
            });
        }
        if (customKeyboardPopup.isShowing()) {
            return;
        }
        isKeyboardVisible = true;

        // 阻止系统输入法管理器干扰
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && etSearch != null) {
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        }

        customKeyboardPopup.setSource(currentSource);
        customKeyboardPopup.show(etSearch.getText().toString());
    }
    
    private void hideCustomKeyboard() {
        isKeyboardVisible = false;
        if (customKeyboardPopup != null) {
            customKeyboardPopup.dismiss();
        }
        // 清除 EditText 的焦点，防止 InputMethodManager 继续尝试管理它
        if (etSearch != null) {
            etSearch.clearFocus();
        }
        // 使用 post 确保焦点转移在 UI 线程正常执行
        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.post(() -> btnBack.requestFocus());
        }
    }
    
    private void showScanSearchDialog() {
        String ipAddress = getIPAddress();
        if (ipAddress == null) {
            Toast.makeText(this, "无法获取局域网地址", Toast.LENGTH_SHORT).show();
            return;
        }

        String searchUrl = "http://" + ipAddress + ":" + SEARCH_SERVER_PORT;
        
        AlertDialog qrDialog = DialogHelper.showQrCodeDialog(
            this,
            "扫码搜索",
            "在手机浏览器访问地址后搜索歌曲",
            searchUrl,
            searchUrl
        );
        
        qrDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "关闭", (d, which) -> {
            if (searchWebServer != null) {
                searchWebServer.stop();
                searchWebServer = null;
            }
        });
        
        searchWebServer = new SearchWebServer(this, SEARCH_SERVER_PORT, (keyword, source) -> {
            mainHandler.post(() -> {
                qrDialog.dismiss();
                if (searchWebServer != null) {
                    searchWebServer.stop();
                    searchWebServer = null;
                }
                
                if (source != null && !source.isEmpty()) {
                    int sourceIdx = -1;
                    for (int i = 0; i < SOURCES.length; i++) {
                        if (SOURCES[i].equals(source)) {
                            sourceIdx = i;
                            break;
                        }
                    }
                    if (sourceIdx >= 0) {
                        selectSource(sourceIdx);
                    }
                }
                
                etSearch.setText(keyword);
                search(keyword);
                Toast.makeText(this, "收到推送的搜索: " + keyword, Toast.LENGTH_SHORT).show();
            });
        });
        
        try {
            searchWebServer.start();
        } catch (Exception e) {
            Toast.makeText(this, "服务启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        
        qrDialog.setOnDismissListener(d -> {
            if (searchWebServer != null) {
                searchWebServer.stop();
                searchWebServer = null;
            }
        });
        
        qrDialog.show();
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
    
    private void setupMiniPlayer() {
    }
    
    private void updateMiniPlayerVisibility() {
    }
    
    private void updateMiniPlayerInfo() {
    }

    private void search(String keyword) {
        hideCustomKeyboard();
        lastKeyword = keyword;
        currentPage = 1;
        hasMore = true;
        allResults.clear();
        showLoading(true);
        
        if ("all".equals(currentSource)) {
            searchAllSources(keyword);
        } else {
            searchSingleSource(keyword, currentSource);
        }
    }
    
    private void searchAllSources(String keyword) {
        ExecutorService executor = Executors.newFixedThreadPool(ALL_SOURCES.length);
        List<MusicInfo>[] results = new List[ALL_SOURCES.length];
        int[] completed = new int[1];
        
        for (int i = 0; i < ALL_SOURCES.length; i++) {
            final int index = i;
            final String source = ALL_SOURCES[index];
            
            executor.submit(() -> {
                LxApiService apiService = LxRetrofitClient.getApiService(SearchActivity.this);
                apiService.searchMusic(keyword, source, 1, 30).enqueue(new Callback<List<MusicInfo>>() {
                    @Override
                    public void onResponse(Call<List<MusicInfo>> call, Response<List<MusicInfo>> response) {
                        synchronized (completed) {
                            if (response.isSuccessful() && response.body() != null) {
                                results[index] = response.body();
                            } else {
                                results[index] = new ArrayList<>();
                            }
                            completed[0]++;
                            
                            if (completed[0] == ALL_SOURCES.length) {
                                runOnUiThread(() -> {
                                    mergeAllResults(results);
                                });
                            }
                        }
                    }
                    
                    @Override
                    public void onFailure(Call<List<MusicInfo>> call, Throwable t) {
                        synchronized (completed) {
                            results[index] = new ArrayList<>();
                            completed[0]++;
                            
                            if (completed[0] == ALL_SOURCES.length) {
                                runOnUiThread(() -> {
                                    mergeAllResults(results);
                                });
                            }
                        }
                    }
                });
            });
        }
    }
    
    private void mergeAllResults(List<MusicInfo>[] results) {
        allResults.clear();
        for (List<MusicInfo> list : results) {
            if (list != null) {
                for (MusicInfo song : list) {
                    song.setSearchSource(getSourceName(song.getSource()));
                    allResults.add(song);
                }
            }
        }
        hasMore = false;
        showLoading(false);
        updateResults();
    }
    
    private String getSourceName(String source) {
        for (int i = 0; i < ALL_SOURCES.length; i++) {
            if (ALL_SOURCES[i].equals(source)) {
                return ALL_SOURCE_NAMES[i];
            }
        }
        return source;
    }
    
    private void searchSingleSource(String keyword, String source) {
        LxApiService apiService = LxRetrofitClient.getApiService(this);
        apiService.searchMusic(keyword, source, currentPage, 30).enqueue(new Callback<List<MusicInfo>>() {
            @Override
            public void onResponse(Call<List<MusicInfo>> call, Response<List<MusicInfo>> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    List<MusicInfo> result = response.body();
                    allResults.addAll(result);
                    hasMore = result.size() >= 30;
                    
                    String sourceName = "";
                    for (int i = 0; i < SOURCES.length; i++) {
                        if (SOURCES[i].equals(source)) {
                            sourceName = SOURCE_NAMES[i];
                            break;
                        }
                    }
                    for (MusicInfo song : result) {
                        song.setSearchSource(sourceName);
                    }
                    
                    updateResults();
                } else {
                    Toast.makeText(SearchActivity.this, "搜索失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<MusicInfo>> call, Throwable t) {
                showLoading(false);
                Toast.makeText(SearchActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateResults() {
        boolean hasHotSearch = !hotSearchWords.isEmpty();
        boolean hasSearchResults = !allResults.isEmpty();
        
        tvHotSearchTitle.setVisibility(hasHotSearch ? View.VISIBLE : View.GONE);
        rvHotSearch.setVisibility(hasHotSearch ? View.VISIBLE : View.GONE);
        
        if (hasSearchResults) {
            tvResultCount.setVisibility(View.VISIBLE);
            tvResultCount.setText("共 " + allResults.size() + " 首");
            rvSearchResults.setVisibility(View.VISIBLE);
            tvNoResults.setVisibility(View.GONE);
        } else {
            tvResultCount.setVisibility(View.GONE);
            rvSearchResults.setVisibility(View.GONE);
            if (!hasHotSearch) {
                tvNoResults.setVisibility(View.VISIBLE);
            } else {
                tvNoResults.setVisibility(View.GONE);
            }
        }
        
        songAdapter.setSongs(allResults);
    }

    private void playSong(MusicInfo song) {
        if (player == null) {
            Toast.makeText(this, "播放器未初始化", Toast.LENGTH_SHORT).show();
            return;
        }
        
        MediaItem mediaItem = createMediaItem(song);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();

        songAdapter.setPlayingSongId(song.getSongmid());

        Toast.makeText(this, "正在播放: " + song.getName(), Toast.LENGTH_SHORT).show();
    }

    private MediaItem createMediaItem(MusicInfo song) {
        Bundle extras = new Bundle();
        extras.putString("song_id", song.getId());
        extras.putString("source", song.getSource());
        extras.putString("songmid", song.getSongmid());
        extras.putString("pic_url", song.getPicUrl());
        extras.putString("original_name", song.getName());
        
        Uri artworkUri = null;
        String coverUrl = song.getPicUrl();
        if (coverUrl != null && !coverUrl.isEmpty()) {
            artworkUri = Uri.parse(coverUrl);
        }
        
        Uri resolveUri = MusicService.buildResolveUri(song.getSource(), song.getSongmid(), song.getName());
        
        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
                .setTitle(song.getName())
                .setArtist(song.getSinger())
                .setAlbumTitle(song.getAlbumName())
                .setExtras(extras);
        
        if (artworkUri != null) {
            metadataBuilder.setArtworkUri(artworkUri);
        }
        
        return new MediaItem.Builder()
                .setMediaId(song.getSongmid())
                .setUri(resolveUri)
                .setMediaMetadata(metadataBuilder.build())
                .build();
    }

    private void showLoading(boolean show) {
        loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        
        floatingPlayerWindow = new FloatingPlayerWindow(this);
        floatingPlayerWindow.connectToService();
        
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, MusicService.class));
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                player = controllerFuture.get();
                setupPlayerListener();
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
        if (positionUpdater != null) {
            mainHandler.removeCallbacks(positionUpdater);
        }
    }
    
    private void setupPlayerListener() {
        if (player == null) return;
        
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                songAdapter.setPlayerPlaying(isPlaying);
            }
            
            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            }
        });
    }
    
    private static class SourceViewHolder extends RecyclerView.ViewHolder {
        ImageView ivRadio;
        TextView tvSourceName;
        
        SourceViewHolder(View itemView) {
            super(itemView);
            ivRadio = itemView.findViewById(R.id.ivRadio);
            tvSourceName = itemView.findViewById(R.id.tvSourceName);
        }
    }
    
    private static class HotSearchViewHolder extends RecyclerView.ViewHolder {
        TextView tv;
        HotSearchViewHolder(TextView tv) { super(tv); this.tv = tv; }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isKeyboardVisible) {
                hideCustomKeyboard();
                return true;
            }
        }
        
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) && !isKeyboardVisible) {
            View currentFocus = getCurrentFocus();
            if (currentFocus == etSearch || currentFocus == btnSearch || currentFocus == btnClear) {
                showCustomKeyboard();
                return true;
            }
            if (currentFocus != null) {
                showCustomKeyboard();
                return true;
            }
        }
        
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            View currentFocus = getCurrentFocus();
            if (currentFocus != null && floatingPlayerWindow != null) {
                if (floatingPlayerWindow.handleLeftKey(currentFocus)) {
                    return true;
                }
            }
        }
        
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                if (currentFocus.getParent() == rvSourceList) {
                    if (!"all".equals(currentSource) && hotSearchWords.isEmpty()) {
                        return true;
                    }
                } else if (currentFocus.getParent() == rvHotSearch) {
                    if (!"all".equals(currentSource) && allResults.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (floatingPlayerWindow != null) {
            floatingPlayerWindow.release();
            floatingPlayerWindow = null;
        }
        if (searchWebServer != null) {
            searchWebServer.stop();
            searchWebServer = null;
        }
    }

    private void collectSingleSongMiMusic(MusicInfo song) {
        LxApiService apiService = LxRetrofitClient.getMiMusicAuthService(this);
        if (apiService == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, top.boluofan.musictv.ConfigActivity.class));
            return;
        }

        apiService.getMiMusicPlaylists(100, 0).enqueue(new Callback<top.boluofan.musictv.api.model.MiPlaylistListResponse>() {
            @Override
            public void onResponse(Call<top.boluofan.musictv.api.model.MiPlaylistListResponse> call, Response<top.boluofan.musictv.api.model.MiPlaylistListResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(SearchActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                List<top.boluofan.musictv.api.model.MiPlaylist> playlists = response.body().getPlaylists();
                if (playlists == null || playlists.isEmpty()) {
                    Toast.makeText(SearchActivity.this, "暂无歌单，请先在歌单库创建歌单", Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] playlistNames = new String[playlists.size()];
                for (int i = 0; i < playlists.size(); i++) {
                    playlistNames[i] = playlists.get(i).getName();
                }

                final MusicInfo finalSong = song;
                DialogHelper.showPlaylistPickerDialog(SearchActivity.this, "选择歌单", playlistNames, (android.content.DialogInterface dialog, int which) -> {
                    importSongToMiMusic(playlists.get(which).getId(), finalSong);
                });
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.MiPlaylistListResponse> call, Throwable t) {
                Toast.makeText(SearchActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void importSongToMiMusic(int playlistId, MusicInfo song) {
        LxApiService apiService = LxRetrofitClient.getMiMusicApiServiceNoTimeout(this);
        if (apiService == null) return;
        List<MusicInfo> songs = new ArrayList<>();
        songs.add(song);
        Map<String, Object> body = buildImportBody(playlistId, "", songs);
        Toast.makeText(SearchActivity.this, "已添加到收藏", Toast.LENGTH_SHORT).show();
        apiService.importSongsToPlaylist(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                // fire-and-forget, ignore response
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                // fire-and-forget, ignore error
            }
        });
    }

    private Map<String, Object> buildImportBody(int playlistId, String newPlaylistName, List<MusicInfo> songs) {
        Map<String, Object> body = new HashMap<>();
        body.put("quality", LxRetrofitClient.getQuality(this));
        body.put("playlist_id", playlistId);
        body.put("new_playlist_name", newPlaylistName);

        List<Map<String, Object>> songsList = new ArrayList<>();
        for (MusicInfo song : songs) {
            songsList.add(song.toMiImportSong());
        }
        body.put("songs", songsList);
        return body;
    }
}
