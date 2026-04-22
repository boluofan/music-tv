package top.boluofan.musictv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import java.util.ArrayList;
import java.util.List;
import top.boluofan.musictv.R;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.util.FocusAnimationHelper;

public class LxMusicAdapter extends RecyclerView.Adapter<LxMusicAdapter.ViewHolder> {
    private List<MusicInfo> songs = new ArrayList<>();
    private OnItemClickListener listener;
    private OnPlayClickListener playListener;
    private OnFullscreenClickListener fullscreenListener;
    private OnDeleteClickListener deleteListener;
    private OnFavClickListener favListener;
    private int playingIndex = -1;
    private boolean isPlaying = false;
    private boolean showDeleteButton = false;
    private boolean showFavButton = true;

    public interface OnItemClickListener {
        void onItemClick(MusicInfo song, int position);
    }

    public interface OnPlayClickListener {
        void onPlayClick(MusicInfo song, int position);
    }

    public interface OnFullscreenClickListener {
        void onFullscreenClick(MusicInfo song, int position);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(MusicInfo song, int position);
    }

    public interface OnFavClickListener {
        void onFavClick(MusicInfo song, int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnPlayClickListener(OnPlayClickListener listener) {
        this.playListener = listener;
    }

    public void setOnFullscreenClickListener(OnFullscreenClickListener listener) {
        this.fullscreenListener = listener;
    }

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.deleteListener = listener;
    }

    public void setOnFavClickListener(OnFavClickListener listener) {
        this.favListener = listener;
    }

    public void setShowDeleteButton(boolean show) {
        this.showDeleteButton = show;
    }

    public void setShowFavButton(boolean show) {
        this.showFavButton = show;
    }

    public void setSongs(List<MusicInfo> songs) {
        this.songs = songs != null ? songs : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setPlayingIndex(int index) {
        int oldIndex = playingIndex;
        playingIndex = index;
        if (oldIndex >= 0) notifyItemChanged(oldIndex);
        if (index >= 0) notifyItemChanged(index);
    }

    public void setPlayerPlaying(boolean playing) {
        if (isPlaying != playing) {
            isPlaying = playing;
            if (playingIndex >= 0) notifyItemChanged(playingIndex);
        }
    }

    public int getPlayingIndex() {
        return playingIndex;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MusicInfo song = songs.get(position);
        holder.bind(song, position == playingIndex, isPlaying);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(song, holder.getAdapterPosition());
            }
        });
        holder.btnPlay.setOnClickListener(v -> {
            if (playListener != null) {
                playListener.onPlayClick(song, holder.getAdapterPosition());
            }
        });
        holder.btnFullscreen.setOnClickListener(v -> {
            if (fullscreenListener != null) {
                fullscreenListener.onFullscreenClick(song, holder.getAdapterPosition());
            }
        });
        holder.btnDelete.setVisibility(showDeleteButton ? View.VISIBLE : View.GONE);
        if (showDeleteButton && deleteListener != null) {
            holder.btnDelete.setOnClickListener(v -> {
                deleteListener.onDeleteClick(song, holder.getAdapterPosition());
            });
        }
        holder.btnFav.setVisibility(showFavButton ? View.VISIBLE : View.GONE);
        if (showFavButton && favListener != null) {
            holder.btnFav.setOnClickListener(v -> {
                favListener.onFavClick(song, holder.getAdapterPosition());
            });
        }
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private static final String[] SOURCES = {"kw", "kg", "tx", "wy", "mg"};
        private static final String[] SOURCE_NAMES = {"小窝", "小枸", "小秋", "小芸", "小蜜"};
        
        private static String getSourceDisplayName(String source) {
            if (source == null || source.isEmpty()) return null;
            for (int i = 0; i < SOURCES.length; i++) {
                if (SOURCES[i].equals(source)) {
                    return SOURCE_NAMES[i];
                }
            }
            return source;
        }
        
        private final ImageView ivEqualizer;
        private final TextView tvIndex;
        private final ImageView ivCover;
        private final TextView tvName;
        private final TextView tvArtist;
        private final TextView tvSource;
        private final ImageView btnPlay;
        private final ImageView btnFullscreen;
        private final ImageView btnDelete;
        private final ImageView btnFav;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivEqualizer = itemView.findViewById(R.id.ivEqualizer);
            tvIndex = itemView.findViewById(R.id.tvIndex);
            ivCover = itemView.findViewById(R.id.ivCover);
            tvName = itemView.findViewById(R.id.tvSongName);
            tvArtist = itemView.findViewById(R.id.tvArtist);
            tvSource = itemView.findViewById(R.id.tvSource);
            btnPlay = itemView.findViewById(R.id.btnItemPlay);
            btnFullscreen = itemView.findViewById(R.id.btnItemFullscreen);
            btnDelete = itemView.findViewById(R.id.btnItemDelete);
            btnFav = itemView.findViewById(R.id.btnItemFav);
        }

        void bind(MusicInfo song, boolean isCurrentSong, boolean isPlayingNow) {
            tvName.setText(song.getName());
            tvArtist.setText(song.getSinger() != null ? song.getSinger() : "未知歌手");
            
            String sourceName = song.getSearchSource();
            if (sourceName == null || sourceName.isEmpty()) {
                sourceName = getSourceDisplayName(song.getSource());
            }
            if (sourceName != null && !sourceName.isEmpty()) {
                tvSource.setText(sourceName);
                tvSource.setVisibility(View.VISIBLE);
            } else {
                tvSource.setVisibility(View.GONE);
            }
            
            if (isCurrentSong) {
                ivEqualizer.setVisibility(View.VISIBLE);
                tvIndex.setVisibility(View.GONE);
            } else {
                ivEqualizer.setVisibility(View.GONE);
                tvIndex.setVisibility(View.VISIBLE);
                tvIndex.setText(String.valueOf(getAdapterPosition() + 1));
            }
            
            if (song.getPicUrl() != null && !song.getPicUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(song.getPicUrl())
                        .placeholder(R.drawable.ic_cover_placeholder)
                        .transform(new RoundedCorners(8))
                        .into(ivCover);
            } else {
                ivCover.setImageResource(R.drawable.ic_cover_placeholder);
            }
        }
    }
}
