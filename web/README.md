# Daily Note — web version (Raspberry Pi)

Zero-dependency Node.js server (Node 18+) + installable web app (PWA) for PC and iPhone.
Data is stored as plain files in `DATA_DIR` (default `web/data`): `notes/YYYY-MM-DD.txt`, `free/*.txt|.pin|.icon`, `habits.json`, `shifts.json`, `backups/`.

## Run locally
```bash
cd web
node server.js          # http://localhost:3000
node --test test/       # tests
```
Env: `PORT` (3000), `HOST` (0.0.0.0), `DATA_DIR`.

## Install on the Raspberry Pi
```bash
sudo apt install nodejs        # needs Node 18+ (check: node -v); otherwise use NodeSource / nvm
# copy the `web/` folder to the Pi, e.g. /home/pi/dailynote/web
sudo cp deploy/dailynote.service /etc/systemd/system/   # edit User / paths inside first
sudo systemctl enable --now dailynote
```
Open `http://<pi-address>:3000` from PC or iPhone on the same network.

## iPhone
Safari → Share → *Add to Home Screen*. Opens full-screen like an app.

**HTTPS matters:** service workers (offline app shell) only run on HTTPS or localhost. Over plain `http://` the app still works
and still queues edits locally if the Pi is unreachable while the page stays open, but it won't open offline from the home screen.
Easiest HTTPS without exposing the Pi to the internet: install Tailscale on the Pi + iPhone + PC, then on the Pi
`sudo tailscale serve --bg 3000` and use the `https://<pi>.<tailnet>.ts.net` address.
There is no login: only expose it on your home network or through Tailscale, never directly on the internet.

## Move data from the Android app
The debug build is debuggable, so:
```bash
mkdir android-data && cd android-data && mkdir notes free
adb shell run-as com.nick.dailynote ls files/notes      # list, then cat each file, or:
adb exec-out run-as com.nick.dailynote tar c files shared_prefs > data.tar   # if the device has tar
```
Arrange as `notes/`, `free/`, `habits.json`, `shifts.xml` in one folder, then
```bash
node tools/pack-android.mjs android-data bundle.json
```
and import `bundle.json` in the web app: Settings → นำเข้า. (Also usable as a restore from Settings → ส่งออก.)

## Password
Set one of: env `DN_PASSWORD`, or `"password"` in `DATA_DIR/config.json`. Without it there is no login (home network / Tailscale only).
The app shows a login screen when the API answers 401; the cookie is HttpOnly and lasts one year.

## Calendar (CalDAV, e.g. iCloud)
Create `DATA_DIR/config.json` (read on every request, no restart needed):
```json
{ "caldav": { "url": "https://caldav.icloud.com/", "username": "you@icloud.com", "password": "app-specific-password" } }
```
For iCloud generate an app-specific password at appleid.apple.com. The server discovers your calendars, shows the week, and can add / edit / delete events
(editing keeps alarms and other properties of the original event). Recurring events are shown read-only (edit them in the calendar app).
Only resources inside your own calendars can be modified.

## Reminders (web push)
Settings → แจ้งเตือน: pick daily times (server local time: check `timedatectl` on the Pi), then press *เปิดแจ้งเตือนบนเครื่องนี้* on each device.
Needs HTTPS (see above) and, on iPhone, the app added to the Home Screen (iOS 16.4+). VAPID keys are generated into `DATA_DIR/push.json`.

## Notes editor extras
Toolbar: indent, bullet, todo, check, divider, header, **B** / U / S marks, table, emoji, calculator, and 👁 read mode (tap ☐ to check items).
Tapping the ☐/☑ glyph in the editor also toggles it.

## Not ported
Per-week habit goal UI, shift-to-calendar mirroring, Android-specific settings (fonts, e-ink tuning), multi-line selection for bold/underline.
