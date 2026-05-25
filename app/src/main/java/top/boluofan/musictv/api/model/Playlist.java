package top.boluofan.musictv.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Playlist {
    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("source")
    private String source;

    @SerializedName("sourceListId")
    private String sourceListId;

    @SerializedName("locationUpdateTime")
    private Long locationUpdateTime;

    @SerializedName("list")
    private List<MusicInfo> songs;

    @SerializedName("picUrl")
    private String picUrl;

    @SerializedName("img")
    private String img;

    @SerializedName("desc")
    private String desc;

    @SerializedName("songCount")
    private Integer songCount;

    @SerializedName("total")
    private String total;

    @SerializedName("time")
    private String time;

    @SerializedName("createTime")
    private Long createTime;

    @SerializedName("creator")
    private String creator;

    @SerializedName("author")
    private String author;

    @SerializedName("playCount")
    private Long playCount;

    @SerializedName("play_count")
    private String playCountStr;

    @SerializedName("shareCount")
    private Long shareCount;

    @SerializedName("info")
    private PlaylistInfo info;

    private boolean isDefault;
    private boolean isLove;

    public static class PlaylistInfo {
        @SerializedName("name")
        private String name;

        @SerializedName("img")
        private String img;

        @SerializedName("author")
        private String author;

        @SerializedName("play_count")
        private String playCount;

        @SerializedName("desc")
        private String desc;

        @SerializedName("time")
        private String time;

        public String getName() { return name; }
        public String getImg() { return img; }
        public String getAuthor() { return author; }
        public String getPlayCount() { return playCount; }
        public String getDesc() { return desc; }
        public String getTime() { return time; }
    }

    public PlaylistInfo getInfo() { return info; }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceListId() {
        return sourceListId;
    }

    public void setSourceListId(String sourceListId) {
        this.sourceListId = sourceListId;
    }

    public Long getLocationUpdateTime() {
        return locationUpdateTime;
    }

    public void setLocationUpdateTime(Long locationUpdateTime) {
        this.locationUpdateTime = locationUpdateTime;
    }

    public List<MusicInfo> getSongs() {
        return songs;
    }

    public void setSongs(List<MusicInfo> songs) {
        this.songs = songs;
    }

    public int getSongCount() {
        if (songCount != null) return songCount;
        if (total != null && !total.isEmpty()) {
            try {
                return Integer.parseInt(total);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return songs != null ? songs.size() : 0;
    }

    public String getPicUrl() {
        if (info != null && info.getImg() != null && !info.getImg().isEmpty()) {
            return info.getImg();
        }
        if (picUrl != null && !picUrl.isEmpty()) return picUrl;
        if (img != null && !img.isEmpty()) return img;
        return getCoverUrl();
    }

    public String getCreator() {
        if (info != null && info.getAuthor() != null && !info.getAuthor().isEmpty()) {
            return info.getAuthor();
        }
        if (creator != null && !creator.isEmpty()) {
            return creator;
        }
        return author;
    }

    public String getTime() {
        if (info != null && info.getTime() != null && !info.getTime().isEmpty()) {
            return info.getTime();
        }
        return time;
    }

    public String getDesc() {
        if (info != null && info.getDesc() != null && !info.getDesc().isEmpty()) {
            return info.getDesc();
        }
        return desc;
    }

    public void setPicUrl(String picUrl) {
        this.picUrl = picUrl;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public void setSongCount(Integer songCount) {
        this.songCount = songCount;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public Long getPlayCount() {
        return playCount;
    }

    public String getPlayCountStr() {
        return playCountStr;
    }

    public void setPlayCount(Long playCount) {
        this.playCount = playCount;
    }

    public Long getShareCount() {
        return shareCount;
    }

    public void setShareCount(Long shareCount) {
        this.shareCount = shareCount;
    }

    public String getFormattedPlayCount() {
        if (info != null && info.getPlayCount() != null && !info.getPlayCount().isEmpty()) {
            return info.getPlayCount();
        }
        if (playCountStr != null && !playCountStr.isEmpty()) {
            return playCountStr;
        }
        if (playCount == null || playCount == 0) return "";
        if (playCount >= 100000000) {
            return String.format("%.1f亿", playCount / 100000000.0);
        } else if (playCount >= 10000) {
            return String.format("%.1f万", playCount / 10000.0);
        }
        return String.valueOf(playCount);
    }

    public boolean isDefault() {
        return "default".equals(id) || isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public boolean isLove() {
        return "love".equals(id) || isLove;
    }

    public void setLove(boolean love) {
        isLove = love;
    }

    public String getCoverUrl() {
        if (songs != null && !songs.isEmpty()) {
            MusicInfo firstSong = songs.get(0);
            return firstSong.getPicUrl();
        }
        return null;
    }

    /**
     * 检查歌单是否来自 MiMusic 接口（通过歌曲 source 判断）
     */
    public boolean isMiMusicSource() {
        if (songs == null || songs.isEmpty()) {
            return false;
        }
        for (MusicInfo song : songs) {
            if (song.getSource() != null && "mimusic".equals(song.getSource())) {
                return true;
            }
        }
        return false;
    }
}
