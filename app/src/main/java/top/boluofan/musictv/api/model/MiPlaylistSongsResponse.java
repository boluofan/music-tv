package top.boluofan.musictv.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * MiMusic 歌单歌曲列表响应，匹配 /api/v1/playlists/{id}/songs 接口返回
 */
public class MiPlaylistSongsResponse {

    @SerializedName("limit")
    private Integer limit;

    @SerializedName("offset")
    private Integer offset;

    @SerializedName("songs")
    private List<MiSong> songs;

    @SerializedName("total")
    private Integer total;

    public Integer getLimit() { return limit; }
    public Integer getOffset() { return offset; }
    public List<MiSong> getSongs() { return songs; }
    public Integer getTotal() { return total; }
}
