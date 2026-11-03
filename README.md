# Follow-Up Board

A board for action items and follow-ups with direct reports. It has a Windows app and an Android app, and both share one Google Sheet.

- **Red glow:** a follow-up is due today or overdue. You can mark it done, or record that you followed up and pick the next date.
- **Blue glow:** the item has no follow-up date yet.
- **Phone notifications, Monday to Friday only:** at 8:00 am if anything is due, plus a reminder at 1:00 pm if it's still due. You can change both times in Settings.

## What's in this folder

| Folder | What it is |
|---|---|
| `web/index.html` | The board itself. Both apps show this same page. |
| `google-apps-script/Code.gs` | The script that connects the apps to your Google Sheet. It goes in the sheet, not on GitHub. |
| `android/` | The Android app (board, sync and notifications). |
| `desktop/` | The Windows app. |
| `.github/workflows/build.yml` | Tells GitHub to build both apps every time you upload. |

## Setup (about 15 minutes, once)

### 1. The Google Sheet

1. Create a new Google Sheet, for example "Follow-Up Board data".
2. Open **Extensions > Apps Script**, delete what's there, and paste in the whole of `google-apps-script/Code.gs`. Save.
3. Choose `setup` in the function dropdown and click **Run**. Approve the permissions when asked. This adds the tabs and creates your access key.
4. Click **Deploy > New deployment**. For the type, pick **Web app**. Set **Execute as: Me** and **Who has access: Anyone**, then click **Deploy**.
5. Copy the **Web app URL** (it ends in `/exec`).
6. Back in the sheet, reload the page. Use **Follow-Up Board > Show connection details** to copy your **access key**.

You'll paste the URL and the key into each app once.

### 2. GitHub builds the apps

1. Create a new **private** repository on GitHub.
2. Click **Add file > Upload files** and drag in everything in this folder, including the `.github` folder. Commit.
3. Open the **Actions** tab and wait for "Build apps" to finish (about 5–8 minutes).
4. Open **Releases** on the right side of the repository page. The newest build has two downloads:
   - `Follow-Up-Board-Setup.exe` for Windows
   - `Follow-Up-Board.apk` for Android

### 3. Install

- **Windows:** run the setup file. If SmartScreen appears, click **More info > Run anyway**.
- **Android:** download the APK on the phone (signed in to GitHub) and open it. Allow "install unknown apps" for your browser when asked. Allow notifications when the app asks.
- In each app, paste the **Web app URL** and **access key** into the connect screen.

### 4. Move your current board over (once)

The Windows app finds the board saved by the earlier offline version. Go to **Import > Copy the board saved on this PC**. You can also choose a `board.json` file there.

## Updating later

Upload the changed files to the same repository. GitHub builds a new release. Install the new APK and setup file over the old ones, and your data stays in the sheet.

If `Code.gs` changes, paste the new version into Apps Script. Then use **Deploy > Manage deployments > Edit (pencil) > Version: New version > Deploy**. The URL stays the same.

## Notes

- Your items live in the Google Sheet, in plain tabs you can read. Avoid editing the `id` column by hand.
- Anyone with both the URL and the key can reach your board. If you need to lock old copies out, use **Make a new access key** in the sheet menu and enter the new key in each app.
- The Android signing key (`android/app/followupboard.keystore`) is in the repository so that each new build installs over the last one. Keep the repository private.
