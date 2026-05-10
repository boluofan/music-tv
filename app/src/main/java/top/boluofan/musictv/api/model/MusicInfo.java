package top.boluofan.musictv.api.model;

import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class MusicInfo {
    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("singer")
    private String singer;

    @SerializedName("source")
    private String source;

    @SerializedName("interval")
    private String interval;

    @SerializedName("img")
    private String img;

    @SerializedName("albumId")
    private String albumId;

    @SerializedName("albumName")
    private String albumName;

    @SerializedName(value = "songmid", alternate = {"musicId"})
    private String songmid;

    @SerializedName("hash")
    private String hash;

    @SerializedName("copyrightId")
    private String copyrightId;

    @SerializedName("types")
    private List<QualityInfo> types;

    @SerializedName("_types")
    private Map<String, QualityDetail> _types;

    @SerializedName("meta")
    private MusicMeta meta;
    
    private String searchSource;
    
    public String getSearchSource() {
        return searchSource;
    }
    
    public void setSearchSource(String searchSource) {
        this.searchSource = searchSource;
    }
    
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

    public String getSinger() {
        return singer;
    }

    public void setSinger(String singer) {
        this.singer = singer;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public MusicMeta getMeta() {
        return meta;
    }

    public void setMeta(MusicMeta meta) {
        this.meta = meta;
    }

    public String getPicUrl() {
        if (img != null) return img;
        if (meta != null && meta.picUrl != null) return meta.picUrl;
        return null;
    }

    public void setPicUrl(String picUrl) {
        this.img = picUrl;
    }

    public List<QualityInfo> getTypes() {
        if (types != null) return types;
        if (meta != null && meta.qualitys != null) return meta.qualitys;
        return null;
    }

    public String getImg() {
        return getPicUrl();
    }

    public void setImg(String img) {
        this.img = img;
    }

    public String getAlbumId() {
        return albumId;
    }

    public void setAlbumId(String albumId) {
        this.albumId = albumId;
    }

    public String getAlbumName() {
        if (albumName != null) return albumName;
        if (meta != null && meta.albumName != null) return meta.albumName;
        return null;
    }

    public void setAlbumName(String albumName) {
        this.albumName = albumName;
    }

    public String getSongmid() {
        if (songmid != null && !songmid.isEmpty()) {
            try {
                return new BigDecimal(songmid).toPlainString();
            } catch (NumberFormatException e) {
                return songmid;
            }
        }
        if (meta != null && meta.songId != null) {
            Object songId = meta.songId;
            if (songId instanceof Number) {
                return new BigDecimal(songId.toString()).toPlainString();
            }
            return String.valueOf(songId);
        }
        return id;
    }

    public void setSongmid(String songmid) {
        this.songmid = songmid;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getCopyrightId() {
        return copyrightId;
    }

    public void setCopyrightId(String copyrightId) {
        this.copyrightId = copyrightId;
    }

    public Map<String, QualityDetail> get_types() {
        if (_types != null) return _types;
        if (meta != null) return meta._qualitys;
        return null;
    }

    public void setTypes(List<QualityInfo> types) {
        this.types = types;
    }

    public void set_types(Map<String, QualityDetail> _types) {
        this._types = _types;
    }

    public static class MusicMeta {
        @SerializedName("songId")
        private Object songId;

        @SerializedName("albumName")
        private String albumName;

        @SerializedName("picUrl")
        private String picUrl;

        @SerializedName("qualitys")
        private List<QualityInfo> qualitys;

        @SerializedName("_qualitys")
        private Map<String, QualityDetail> _qualitys;

        public Object getSongId() {
            return songId;
        }

        public void setSongId(Object songId) {
            this.songId = songId;
        }

        public String getAlbumName() {
            return albumName;
        }

        public void setAlbumName(String albumName) {
            this.albumName = albumName;
        }

        public String getPicUrl() {
            return picUrl;
        }

        public void setPicUrl(String picUrl) {
            this.picUrl = picUrl;
        }

        public List<QualityInfo> getQualitys() {
            return qualitys;
        }

        public void setQualitys(List<QualityInfo> qualitys) {
            this.qualitys = qualitys;
        }

        public Map<String, QualityDetail> get_qualitys() {
            return _qualitys;
        }

        public void set_qualitys(Map<String, QualityDetail> _qualitys) {
            this._qualitys = _qualitys;
        }
    }

    public static class QualityInfo {
        @SerializedName("type")
        private String type;

        @SerializedName("size")
        private String size;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSize() {
            return size;
        }

        public void setSize(String size) {
            this.size = size;
        }
    }

    public static class QualityDetail {
        @SerializedName("size")
        private String size;

        public String getSize() {
            return size;
        }

        public void setSize(String size) {
            this.size = size;
        }
    }
}
