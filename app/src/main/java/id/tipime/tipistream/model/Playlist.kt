package id.tipime.tipistream.model

import com.google.gson.annotations.SerializedName

class Playlist {
    var categories: ArrayList<Category> = ArrayList()
    @SerializedName("drm_licenses")
    var drmLicenses: ArrayList<DrmLicense> = ArrayList()
    @SerializedName("message")
    var message: String? = null

    companion object {
        var cached = Playlist()
        var favorites = Favorites()
    }
}