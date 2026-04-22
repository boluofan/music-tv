package top.boluofan.musictv.api;

import java.util.List;
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;
import top.boluofan.musictv.api.model.HotListResponse;
import top.boluofan.musictv.api.model.ListData;
import top.boluofan.musictv.api.model.LoginResponse;
import top.boluofan.musictv.api.model.LyricInfo;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.api.model.MusicUrlResponse;
import top.boluofan.musictv.api.model.Playlist;
import top.boluofan.musictv.api.model.SearchResult;

public interface LxApiService {

    @POST("api/user/verify")
    Call<LoginResponse> verifyUser(@Body Map<String, String> body);

    @GET("api/user/list")
    Call<ListData> getUserList(
            @Header("x-user-name") String username,
            @Header("x-user-password") String password,
            @Header("x-user-token") String token
    );

    @POST("api/user/list")
    Call<ResponseBody> updateUserList(
            @Header("x-user-name") String username,
            @Header("x-user-password") String password,
            @Header("x-user-token") String token,
            @Body ListData listData
    );

    @GET("search")
    Call<List<MusicInfo>> searchMusic(
            @Query("name") String keyword,
            @Query("source") String source,
            @Query("page") int page,
            @Query("limit") int limit
    );

    @POST("url")
    Call<MusicUrlResponse> getMusicUrl(@Body Map<String, Object> body);

    @GET("lyric")
    Call<LyricInfo> getLyric(@Query("source") String source, @Query("songmid") String songmid, @Query("quality") String quality);

    @GET("hotSearch")
    Call<ResponseBody> getHotSearch(@Query("source") String source);

    @GET("tipSearch")
    Call<ResponseBody> tipSearch(
            @Query("name") String keyword,
            @Query("source") String source
    );

    @GET("songList/tags")
    Call<ResponseBody> getSongListTags(@Query("source") String source);

    @GET("songList/list")
    Call<ResponseBody> getSongListList(
            @Query("source") String source,
            @Query("tagId") String tagId,
            @Query("sortId") String sortId,
            @Query("page") int page
    );

    @GET("songList/detail")
    Call<Playlist> getPlaylistDetail(
            @Query("source") String source,
            @Query("id") String id,
            @Query("page") int page
    );

    @GET("songList/search")
    Call<ResponseBody> searchSongList(
            @Query("source") String source,
            @Query("text") String keyword,
            @Query("page") int page
    );

    @GET("config")
    Call<ResponseBody> getPlayerConfig();

    @POST("cache/lyric")
    Call<ResponseBody> cacheLyric(@Body Map<String, Object> body);

    @GET("cache/lyric")
    Call<ResponseBody> getCachedLyric(
            @Query("source") String source,
            @Query("songmid") String songmid,
            @Query("songId") String songId
    );

    @POST("api/data/delete-playlist")
    Call<ResponseBody> deletePlaylist(
            @Header("x-frontend-auth") String auth,
            @Body Map<String, Object> body
    );

    @POST("api/data/rename-playlist")
    Call<ResponseBody> renamePlaylist(
            @Header("x-frontend-auth") String auth,
            @Body Map<String, Object> body
    );

    @POST("api/data/delete-song")
    Call<ResponseBody> deleteSong(
            @Header("x-frontend-auth") String auth,
            @Body Map<String, Object> body
    );

    @POST("api/data/batch-delete-songs")
    Call<ResponseBody> batchDeleteSongs(
            @Header("x-frontend-auth") String auth,
            @Body Map<String, Object> body
    );

    @GET("comment")
    Call<ResponseBody> getComment(
            @Query("source") String source,
            @Query("songmid") String songmid
    );

    @GET("leaderboard/boards")
    Call<ResponseBody> getLeaderboardBoards(@Query("source") String source);

    @GET("leaderboard/list")
    Call<ResponseBody> getLeaderboardList(
            @Query("source") String source,
            @Query("bangid") String bangId,
            @Query("page") int page
    );

    @POST("user/list/remove")
    Call<ResponseBody> removeSongsFromPlaylist(
            @Header("x-user-name") String username,
            @Header("x-user-password") String password,
            @Header("x-user-token") String token,
            @Body Map<String, Object> body
    );
}
