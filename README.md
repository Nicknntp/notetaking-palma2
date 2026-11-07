# Daily Note (notetaking-palma2)

Daily note app for the Boox Palma 2 (e-ink, Android 13). Kotlin + Jetpack Compose, package `com.nick.dailynote`.

- Daily notes with bullets, to-dos, headings and dividers
- Week calendar that reads/writes the Android calendar (e.g. iCloud synced via DAVx5)
- Habits and reminders (notifications, multiple times per day)

## Build

Requires JDK 17 or 21 (Gradle 8.7 does not run on newer Java) and the Android SDK.

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
.\gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
Install on the device with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Every push to `main` is also built by GitHub Actions; the debug APK can be downloaded from the run's **Artifacts** section.

> Keep the project in a path with English characters only, e.g. `D:\Projects\DailyNote`.

## คีย์ลัด

| พิมพ์ | ผลลัพธ์ |
|---|---|
| `- ` | bullet |
| `[] ` | ☐ (แตะเพื่อติ๊ก) |
| `---` | เส้นคั่น |
| `# ` `## ` `### ` | หัวข้อ |
| `"` | “ ” |

**Toolbar:** ⇥ เพิ่มระดับ · ⇤ ลดระดับ · • bullet · ☐ todo · ─ เส้นคั่น

**บนสุด:** 📅 Week ปฏิทิน · ⚙ ตั้งค่า (เด้งไปวันนี้ / แจ้งเตือนหลายเวลา)
