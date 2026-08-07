package top.boluofan.musictv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.widget.Button;
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
import top.boluofan.musictv.api.model.Playlist;
import top.boluofan.musictv.ui.adapter.SquarePlaylistAdapter;

import java.util.ArrayList;
import java.util.List;

public class SongSquareFragment extends Fragment {
    private static final String TAG = "SongSquareFragment";
    private static final int TV_PAGE_SIZE = 12;
    
    private RecyclerView rvSourceList;
    private RecyclerView rvPlaylists;
    private ProgressBar loadingProgress;
    private Button btnPrevPage;
    private Button btnNextPage;
    private TextView tvPageNumber;
    
    private final List<Playlist> playlists = new ArrayList<>();
    private final List<Playlist> loadedPlaylists = new ArrayList<>();
    private String currentSource = "mg";
    private int currentSourceIndex = 0;
    
    private final String[] SOURCES = {"mg", "kw", "kg", "tx", "wy"};
    private final String[] SOURCE_NAMES = {"小蜜", "小窝", "小枸", "小秋", "小芸"};
    
    private SquarePlaylistAdapter playlistAdapter;
    private int currentPage = 1;
    private int remotePage = 1;
    private boolean remoteHasMore = true;
    private boolean isLoading = false;
    private Call<okhttp3.ResponseBody> activePageCall;
    
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
        btnPrevPage = view.findViewById(R.id.btnPrevPage);
        btnNextPage = view.findViewById(R.id.btnNextPage);
        tvPageNumber = view.findViewById(R.id.tvPageNumber);
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
        
        btnPrevPage.setOnClickListener(v -> changePage(-1));
        btnNextPage.setOnClickListener(v -> changePage(1));
        updatePager();
        
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

        if (activePageCall != null) {
            activePageCall.cancel();
            activePageCall = null;
        }
        isLoading = false;
        
        currentSourceIndex = position;
        String newSource = SOURCES[position];
        
        currentSource = newSource;
        currentPage = 1;
        remotePage = 1;
        remoteHasMore = true;
        loadedPlaylists.clear();
        playlists.clear();
        
        if (playlistAdapter != null) {
            playlistAdapter.notifyDataSetChanged();
        }
        
        if (rvSourceList.getAdapter() != null) {
            rvSourceList.getAdapter().notifyDataSetChanged();
        }
        
        loadPlaylists(false);
        
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

    private void loadPlaylists(boolean advanceAfterLoad) {
        if (isLoading) return;
        isLoading = true;
        showLoading(true);
        updatePager();

        LxApiService apiService = LxRetrofitClient.getApiService(requireContext());
        activePageCall = apiService.getSongListList(currentSource, "", "hot", remotePage);
        activePageCall.enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (!isAdded() || call != activePageCall) return;
                isLoading = false;
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String bodyStr = response.body().string();
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        SongListResult result = gson.fromJson(bodyStr, SongListResult.class);
                        if (result != null && result.getList() != null) {
                            List<Playlist> remoteItems = result.getList();
                            if (remotePage == 1) {
                                loadedPlaylists.clear();
                            }
                            loadedPlaylists.addAll(remoteItems);
                            remoteHasMore = result.hasMore(remotePage, remoteItems.size());

                            if (advanceAfterLoad) {
                                int nextStart = currentPage * TV_PAGE_SIZE;
                                if (nextStart < loadedPlaylists.size()) {
                                    currentPage++;
                                } else {
                                    remoteHasMore = false;
                                }
                            }
                            showCurrentPage();
                        }
                    } catch (Exception e) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "解析失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                }
                updatePager();
                
                if (!advanceAfterLoad) rvSourceList.post(() -> {
                    if (!isAdded() || rvSourceList == null) return;
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
                if (call.isCanceled() || !isAdded() || call != activePageCall) return;
                if (advanceAfterLoad && remotePage > 1) {
                    remotePage--;
                }
                isLoading = false;
                showLoading(false);
                Toast.makeText(requireContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                updatePager();
            }
        });
    }

    private void changePage(int offset) {
        if (isLoading) return;
        if (offset < 0) {
            if (currentPage <= 1) return;
            currentPage--;
            showCurrentPage();
            return;
        }

        int nextStart = currentPage * TV_PAGE_SIZE;
        int cachedRemaining = loadedPlaylists.size() - nextStart;
        if (cachedRemaining >= TV_PAGE_SIZE || (!remoteHasMore && cachedRemaining > 0)) {
            currentPage++;
            showCurrentPage();
        } else if (remoteHasMore) {
            remotePage++;
            loadPlaylists(true);
        }
    }

    private void showCurrentPage() {
        int start = (currentPage - 1) * TV_PAGE_SIZE;
        int end = Math.min(start + TV_PAGE_SIZE, loadedPlaylists.size());
        playlists.clear();
        if (start < end) {
            playlists.addAll(loadedPlaylists.subList(start, end));
        }
        updatePlaylistList();
        if (rvPlaylists != null) {
            rvPlaylists.scrollToPosition(0);
        }
        updatePager();
    }

    private void updatePager() {
        if (tvPageNumber != null) {
            tvPageNumber.setText("第 " + currentPage + " 页");
        }
        if (btnPrevPage != null) {
            btnPrevPage.setEnabled(!isLoading && currentPage > 1);
            btnPrevPage.setAlpha(btnPrevPage.isEnabled() ? 1.0f : 0.35f);
        }
        if (btnNextPage != null) {
            boolean hasCachedNextPage = currentPage * TV_PAGE_SIZE < loadedPlaylists.size();
            btnNextPage.setEnabled(!isLoading && (hasCachedNextPage || remoteHasMore));
            btnNextPage.setAlpha(btnNextPage.isEnabled() ? 1.0f : 0.35f);
        }
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
        private int limit;
        private int total;
        
        public List<Playlist> getList() { return list; }

        boolean hasMore(int page, int receivedCount) {
            if (limit > 0 && total > 0) {
                return page * limit < total;
            }
            if (limit > 0) {
                return receivedCount >= limit;
            }
            return receivedCount >= 20;
        }
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
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            View focusedView = rvPlaylists != null ? rvPlaylists.findFocus() : null;
            if (focusedView != null && gridLayoutManager != null && playlistAdapter != null) {
                int position = gridLayoutManager.getPosition(focusedView);
                int itemCount = playlists.size();
                int lastRowStart = itemCount == 0 ? 0 : ((itemCount - 1) / spanCount) * spanCount;
                if (position != RecyclerView.NO_POSITION && position >= lastRowStart) {
                    if (btnNextPage != null && btnNextPage.isEnabled()) {
                        return btnNextPage.requestFocus();
                    }
                    if (btnPrevPage != null && btnPrevPage.isEnabled()) {
                        return btnPrevPage.requestFocus();
                    }
                    View tabSongSquare = getActivity() != null
                            ? getActivity().findViewById(R.id.tabSongSquare)
                            : null;
                    return tabSongSquare != null && tabSongSquare.requestFocus();
                }
            }
        }

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

    @Override
    public void onDestroyView() {
        if (activePageCall != null) {
            activePageCall.cancel();
            activePageCall = null;
        }
        if (floatingPlayerWindow != null) {
            floatingPlayerWindow.release();
            floatingPlayerWindow = null;
        }
        rvSourceList = null;
        rvPlaylists = null;
        loadingProgress = null;
        btnPrevPage = null;
        btnNextPage = null;
        tvPageNumber = null;
        super.onDestroyView();
    }
}
