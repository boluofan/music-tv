package top.boluofan.musictv.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import okhttp3.ResponseBody;
import top.boluofan.musictv.MusicService;
import top.boluofan.musictv.PlayerActivity;
import top.boluofan.musictv.R;
import top.boluofan.musictv.util.DialogHelper;
import top.boluofan.musictv.FloatingPlayerWindow;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.ui.adapter.LxMusicAdapter;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RankingActivity extends AppCompatActivity {
    private static final String TAG = "RankingActivity";
    
    private RecyclerView rvSourceList;
    private RecyclerView rvBoards;
    private RecyclerView rvSongs;
    private ImageButton btnBack;
    private ImageButton btnPlayAll;
    private ImageButton btnShuffle;
    private ImageButton btnFavorite;
    private ProgressBar loadingProgress;
    
    private String currentSource = "tx";
    private int currentSourceIndex = 0;
    private String currentBoardId = "";
    private int currentBoardIndex = 0;
    
    private final String[] SOURCES = {"tx", "mg", "kw", "kg", "wy"};
    private final String[] SOURCE_NAMES = {"小秋", "小蜜", "小窝", "小枸", "小芸"};
    
    private List<BoardInfo> boards = new ArrayList<>();
    private List<MusicInfo> songs = new ArrayList<>();
    
    private LxMusicAdapter songAdapter;
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private FloatingPlayerWindow floatingPlayerWindow;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable positionUpdater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ranking);
        
        initViews();
        setupRecyclerViews();
        setupListeners();
        
        loadBoards();
    }

    private void initViews() {
        rvSourceList = findViewById(R.id.rvSourceList);
        rvBoards = findViewById(R.id.rvBoards);
        rvSongs = findViewById(R.id.rvSongs);
        btnBack = findViewById(R.id.btnBack);
        btnPlayAll = findViewById(R.id.btnPlayAll);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnFavorite = findViewById(R.id.btnFavorite);
        loadingProgress = findViewById(R.id.loadingProgress);
        
        btnBack.setOnClickListener(v -> finish());
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
        
        rvBoards.setLayoutManager(new LinearLayoutManager(this));
        rvBoards.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<BoardViewHolder>() {
            @NonNull
            @Override
            public BoardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = getLayoutInflater().inflate(R.layout.item_ranking_board, parent, false);
                return new BoardViewHolder(view);
            }
            
            @Override
            public void onBindViewHolder(@NonNull BoardViewHolder holder, int position) {
                BoardInfo board = boards.get(position);
                holder.tv.setText(board.name);
                holder.tv.setTag(position);
            }
            
            @Override
            public int getItemCount() {
                return boards.size();
            }
        });

        rvBoards.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && boards.size() > 0 && currentBoardIndex >= 0 && currentBoardIndex < boards.size()) {
                rvBoards.post(() -> {
                    if (rvBoards.getChildCount() > currentBoardIndex) {
                        rvBoards.getChildAt(currentBoardIndex).requestFocus();
                    } else if (rvBoards.getChildCount() > 0) {
                        rvBoards.getChildAt(0).requestFocus();
                    }
                });
            }
        });

        rvSongs.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && boards.size() > 0 && currentBoardIndex >= 0 && currentBoardIndex < boards.size()) {
                rvBoards.post(() -> {
                    for (int i = 0; i < rvBoards.getChildCount(); i++) {
                        rvBoards.getChildAt(i).setSelected(i == currentBoardIndex);
                    }
                });
            }
        });
        
        songAdapter = new LxMusicAdapter();
        rvSongs.setAdapter(songAdapter);
        rvSongs.setLayoutManager(new LinearLayoutManager(this));

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
        
        rvSourceList.post(() -> {
            if (rvSourceList.getChildCount() > 0) {
                rvSourceList.getChildAt(0).requestFocus();
            }
        });
    }
    
    private void setupListeners() {
        btnPlayAll.setOnClickListener(v -> playAll(false));
        btnShuffle.setOnClickListener(v -> playAll(true));
        btnFavorite.setOnClickListener(v -> collectPlaylist());
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
        
        apiService.getUserList(username, password,token).enqueue(new Callback<top.boluofan.musictv.api.model.ListData>() {
            @Override
            public void onResponse(Call<top.boluofan.musictv.api.model.ListData> call, Response<top.boluofan.musictv.api.model.ListData> response) {
                btnFavorite.setEnabled(true);
                
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(RankingActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                top.boluofan.musictv.api.model.ListData listData = response.body();
                
                String boardName = currentBoardId.isEmpty() ? SOURCE_NAMES[currentSourceIndex] + "排行榜" : boards.get(currentBoardIndex).name;
                
                top.boluofan.musictv.api.model.Playlist existingPlaylist = null;
                if (listData.getUserList() != null) {
                    for (top.boluofan.musictv.api.model.Playlist p : listData.getUserList()) {
                        if (boardName.equals(p.getName())) {
                            existingPlaylist = p;
                            break;
                        }
                    }
                }
                
                if (existingPlaylist != null) {
                    final top.boluofan.musictv.api.model.ListData finalListData = listData;
                    final top.boluofan.musictv.api.model.Playlist finalExistingPlaylist = existingPlaylist;
                    final String finalBoardName = boardName;
                    android.content.Context ctx = RankingActivity.this;
                    DialogHelper.showOverwriteConfirmDialog(ctx, boardName, new DialogHelper.IDialogCallback() {
                        @Override
                        public void onConfirm() {
                            doCollectPlaylist(finalListData, finalExistingPlaylist, finalBoardName);
                        }

                        @Override
                        public void onCancel() {
                        }
                    });
                } else {
                    doCollectPlaylist(listData, null, boardName);
                }
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.ListData> call, Throwable t) {
                btnFavorite.setEnabled(true);
                Toast.makeText(RankingActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void doCollectPlaylist(top.boluofan.musictv.api.model.ListData listData, top.boluofan.musictv.api.model.Playlist existingPlaylist, String boardName) {
        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);
        LxApiService apiService = LxRetrofitClient.getApiService(this);
        
        top.boluofan.musictv.api.model.Playlist newPlaylist;
        if (existingPlaylist != null) {
            newPlaylist = existingPlaylist;
            newPlaylist.setSongs(new ArrayList<>(songs));
            newPlaylist.setSongCount(songs.size());
            newPlaylist.setSource(currentSource);
            newPlaylist.setSourceListId(currentBoardId);
        } else {
            newPlaylist = new top.boluofan.musictv.api.model.Playlist();
            newPlaylist.setId("playlist_" + System.currentTimeMillis());
            newPlaylist.setName(boardName);
            newPlaylist.setSource(currentSource);
            newPlaylist.setSourceListId(currentBoardId);
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
                    Toast.makeText(RankingActivity.this, existingPlaylist != null ? "覆盖成功" : "收藏成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(RankingActivity.this, "收藏失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                btnFavorite.setEnabled(true);
                Toast.makeText(RankingActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void collectPlaylistMiMusic() {
        LxApiService apiService = LxRetrofitClient.getMiMusicApiService(this);
        if (apiService == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, top.boluofan.musictv.ConfigActivity.class));
            return;
        }

        btnFavorite.setEnabled(false);

        String boardName = currentBoardId.isEmpty() ? SOURCE_NAMES[currentSourceIndex] + "排行榜" : boards.get(currentBoardIndex).name;
        final String finalBoardName = boardName;

        apiService.getMiMusicPlaylists(100, 0).enqueue(new Callback<top.boluofan.musictv.api.model.MiPlaylistListResponse>() {
            @Override
            public void onResponse(Call<top.boluofan.musictv.api.model.MiPlaylistListResponse> call, Response<top.boluofan.musictv.api.model.MiPlaylistListResponse> response) {
                btnFavorite.setEnabled(true);

                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(RankingActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                top.boluofan.musictv.api.model.MiPlaylist existingPlaylist = null;
                final top.boluofan.musictv.api.model.MiPlaylist finalExistingPlaylist = existingPlaylist;
                List<top.boluofan.musictv.api.model.MiPlaylist> playlists = response.body().getPlaylists();
                if (playlists != null) {
                    for (top.boluofan.musictv.api.model.MiPlaylist p : playlists) {
                        if (finalBoardName.equals(p.getName())) {
                            existingPlaylist = p;
                            break;
                        }
                    }
                }

                if (existingPlaylist != null) {
                    final int playlistId = existingPlaylist.getId();
                    DialogHelper.showOverwriteConfirmDialog(RankingActivity.this, finalBoardName, new DialogHelper.IDialogCallback() {
                        @Override
                        public void onConfirm() {
                            importPlaylistToMiMusic(playlistId, true, finalBoardName);
                        }

                        @Override
                        public void onCancel() {
                        }
                    });
                } else {
                    importPlaylistToMiMusic(0, false, finalBoardName);
                }
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.MiPlaylistListResponse> call, Throwable t) {
                btnFavorite.setEnabled(true);
                Toast.makeText(RankingActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void importPlaylistToMiMusic(int existPlaylistId, boolean isOverwrite, String playlistName) {
        LxApiService apiService = LxRetrofitClient.getMiMusicApiService(this);
        if (apiService == null) return;

        Map<String, Object> body = buildImportBody(isOverwrite ? existPlaylistId : 0, playlistName, songs);

        btnFavorite.setEnabled(false);
        apiService.importSongsToPlaylist(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                btnFavorite.setEnabled(true);
                if (response.isSuccessful()) {
                    Toast.makeText(RankingActivity.this, isOverwrite ? "覆盖成功" : "收藏成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(RankingActivity.this, "收藏失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                btnFavorite.setEnabled(true);
                Toast.makeText(RankingActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(RankingActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                top.boluofan.musictv.api.model.ListData listData = response.body();
                List<top.boluofan.musictv.api.model.Playlist> userPlaylists = listData.getUserList();
                if (userPlaylists == null || userPlaylists.isEmpty()) {
                    Toast.makeText(RankingActivity.this, "暂无歌单，请先在歌单库创建歌单", Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] playlistNames = new String[userPlaylists.size()];
                for (int i = 0; i < userPlaylists.size(); i++) {
                    playlistNames[i] = userPlaylists.get(i).getName();
                }

                final MusicInfo finalSong = song;
                DialogHelper.showPlaylistPickerDialog(RankingActivity.this, "选择歌单", playlistNames, (android.content.DialogInterface dialog, int which) -> {
                    fetchAndAddSongToPlaylist(userPlaylists.get(which).getName(), finalSong);
                });
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.ListData> call, Throwable t) {
                Toast.makeText(RankingActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void collectSingleSongMiMusic(MusicInfo song) {
        LxApiService apiService = LxRetrofitClient.getMiMusicApiService(this);
        if (apiService == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, top.boluofan.musictv.ConfigActivity.class));
            return;
        }

        apiService.getMiMusicPlaylists(100, 0).enqueue(new Callback<top.boluofan.musictv.api.model.MiPlaylistListResponse>() {
            @Override
            public void onResponse(Call<top.boluofan.musictv.api.model.MiPlaylistListResponse> call, Response<top.boluofan.musictv.api.model.MiPlaylistListResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(RankingActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                List<top.boluofan.musictv.api.model.MiPlaylist> playlists = response.body().getPlaylists();
                if (playlists == null || playlists.isEmpty()) {
                    Toast.makeText(RankingActivity.this, "暂无歌单，请先在歌单库创建歌单", Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] playlistNames = new String[playlists.size()];
                for (int i = 0; i < playlists.size(); i++) {
                    playlistNames[i] = playlists.get(i).getName();
                }

                final MusicInfo finalSong = song;
                DialogHelper.showPlaylistPickerDialog(RankingActivity.this, "选择歌单", playlistNames, (android.content.DialogInterface dialog, int which) -> {
                    importSongToMiMusic(playlists.get(which).getId(), finalSong);
                });
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.MiPlaylistListResponse> call, Throwable t) {
                Toast.makeText(RankingActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void importSongToMiMusic(int playlistId, MusicInfo song) {
        LxApiService apiService = LxRetrofitClient.getMiMusicApiService(this);
        if (apiService == null) return;
        List<MusicInfo> songs = new ArrayList<>();
        songs.add(song);
        Map<String, Object> body = buildImportBody(playlistId, "", songs);
        apiService.importSongsToPlaylist(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(RankingActivity.this, "已添加到收藏", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(RankingActivity.this, "添加失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(RankingActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
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

    private void fetchAndAddSongToPlaylist(String playlistName, MusicInfo song) {
        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);
        LxApiService apiService = LxRetrofitClient.getLxAuthService(this);

        apiService.getUserList(username, password, token).enqueue(new Callback<top.boluofan.musictv.api.model.ListData>() {
            @Override
            public void onResponse(Call<top.boluofan.musictv.api.model.ListData> call, Response<top.boluofan.musictv.api.model.ListData> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(RankingActivity.this, "获取歌单失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                top.boluofan.musictv.api.model.ListData listData = response.body();
                List<top.boluofan.musictv.api.model.Playlist> userPlaylists = listData.getUserList();
                if (userPlaylists == null) {
                    Toast.makeText(RankingActivity.this, "歌单不存在", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(RankingActivity.this, "歌单不存在", Toast.LENGTH_SHORT).show();
                    return;
                }

                addSongToPlaylist(listData, targetPlaylist, song);
            }

            @Override
            public void onFailure(Call<top.boluofan.musictv.api.model.ListData> call, Throwable t) {
                Toast.makeText(RankingActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(RankingActivity.this, "已添加到「" + playlist.getName() + "」", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(RankingActivity.this, "添加失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                Toast.makeText(RankingActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void selectSource(int position) {
        if (position < 0 || position >= SOURCES.length) return;
        
        currentSourceIndex = position;
        currentSource = SOURCES[position];
        currentBoardId = "";
        currentBoardIndex = 0;
        boards.clear();
        songs.clear();
        
        if (rvBoards.getAdapter() != null) {
            rvBoards.getAdapter().notifyDataSetChanged();
        }
        if (songAdapter != null) {
            songAdapter.notifyDataSetChanged();
        }
        
        if (rvSourceList.getAdapter() != null) {
            rvSourceList.getAdapter().notifyDataSetChanged();
        }
        
        loadBoards();
        
        rvSourceList.post(() -> {
            if (rvSourceList.getChildCount() > position) {
                View itemView = rvSourceList.getChildAt(position);
                if (itemView != null) {
                    itemView.requestFocus();
                }
            }
        });
    }
    
    private void selectBoard(int position) {
        if (position < 0 || position >= boards.size()) return;
        
        currentBoardIndex = position;
        currentBoardId = getBangId(boards.get(position).id);
        
        if (rvBoards.getAdapter() != null) {
            rvBoards.getAdapter().notifyDataSetChanged();
        }
        
        loadSongs();
        
        rvBoards.post(() -> {
            for (int i = 0; i < rvBoards.getChildCount(); i++) {
                rvBoards.getChildAt(i).setSelected(i == position);
            }
            if (rvBoards.getChildCount() > position) {
                View itemView = rvBoards.getChildAt(position);
                if (itemView != null) {
                    itemView.requestFocus();
                }
            }
        });
    }
    
    private String getBangId(String fullId) {
        if (fullId == null) return "";
        int index = fullId.indexOf("__");
        if (index >= 0 && index + 2 < fullId.length()) {
            return fullId.substring(index + 2);
        }
        return fullId;
    }
    
    private void loadBoards() {
        showLoading(true);
        
        LxApiService apiService = LxRetrofitClient.getApiService(this);
        apiService.getLeaderboardBoards(currentSource).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String bodyStr = response.body().string();
                        Gson gson = new Gson();
                        JsonObject root = gson.fromJson(bodyStr, JsonObject.class);
                        JsonArray list = root.getAsJsonArray("list");
                        
                        boards.clear();
                        if (list != null) {
                            for (int i = 0; i < list.size(); i++) {
                                JsonObject item = list.get(i).getAsJsonObject();
                                BoardInfo board = new BoardInfo();
                                board.id = item.get("id").getAsString();
                                board.name = item.get("name").getAsString();
                                boards.add(board);
                            }
                        }
                        
                        if (rvBoards.getAdapter() != null) {
                            rvBoards.getAdapter().notifyDataSetChanged();
                        }
                        
                        rvBoards.post(() -> {
                            if (rvBoards.getChildCount() > 0) {
                                rvBoards.getChildAt(0).requestFocus();
                            } else {
                                rvBoards.requestFocus();
                            }
                        });
                        
                        if (!boards.isEmpty()) {
                            selectBoard(0);
                        }
                    } catch (Exception e) {
                        Toast.makeText(RankingActivity.this, "解析失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(RankingActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                showLoading(false);
                Toast.makeText(RankingActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void loadSongs() {
        if (currentBoardId.isEmpty()) return;
        
        showLoading(true);
        
        LxApiService apiService = LxRetrofitClient.getApiService(this);
        apiService.getLeaderboardList(currentSource, currentBoardId, 1).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String bodyStr = response.body().string();
                        Gson gson = new Gson();
                        JsonObject root = gson.fromJson(bodyStr, JsonObject.class);
                        JsonArray list = root.getAsJsonArray("list");
                        
                        songs.clear();
                        if (list != null) {
                            for (int i = 0; i < list.size(); i++) {
                                JsonObject item = list.get(i).getAsJsonObject();
                                MusicInfo music = new MusicInfo();
                                String musicId = item.has("id") ? item.get("id").getAsString() :
                                    (item.has("musicId") ? item.get("musicId").getAsString() : "");
                                music.setId(musicId);
                                music.setName(item.has("name") ? item.get("name").getAsString() : "");
                                music.setSinger(item.has("singer") ? item.get("singer").getAsString() : "");
                                music.setSource(currentSource);
                                String songmid = item.has("songmid") ? item.get("songmid").getAsString() :
                                    (item.has("musicId") ? item.get("musicId").getAsString() : "");
                                music.setSongmid(songmid);
                                music.setPicUrl(item.has("img") ? item.get("img").getAsString() : 
                                    (item.has("picUrl") ? item.get("picUrl").getAsString() : ""));
                                music.setAlbumName(item.has("album") ? item.get("album").getAsString() : "");
                                songs.add(music);
                            }
                        }
                        
                        songAdapter.setSongs(songs);
                    } catch (Exception e) {
                        Toast.makeText(RankingActivity.this, "解析失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(RankingActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                showLoading(false);
                Toast.makeText(RankingActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
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
    
    private void showLoading(boolean show) {
        loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
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
    
    private void setupPlayerListener() {
        if (player == null) return;
        
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                songAdapter.notifyDataSetChanged();
            }
            
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                songAdapter.setPlayerPlaying(isPlaying);
            }
            
            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                songAdapter.notifyDataSetChanged();
            }
        });
    }
    
    private static class BoardInfo {
        String id;
        String name;
    }
    
    private static class SourceViewHolder extends RecyclerView.ViewHolder {
        TextView tvSourceName;
        ImageView ivRadio;
        SourceViewHolder(View view) { 
            super(view);
            tvSourceName = view.findViewById(R.id.tvSourceName);
            ivRadio = view.findViewById(R.id.ivRadio);
        }
    }
    
    private class BoardViewHolder extends RecyclerView.ViewHolder {
        TextView tv;
        BoardViewHolder(View view) { 
            super(view); 
            tv = view.findViewById(R.id.tvBoardName);
            tv.setOnClickListener(v -> {
                int position = (int) tv.getTag();
                RankingActivity.this.selectBoard(position);
            });
        }
    }
}
