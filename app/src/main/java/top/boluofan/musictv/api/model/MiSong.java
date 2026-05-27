package top.boluofan.musictv.api.model;

import android.os.Bundle;
import com.google.gson.annotations.SerializedName;

/**
 * MiMusic 歌单内歌曲模型，匹配 /api/v1/playlists/{id}/songs 接口返回的单个歌曲结构
 */
public class MiSong {

    @SerializedName("id")
    private Integer id;

    @SerializedName("type")
    private String type;

    @SerializedName("title")
    private String title;

    @SerializedName("artist")
    private String artist;

    @SerializedName("album")
    private String album;

    @SerializedName("duration")
    private Double duration;

    @SerializedName("file_path")
    private String filePath;

    @SerializedName("url")
    private String url;

    @SerializedName("cover_path")
    private String coverPath;

    @SerializedName("cover_url")
    private String coverUrl;

    @SerializedName("lyric_url")
    private String lyricUrl;

    @SerializedName("file_size")
    private Long fileSize;

    @SerializedName("format")
    private String format;

    @SerializedName("bit_rate")
    private Integer bitRate;

    @SerializedName("sample_rate")
    private Integer sampleRate;

    @SerializedName("is_live")
    private Boolean isLive;

    @SerializedName("cache_hash")
    private String cacheHash;

    @SerializedName("added_at")
    private String addedAt;

    @SerializedName("updated_at")
    private String updatedAt;

    // 转换为 MusicInfo
    public MusicInfo toMusicInfo() {
        MusicInfo info = new MusicInfo();
        info.setId(String.valueOf(id));
        info.setName(title != null ? title : "");
        info.setSinger(artist != null ? artist : "");
        info.setAlbumName(album != null ? album : "");
        info.setPicUrl(coverUrl != null ? coverUrl : (coverPath != null ? coverPath : ""));
        // 设置 source 为 mimusic，用于标识
        info.setSource("mimusic");
        // 将 cacheHash 存入 songmid，因为 buildResolveUri 需要用到
        info.setSongmid(cacheHash != null ? cacheHash : String.valueOf(id));
        // duration 是秒，MusicInfo 用 interval 字段（字符串格式 "mm:ss"）
        if (duration != null) {
            int totalSeconds = duration.intValue();
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            info.setInterval(String.format("%d:%02d", minutes, seconds));
        }

        // 将 MiSong 的原始信息存入 meta extras，以便后续播放时使用
        // 这些信息用于 MiMusicPlayerHelper 判断是本地歌曲还是网络歌曲
        MusicInfo.MusicMeta meta = new MusicInfo.MusicMeta();
        meta.setSongId(id);
        Bundle extras = new Bundle();
        extras.putString("mi_song_type", type != null ? type : "");
        extras.putString("file_path", filePath != null ? filePath : "");
        extras.putString("url", url != null ? url : "");
        extras.putString("cache_hash", cacheHash != null ? cacheHash : "");
        extras.putString("lyric_url", lyricUrl != null ? lyricUrl : "");
        meta.setExtras(extras);
        info.setMeta(meta);

        return info;
    }

    public Integer getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public Double getDuration() { return duration; }
    public String getFilePath() { return filePath; }
    public String getUrl() { return url; }
    public String getCoverUrl() { return coverUrl; }
    public String getCoverPath() { return coverPath; }
    public String getLyricUrl() { return lyricUrl; }
    public String getCacheHash() { return cacheHash; }
    public String getUpdatedAt() { return updatedAt; }
    public String getAddedAt() { return addedAt; }
}
