package top.boluofan.musictv.ui.navigation

sealed class Screen(val route: String, val label: String) {
    object Home : Screen("home", "首页")
    object Discover : Screen("discover", "发现")
    object Search : Screen("search", "搜索")
    object My : Screen("my", "我的")
    object Settings : Screen("settings", "设置")
    data class PlaylistDetail(val playlistId: String, val source: String? = null) : Screen("playlist_detail", "歌单详情")
    data class LeaderboardDetail(val bangid: String, val boardName: String, val source: String) : Screen("leaderboard_detail", "榜单详情")
    data class ArtistDetail(val artistId: String, val artistName: String, val source: String) : Screen("artist_detail", "歌手详情")
    data class AlbumDetail(
        val albumId: String,
        val albumName: String,
        val source: String,
        val cover: String? = null,
        val singer: String? = null
    ) : Screen("album_detail", "专辑详情")

    companion object {
        val all = listOf<Screen>(Home, Discover, Search, My)
    }
}

/** SaveableStateHolder 用的唯一状态键 */
val Screen.stateKey: String
    get() = when (this) {
        is Screen.PlaylistDetail -> "$route:$source:$playlistId"
        is Screen.LeaderboardDetail -> "$route:$source:$bangid"
        is Screen.ArtistDetail -> "$route:$source:$artistId"
        is Screen.AlbumDetail -> "$route:$source:$albumId"
        else -> route
    }
