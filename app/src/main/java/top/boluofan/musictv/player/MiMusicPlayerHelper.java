package top.boluofan.musictv.player;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.UnstableApi;
import top.boluofan.musictv.MusicService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.MusicInfo;

/**
 * MiMusic 歌曲播放助手
 * 用于处理 MiMusic 用户歌单中歌曲的播放
 *
 * 新架构(2026):
 * - 所有歌曲:后端 MarshalJSON 统一 song.url 为 /api/v1/songs/{id}/play
 * - 本地歌曲封面: /api/v1/songs/{id}/cover
 * - 网络歌曲封面:保留原始 CoverURL (外部 CDN)
 */
@UnstableApi
public class MiMusicPlayerHelper {
    private static final String TAG = "MiMusicPlayerHelper";

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
            return createMediaItemFromResolveUri(context, musicInfo, resolveUri, accessToken);
        }

        // 非 MiMusic 歌曲，使用传统方式
        Uri resolveUri = MusicService.buildResolveUri(
                musicInfo.getSource() != null ? musicInfo.getSource() : "mimusic",
                musicInfo.getSongmid(),
                musicInfo.getName()
        );
        return createMediaItemFromResolveUri(context, musicInfo, resolveUri, accessToken);
    }

    /**
     * 从解析后的 URI 创建 MediaItem
     */
    private static MediaItem createMediaItemFromResolveUri(Context context, MusicInfo musicInfo, Uri resolveUri, String accessToken) {
        // 新架构(2026): coverUrl 可能是相对路径，需要通过 buildCoverUrl 处理
        String coverUrl = buildCoverUrl(context, musicInfo.getPicUrl(), accessToken);

        Bundle extras = new Bundle();
        extras.putString("song_id", musicInfo.getId());
        extras.putString("source", musicInfo.getSource());
        extras.putString("songmid", musicInfo.getSongmid());
        extras.putString("pic_url", coverUrl);
        extras.putString("original_name", musicInfo.getName());

        Uri artworkUri = Uri.parse(coverUrl);

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

        String songUrl = buildSongUrl(context, url, accessToken);

        // 新架构(2026): coverUrl 可能是相对路径，需要通过 buildCoverUrl 处理
        String coverUrl = buildCoverUrl(context, musicInfo.getPicUrl(), accessToken);

        Bundle extras = new Bundle();
        extras.putString("song_id", musicInfo.getId());
        extras.putString("source", musicInfo.getSource());
        extras.putString("songmid", musicInfo.getSongmid());
        extras.putString("pic_url", coverUrl);
        extras.putString("original_name", musicInfo.getName());
        extras.putString("mi_song_type", miSongType != null ? miSongType : "");
        extras.putString("file_path", filePath != null ? filePath : "");
        // 如果有歌词 URL，也存入 extras，供 PlayerActivity 直接使用
        if (musicInfo.getMeta() != null && musicInfo.getMeta().getExtras() != null) {
            String lyricUrl = musicInfo.getMeta().getExtras().getString("lyric_url");
            if (lyricUrl != null && !lyricUrl.isEmpty()) {
                extras.putString("lyric_url", lyricUrl);
            }
        }

        Uri artworkUri = Uri.parse(coverUrl);

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
     * 构建封面 URL（统一处理相对路径和外部 URL）
     * 新架构(2026):后端已统一 coverUrl 字段
     *
     * @param context Context
     * @param coverUrl 封面 URL（相对路径或外部 URL）
     * @param accessToken 访问令牌
     * @return 完整的封面 URL
     */
    public static String buildCoverUrl(Context context, String coverUrl, String accessToken) {
        if (coverUrl == null || coverUrl.isEmpty()) {
            return "";
        }
        if (coverUrl.startsWith("/")) {
            // 相对路径：拼接 baseUrl 并附加 access_token
            String baseUrl = LxRetrofitClient.getPureServerUrl(context);
            String token = accessToken;
            if (token == null || token.isEmpty()) {
                token = LxRetrofitClient.getMiAccessToken(context);
            }
            if (token == null) {
                token = "";
            }
            String separator = coverUrl.contains("?") ? "&" : "?";
            return baseUrl + coverUrl + separator + "access_token=" + token;
        } else {
            // 外部 URL：直接返回
            return coverUrl;
        }
    }

    /**
     * 构建歌曲 URL（处理相对路径和外部 URL）
     * 
     * 新架构(2026):所有歌曲 URL 都是 /api/v1/songs/{id}/play 相对路径
     * - 相对路径:拼接 baseUrl 并附加 access_token
     * - 外部 URL:走代理解决 CORS
     */
    private static String buildSongUrl(Context context, String url, String accessToken) {
        String result;
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("无法播放：歌曲没有有效的播放源");
        }
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
     * 新架构(2026): lyricUrl 永远是 /api/v1/songs/{id}/lyric 相对路径或空字符串
     *
     * @param context Context
     * @param lyricUrl lyric_url 字段值（相对路径或空）
     * @param accessToken 访问令牌，可为 null
     * @return 歌词完整 URL，或空字符串（无歌词）
     */
    public static String buildLyricUrl(Context context, String lyricUrl, String accessToken) {
        return buildNetworkLyricUrl(context, lyricUrl, accessToken);
    }

    /**
     * 构建网络歌词 URL
     * 新架构(2026): lyricUrl 是 /api/v1/songs/{id}/lyric 相对路径，需要拼接 baseUrl 并附加 access_token
     *
     * @param context Context
     * @param lyricUrl lyric_url 字段值
     * @param accessToken 访问令牌
     * @return 歌词完整 URL，如果是空则返回空字符串
     */
    private static String buildNetworkLyricUrl(Context context, String lyricUrl, String accessToken) {
        if (lyricUrl == null || lyricUrl.isEmpty()) {
            return "";
        }

        // 新架构: lyricUrl 永远是相对路径 /api/v1/songs/{id}/lyric
        String result;
        Log.d(TAG, "Lyric URL: " + lyricUrl);
        
        // 相对路径：拼接 baseUrl
        String baseUrl = LxRetrofitClient.getPureServerUrl(context);
        String token = accessToken;
        if (token == null || token.isEmpty()) {
            token = LxRetrofitClient.getMiAccessToken(context);
        }
        if (token == null) {
            token = "";
        }

        String separator = lyricUrl.contains("?") ? "&" : "?";
        result = baseUrl + lyricUrl + separator + "access_token=" + token;
        Log.d(TAG, "Lyric URL with token: " + result);

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
}