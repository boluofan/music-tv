package top.boluofan.musictv.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import okhttp3.ResponseBody;
import top.boluofan.musictv.MusicService;
import top.boluofan.musictv.R;
import top.boluofan.musictv.FloatingPlayerWindow;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.ListData;
import top.boluofan.musictv.api.model.MiPlaylist;
import top.boluofan.musictv.api.model.MiPlaylistSongsResponse;
import top.boluofan.musictv.api.model.MiSong;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.api.model.Playlist;
import top.boluofan.musictv.ui.adapter.LxMusicAdapter;
import top.boluofan.musictv.PlaylistAdapter;
import top.boluofan.musictv.player.MiMusicPlayerHelper;
import top.boluofan.musictv.util.DialogHelper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import android.content.ComponentName;
import android.os.Handler;
import android.os.Looper;

@UnstableApi
public class LibraryActivity extends AppCompatActivity {
    private static final String TAG = "LibraryActivity";
    
    private RecyclerView rvPlaylists;
    private RecyclerView rvSongs;
    private PlaylistAdapter playlistAdapter;
    private LxMusicAdapter songAdapter;
    
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private FloatingPlayerWindow floatingPlayerWindow;
    
    private TextView tvPlaylistTitle;
    private TextView tvSongCount;
    private ImageButton btnBack;
    private ImageButton btnPlayAll;
    private ImageButton btnShuffle;
    
    private ListData listData;
    private Playlist currentPlaylist;
    private MiPlaylist currentMiPlaylist;
    private List<MiPlaylist> miPlaylistList;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        handler = new Handler(Looper.getMainLooper());
        initViews();
        setupRecyclerViews();
        setupListeners();
        
        floatingPlayerWindow = new FloatingPlayerWindow(this);
        floatingPlayerWindow.connectToService();
        
        loadUserData();
    }

    private void initViews() {
        rvPlaylists = findViewById(R.id.rvPlaylists);
        rvSongs = findViewById(R.id.rvSongs);
        tvPlaylistTitle = findViewById(R.id.tvPlaylistTitle);
        tvSongCount = findViewById(R.id.tvSongCount);
        btnBack = findViewById(R.id.btnBack);
        btnPlayAll = findViewById(R.id.btnPlayAll);
        btnShuffle = findViewById(R.id.btnShuffle);
        ImageButton btnRefresh = findViewById(R.id.btnRefresh);
        ImageButton btnLogout = findViewById(R.id.btnLogout);
        
        btnRefresh.setOnClickListener(v -> loadUserData());
        btnLogout.setOnClickListener(v -> logout());
    }

    private void setupRecyclerViews() {
        playlistAdapter = new PlaylistAdapter();
        rvPlaylists.setAdapter(playlistAdapter);
        rvPlaylists.setLayoutManager(new LinearLayoutManager(this));
        
        songAdapter = new LxMusicAdapter();
        songAdapter.setShowDeleteButton(true);
        songAdapter.setShowFavButton(false);
        rvSongs.setAdapter(songAdapter);
        rvSongs.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        
        btnPlayAll.setOnClickListener(v -> playAll(false));
        btnShuffle.setOnClickListener(v -> playAll(true));
        
        playlistAdapter.setOnItemClickListener(playlistName -> {
            loadPlaylistSongs(playlistName);
        });
        
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
        
        songAdapter.setOnDeleteClickListener((song, position) -> {
            showDeleteConfirmDialog(song, position);
        });
    }

    private void loadUserData() {
        String apiType = LxRetrofitClient.getApiType(this);

        if (LxRetrofitClient.API_TYPE_MiMusic.equals(apiType)) {
            loadMiMusicUserData();
        } else {
            loadLxMusicUserData();
        }
    }

    private void loadLxMusicUserData() {
        // 清除 MiMusic 相关状态
        currentMiPlaylist = null;
        miPlaylistList = null;

        LxApiService apiService = LxRetrofitClient.getLxAuthService(this);

        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, top.boluofan.musictv.ConfigActivity.class));
            finish();
            return;
        }

        apiService.getUserList(username, password, token).enqueue(new Callback<ListData>() {
            @Override
            public void onResponse(Call<ListData> call, Response<ListData> response) {
                if (response.isSuccessful() && response.body() != null) {
                    listData = response.body();
                    miPlaylistList = null;
                    updatePlaylistList();
                } else {
                    Toast.makeText(LibraryActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ListData> call, Throwable t) {
                Toast.makeText(LibraryActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadMiMusicUserData() {
        LxApiService apiService = LxRetrofitClient.getMiMusicAuthService(this);
        if (apiService == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, top.boluofan.musictv.ConfigActivity.class));
            finish();
            return;
        }

        apiService.getMiMusicPlaylists(100, 0).enqueue(new Callback<top.boluofan.musictv.api.model.MiPlaylistListResponse>() {
            @Override
            public void onResponse(Call<top.boluofan.musictv.api.model.MiPlaylistListResponse> call, Response<top.boluofan.musictv.api.model.MiPlaylistListResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // 展示非电台的歌单（type 为空或 "normal"），过滤掉 type="radio"
                    List<top.boluofan.musictv.api.model.MiPlaylist> allPlaylists = response.body().getPlaylists();
                    miPlaylistList = new java.util.ArrayList<>();
                    if (allPlaylists != null) {
                        for (top.boluofan.musictv.api.model.MiPlaylist p : allPlaylists) {
                            Integer songCount = p.getSongCount();
                            String type = p.getType();
                            if (songCount > 0 && (type.isEmpty() || "normal".equals(type))) {
                                miPlaylistList.add(p);
                            }
                        }
                    }
                    listData = null;
                    updatePlaylistList();
                } else {
                    Toast.makeText(LibraryActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.MiPlaylistListResponse> call, Throwable t) {
                Toast.makeText(LibraryActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updatePlaylistList() {
        java.util.Map<String, List<String>> playlistData = new java.util.HashMap<>();

        if (miPlaylistList != null && !miPlaylistList.isEmpty()) {
            // MiMusic 模式
            for (MiPlaylist playlist : miPlaylistList) {
                List<String> songNames = new ArrayList<>();
                // MiMusic API 返回的歌单有 song_count，但不包含歌曲详情
                // 歌曲详情需要通过 loadMiMusicPlaylistSongs 单独加载
                playlistData.put(playlist.getName(), songNames);
            }
            playlistAdapter.setData(playlistData);
            // 设置每个歌单的歌曲数量
            for (MiPlaylist playlist : miPlaylistList) {
                playlistAdapter.setPlaylistSongCount(playlist.getName(), playlist.getSongCount() != null ? playlist.getSongCount() : 0);
            }

            if (!playlistData.isEmpty()) {
                String firstKey = playlistData.keySet().iterator().next();
                loadPlaylistSongs(firstKey);
            }
        } else if (listData != null) {
            // 洛雪音乐模式
            Playlist defaultPlaylist = listData.getDefaultPlaylist();
            if (defaultPlaylist != null && defaultPlaylist.getSongs() != null && !defaultPlaylist.getSongs().isEmpty()) {
                List<String> songNames = new ArrayList<>();
                for (MusicInfo song : defaultPlaylist.getSongs()) {
                    songNames.add(song.getName());
                }
                playlistData.put(defaultPlaylist.getName(), songNames);
            }

            Playlist lovePlaylist = listData.getLovePlaylist();
            if (lovePlaylist != null && lovePlaylist.getSongs() != null && !lovePlaylist.getSongs().isEmpty()) {
                List<String> songNames = new ArrayList<>();
                for (MusicInfo song : lovePlaylist.getSongs()) {
                    songNames.add(song.getName());
                }
                playlistData.put(lovePlaylist.getName(), songNames);
            }

            if (listData.getUserList() != null) {
                for (Playlist playlist : listData.getUserList()) {
                    List<String> songNames = new ArrayList<>();
                    if (playlist.getSongs() != null) {
                        for (MusicInfo song : playlist.getSongs()) {
                            songNames.add(song.getName());
                        }
                    }
                    playlistData.put(playlist.getName(), songNames);
                }
            }

            playlistAdapter.setData(playlistData);

            if (!playlistData.isEmpty()) {
                String firstKey = playlistData.keySet().iterator().next();
                loadPlaylistSongs(firstKey);
            }
        }
    }

    private void updateSongList() {
        if (currentPlaylist == null || currentPlaylist.getSongs() == null) {
            songAdapter.setSongs(null);
            tvSongCount.setText("0 首歌曲");
            return;
        }
        
        songAdapter.setSongs(currentPlaylist.getSongs());
        tvPlaylistTitle.setText(currentPlaylist.getName());
        tvSongCount.setText(currentPlaylist.getSongCount() + " 首歌曲");
    }

    private void loadPlaylistSongs(String playlistName) {
        if (miPlaylistList != null && !miPlaylistList.isEmpty()) {
            // MiMusic 模式
            loadMiMusicPlaylistSongs(playlistName);
        } else if (listData != null) {
            // 洛雪音乐模式
            Playlist targetPlaylist = null;

            Playlist defaultPlaylist = listData.getDefaultPlaylist();
            if (defaultPlaylist != null && defaultPlaylist.getName().equals(playlistName)) {
                targetPlaylist = defaultPlaylist;
            }

            if (targetPlaylist == null) {
                Playlist lovePlaylist = listData.getLovePlaylist();
                if (lovePlaylist != null && lovePlaylist.getName().equals(playlistName)) {
                    targetPlaylist = lovePlaylist;
                }
            }

            if (targetPlaylist == null && listData.getUserList() != null) {
                for (Playlist playlist : listData.getUserList()) {
                    if (playlist.getName().equals(playlistName)) {
                        targetPlaylist = playlist;
                        break;
                    }
                }
            }

            if (targetPlaylist != null) {
                currentPlaylist = targetPlaylist;
                updateSongList();
            }
        }
    }

    private void loadMiMusicPlaylistSongs(String playlistName) {
        if (miPlaylistList == null) return;

        MiPlaylist targetMiPlaylist = null;
        for (MiPlaylist playlist : miPlaylistList) {
            if (playlist.getName().equals(playlistName)) {
                targetMiPlaylist = playlist;
                break;
            }
        }

        if (targetMiPlaylist == null) return;

        // 保存当前 MiPlaylist 引用用于删除操作
        currentMiPlaylist = targetMiPlaylist;

        LxApiService apiService = LxRetrofitClient.getMiMusicAuthService(this);
        if (apiService == null) return;

        // 保存当前选中的 Playlist 用于播放
        currentPlaylist = targetMiPlaylist.toPlaylist();

        tvPlaylistTitle.setText(targetMiPlaylist.getName());
        tvSongCount.setText(targetMiPlaylist.getSongCount() + " 首歌曲");
        songAdapter.setSongs(null);

        apiService.getMiMusicPlaylistSongs(targetMiPlaylist.getId(), 500, 0)
                .enqueue(new Callback<MiPlaylistSongsResponse>() {
                    @Override
                    public void onResponse(Call<MiPlaylistSongsResponse> call, Response<MiPlaylistSongsResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<MusicInfo> songs = new ArrayList<>();
                            List<MiSong> miSongs = response.body().getSongs();
                            if (miSongs != null) {
                                // 按 updatedAt 降序排列，新收藏的在最上面
                                java.util.Collections.sort(miSongs, (a, b) -> {
                                    String aTime = a.getUpdatedAt();
                                    String bTime = b.getUpdatedAt();
                                    if (aTime == null && bTime == null) return 0;
                                    if (aTime == null) return 1;
                                    if (bTime == null) return -1;
                                    return bTime.compareTo(aTime);
                                });

                                String accessToken = LxRetrofitClient.getMiAccessToken(LibraryActivity.this);
                                for (MiSong miSong : miSongs) {
                                    MusicInfo musicInfo = miSong.toMusicInfo();
                                    // 本地歌曲：更新封面地址为带 token 的完整 URL
                                    if ("local".equals(miSong.getType()) && miSong.getCoverPath() != null && !miSong.getCoverPath().isEmpty()) {
                                        String coverUrl = MiMusicPlayerHelper.buildCoverUrl(LibraryActivity.this, miSong.getCoverPath(), accessToken);
                                        musicInfo.setPicUrl(coverUrl);
                                    }
                                    songs.add(musicInfo);
                                }
                            }
                            currentPlaylist.setSongs(songs);
                            currentPlaylist.setSongCount(songs.size());
                            updateSongList();

                            // 更新播放列表适配器的数据
                            java.util.Map<String, List<String>> playlistData = playlistAdapter.getData();
                            if (playlistData != null) {
                                List<String> songNames = new ArrayList<>();
                                for (MusicInfo song : songs) {
                                    songNames.add(song.getName());
                                }
                                playlistData.put(playlistName, songNames);
                                playlistAdapter.notifyPlaylistUpdated(playlistName, songNames);
                            }
                        } else {
                            Toast.makeText(LibraryActivity.this, "加载歌曲失败", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<MiPlaylistSongsResponse> call, Throwable t) {
                        Toast.makeText(LibraryActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void playSongAtIndex(int index) {
        if (currentPlaylist == null || currentPlaylist.getSongs() == null || player == null) return;
        if (index < 0 || index >= currentPlaylist.getSongs().size()) return;

        // 检测当前接口模式
        boolean isMiMusicMode = LxRetrofitClient.API_TYPE_MiMusic.equals(LxRetrofitClient.getApiType(this));

        // 检查数据兼容性：如果当前是 LXServer 模式但歌单是 MiMusic 格式，提示刷新
        if (!isMiMusicMode && currentPlaylist.isMiMusicSource()) {
            Toast.makeText(this, "歌单数据来自 MiMusic 模式，请重新获取歌单", Toast.LENGTH_LONG).show();
            return;
        }

        String accessToken = isMiMusicMode ? LxRetrofitClient.getMiAccessToken(this) : null;

        List<MediaItem> mediaItems = new ArrayList<>();
        for (MusicInfo song : currentPlaylist.getSongs()) {
            MediaItem item;
            if (isMiMusicMode && song.getSource() != null && "mimusic".equals(song.getSource())) {
                // MiMusic 用户歌单歌曲，使用专用播放方法
                item = MiMusicPlayerHelper.createMediaItem(this, song, accessToken);
            } else {
                item = createMediaItem(song);
            }
            mediaItems.add(item);
        }

        player.setMediaItems(mediaItems, index, 0);
        player.prepare();
        player.play();

        songAdapter.setPlayingSongId(currentPlaylist.getSongs().get(index).getSongmid());
    }

    private void playAll(boolean shuffle) {
        if (currentPlaylist == null || currentPlaylist.getSongs() == null || currentPlaylist.getSongs().isEmpty()) {
            Toast.makeText(this, "没有可播放的歌曲", Toast.LENGTH_SHORT).show();
            return;
        }

        if (player == null) return;

        // 检测当前接口模式
        boolean isMiMusicMode = LxRetrofitClient.API_TYPE_MiMusic.equals(LxRetrofitClient.getApiType(this));

        // 检查数据兼容性：如果当前是 LXServer 模式但歌单是 MiMusic 格式，提示刷新
        if (!isMiMusicMode && currentPlaylist.isMiMusicSource()) {
            Toast.makeText(this, "歌单数据来自 MiMusic 模式，请重新获取歌单", Toast.LENGTH_LONG).show();
            return;
        }

        String accessToken = isMiMusicMode ? LxRetrofitClient.getMiAccessToken(this) : null;

        List<MediaItem> mediaItems = new ArrayList<>();
        for (MusicInfo song : currentPlaylist.getSongs()) {
            MediaItem item;
            if (isMiMusicMode && song.getSource() != null && "mimusic".equals(song.getSource())) {
                // MiMusic 用户歌单歌曲，使用专用播放方法
                item = MiMusicPlayerHelper.createMediaItem(this, song, accessToken);
            } else {
                item = createMediaItem(song);
            }
            mediaItems.add(item);
        }

        int startIndex = shuffle ? (int) (Math.random() * currentPlaylist.getSongs().size()) : 0;

        player.setMediaItems(mediaItems, startIndex, 0);
        player.prepare();
        player.play();
        songAdapter.setPlayingSongId(currentPlaylist.getSongs().get(startIndex).getSongmid());

        Toast.makeText(this, shuffle ? "随机播放" : "播放全部", Toast.LENGTH_SHORT).show();
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
        
        MediaItem.Builder builder = new MediaItem.Builder()
                .setMediaId(song.getSongmid())
                .setUri(resolveUri);
        
        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
                .setTitle(song.getName())
                .setArtist(song.getSinger())
                .setAlbumTitle(song.getAlbumName())
                .setExtras(extras);
        
        if (artworkUri != null) {
            metadataBuilder.setArtworkUri(artworkUri);
        }
        
        builder.setMediaMetadata(metadataBuilder.build());
        
        return builder.build();
    }

    private void showDeleteConfirmDialog(MusicInfo song, int position) {
        if (currentPlaylist == null) return;
        
        String listId = currentPlaylist.getId();
        if ("default".equals(listId) || "love".equals(listId) || "temp".equals(listId)) {
            Toast.makeText(this, "系统歌单无法删除歌曲", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String songId = song.getId();
        if (songId == null || songId.isEmpty()) {
            songId = song.getSongmid();
        }
        if (songId == null || songId.isEmpty()) {
            Toast.makeText(this, "无法获取歌曲ID", Toast.LENGTH_SHORT).show();
            return;
        }
        
        final String finalSongId = songId;
        DialogHelper.showDeleteConfirmDialog(this, song.getName(), new DialogHelper.IDialogCallback() {
            @Override
            public void onConfirm() {
                deleteSong(song, position, finalSongId);
            }

            @Override
            public void onCancel() {
            }
        });
    }

    private void deleteSong(MusicInfo song, int position, String songId) {
        if (currentPlaylist == null) return;

        String apiType = LxRetrofitClient.getApiType(this);

        if (LxRetrofitClient.API_TYPE_MiMusic.equals(apiType) && currentMiPlaylist != null) {
            // MiMusic 模式：使用 DELETE /playlists/{playlistId}/songs/{songId}
            deleteSongMiMusic(song, songId);
        } else {
            // 洛雪音乐模式：获取完整列表，修改后保存
            deleteSongLxMusic(song, songId);
        }
    }

    private void deleteSongMiMusic(MusicInfo song, String songId) {
        LxApiService apiService = LxRetrofitClient.getMiMusicAuthService(this);
        if (apiService == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        // 将字符串 songId 转换为整数
        int songIdInt;
        try {
            songIdInt = Integer.parseInt(songId);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "无法获取歌曲ID", Toast.LENGTH_SHORT).show();
            return;
        }

        int playlistId = currentMiPlaylist.getId();

        apiService.removeSongFromPlaylist(playlistId, songIdInt).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(LibraryActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                        loadUserData();
                    } else {
                        Toast.makeText(LibraryActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                runOnUiThread(() -> Toast.makeText(LibraryActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void deleteSongLxMusic(MusicInfo song, String songId) {
        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        LxApiService apiService = LxRetrofitClient.getLxAuthService(this);

        apiService.getUserList(username, password, token).enqueue(new Callback<ListData>() {
            @Override
            public void onResponse(Call<ListData> call, Response<ListData> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> Toast.makeText(LibraryActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show());
                    return;
                }

                ListData listData = response.body();
                List<Playlist> userPlaylists = listData.getUserList();
                if (userPlaylists == null) {
                    runOnUiThread(() -> Toast.makeText(LibraryActivity.this, "歌单不存在", Toast.LENGTH_SHORT).show());
                    return;
                }

                Playlist targetPlaylist = null;
                for (Playlist p : userPlaylists) {
                    if (currentPlaylist.getName().equals(p.getName())) {
                        targetPlaylist = p;
                        break;
                    }
                }

                if (targetPlaylist == null) {
                    runOnUiThread(() -> Toast.makeText(LibraryActivity.this, "歌单不存在", Toast.LENGTH_SHORT).show());
                    return;
                }

                List<MusicInfo> songList = targetPlaylist.getSongs();
                if (songList == null) {
                    runOnUiThread(() -> Toast.makeText(LibraryActivity.this, "歌单为空", Toast.LENGTH_SHORT).show());
                    return;
                }

                boolean removed = false;
                for (int i = 0; i < songList.size(); i++) {
                    MusicInfo m = songList.get(i);
                    String mId = m.getId();
                    if (mId == null || mId.isEmpty()) mId = m.getSongmid();
                    if (songId.equals(mId)) {
                        songList.remove(i);
                        removed = true;
                        break;
                    }
                }

                if (!removed) {
                    runOnUiThread(() -> Toast.makeText(LibraryActivity.this, "歌曲不存在", Toast.LENGTH_SHORT).show());
                    return;
                }

                targetPlaylist.setSongs(songList);
                targetPlaylist.setSongCount(songList.size());

                apiService.updateUserList(username, password, token, listData).enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        runOnUiThread(() -> {
                            if (response.isSuccessful()) {
                                Toast.makeText(LibraryActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                                loadUserData();
                            } else {
                                Toast.makeText(LibraryActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        runOnUiThread(() -> Toast.makeText(LibraryActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            }

            @Override
            public void onFailure(Call<ListData> call, Throwable t) {
                runOnUiThread(() -> Toast.makeText(LibraryActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void logout() {
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        if (floatingPlayerWindow != null) {
            floatingPlayerWindow.release();
            floatingPlayerWindow = null;
        }
        // MiMusic 模式下清除所有配置（等价于设置界面的清除缓存），lxserver 模式下只清除用户信息
        if (LxRetrofitClient.isMiMusicApi(this)) {
            LxRetrofitClient.clearConfig(this);
        } else {
            LxRetrofitClient.clearUserInfo(this);
        }
        Intent intent = new Intent(this, MainActivity.class);
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
                player.addListener(new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        runOnUiThread(() -> {
                            songAdapter.setPlayerPlaying(isPlaying);
                        });
                    }
                });
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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            View currentFocus = getCurrentFocus();
            if (currentFocus != null && floatingPlayerWindow != null) {
                if (floatingPlayerWindow.handleLeftKey(currentFocus)) {
                    return true;
                }
            }
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
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
    }
}
