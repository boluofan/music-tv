package top.boluofan.musictv.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * MiMusic 歌单列表项模型，匹配 /api/v1/playlists 接口返回的单个歌单结构
 */
public class MiPlaylist {

    @SerializedName("id")
    private Integer id;

    @SerializedName("type")
    private String type;

    @SerializedName("name")
    private String name;

    @SerializedName("description")
    private String description;

    @SerializedName("cover_path")
    private String coverPath;

    @SerializedName("cover_url")
    private String coverUrl;

    @SerializedName("labels")
    private List<String> labels;

    @SerializedName("song_count")
    private Integer songCount;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("updated_at")
    private String updatedAt;

    // 转换为 Playlist
    public Playlist toPlaylist() {
        Playlist playlist = new Playlist();
        playlist.setId(String.valueOf(id));
        playlist.setName(name);
        playlist.setDesc(description != null ? description : "");
        playlist.setPicUrl(coverUrl != null ? coverUrl : (coverPath != null ? coverPath : ""));
        playlist.setSongCount(songCount != null ? songCount : 0);
        playlist.setSource("mimusic");
        return playlist;
    }

    public Integer getId() { return id; }
    public String getType() { return type; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCoverPath() { return coverPath; }
    public String getCoverUrl() { return coverUrl; }
    public List<String> getLabels() { return labels; }
    public Integer getSongCount() { return songCount; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}
