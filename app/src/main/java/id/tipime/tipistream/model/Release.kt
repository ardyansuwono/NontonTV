package id.tipime.tipistream.model

class Release {
    var versionCode = 0
    lateinit var versionName: String
    lateinit var changelog: List<String>
    var downloadUrl = ""
}