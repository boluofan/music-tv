package top.boluofan.musictv.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * MiMusic 歌单列表响应模型，匹配 /api/v1/playlists 接口返回
 */
public class MiPlaylistListResponse {

    @SerializedName("limit")
    private Integer limit;

    @SerializedName("offset")
    private Integer offset;

    @SerializedName("playlists")
    private List<MiPlaylist> playlists;

    public Integer getLimit() { return limit; }
    public Integer getOffset() { return offset; }
    public List<MiPlaylist> getPlaylists() { return playlists; }
}
