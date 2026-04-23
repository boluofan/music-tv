package top.boluofan.musictv.api.model;

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

    @SerializedName("lyric")
    private String lyric;

    @SerializedName("lyric_source")
    private String lyricSource;

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
        return info;
    }

    public Integer getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public Double getDuration() { return duration; }
    public String getUrl() { return url; }
    public String getCoverUrl() { return coverUrl; }
    public String getLyric() { return lyric; }
    public String getCacheHash() { return cacheHash; }
}
