package top.boluofan.musictv.data.model

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("username") val username: String? = null
)

data class AuthVerifyResponse(
    @SerializedName("valid") val valid: Boolean = false,
    @SerializedName("username") val username: String? = null
)

data class SimpleResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null
)
