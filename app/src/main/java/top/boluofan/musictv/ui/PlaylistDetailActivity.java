package top.boluofan.musictv.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.KeyEvent;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import okhttp3.ResponseBody;
import top.boluofan.musictv.MusicService;
import top.boluofan.musictv.R;
import top.boluofan.musictv.FloatingPlayerWindow;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.api.model.Playlist;
import top.boluofan.musictv.ui.adapter.LxMusicAdapter;
import top.boluofan.musictv.util.DialogHelper;
import android.net.Uri;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PlaylistDetailActivity extends AppCompatActivity {
    private static final String TAG = "PlaylistDetailActivity";
    
    private ImageButton btnBack;
    private TextView tvTitle;
    private ImageView ivCover;
    private TextView tvPlaylistName;
    private TextView tvPlaylistInfo;
    private TextView tvPlaylistDesc;
    private TextView tvPlaylistSource;
    private TextView tvPlaylistPlayCount;
    private TextView tvPlaylistCreateTime;
    private ImageButton btnPlayAll;
    private ImageButton btnShuffle;
    private ImageButton btnFavorite;
    private RecyclerView rvSongs;
    private ProgressBar loadingProgress;
    
    private String playlistId;
    private String playlistName;
    private String playlistSource;
    private String playlistCover;
    
    private LxMusicAdapter songAdapter;
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private FloatingPlayerWindow floatingPlayerWindow;
    private List<MusicInfo> songs = new ArrayList<>();
    
    private final String[] SOURCES = {"mg", "kw", "kg", "tx", "wy"};
    private final String[] SOURCE_NAMES = {"小蜜", "小窝", "小枸", "小秋", "小芸"};
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        android.util.Log.d(TAG, "onCreate called");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_detail);

        initViews();
        setupListeners();
        loadIntentData();

        floatingPlayerWindow = new FloatingPlayerWindow(this);
        floatingPlayerWindow.connectToService();

        loadPlaylistDetail();
    }
    
    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvTitle = findViewById(R.id.tvTitle);
        ivCover = findViewById(R.id.ivCover);
        tvPlaylistName = findViewById(R.id.tvPlaylistName);
        tvPlaylistInfo = findViewById(R.id.tvPlaylistInfo);
        tvPlaylistDesc = findViewById(R.id.tvPlaylistDesc);
        tvPlaylistSource = findViewById(R.id.tvPlaylistSource);
        tvPlaylistPlayCount = findViewById(R.id.tvPlaylistPlayCount);
        tvPlaylistCreateTime = findViewById(R.id.tvPlaylistCreateTime);
        btnPlayAll = findViewById(R.id.btnPlayAll);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnFavorite = findViewById(R.id.btnFavorite);
        rvSongs = findViewById(R.id.rvSongs);
        loadingProgress = findViewById(R.id.loadingProgress);
        
        songAdapter = new LxMusicAdapter();
        rvSongs.setAdapter(songAdapter);
        rvSongs.setLayoutManager(new LinearLayoutManager(this));
    }
    
    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        
        btnPlayAll.setOnClickListener(v -> playAll(false));
        btnShuffle.setOnClickListener(v -> playAll(true));
        btnFavorite.setOnClickListener(v -> collectPlaylist());
        
        songAdapter.setOnItemClickListener((song, position) -> {
            playSongAtIndex(position);
        });
        
        songAdapter.setOnPlayClickListener((song, position) -> {
            playSongAtIndex(position);
        });
        
        songAdapter.setOnFullscreenClickListener((song, position) -> {
            playSongAtIndex(position);
            startActivity(new Intent(this, top.boluofan.musictv.PlayerActivity.class));
        });

        songAdapter.setOnFavClickListener((song, position) -> {
            collectSingleSong(song);
        });
    }
    
    private void collectPlaylist() {
        if (!LxRetrofitClient.isLoggedIn(this)) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, top.boluofan.musictv.ConfigActivity.class);
            intent.putExtra("server_url", LxRetrofitClient.getServerUrl(this));
            startActivity(intent);
            return;
        }

        if (songs.isEmpty()) {
            Toast.makeText(this, "歌单为空，无法收藏", Toast.LENGTH_SHORT).show();
            return;
        }

        if (LxRetrofitClient.API_TYPE_MiMusic.equals(LxRetrofitClient.getApiType(this))) {
            collectPlaylistMiMusic();
        } else {
            collectPlaylistLxMusic();
        }
    }

    private void collectPlaylistLxMusic() {
        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);
        LxApiService apiService = LxRetrofitClient.getLxAuthService(this);

        btnFavorite.setEnabled(false);

        apiService.getUserList(username, password, token).enqueue(new Callback<top.boluofan.musictv.api.model.ListData>() {
            @Override
            public void onResponse(Call<top.boluofan.musictv.api.model.ListData> call, Response<top.boluofan.musictv.api.model.ListData> response) {
                btnFavorite.setEnabled(true);

                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(PlaylistDetailActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                top.boluofan.musictv.api.model.ListData listData = response.body();

                top.boluofan.musictv.api.model.Playlist existingPlaylist = null;
                if (listData.getUserList() != null) {
                    for (top.boluofan.musictv.api.model.Playlist p : listData.getUserList()) {
                        if (playlistName.equals(p.getName())) {
                            existingPlaylist = p;
                            break;
                        }
                    }
                }

                if (existingPlaylist != null) {
                    final top.boluofan.musictv.api.model.ListData finalListData = listData;
                    final top.boluofan.musictv.api.model.Playlist finalExistingPlaylist = existingPlaylist;
                    android.content.Context ctx = PlaylistDetailActivity.this;
                    DialogHelper.showOverwriteConfirmDialog(ctx, playlistName, new DialogHelper.IDialogCallback() {
                        @Override
                        public void onConfirm() {
                            doCollectPlaylist(finalListData, finalExistingPlaylist);
                        }

                        @Override
                        public void onCancel() {
                        }
                    });
                } else {
                    doCollectPlaylist(listData, null);
                }
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.ListData> call, Throwable t) {
                btnFavorite.setEnabled(true);
                Toast.makeText(PlaylistDetailActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void collectPlaylistMiMusic() {
        LxApiService apiService = LxRetrofitClient.getMiMusicAuthService(this);
        if (apiService == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, top.boluofan.musictv.ConfigActivity.class));
            return;
        }

        btnFavorite.setEnabled(false);

        apiService.getMiMusicPlaylists(100, 0).enqueue(new Callback<top.boluofan.musictv.api.model.MiPlaylistListResponse>() {
            @Override
            public void onResponse(Call<top.boluofan.musictv.api.model.MiPlaylistListResponse> call, Response<top.boluofan.musictv.api.model.MiPlaylistListResponse> response) {
                btnFavorite.setEnabled(true);

                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(PlaylistDetailActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                top.boluofan.musictv.api.model.MiPlaylist existingPlaylist = null;
                List<top.boluofan.musictv.api.model.MiPlaylist> playlists = response.body().getPlaylists();
                if (playlists != null) {
                    for (top.boluofan.musictv.api.model.MiPlaylist p : playlists) {
                        if (playlistName.equals(p.getName())) {
                            existingPlaylist = p;
                            break;
                        }
                    }
                }

                if (existingPlaylist != null) {
                    final top.boluofan.musictv.api.model.MiPlaylist finalExistingPlaylist = existingPlaylist;
                    DialogHelper.showOverwriteConfirmDialog(PlaylistDetailActivity.this, playlistName, new DialogHelper.IDialogCallback() {
                        @Override
                        public void onConfirm() {
                            importPlaylistToMiMusic(finalExistingPlaylist.getId(), true,"");
                        }

                        @Override
                        public void onCancel() {
                        }
                    });
                } else {
                    importPlaylistToMiMusic(0, false, playlistName);
                }
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.MiPlaylistListResponse> call, Throwable t) {
                btnFavorite.setEnabled(true);
                Toast.makeText(PlaylistDetailActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void importPlaylistToMiMusic(int existPlaylistId, boolean isOverwrite, String playlistName) {
        LxApiService apiService = LxRetrofitClient.getMiMusicApiServiceNoTimeout(this);
        if (apiService == null) return;

        Map<String, Object> body = buildImportBody(isOverwrite ? existPlaylistId : 0, playlistName, songs);

        Toast.makeText(PlaylistDetailActivity.this, isOverwrite ? "覆盖成功" : "收藏成功", Toast.LENGTH_SHORT).show();
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

    private Map<String, Object> buildImportBody(int playlistId, String newPlaylistName,List<MusicInfo> songs) {
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
    
    private void doCollectPlaylist(top.boluofan.musictv.api.model.ListData listData, top.boluofan.musictv.api.model.Playlist existingPlaylist) {
        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);
        LxApiService apiService = LxRetrofitClient.getLxAuthService(this);
        
        top.boluofan.musictv.api.model.Playlist newPlaylist;
        if (existingPlaylist != null) {
            newPlaylist = existingPlaylist;
            newPlaylist.setSongs(new ArrayList<>(songs));
            newPlaylist.setSongCount(songs.size());
            newPlaylist.setSource(playlistSource);
            newPlaylist.setSourceListId(playlistId);
        } else {
            newPlaylist = new top.boluofan.musictv.api.model.Playlist();
            newPlaylist.setId("playlist_" + System.currentTimeMillis());
            newPlaylist.setName(playlistName);
            newPlaylist.setSource(playlistSource);
            newPlaylist.setSourceListId(playlistId);
            newPlaylist.setSongs(new ArrayList<>(songs));
            newPlaylist.setSongCount(songs.size());
            
            if (listData.getUserList() == null) {
                listData.setUserList(new ArrayList<>());
            }
            listData.getUserList().add(newPlaylist);
        }
        
        btnFavorite.setEnabled(false);
        apiService.updateUserList(username, password, token, listData).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                btnFavorite.setEnabled(true);
                if (response.isSuccessful()) {
                    Toast.makeText(PlaylistDetailActivity.this, existingPlaylist != null ? "覆盖成功" : "收藏成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(PlaylistDetailActivity.this, "收藏失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                btnFavorite.setEnabled(true);
                Toast.makeText(PlaylistDetailActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
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
                    Toast.makeText(PlaylistDetailActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                top.boluofan.musictv.api.model.ListData listData = response.body();
                List<top.boluofan.musictv.api.model.Playlist> userPlaylists = listData.getUserList();
                if (userPlaylists == null || userPlaylists.isEmpty()) {
                    Toast.makeText(PlaylistDetailActivity.this, "暂无歌单，请先在歌单库创建歌单", Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] playlistNames = new String[userPlaylists.size()];
                for (int i = 0; i < userPlaylists.size(); i++) {
                    playlistNames[i] = userPlaylists.get(i).getName();
                }

                final MusicInfo finalSong = song;
                DialogHelper.showPlaylistPickerDialog(PlaylistDetailActivity.this, "选择歌单", playlistNames, (android.content.DialogInterface dialog, int which) -> {
                    fetchAndAddSongToPlaylist(userPlaylists.get(which).getName(), finalSong);
                });
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.ListData> call, Throwable t) {
                Toast.makeText(PlaylistDetailActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
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
                    Toast.makeText(PlaylistDetailActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                List<top.boluofan.musictv.api.model.MiPlaylist> playlists = response.body().getPlaylists();
                if (playlists == null || playlists.isEmpty()) {
                    Toast.makeText(PlaylistDetailActivity.this, "暂无歌单，请先在歌单库创建歌单", Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] playlistNames = new String[playlists.size()];
                for (int i = 0; i < playlists.size(); i++) {
                    playlistNames[i] = playlists.get(i).getName();
                }

                final MusicInfo finalSong = song;
                DialogHelper.showPlaylistPickerDialog(PlaylistDetailActivity.this, "选择歌单", playlistNames, (android.content.DialogInterface dialog, int which) -> {
                    importSongToMiMusic(playlists.get(which).getId(), finalSong);
                });
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.MiPlaylistListResponse> call, Throwable t) {
                Toast.makeText(PlaylistDetailActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void importSongToMiMusic(int playlistId, MusicInfo song) {
        LxApiService apiService = LxRetrofitClient.getMiMusicApiServiceNoTimeout(this);
        if (apiService == null) return;
        List<MusicInfo> songs = new ArrayList<>();
        songs.add(song);
        Map<String, Object> body = buildImportBody(playlistId, "", songs);
        Toast.makeText(PlaylistDetailActivity.this, "已添加到收藏", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(PlaylistDetailActivity.this, "已添加到「" + playlist.getName() + "」", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(PlaylistDetailActivity.this, "添加失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                Toast.makeText(PlaylistDetailActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(PlaylistDetailActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                top.boluofan.musictv.api.model.ListData listData = response.body();
                List<top.boluofan.musictv.api.model.Playlist> userPlaylists = listData.getUserList();
                if (userPlaylists == null) {
                    Toast.makeText(PlaylistDetailActivity.this, "歌单不存在", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(PlaylistDetailActivity.this, "歌单不存在", Toast.LENGTH_SHORT).show();
                    return;
                }

                addSongToPlaylist(listData, targetPlaylist, song);
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.ListData> call, Throwable t) {
                Toast.makeText(PlaylistDetailActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void loadIntentData() {
        playlistId = getIntent().getStringExtra("playlist_id");
        playlistName = getIntent().getStringExtra("playlist_name");
        playlistSource = getIntent().getStringExtra("playlist_source");
        playlistCover = getIntent().getStringExtra("playlist_cover");
        
        tvTitle.setText("歌单详情");
        tvPlaylistName.setText(playlistName);
        
        if (playlistCover != null && !playlistCover.isEmpty()) {
            Glide.with(this).load(playlistCover)
                    .placeholder(R.drawable.ic_cover_placeholder)
                    .into(ivCover);
        }
        
        String sourceName = getSourceName(playlistSource);
        tvPlaylistSource.setText(sourceName);
    }
    
    private String getSourceName(String source) {
        if (source == null) return "来源: 未知";
        for (int i = 0; i < SOURCES.length; i++) {
            if (source.equals(SOURCES[i])) {
                return "来源: " + SOURCE_NAMES[i];
            }
        }
        return "来源: " + source;
    }
    
    private void loadPlaylistDetail() {
        android.util.Log.d(TAG, "loadPlaylistDetail: playlistId=" + playlistId + ", playlistSource=" + playlistSource);
        if (playlistId == null || playlistSource == null) {
            android.util.Log.d(TAG, "loadPlaylistDetail: early return due to null");
            return;
        }

        // 检查是否是 MiMusic 模式
        String apiType = LxRetrofitClient.getApiType(this);
        if (LxRetrofitClient.API_TYPE_MiMusic.equals(apiType)) {
            // MiMusic 模式：使用 getApiService，它的 baseUrl 是 /plugin/tv-api/
            showLoading(true);
            LxApiService apiService = LxRetrofitClient.getApiService(this);
            apiService.getPlaylistDetail(playlistSource, playlistId, 1).enqueue(new Callback<Playlist>() {
                @Override
                public void onResponse(Call<Playlist> call, Response<Playlist> response) {
                    showLoading(false);
                    if (response.isSuccessful() && response.body() != null) {
                        updateUI(response.body());
                    } else {
                        Toast.makeText(PlaylistDetailActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Playlist> call, Throwable t) {
                    showLoading(false);
                    Toast.makeText(PlaylistDetailActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        showLoading(true);

        LxApiService apiService = LxRetrofitClient.getApiService(this);
        apiService.getPlaylistDetail(playlistSource, playlistId, 1).enqueue(new Callback<Playlist>() {
            @Override
            public void onResponse(Call<Playlist> call, Response<Playlist> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    Playlist playlist = response.body();
                    updateUI(playlist);
                } else {
                    Toast.makeText(PlaylistDetailActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Playlist> call, Throwable t) {
                showLoading(false);
                Toast.makeText(PlaylistDetailActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void updateUI(Playlist playlist) {
        tvPlaylistInfo.setText(playlist.getSongCount() + " 首歌曲");
        
        String creator = playlist.getCreator();
        if (creator != null && !creator.isEmpty()) {
            tvPlaylistSource.setText("来源: " + creator);
        }
        
        String desc = playlist.getDesc();
        if (desc != null && !desc.isEmpty()) {
            tvPlaylistDesc.setText(desc);
            tvPlaylistDesc.setVisibility(View.VISIBLE);
        } else {
            tvPlaylistDesc.setVisibility(View.GONE);
        }
        
        String playCountText = playlist.getFormattedPlayCount();
        if (playCountText != null && !playCountText.isEmpty()) {
            tvPlaylistPlayCount.setText("播放: " + playCountText);
            tvPlaylistPlayCount.setVisibility(View.VISIBLE);
        } else {
            tvPlaylistPlayCount.setVisibility(View.GONE);
        }
        
        String createTime = playlist.getTime();
        if (createTime != null && !createTime.isEmpty()) {
            tvPlaylistCreateTime.setText("创建时间: " + createTime);
            tvPlaylistCreateTime.setVisibility(View.VISIBLE);
        } else if (playlist.getCreateTime() != null && playlist.getCreateTime() > 0) {
            String formattedTime = formatTime(playlist.getCreateTime());
            tvPlaylistCreateTime.setText("创建时间: " + formattedTime);
            tvPlaylistCreateTime.setVisibility(View.VISIBLE);
        } else {
            tvPlaylistCreateTime.setVisibility(View.GONE);
        }
        
        String coverUrl = playlist.getPicUrl();
        if (coverUrl != null && !coverUrl.isEmpty() && !isFinishing() && !isDestroyed()) {
            Glide.with(this).load(coverUrl)
                    .placeholder(R.drawable.ic_cover_placeholder)
                    .into(ivCover);
        }
        
        if (playlist.getSongs() != null) {
            songs = playlist.getSongs();
            songAdapter.setSongs(songs);
        }
    }
    
    private void playAll(boolean shuffle) {
        if (songs.isEmpty()) {
            Toast.makeText(this, "没有可播放的歌曲", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (player == null) return;
        
        List<MediaItem> mediaItems = new ArrayList<>();
        for (MusicInfo song : songs) {
            mediaItems.add(createMediaItem(song));
        }
        
        int startIndex = shuffle ? (int) (Math.random() * songs.size()) : 0;
        
        player.setMediaItems(mediaItems, startIndex, 0);
        player.prepare();
        player.play();
        
        Toast.makeText(this, shuffle ? "随机播放" : "播放全部", Toast.LENGTH_SHORT).show();
    }
    
    private void playSongAtIndex(int position) {
        if (songs.isEmpty() || player == null) return;
        
        List<MediaItem> mediaItems = new ArrayList<>();
        for (MusicInfo song : songs) {
            mediaItems.add(createMediaItem(song));
        }
        
        player.setMediaItems(mediaItems, position, 0);
        player.prepare();
        player.play();
        songAdapter.setPlayingIndex(position);
    }
    
    private MediaItem createMediaItem(MusicInfo song) {
        Bundle extras = new Bundle();
        extras.putString("song_id", song.getId());
        extras.putString("source", song.getSource());
        extras.putString("songmid", song.getSongmid());
        extras.putString("pic_url", song.getPicUrl());
        extras.putString("original_name", song.getName());
        
        Uri artworkUri = song.getPicUrl() != null ? Uri.parse(song.getPicUrl()) : null;
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
    
    private String formatTime(Long timestamp) {
        if (timestamp == null || timestamp <= 0) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
    
    private void showLoading(boolean show) {
        loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
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
