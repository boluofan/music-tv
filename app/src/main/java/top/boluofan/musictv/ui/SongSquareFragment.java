package top.boluofan.musictv.ui;

import android.content.Intent;
import android.os.Bundle;
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
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.R;
import top.boluofan.musictv.FloatingPlayerWindow;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.MiPlaylist;
import top.boluofan.musictv.api.model.MiPlaylistListResponse;
import top.boluofan.musictv.api.model.Playlist;
import top.boluofan.musictv.ui.adapter.SquarePlaylistAdapter;

import java.util.ArrayList;
import java.util.List;

public class SongSquareFragment extends Fragment {
    private static final String TAG = "SongSquareFragment";
    
    private RecyclerView rvSourceList;
    private RecyclerView rvPlaylists;
    private ProgressBar loadingProgress;
    
    private List<Playlist> playlists = new ArrayList<>();
    private String currentSource = "mg";
    private int currentSourceIndex = 0;
    
    private final String[] SOURCES = {"mg", "kw", "kg", "tx", "wy"};
    private final String[] SOURCE_NAMES = {"小蜜", "小窝", "小枸", "小秋", "小芸"};
    
    private SquarePlaylistAdapter playlistAdapter;
    private int currentPage = 1;
    private boolean hasMore = true;
    private boolean isLoading = false;
    private boolean isLoadingMore = false;
    
    private FloatingPlayerWindow floatingPlayerWindow;
    private GridLayoutManager gridLayoutManager;
    private int spanCount = 6;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_song_square, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        setupRecyclerViews();
        
        if (getActivity() != null) {
            floatingPlayerWindow = new FloatingPlayerWindow(getActivity());
            floatingPlayerWindow.connectToService();
        }
        
        loadSources();
    }

    private void initViews(View view) {
        rvSourceList = view.findViewById(R.id.rvSourceList);
        rvPlaylists = view.findViewById(R.id.rvPlaylists);
        loadingProgress = view.findViewById(R.id.loadingProgress);
    }

    private void setupRecyclerViews() {
        rvSourceList.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        
        androidx.recyclerview.widget.RecyclerView.Adapter<SourceViewHolder> sourceAdapter = 
                new androidx.recyclerview.widget.RecyclerView.Adapter<SourceViewHolder>() {
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
        };
        
        rvSourceList.setAdapter(sourceAdapter);
        
        playlistAdapter = new SquarePlaylistAdapter();
        rvPlaylists.setAdapter(playlistAdapter);
        gridLayoutManager = new GridLayoutManager(requireContext(), spanCount);
        rvPlaylists.setLayoutManager(gridLayoutManager);
        
        rvPlaylists.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null || isLoadingMore || !hasMore) return;
                
                int spanCount = gridLayoutManager.getSpanCount();
                int totalItemCount = layoutManager.getItemCount();
                int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
                
                if (dy <= 0) return;
                
                if (lastVisiblePosition >= totalItemCount - spanCount * 2) {
                    loadMorePlaylists();
                }
            }
        });
        
        rvPlaylists.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                View bottomNav = getActivity().findViewById(R.id.bottomNav);
                if (bottomNav != null) {
                    bottomNav.requestFocus();
                }
            }
        });
        
        playlistAdapter.setOnItemClickListener(playlist -> {
            Intent intent = new Intent(requireContext(), PlaylistDetailActivity.class);
            intent.putExtra("playlist_id", playlist.getId());
            intent.putExtra("playlist_name", playlist.getName());
            intent.putExtra("playlist_source", currentSource);
            intent.putExtra("playlist_cover", playlist.getCoverUrl());
            startActivity(intent);
        });
        
        rvSourceList.post(() -> {
            if (rvSourceList.getChildCount() > 0) {
                rvSourceList.getChildAt(0).requestFocus();
            }
        });
    }
    
    private void selectSource(int position) {
        if (position < 0 || position >= SOURCES.length) return;
        
        currentSourceIndex = position;
        String newSource = SOURCES[position];
        
        currentSource = newSource;
        currentPage = 1;
        hasMore = true;
        playlists.clear();
        
        if (playlistAdapter != null) {
            playlistAdapter.notifyDataSetChanged();
        }
        
        if (rvSourceList.getAdapter() != null) {
            rvSourceList.getAdapter().notifyDataSetChanged();
        }
        
        loadPlaylists();
        
        rvSourceList.post(() -> {
            if (rvSourceList.getChildCount() > position) {
                View itemView = rvSourceList.getChildAt(position);
                if (itemView != null) {
                    itemView.requestFocus();
                }
            }
        });
    }
    
    private void loadSources() {
        // MiMusic 模式下，SongSquare 不加载用户歌单（用户歌单在"我的歌单"中加载）
        // SongSquare 只展示各平台歌单广场
        selectSource(0);
    }

    private void loadPlaylists() {
        if (isLoading) return;
        isLoading = true;
        showLoading(true);

        // 检查是否是 MiMusic 模式
        if ("mimusic".equals(currentSource)) {
            loadMiMusicPlaylists();
            return;
        }

        LxApiService apiService = LxRetrofitClient.getApiService(requireContext());
        apiService.getSongListList(currentSource, "", "hot", currentPage).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                isLoading = false;
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String bodyStr = response.body().string();
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        SongListResult result = gson.fromJson(bodyStr, SongListResult.class);
                        if (result != null && result.getList() != null) {
                            if (currentPage == 1) {
                                playlists.clear();
                            }
                            hasMore = result.getList().size() >= 20;
                            playlists.addAll(result.getList());
                            updatePlaylistList();
                        }
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "解析失败", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                }
                
                rvSourceList.post(() -> {
                    if (rvSourceList.getChildCount() > currentSourceIndex) {
                        View itemView = rvSourceList.getChildAt(currentSourceIndex);
                        if (itemView != null && itemView.isFocusable()) {
                            itemView.requestFocus();
                        }
                    }
                });
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                isLoading = false;
                showLoading(false);
                Toast.makeText(requireContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadMiMusicPlaylists() {
        LxApiService apiService = LxRetrofitClient.getMiMusicApiService(requireContext());
        if (apiService == null) {
            isLoading = false;
            showLoading(false);
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        apiService.getMiMusicPlaylists(100, (currentPage - 1) * 100).enqueue(new Callback<MiPlaylistListResponse>() {
            @Override
            public void onResponse(Call<MiPlaylistListResponse> call, Response<MiPlaylistListResponse> response) {
                isLoading = false;
                showLoading(false);
                if (response.isSuccessful() && response.body() != null && response.body().getPlaylists() != null) {
                    List<MiPlaylist> miPlaylists = response.body().getPlaylists();
                    if (currentPage == 1) {
                        playlists.clear();
                    }
                    hasMore = miPlaylists.size() >= 100;
                    for (MiPlaylist miPlaylist : miPlaylists) {
                        playlists.add(miPlaylist.toPlaylist());
                    }
                    updatePlaylistList();
                } else {
                    Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<MiPlaylistListResponse> call, Throwable t) {
                isLoading = false;
                showLoading(false);
                Toast.makeText(requireContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadMorePlaylists() {
        if (isLoadingMore || !hasMore) return;

        // MiMusic 模式使用分页加载
        if ("mimusic".equals(currentSource)) {
            loadMoreMiMusicPlaylists();
            return;
        }

        isLoadingMore = true;
        currentPage++;

        playlistAdapter.setShowFooter(true);

        LxApiService apiService = LxRetrofitClient.getApiService(requireContext());
        apiService.getSongListList(currentSource, "", "hot", currentPage).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                isLoadingMore = false;
                playlistAdapter.setShowFooter(false);
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String bodyStr = response.body().string();
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        SongListResult result = gson.fromJson(bodyStr, SongListResult.class);
                        if (result != null && result.getList() != null) {
                            hasMore = result.getList().size() >= 20;
                            playlists.addAll(result.getList());
                            playlistAdapter.addData(result.getList());
                        }
                    } catch (Exception e) {
                        currentPage--;
                        Toast.makeText(requireContext(), "解析失败", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    currentPage--;
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                isLoadingMore = false;
                playlistAdapter.setShowFooter(false);
                currentPage--;
            }
        });
    }

    private void loadMoreMiMusicPlaylists() {
        if (isLoadingMore || !hasMore) return;
        isLoadingMore = true;
        currentPage++;

        playlistAdapter.setShowFooter(true);

        LxApiService apiService = LxRetrofitClient.getMiMusicApiService(requireContext());
        if (apiService == null) {
            isLoadingMore = false;
            playlistAdapter.setShowFooter(false);
            currentPage--;
            return;
        }

        apiService.getMiMusicPlaylists(100, (currentPage - 1) * 100).enqueue(new Callback<MiPlaylistListResponse>() {
            @Override
            public void onResponse(Call<MiPlaylistListResponse> call, Response<MiPlaylistListResponse> response) {
                isLoadingMore = false;
                playlistAdapter.setShowFooter(false);
                if (response.isSuccessful() && response.body() != null && response.body().getPlaylists() != null) {
                    List<MiPlaylist> miPlaylists = response.body().getPlaylists();
                    hasMore = miPlaylists.size() >= 100;
                    List<Playlist> convertedPlaylists = new ArrayList<>();
                    for (MiPlaylist miPlaylist : miPlaylists) {
                        convertedPlaylists.add(miPlaylist.toPlaylist());
                    }
                    playlists.addAll(convertedPlaylists);
                    playlistAdapter.addData(convertedPlaylists);
                } else {
                    currentPage--;
                }
            }

            @Override
            public void onFailure(Call<MiPlaylistListResponse> call, Throwable t) {
                isLoadingMore = false;
                playlistAdapter.setShowFooter(false);
                currentPage--;
            }
        });
    }

    private void updatePlaylistList() {
        playlistAdapter.setData(playlists);
    }

    private void showLoading(boolean show) {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
    
    private static class SongListResult {
        private List<Playlist> list;
        
        public List<Playlist> getList() { return list; }
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
    
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (rvPlaylists != null && gridLayoutManager != null) {
                View focusedView = rvPlaylists.findFocus();
                if (focusedView != null) {
                    int position = gridLayoutManager.getPosition(focusedView);
                    if (position != RecyclerView.NO_POSITION && position % spanCount == spanCount - 1) {
                        View tabSettings = getActivity().findViewById(R.id.tabSettings);
                        if (tabSettings != null) {
                            return tabSettings.requestFocus();
                        }
                    }
                }
            }
        }
        
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (rvPlaylists != null && gridLayoutManager != null) {
                View focusedView = rvPlaylists.findFocus();
                if (focusedView != null) {
                    int position = gridLayoutManager.getPosition(focusedView);
                    if (position != RecyclerView.NO_POSITION && position % spanCount == 0) {
                        if (floatingPlayerWindow != null && floatingPlayerWindow.getContainer().getVisibility() == View.VISIBLE) {
                            if (floatingPlayerWindow.requestFocus()) {
                                return true;
                            }
                        }
                        View fragmentView = getView();
                        if (fragmentView != null) {
                            RecyclerView sourceListRv = fragmentView.findViewById(R.id.rvSourceList);
                            if (sourceListRv != null) {
                                RecyclerView.LayoutManager sourceLm = sourceListRv.getLayoutManager();
                                if (sourceLm != null) {
                                    View firstSource = sourceLm.findViewByPosition(0);
                                    if (firstSource != null) {
                                        firstSource.setFocusable(true);
                                        return firstSource.requestFocus();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return false;
    }
}
