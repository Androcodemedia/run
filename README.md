# Run Analyst — Android (Kotlin Native)

Aplikasi analisa latihan lari untuk Android: Kotlin + Jetpack Compose, data dari **Health Connect**
(gratis — dari Huawei Health / Strava yang menulis ke Health Connect), tanpa API berbayar.

Fitur: tren jarak mingguan, training load, konsistensi, pace, rekomendasi otomatis (Bahasa Indonesia).

## Persiapan (sekali saja)
1. Install **Android Studio** (dengan SDK 35) — developer.android.com/studio
2. Di HP: install **Health Connect** dari Play Store, lalu di Huawei Health aktifkan
   *Pengaturan → Sinkronisasi data ke Health Connect* (izinkan semua)
3. Strava juga bisa menulis ke Health Connect jika diinginkan

## Cara build
**Opsi A — di Android Studio:**
1. Buka folder proyek ini (Open → pilih folder `runanalyst-android`)
2. Tunggu Gradle sync selesai
3. Run ▶ dengan HP tersambung (aktifkan USB debugging), atau Build → Build APK

**Opsi B — otomatis via GitHub (tanpa install apa pun):**
1. Buat repo GitHub, push folder ini (`git init && git add . && git commit -m "init" && git push`)
2. Buka tab **Actions** di repo → workflow "Build APK" jalan otomatis
3. Setelah selesai, unduh artifact **RunAnalyst-debug-apk** → file APK → pasang di HP

## Catatan
- Data dibaca langsung di HP (session lari + jarak + HR). Tidak ada data yang dikirim keluar.
- Zona intensitas dihitung dari pace (5:00/km = keras, >5:30 = easy) — bisa disesuaikan di `Analysis.kt` (`paceToIntensity`… lihat `RunActivity.intensity`).
- `readRuns(180)` = 180 hari terakhir; ubah sesuai kebutuhan di `HealthConnectManager.kt`.
