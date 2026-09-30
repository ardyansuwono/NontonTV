package id.tipime.tipistream.model

class TrakteerItem {
    var id: String? = null
    var supporter_name: String? = null
    var support_message: String? = null
    var is_anonym: Boolean = false
}

class TrakteerSession {
    var is_paused: Boolean = false
    var is_hidden: Boolean = false
}

/** Response of GET https://ws.trakteer.id/api/overlay/running-text?stream_key=<token> */
class TrakteerRunningText {
    var session: TrakteerSession? = null
    var items: ArrayList<TrakteerItem>? = null
    var mode: String? = null
}
