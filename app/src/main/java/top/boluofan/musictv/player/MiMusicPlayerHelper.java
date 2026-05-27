package top.boluofan.musictv.player;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.UnstableApi;
import top.boluofan.musictv.MusicService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.MiSong;
import top.boluofan.musictv.api.model.MusicInfo;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * MiMusic 歌曲播放助手
 * 用于处理 MiMusic 用户歌单中歌曲的播放
 *
 * 播放逻辑：
 * - 本地歌曲（type == "local" && filePath != null）：使用服务器 /music/{base62编码路径}{扩展名}?access_token=xxx
 * - 网络歌曲/电台（url != null && url.isNotEmpty）：直接使用 url，相对路径则拼接 baseUrl
 */
@UnstableApi
public class MiMusicPlayerHelper {
    private static final String TAG = "MiMusicPlayerHelper";

    /**
     * 为 MiSong 创建 MediaItem
     *
     * @param context Context
     * @param miSong MiSong 实例
     * @param accessToken 访问令牌，可为 null
     * @return MediaItem
     */
    public static MediaItem createMediaItem(Context context, MiSong miSong, String accessToken) {
        String songUrl = buildSongUrl(context, miSong, accessToken);

        Bundle extras = new Bundle();
        extras.putString("song_id", String.valueOf(miSong.getId()));
        extras.putString("source", miSong.getType() != null ? miSong.getType() : "mimusic");
        extras.putString("songmid", miSong.getCacheHash() != null ? miSong.getCacheHash() : String.valueOf(miSong.getId()));
        extras.putString("pic_url", miSong.getCoverUrl() != null ? miSong.getCoverUrl() : (miSong.getCoverPath() != null ? miSong.getCoverPath() : ""));
        extras.putString("original_name", miSong.getTitle() != null ? miSong.getTitle() : "");
        extras.putString("mi_song_type", miSong.getType() != null ? miSong.getType() : "");
        extras.putString("file_path", miSong.getFilePath() != null ? miSong.getFilePath() : "");

        Uri artworkUri = null;
        String coverUrl = miSong.getCoverUrl();
        String coverPath = miSong.getCoverPath();
        if (coverUrl != null && !coverUrl.isEmpty()) {
            artworkUri = Uri.parse(coverUrl);
        } else if (coverPath != null && !coverPath.isEmpty()) {
            // 本地歌曲封面：/cover/{base62编码的路径}{扩展名}?access_token=xxx
            artworkUri = Uri.parse(buildCoverUrl(context, coverPath, accessToken));
        }

        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
                .setTitle(miSong.getTitle() != null ? miSong.getTitle() : "")
                .setArtist(miSong.getArtist() != null ? miSong.getArtist() : "")
                .setAlbumTitle(miSong.getAlbum() != null ? miSong.getAlbum() : "")
                .setExtras(extras);

        if (artworkUri != null && !artworkUri.toString().isEmpty()) {
            metadataBuilder.setArtworkUri(artworkUri);
        }

        return new MediaItem.Builder()
                .setMediaId(miSong.getCacheHash() != null ? miSong.getCacheHash() : String.valueOf(miSong.getId()))
                .setUri(songUrl)
                .setMediaMetadata(metadataBuilder.build())
                .build();
    }

    /**
     * 为 MusicInfo（从 MiSong 转换而来）创建 MediaItem
     * 使用传统的 lxmusic://resolve URI 方式
     *
     * @param context Context
     * @param musicInfo MusicInfo 实例
     * @param accessToken 访问令牌，可为 null
     * @return MediaItem
     */
    public static MediaItem createMediaItem(Context context, MusicInfo musicInfo, String accessToken) {
        // 检查是否是 MiMusic 歌曲（source == "mimusic"）
        if ("mimusic".equals(musicInfo.getSource())) {
            // 尝试从 extras 中获取 MiSong 的原始信息
            // MiSong 转换时，type 存在 extras 的 "mi_song_type"，filePath 存在 "file_path"，url 存在 "url"
            Bundle extras = musicInfo.getMeta() != null ? musicInfo.getMeta().getExtras() : null;

            if (extras != null) {
                String miSongType = extras.getString("mi_song_type");
                String filePath = extras.getString("file_path");
                String url = extras.getString("url");

                if (miSongType != null || filePath != null || url != null) {
                    // 可以重建 MiSong 的播放
                    return createMediaItemFromParts(context, musicInfo, miSongType, filePath, url, accessToken);
                }
            }

            // 如果没有 extras，回退到使用 resolve URI（需要服务器解析）
            // 但这种情况可能无法正确播放本地歌曲
            Uri resolveUri = MusicService.buildResolveUri(
                    musicInfo.getSource() != null ? musicInfo.getSource() : "mimusic",
                    musicInfo.getSongmid(),
                    musicInfo.getName()
            );
            return createMediaItemFromResolveUri(musicInfo, resolveUri);
        }

        // 非 MiMusic 歌曲，使用传统方式
        Uri resolveUri = MusicService.buildResolveUri(
                musicInfo.getSource() != null ? musicInfo.getSource() : "mimusic",
                musicInfo.getSongmid(),
                musicInfo.getName()
        );
        return createMediaItemFromResolveUri(musicInfo, resolveUri);
    }

    /**
     * 从解析后的 URI 创建 MediaItem
     */
    private static MediaItem createMediaItemFromResolveUri(MusicInfo musicInfo, Uri resolveUri) {
        Bundle extras = new Bundle();
        extras.putString("song_id", musicInfo.getId());
        extras.putString("source", musicInfo.getSource());
        extras.putString("songmid", musicInfo.getSongmid());
        extras.putString("pic_url", musicInfo.getPicUrl());
        extras.putString("original_name", musicInfo.getName());

        Uri artworkUri = musicInfo.getPicUrl() != null ? Uri.parse(musicInfo.getPicUrl()) : null;

        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
                .setTitle(musicInfo.getName())
                .setArtist(musicInfo.getSinger())
                .setAlbumTitle(musicInfo.getAlbumName())
                .setExtras(extras);

        if (artworkUri != null && !artworkUri.toString().isEmpty()) {
            metadataBuilder.setArtworkUri(artworkUri);
        }

        return new MediaItem.Builder()
                .setMediaId(musicInfo.getSongmid())
                .setUri(resolveUri)
                .setMediaMetadata(metadataBuilder.build())
                .build();
    }

    /**
     * 根据 MiSong 各部分信息创建 MediaItem
     */
    private static MediaItem createMediaItemFromParts(Context context, MusicInfo musicInfo,
            String miSongType, String filePath, String url, String accessToken) {

        String songUrl;

        // 本地歌曲
        if ("local".equals(miSongType) && filePath != null && !filePath.isEmpty()) {
            songUrl = buildLocalSongUrl(context, filePath, accessToken);
        }
        // 网络歌曲/电台
        else if (url != null && !url.isEmpty()) {
            songUrl = buildNetworkSongUrl(context, url, accessToken);
        }
        // 没有有效源，使用 resolve URI
        else {
            Log.w(TAG, "MiSong has no valid source, falling back to resolve URI");
            Uri resolveUri = MusicService.buildResolveUri(
                    musicInfo.getSource() != null ? musicInfo.getSource() : "mimusic",
                    musicInfo.getSongmid(),
                    musicInfo.getName()
            );
            return createMediaItemFromResolveUri(musicInfo, resolveUri);
        }

        Bundle extras = new Bundle();
        extras.putString("song_id", musicInfo.getId());
        extras.putString("source", musicInfo.getSource());
        extras.putString("songmid", musicInfo.getSongmid());
        extras.putString("pic_url", musicInfo.getPicUrl());
        extras.putString("original_name", musicInfo.getName());
        extras.putString("mi_song_type", miSongType != null ? miSongType : "");
        extras.putString("file_path", filePath != null ? filePath : "");
        // 如果有内嵌歌词，也存入 extras，供 PlayerActivity 直接使用
        if (musicInfo.getMeta() != null && musicInfo.getMeta().getExtras() != null) {
            String lyric = musicInfo.getMeta().getExtras().getString("lyric");
            if (lyric != null && !lyric.isEmpty()) {
                extras.putString("lyrics", lyric);
            }
        }

        Uri artworkUri = musicInfo.getPicUrl() != null ? Uri.parse(musicInfo.getPicUrl()) : null;

        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
                .setTitle(musicInfo.getName())
                .setArtist(musicInfo.getSinger())
                .setAlbumTitle(musicInfo.getAlbumName())
                .setExtras(extras);

        if (artworkUri != null && !artworkUri.toString().isEmpty()) {
            metadataBuilder.setArtworkUri(artworkUri);
        }

        return new MediaItem.Builder()
                .setMediaId(musicInfo.getSongmid())
                .setUri(songUrl)
                .setMediaMetadata(metadataBuilder.build())
                .build();
    }

    // ========== 原有方法保留兼容 ==========

    /**
     * 为 MusicInfo（从 MiSong 转换而来）创建 MediaItem
     * 使用传统的 lxmusic://resolve URI 方式
     *
     * @param musicInfo MusicInfo 实例
     * @return MediaItem
     * @deprecated 使用 {@link #createMediaItem(Context, MusicInfo, String)} 代替
     */
    @Deprecated
    public static MediaItem createMediaItem(MusicInfo musicInfo) {
        Uri resolveUri = MusicService.buildResolveUri(
                musicInfo.getSource() != null ? musicInfo.getSource() : "mimusic",
                musicInfo.getSongmid(),
                musicInfo.getName()
        );

        Bundle extras = new Bundle();
        extras.putString("song_id", musicInfo.getId());
        extras.putString("source", musicInfo.getSource());
        extras.putString("songmid", musicInfo.getSongmid());
        extras.putString("pic_url", musicInfo.getPicUrl());
        extras.putString("original_name", musicInfo.getName());

        Uri artworkUri = musicInfo.getPicUrl() != null ? Uri.parse(musicInfo.getPicUrl()) : null;

        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
                .setTitle(musicInfo.getName())
                .setArtist(musicInfo.getSinger())
                .setAlbumTitle(musicInfo.getAlbumName())
                .setExtras(extras);

        if (artworkUri != null && !artworkUri.toString().isEmpty()) {
            metadataBuilder.setArtworkUri(artworkUri);
        }

        return new MediaItem.Builder()
                .setMediaId(musicInfo.getSongmid())
                .setUri(resolveUri)
                .setMediaMetadata(metadataBuilder.build())
                .build();
    }

    /**
     * 构建歌曲播放 URL
     *
     * @param context Context
     * @param miSong MiSong 实例
     * @param accessToken 访问令牌
     * @return 播放 URL
     */
    private static String buildSongUrl(Context context, MiSong miSong, String accessToken) {
        String type = miSong.getType();

        Log.d(TAG, "歌曲类型----------: " + type);
        // 本地歌曲
        if ("local".equals(type) && miSong.getFilePath() != null && !miSong.getFilePath().isEmpty()) {
            return buildLocalSongUrl(context, miSong.getFilePath(), accessToken);
        }

        // 网络歌曲/电台
        if (miSong.getUrl() != null && !miSong.getUrl().isEmpty()) {
            return buildNetworkSongUrl(context, miSong.getUrl(), accessToken);
        }

        // 没有有效源，抛出异常
        Log.e(TAG, "MiSong has no valid source: type=" + type + ", filePath=" + miSong.getFilePath() + ", url=" + miSong.getUrl());
        throw new IllegalArgumentException("无法播放：歌曲没有有效的播放源");
    }

    /**
     * 构建本地歌曲 URL
     * 格式: /music/{base62编码的路径}{扩展名}?access_token=xxx
     */
    private static String buildLocalSongUrl(Context context, String filePath, String accessToken) {
        String baseUrl = LxRetrofitClient.getPureServerUrl(context);

        // 获取路径和扩展名
        String pathWithoutExt = getPathWithoutExtension(filePath);
        String ext = getExtension(filePath);

        // Base62 编码
        String encodedPath = base62Encode(pathWithoutExt);

        // 获取 token
        String token = accessToken;
        if (token == null || token.isEmpty()) {
            token = LxRetrofitClient.getMiAccessToken(context);
        }
        if (token == null) {
            token = "";
        }

        String result = baseUrl + "/music/" + encodedPath + ext + "?access_token=" + token;
        Log.d(TAG, "Local song URL: " + result);
        return result;
    }

    /**
     * 构建本地歌曲封面 URL（公开方法，供外部调用）
     * 格式: /cover/{base62编码的路径}{扩展名}?access_token=xxx
     *
     * @param context Context
     * @param coverPath 封面路径，如 "/app/data/covers/26/af/xxx.jpg"
     * @param accessToken 访问令牌
     * @return 封面完整 URL
     */
    public static String buildCoverUrl(Context context, String coverPath, String accessToken) {
        if (coverPath == null || coverPath.isEmpty()) {
            return "";
        }
        return buildCoverUrlInternal(context, coverPath, accessToken);
    }

    /**
     * 构建本地歌曲封面 URL
     * 格式: /cover/{base62编码的路径}{扩展名}?access_token=xxx
     */
    private static String buildCoverUrlInternal(Context context, String coverPath, String accessToken) {
        String baseUrl = LxRetrofitClient.getPureServerUrl(context);

        // 获取路径和扩展名
        String pathWithoutExt = getPathWithoutExtension(coverPath);
        String ext = getExtension(coverPath);

        // Base62 编码
        String encodedPath = base62Encode(pathWithoutExt);

        // 获取 token
        String token = accessToken;
        if (token == null || token.isEmpty()) {
            token = LxRetrofitClient.getMiAccessToken(context);
        }
        if (token == null) {
            token = "";
        }

        String result = baseUrl + "/cover/" + encodedPath + ext + "?access_token=" + token;
        Log.d(TAG, "Cover URL: " + result);
        return result;
    }

    /**
     * 构建网络歌曲 URL
     * 相对路径需要拼接 baseUrl 并附加 access_token
     */
    private static String buildNetworkSongUrl(Context context, String url, String accessToken) {
        String result;
        Log.d(TAG, "Net song bef URL: " + url);
        if (url.startsWith("/")) {
            // 获取纯净的服务器，需要拼接 baseUrl
            String baseUrl = LxRetrofitClient.getPureServerUrl(context);
            String token = accessToken;
            if (token == null || token.isEmpty()) {
                token = LxRetrofitClient.getMiAccessToken(context);
            }
            if (token == null) {
                token = "";
            }

            String separator = url.contains("?") ? "&" : "?";
            result = baseUrl + url + separator + "access_token=" + token+"&prefetch=true";
            Log.d(TAG, "Server-relative URL with token: " + result);
        } else {
            // 构建代理地址
            result = buildProxyUrl(context, url, accessToken);
            Log.d(TAG, "Network song URL (absolute): " + result);
        }

        return result;
    }

    /**
     * 构建歌词 URL（公开方法）
     * 根据 lyric 内容判断：
     * - 内嵌歌词（非 URL）：返回空字符串，调用方应直接使用 lyric 文本
     * - 网络歌词 URL：返回完整可请求的 URL
     *
     * @param context Context
     * @param lyric lyric 字段值
     * @param accessToken 访问令牌，可为 null
     * @return 歌词完整 URL，或空字符串（内嵌歌词）
     */
    public static String buildLyricUrl(Context context, String lyric, String accessToken) {
        return buildNetworkLyricUrl(context, lyric, accessToken);
    }

    /**
     * 构建网络歌词 URL
     * 相对路径需要拼接 baseUrl 并附加 access_token
     * 内嵌歌词（非 URL 格式）返回空字符串
     *
     * @param context Context
     * @param lyric lyric 字段值，可能是 URL 或内嵌歌词文本
     * @param accessToken 访问令牌
     * @return 歌词完整 URL，如果是内嵌歌词则返回空字符串
     */
    private static String buildNetworkLyricUrl(Context context, String lyric, String accessToken) {
        if (lyric == null || lyric.isEmpty()) {
            return "";
        }

        // 如果不是以 / 或 http(s) 开头，说明是内嵌歌词文本
        if (!lyric.startsWith("/") && !lyric.startsWith("http://") && !lyric.startsWith("https://")) {
            Log.d(TAG, "Embedded lyric text detected");
            return "";
        }

        String result;
        Log.d(TAG, "Net lyric bef URL: " + lyric);
        if (lyric.startsWith("/")) {
            // 相对路径：拼接 baseUrl
            String baseUrl = LxRetrofitClient.getPureServerUrl(context);
            String token = accessToken;
            if (token == null || token.isEmpty()) {
                token = LxRetrofitClient.getMiAccessToken(context);
            }
            if (token == null) {
                token = "";
            }

            String separator = lyric.contains("?") ? "&" : "?";
            result = baseUrl + lyric + separator + "access_token=" + token;
            Log.d(TAG, "Server-relative lyric URL with token: " + result);
        } else {
            // 绝对路径：使用代理
            result = buildProxyUrl(context, lyric, accessToken);
            Log.d(TAG, "Network lyric URL (absolute): " + result);
        }

        return result;
    }

    /**
     * 获取代理地址
     */
    private static String buildProxyUrl(Context context, String externalUrl, String accessToken) {
        if (!externalUrl.startsWith("http://") && !externalUrl.startsWith("https://")) {
            return externalUrl;
        }

        String baseUrl = LxRetrofitClient.getServerUrl(context);
        if (externalUrl.startsWith(baseUrl)) {
            return externalUrl;
        }

        String token = accessToken;
        if (token == null || token.isEmpty()) {
            token = LxRetrofitClient.getMiAccessToken(context);
        }
        if (token == null) {
            token = "";
        }

        String encodedUrl = Uri.encode(externalUrl);
        return baseUrl + "/proxy?url=" + encodedUrl + "&access_token=" + token;
    }

    /**
     * 获取不带扩展名的路径
     */
    private static String getPathWithoutExtension(String filePath) {
        if (filePath == null) return "";
        int lastDot = filePath.lastIndexOf('.');
        int lastSlashForward = filePath.lastIndexOf('/');
        int lastSlashBack = filePath.lastIndexOf('\\');
        int lastSlash = lastSlashForward > lastSlashBack ? lastSlashForward : lastSlashBack;
        // 确保点号在最后一个路径分隔符之后（是扩展名而非目录名中的点）
        if (lastDot > lastSlash && lastDot > 0) {
            return filePath.substring(0, lastDot);
        }
        return filePath;
    }

    /**
     * 获取扩展名（包含点）
     */
    private static String getExtension(String filePath) {
        if (filePath == null) return "";
        int lastDot = filePath.lastIndexOf('.');
        int lastSlashForward = filePath.lastIndexOf('/');
        int lastSlashBack = filePath.lastIndexOf('\\');
        int lastSlash = lastSlashForward > lastSlashBack ? lastSlashForward : lastSlashBack;
        if (lastDot > lastSlash && lastDot > 0) {
            return filePath.substring(lastDot);
        }
        return "";
    }

    /**
     * Base62 编码
     * 将路径字节数组用 Base64 编码的可打印字符（0-9, A-Z, a-z）表示
     */
    private static String base62Encode(String input) {
        if (input == null || input.isEmpty()) return "0";

        // 将字符串转为 UTF-8 字节数组
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);

        // 转为大整数
        BigInteger num = BigInteger.ZERO;
        for (byte b : bytes) {
            // 将 byte 转为无符号整数 (0-255)
            int unsignedByte = b >= 0 ? b : b + 256;
            num = num.multiply(BigInteger.valueOf(256)).add(BigInteger.valueOf(unsignedByte));
        }

        // Base62 字符集
        String base62Chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

        // 转为 Base62
        if (num.equals(BigInteger.ZERO)) return String.valueOf(base62Chars.charAt(0));

        String result = "";
        BigInteger divisor = BigInteger.valueOf(62);
        while (num.compareTo(BigInteger.ZERO) > 0) {
            int remainder = num.mod(divisor).intValue();
            result = base62Chars.charAt(remainder) + result;
            num = num.divide(divisor);
        }
        return result;
    }

    /**
     * 检查是否是 MiMusic 本地歌曲
     */
    public static boolean isMiMusicLocalSong(MiSong miSong) {
        return miSong != null && "local".equals(miSong.getType()) &&
                miSong.getFilePath() != null && !miSong.getFilePath().isEmpty();
    }

    /**
     * 检查是否有有效的播放 URL
     */
    public static boolean hasValidSource(MiSong miSong) {
        if (miSong == null) return false;
        if ("local".equals(miSong.getType()) && miSong.getFilePath() != null && !miSong.getFilePath().isEmpty()) {
            return true;
        }
        if (miSong.getUrl() != null && !miSong.getUrl().isEmpty()) {
            return true;
        }
        return false;
    }
}