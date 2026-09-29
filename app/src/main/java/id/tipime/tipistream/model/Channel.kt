package id.tipime.tipistream.model

import com.google.gson.annotations.SerializedName

class Channel {
    var name: String? = null
    @SerializedName("logo_url")
    var logoUrl: String? = null
    @SerializedName("stream_url")
    var streamUrl: String? = null
    @SerializedName("drm_id")
    var drmId: String? = null
    @SerializedName("manifest_type")
    var manifestType: String? = null
    @SerializedName("user_agent")
    var userAgent: String? = null
    @SerializedName("referer")
    var referer: String? = null
    @SerializedName("origin")
    var origin: String? = null
}