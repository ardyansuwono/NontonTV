package id.tipime.tipistream.model

import com.google.gson.annotations.SerializedName

/**
 * Trakteer "running text" REST v2 responses.
 *
 * Two endpoints share the same wrapper fields but use a different list key:
 *
 * `GET stream/<key>/top-supporters` →
 * ```
 * { "pageUrl": "...", "unitName": "Permen", "supporter": [ { "supporter_name": "Febri", "sum": 24000, ... } ] }
 * ```
 *
 * `GET stream/<key>/latest-tips` →
 * ```
 * { "pageUrl": "...", "unitName": "Permen", "latestTip": [ { "display_name": "Ghalang", "support_message": "Server down bang", ... } ] }
 * ```
 *
 * Only the fields we display are declared. [items] returns whichever list the endpoint used.
 */
class TrakteerTopSupporter {
    @SerializedName("supporter")
    var topSupporters: ArrayList<Supporter>? = null

    @SerializedName("latestTip")
    var latestTips: ArrayList<Supporter>? = null

    @SerializedName("unitName")
    var unitName: String? = null

    @SerializedName("pageUrl")
    var pageUrl: String? = null
}

class Supporter {
    @SerializedName("supporter_name")
    var name: String? = null

    @SerializedName("display_name")
    var displayName: String? = null

    @SerializedName("sum")
    var sum: Double = 0.0

    @SerializedName("support_message")
    var supportMessage: String? = null

    @SerializedName("quantity")
    var quantity: Int = 0

    @SerializedName("avatar")
    var avatar: String? = null
}
