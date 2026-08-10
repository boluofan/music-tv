package top.boluofan.musictv.api;

import android.content.Context;
import android.util.Log;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.api.model.ListData;
import top.boluofan.musictv.api.model.LoginResponse;
import top.boluofan.musictv.api.model.LyricInfo;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.api.model.MusicUrlResponse;
import top.boluofan.musictv.api.model.Playlist;
import top.boluofan.musictv.api.model.SearchResult;

public class MusicRepository {
    private static final String TAG = "MusicRepository";
    private static MusicRepository instance;
    private final LxApiService apiService;
    private final Context context;

    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    private MusicRepository(Context context) {
        this.context = context.getApplicationContext();
        this.apiService = LxRetrofitClient.getLxAuthService(context);
    }

    public static synchronized MusicRepository getInstance(Context context) {
        if (instance == null) {
            instance = new MusicRepository(context);
        }
        return instance;
    }

    public void verifyUser(String username, String password, RepositoryCallback<Boolean> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);

        apiService.loginUser(body).enqueue(new retrofit2.Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    LxRetrofitClient.saveToken(context, response.body().getToken());
                    callback.onSuccess(true);
                } else {
                    callback.onError("认证失败");
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void getUserList(RepositoryCallback<ListData> callback) {
        String username = LxRetrofitClient.getUsername(context);
        String password = LxRetrofitClient.getPassword(context);
        String token = LxRetrofitClient.getToken(context);

        apiService.getUserList(username, password, token).enqueue(new retrofit2.Callback<ListData>() {
            @Override
            public void onResponse(Call<ListData> call, Response<ListData> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("获取用户列表失败: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ListData> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void updateUserList(ListData listData, RepositoryCallback<Boolean> callback) {
        String username = LxRetrofitClient.getUsername(context);
        String password = LxRetrofitClient.getPassword(context);
        String token = LxRetrofitClient.getToken(context);

        apiService.updateUserList(username, password, token, listData).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                callback.onSuccess(response.isSuccessful());
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void searchMusic(String keyword, String source, int page, int limit, RepositoryCallback<List<MusicInfo>> callback) {
        apiService.searchMusic(keyword, source, page, limit).enqueue(new retrofit2.Callback<List<MusicInfo>>() {
            @Override
            public void onResponse(Call<List<MusicInfo>> call, Response<List<MusicInfo>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("搜索失败: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<MusicInfo>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void getMusicUrl(String source, String songmid, String quality, RepositoryCallback<MusicUrlResponse> callback) {
        Map<String, Object> body = new HashMap<>();
        Map<String, String> songInfo = new HashMap<>();
        songInfo.put("source", source);
        songInfo.put("songmid", songmid);
        body.put("songInfo", songInfo);
        body.put("quality", quality);

        String username = LxRetrofitClient.getUsername(context);
        String password = LxRetrofitClient.getPassword(context);
        String token = LxRetrofitClient.getToken(context);

        LxApiService musicApiService = LxRetrofitClient.getApiService(context);
        musicApiService.getMusicUrl(username, password, token, body).enqueue(new retrofit2.Callback<MusicUrlResponse>() {
            @Override
            public void onResponse(Call<MusicUrlResponse> call, Response<MusicUrlResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("获取播放链接失败: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<MusicUrlResponse> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void getLyric(String source, String songmid, String quality, RepositoryCallback<LyricInfo> callback) {
        apiService.getLyric(source, songmid, quality).enqueue(new retrofit2.Callback<LyricInfo>() {
            @Override
            public void onResponse(Call<LyricInfo> call, Response<LyricInfo> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("获取歌词失败: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<LyricInfo> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void getHotSearch(String source, RepositoryCallback<List<String>> callback) {
        apiService.getHotSearch(source).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String bodyStr = response.body().string();
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        HotSearchResult result = gson.fromJson(bodyStr, HotSearchResult.class);
                        callback.onSuccess(result.getList());
                    } catch (Exception e) {
                        callback.onError("解析失败: " + e.getMessage());
                    }
                } else {
                    callback.onError("获取热搜失败: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void getSongListTags(String source, RepositoryCallback<SongListTagsResult> callback) {
        apiService.getSongListTags(source).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String bodyStr = response.body().string();
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        SongListTagsResult result = gson.fromJson(bodyStr, SongListTagsResult.class);
                        callback.onSuccess(result);
                    } catch (Exception e) {
                        callback.onError("解析失败: " + e.getMessage());
                    }
                } else {
                    callback.onError("获取标签失败: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void getSongList(String source, String tagId, String sortId, int page, RepositoryCallback<List<Playlist>> callback) {
        apiService.getSongListList(source, tagId, sortId, page).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String bodyStr = response.body().string();
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        PlaylistListResult result = gson.fromJson(bodyStr, PlaylistListResult.class);
                        callback.onSuccess(result.getList());
                    } catch (Exception e) {
                        callback.onError("解析失败: " + e.getMessage());
                    }
                } else {
                    callback.onError("获取歌单失败: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void getPlaylistDetail(String source, String id, int page, RepositoryCallback<Playlist> callback) {
        apiService.getPlaylistDetail(source, id, page).enqueue(new retrofit2.Callback<Playlist>() {
            @Override
            public void onResponse(Call<Playlist> call, Response<Playlist> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("获取歌单详情失败: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Playlist> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void deletePlaylist(String username, String playlistId, RepositoryCallback<Boolean> callback) {
        String auth = LxRetrofitClient.getAdminAuth(context);
        if (auth == null) {
            callback.onError("未配置管理员密码");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("playlistId", playlistId);

        apiService.deletePlaylist(auth, body).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                callback.onSuccess(response.isSuccessful());
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void renamePlaylist(String username, String playlistId, String newName, RepositoryCallback<Boolean> callback) {
        String auth = LxRetrofitClient.getAdminAuth(context);
        if (auth == null) {
            callback.onError("未配置管理员密码");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("playlistId", playlistId);
        body.put("newName", newName);

        apiService.renamePlaylist(auth, body).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                callback.onSuccess(response.isSuccessful());
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void addToPlaylist(Playlist playlist, MusicInfo song, RepositoryCallback<Boolean> callback) {
        getUserList(new RepositoryCallback<ListData>() {
            @Override
            public void onSuccess(ListData result) {
                if (result.getUserList() == null) {
                    callback.onError("用户列表为空");
                    return;
                }

                boolean found = false;
                for (Playlist p : result.getUserList()) {
                    if (p.getId().equals(playlist.getId())) {
                        if (p.getSongs() == null) {
                            p.setSongs(new java.util.ArrayList<>());
                        }
                        p.getSongs().add(song);
                        found = true;
                        break;
                    }
                }

                if (found) {
                    updateUserList(result, new RepositoryCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean success) {
                            callback.onSuccess(success);
                        }

                        @Override
                        public void onError(String error) {
                            callback.onError(error);
                        }
                    });
                } else {
                    callback.onError("未找到目标歌单");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void removeFromPlaylist(Playlist playlist, int songIndex, RepositoryCallback<Boolean> callback) {
        getUserList(new RepositoryCallback<ListData>() {
            @Override
            public void onSuccess(ListData result) {
                if (result.getUserList() == null) {
                    callback.onError("用户列表为空");
                    return;
                }

                boolean found = false;
                for (Playlist p : result.getUserList()) {
                    if (p.getId().equals(playlist.getId())) {
                        if (p.getSongs() != null && songIndex >= 0 && songIndex < p.getSongs().size()) {
                            p.getSongs().remove(songIndex);
                            found = true;
                        }
                        break;
                    }
                }

                if (found) {
                    updateUserList(result, new RepositoryCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean success) {
                            callback.onSuccess(success);
                        }

                        @Override
                        public void onError(String error) {
                            callback.onError(error);
                        }
                    });
                } else {
                    callback.onError("未找到目标歌曲");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public static class HotSearchResult {
        private List<String> list;

        public List<String> getList() {
            return list;
        }
    }

    public static class SongListTagsResult {
        private Object tags;
        private List<Object> sortList;

        public Object getTags() {
            return tags;
        }

        public List<Object> getSortList() {
            return sortList;
        }
    }

    public static class PlaylistListResult {
        private List<Playlist> list;

        public List<Playlist> getList() {
            return list;
        }
    }
}
