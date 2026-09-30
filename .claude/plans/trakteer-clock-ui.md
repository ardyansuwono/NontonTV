# Rencana Implementasi — Jam + Running Text Trakteer Bergantian di Banner Bawah

## Keputusan user (final)

1. **Jam** diletakkan di **banner bawah** (band), bukan sidebar — sesuai teks "jam yang
   singkron" di mockup.
2. **Banner rotasi**: pesan playlist dan pesan Trakteer **bergantian** tampil (bukan digabung).
3. Trakteer via **WebSocket real-time** (WS notifikasi + re-fetch REST, sama seperti app web
   Trakteer).
4. Token Trakteer **bisa diubah di Settings**, default `trv2-…` di `strings.xml`.

## Protokol Trakteer (sudah diverifikasi)

- REST: `GET https://ws.trakteer.id/api/overlay/running-text?stream_key=<token>` →
  `{"session":{"is_paused","is_hidden"},"items":[{supporter_name, support_message, is_anonym}],"mode":"latest"}`
  (dikonfirmasi 200 OK dengan token user; `stream_key` == token `trv2-…`).
- WS: `wss://ws.trakteer.id/ws?token=<token>`; pesan `{"type":"running-text:refresh"}` → re-fetch.
- Anonim → "Seseorang" (menyusul fungsi `Xs()` app web Trakteer).

---

## Langkah 1 — Preferences

**File:** `app/src/main/java/id/tipime/tipistream/extra/Preferences.kt`

```kotlin
companion object { …
    private const val TRAKTEER_TOKEN = "TRAKTEER_TOKEN"
    private const val TRAKTEER_ENABLED = "TRAKTEER_ENABLED"
}

var trakteerToken: String
    get() = preferences.getString(TRAKTEER_TOKEN,
        context.getString(R.string.trakteer_token_default)).toString()
    set(value) { editor.putString(TRAKTEER_TOKEN, value).apply() }

var trakteerEnabled: Boolean
    get() = preferences.getBoolean(TRAKTEER_ENABLED, true)
    set(value) { editor.putBoolean(TRAKTEER_ENABLED, value).apply() }
```

## Langkah 2 — Strings

**File:** `app/src/main/res/values/strings.xml` (dan `values-in/strings.xml`)

```xml
<string name="trakteer_token_default" translatable="false">trv2-hYkvTk5qIqwi0j461tjZgXB0ZuZ5wdrCm6Q06eouLJ8oRhOkcBxzrqv</string>
<string name="trakteer_running_text">Trakteer Running Text</string>
<string name="trakteer_token">Trakteer Token</string>
<string name="trakteer_token_hint">Token widget running-text dari overlay.trakteer.id</string>
<string name="trakteer_enable">Tampilkan running text Trakteer</string>
<string name="trakteer_empty">Belum ada dukungan</string>
```

## Langkah 3 — Model data

**File baru:** `app/src/main/java/id/tipime/tipistream/model/TrakteerRunningText.kt`

```kotlin
class TrakteerItem {
    var id: String? = null
    var supporter_name: String? = null
    var support_message: String? = null
    var is_anonym: Boolean = false
}
class TrakteerSession { var is_paused = false; var is_hidden = false }
class TrakteerRunningText {
    var session: TrakteerSession? = null
    var items: ArrayList<TrakteerItem>? = null
    var mode: String? = null
}
```

## Langkah 4 — TrakteerClient (WebSocket + REST)

**File baru:** `app/src/main/java/id/tipime/tipistream/extra/TrakteerClient.kt`

- OkHttp WebSocket ke `wss://ws.trakteer.id/ws?token=<token>`.
- `onOpen` + tiap pesan masuk → GET REST (pakai `HttpClient(useCache=false)` yang sudah ada,
  atau client OkHttp baru) → parse Gson → callback `(List<String> messages, hidden: Boolean)`.
- `session.is_hidden == true` → callback list kosong (sembunyikan).
- Reconnect backoff 3s → maks 30s, berhenti setelah beberapa kali gagal (token invalid) +
  toast singkat. `fun stop()` menutup bersih.
- Format pesan: `"<name>: \"<message>\""` (name → "Seseorang" bila anonym/kosong).

## Langkah 5 — Layout: banner bawah = jam + running text berotasi

**File:** `app/src/main/res/layout/activity_main.xml`

Ganti `TextView#banner` tunggal menjadi `LinearLayout` horizontal di band cyan:

```xml
<LinearLayout android:id="@+id/banner"
    android:layout_width="0dp" android:layout_height="wrap_content"
    android:orientation="horizontal" android:gravity="center_vertical"
    android:background="@color/brand_indigo_dark"
    android:paddingTop="8dp" android:paddingBottom="8dp"
    android:paddingStart="16dp" android:paddingEnd="16dp"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintBottom_toBottomOf="parent">

    <TextClock android:id="@+id/clock"
        android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:format12Hour="HH:mm" android:format24Hour="HH:mm"
        android:textColor="@android:color/white" android:textSize="14sp"
        android:textStyle="bold" android:layout_marginEnd="16dp"
        tools:ignore="TextClockDeprecation" />

    <View android:layout_width="1dp" android:layout_height="20dp"
        android:layout_marginEnd="16dp" android:background="#33FFFFFF"
        android:visibility="gone" />  <!-- divider muncul saat ada running text -->

    <ViewFlipper android:id="@+id/banner_flipper"
        android:layout_width="0dp" android:layout_height="wrap_content"
        android:layout_weight="1" android:autoStart="true"
        android:flipInterval="8000" android:inAnimation="@null" android:outAnimation="@null">
        <!-- child TextView marquee dibangun dinamis di kode -->
    </ViewFlipper>
</LinearLayout>
```

Setiap child = `TextView` marquee (`ellipsize="marquee"`, `singleLine`, `isSelected=true`).
`ViewFlipper` otomatis berganti tiap 8 detik → efek "rotate" antar pesan.

## Langkah 6 — MainActivity

**File:** `app/src/main/java/id/tipime/tipistream/MainActivity.kt`

- Ganti `setBanner(message)` → `updateBanner()`:
  - Simpan `playlistMessage` (dari `Playlist.cached.message`) dan `trakteerMessages` di field.
  - Bangun ulang child `ViewFlipper`: [pesan playlist?] + [pesan Trakteer…] (tiap Trakteer item
    jadi 1 child). Kalau kosong total → `banner.visibility = GONE`.
  - Tiap child TextView marquee `isSelected = true`; saat ditampilkan set focus agar marquee jalan.
- `onCreate`: mulai `TrakteerClient` bila `preferences.trakteerEnabled`; token dari preferences.
  - Callback (main thread) → `trakteerMessages = …` → `updateBanner()`.
- `onDestroy`: `trakteerClient?.stop()`.
- Tambah callback `TRAKTEER_CHANGED` di `broadcastReceiver` untuk reconnect setelah setting diubah.

## Langkah 7 — Setting UI

**File:** `app/src/main/res/layout/setting_app_fragment.xml`
Section "Trakteer": `SwitchCompat#trakteer_enabled` + `EditText#trakteer_token`
(hint dari string; `inputType="textVisiblePassword"` agar token terbaca jelas).

**File:** `app/src/main/java/id/tipime/tipistream/dialog/SettingAppFragment.kt`
Companion: `var trakteerEnabled = true`, `var trakteerToken = ""`; bind di `onCreateView`.

**File:** `app/src/main/java/id/tipime/tipistream/dialog/SettingDialog.kt`
Init di `onCreateView`:
```kotlin
SettingAppFragment.trakteerEnabled = preferences.trakteerEnabled
SettingAppFragment.trakteerToken = preferences.trakteerToken
```
Saat OK: simpan keduanya; bila berubah → broadcast `TRAKTEER_CHANGED`.

## Langkah 8 — Build

```
cd /workspaces/NontonTV && export JAVA_HOME=/home/codespace/java/21.0.12+1-ms && \
export ANDROID_HOME=$HOME/android-sdk && ./gradlew :app:assembleDebug
```

## Catatan

- Tidak ada dependensi baru (OkHttp + Gson sudah ada; `TextClock` & `ViewFlipper` dari platform).
- minSdk 21 → `TextClock` (API 17+) aman.
- `is_paused` tidak menyembunyikan (itu hanya jeda animasi web); hanya `is_hidden` yang sembunyikan.
- Token invalid → reconnect berhenti setelah N gagal + toast, tidak spam jaringan.
