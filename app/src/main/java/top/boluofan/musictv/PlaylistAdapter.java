package top.boluofan.musictv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {

    private List<String> playlists = new ArrayList<>();
    private Map<String, List<String>> data;
    private Map<String, Integer> songCounts;
    private OnItemClickListener listener;
    private int selectedPosition = 0;

    public interface OnItemClickListener {
        void onItemClick(String playlistName);
    }

    public void setData(Map<String, List<String>> data) {
        this.data = data;
        this.playlists = new ArrayList<>(data.keySet());
        // 初始化歌曲数量为空
        this.songCounts = new java.util.HashMap<>();
        notifyDataSetChanged();
    }

    public Map<String, List<String>> getData() {
        return data;
    }

    // 设置歌单的歌曲数量（用于 MiMusic）
    public void setPlaylistSongCount(String playlistName, int count) {
        if (songCounts == null) {
            songCounts = new java.util.HashMap<>();
        }
        songCounts.put(playlistName, count);
        int index = playlists.indexOf(playlistName);
        if (index != -1) {
            notifyItemChanged(index);
        }
    }

    public void setSelection(int position) {
        int oldPos = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(oldPos);
        notifyItemChanged(selectedPosition);
    }

    public void notifyPlaylistUpdated(String playlistName, List<String> newSongs) {
        if (data != null) {
            data.put(playlistName, newSongs);
            // 同步更新歌曲数量
            if (songCounts != null) {
                songCounts.put(playlistName, newSongs != null ? newSongs.size() : 0);
            }
            int index = playlists.indexOf(playlistName);
            if (index != -1) {
                notifyItemChanged(index);
            } else {
                playlists.add(playlistName);
                notifyItemInserted(playlists.size() - 1);
            }
        }
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String name = playlists.get(position);
        List<String> songs = data.get(name);
        // 优先使用单独设置的歌曲数量（MiMusic），其次使用列表大小
        Integer count = songCounts != null ? songCounts.get(name) : null;
        holder.tvName.setText(name.equals("All Songs") ? "所有歌曲" : name);
        holder.tvCount.setText(String.valueOf(count != null ? count : (songs != null ? songs.size() : 0)));

        holder.itemView.setOnClickListener(v -> {
            int oldPos = selectedPosition;
            int newPos = holder.getAdapterPosition();
            if (newPos == RecyclerView.NO_POSITION) return;

            selectedPosition = newPos;
            if (oldPos != selectedPosition) {
                notifyItemChanged(oldPos);
                notifyItemChanged(selectedPosition);
            }
            if (listener != null) listener.onItemClick(name);
        });

        // Simple selection visual
        holder.itemView.setSelected(selectedPosition == position);
        if (selectedPosition == position) {
            holder.tvName.setTextColor(0xFFFFFFFF); // White
        } else {
            holder.tvName.setTextColor(0xFF9CA3AF); // Gray
        }

        // Strict Focus Trapping for TV Remote
        holder.itemView.setNextFocusDownId(View.NO_ID);
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvCount;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvCount = itemView.findViewById(R.id.tvCount);
        }
    }
}
