package top.boluofan.musictv.data.model

import com.google.gson.annotations.SerializedName

data class VersionJson(
    @SerializedName("version") val version: String?,
    @SerializedName("version_code") val versionCode: Int?,
    @SerializedName("git_commit") val gitCommit: String?,
    @SerializedName("build_time") val buildTime: String?,
    @SerializedName("release_notes") val releaseNotes: String?
)

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String? = null,
    val buildTime: String? = null,
    val isDev: Boolean = false
)
