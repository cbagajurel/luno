# Publishing to Google Play

Releases are automated end to end: push a tag, and GitHub Actions builds a
signed AAB, derives a fresh versionCode from what Play already has, uploads it
to the **internal** track, and cuts a GitHub Release carrying the sideloadable
`sendOnly` APK. Promotion to production stays manual and deliberate.

The one-time setup below is not automatable — Play Console has no API for
creating an app, and the permissions declaration is a human review.

---

## Read this before anything else: the SMS permission problem

Luno declares `SEND_SMS` and `RECEIVE_SMS`. Both are **restricted permissions**
under Play's [Use of SMS or Call Log permission
groups](https://support.google.com/googleplay/android-developer/answer/10208820)
policy, and this is the single largest risk to publishing.

The policy grants these permissions to apps that are the device's **default SMS,
Phone, or Assistant handler**, plus a short exception list — backup/restore,
device automation, companion-device apps, connected-device messaging. **A
self-hosted SMS relay is not on that list.** Expect the declaration to be
scrutinised, and plan for rejection as a realistic outcome rather than an
unlikely one.

Three things that are commonly misunderstood:

- **The testing tracks are not a loophole.** The declaration applies to internal,
  closed and open testing alike. Until it clears, you cannot publish *any*
  change — including edits to the store listing.
- **Rejection is not a dead end.** [Managed Google
  Play](https://support.google.com/googleplay/work/answer/6145139) private-app
  publishing distributes to your own organisation only, does not go through
  public policy review, and uses the identical Publisher API — every lane in
  this repo works unchanged against it.
- **Play distribution is worth the fight.** Sideloaded builds on Android 15+
  cannot be granted SMS permissions at all; the toggle is greyed out. See
  [`play-protect.md`](play-protect.md). Play is the only path to an app that
  simply works on a fresh device.

When you fill in the declaration form, the argument to make is the one already
written into `full_description.txt`: SMS is not a feature of the app, it is the
entire app, acting solely on behalf of the device's owner, sending message
content to no party other than the backend that owner configured.

---

## One-time setup

### 1. Generate the upload key

```
tool/generate_upload_keystore.sh
```

Writes `android/upload-keystore.jks` and `android/key.properties`, both
gitignored, and prints the base64 blob you will paste into GitHub secrets.

Back the keystore up. With Play App Signing enabled (step 2) a lost upload key
is recoverable via a support request rather than fatal — but it still costs you
days.

### 2. Create the app in Play Console

At [play.google.com/console](https://play.google.com/console):

1. **Create app** — name "Luno", English (US), App, Free.
2. **Release → Setup → App signing** — accept **Play App Signing**. Google holds
   the real signing key; your upload key only authenticates uploads.
3. Complete **App content**: privacy policy `https://www.nexneotech.com/privacy`,
   ads declaration, content rating questionnaire, target audience, data safety.
4. In **Data safety**, set the account-deletion URL to
   `https://oss.nexneotech.com/luno/legal/data-deletion`.
5. **App content → Sensitive app permissions** — complete the Call log & SMS
   Permissions Declaration Form. See the warning above.

### 3. Create the service account

1. Play Console → **Setup → API access** → link (or create) a Google Cloud
   project.
2. **Create new service account**, which takes you to Google Cloud Console.
   Create it, then **Keys → Add key → JSON** and download.
3. Back in Play Console, **grant access** to that service account with the
   **Release manager** role, scoped to Luno.
4. Store the JSON outside the repo. Point `SUPPLY_JSON_KEY` at it.

Permission propagation takes a few minutes. If `fastlane validate` returns a
403 immediately after granting, wait and retry before debugging anything.

### 4. Add GitHub secrets

Under **Settings → Environments → `play-store`** (create the environment; a
required reviewer on it gives you an approval gate on every publish).

The keystore is a binary file and GitHub secrets hold only text, so it travels
as base64: the generator writes that text to `upload-keystore.jks.base64`, you
paste it in, and the workflow runs `base64 -d` to reconstruct the exact file on
the runner.

There is no file-upload option — every secret is text. Run each command from the
repo root to put the exact value on the clipboard, then paste it into GitHub:

```bash
# PLAY_KEYSTORE_BASE64        (~5 KB, one line)
pbcopy < android/upload-keystore.jks.base64

# PLAY_KEYSTORE_PASSWORD      (40 chars)
grep storePassword android/key.properties | cut -d= -f2- | tr -d '\n' | pbcopy

# PLAY_KEY_ALIAS
printf 'upload' | pbcopy

# PLAY_KEY_PASSWORD           (same value as the store password)
grep keyPassword android/key.properties | cut -d= -f2- | tr -d '\n' | pbcopy

# PLAY_SERVICE_ACCOUNT_JSON   (the file downloaded from Google Cloud Console)
pbcopy < "$SUPPLY_JSON_KEY"
```

`tr -d '\n'` is load-bearing on the two passwords. A trailing newline is
invisible in the GitHub UI, rides along into the secret, and surfaces later as a
wrong-password failure that points nowhere near the actual cause.

On Linux, substitute `xclip -selection clipboard` for `pbcopy`.

Paste the base64 as a single line. A stray newline is harmless — `base64 -d`
ignores whitespace. A *truncated* paste is not: it decodes into a partial
keystore and the workflow's `keytool -list` check fails with
`keytool error: java.io.EOFException`. If you see that, the secret is short, not
wrong.

Delete `android/upload-keystore.jks.base64` once the secret is saved. The
keystore itself is the copy worth keeping.

### 5. Fill in the store graphics

Drop raw device captures into `assets/screenshots/` and run:

```
tool/prepare_screenshots.sh
```

That letterboxes each one onto a Play-compliant 1080 × 1920 canvas — a raw 20:9
capture is 2.22 and Play rejects anything above 2.0 — and writes them into
`android/fastlane/metadata/android/en-US/images/phoneScreenshots/`.

The icon is already in place. **`featureGraphic.png` (1024 × 500) is still
missing and Play will not publish without it.** See that directory's
`README.md` for the remaining requirements.

---

## Releasing

### Check everything works, publish nothing

```
cd android && bundle exec fastlane validate
```

Builds a signed AAB and runs the upload in `validate_only` mode. This is the
lane that catches a debug-signed build, a bad credential, or metadata that
breaks Play's limits — before a versionCode is burned.

The same dry run is available in CI: **Actions → Android Release → Run
workflow**, leaving "validate_only" checked.

### Ship to the internal track

```
git tag v1.0.0 && git push origin v1.0.0
```

The tag must match `version:` in `pubspec.yaml` or the workflow fails before
building anything. From there:

```
tag push → verify tag/pubspec agree → flutter test → build signed AAB
         → upload to internal track → GitHub Release with the sendOnly APK
```

### Promote

Never automatic. When the internal build has been on real devices and you are
satisfied:

```
cd android
bundle exec fastlane promote from:internal to:production rollout:0.1
```

Starts at a 10% staged rollout. Raise it from Play Console, or re-run with a
higher `rollout:`.

### Update the store listing only

```
cd android && bundle exec fastlane metadata
```

Pushes text, icon, feature graphic and screenshots with no binary. Release lanes
deliberately skip metadata so shipping a build never silently rewrites the
listing.

---

## How versionCode is decided

`pubspec.yaml`'s `+1` build number is not maintained per release and a CI run
number resets if the repo moves — either would eventually collide with a code
Play has already accepted, which is permanently unrecoverable for that number.

So the Fastfile asks Play instead: it reads the highest versionCode across
`internal`, `alpha`, `beta` and `production`, and adds one. No bookkeeping, no
drift, correct on a brand-new app (where every track is empty and it starts at
1). Set `LUNO_BUILD_NUMBER` to override, which you should only need when
reproducing a specific historical build.

`versionName` always comes from `pubspec.yaml`.

## Which flavour goes where

`full` and `sendOnly` share the applicationId `com.luno.gateway`, so only one
can occupy the Play listing.

| Flavour | Channel | Why |
| --- | --- | --- |
| `full` | Play Store | Play installs are exempt from the restricted-settings block, so this is the build that actually works |
| `sendOnly` | GitHub Releases | Installs without a Play Protect warning; exists purely for sideloading |

## Troubleshooting

**`Package not found: com.luno.gateway`** — the first upload of an app must be
made by hand through Play Console. The API cannot create the listing. Upload one
AAB manually, then every subsequent release automates.

**`APK signed with a debug certificate` / `Your upload key is invalid`** —
Gradle fell back to the debug key. `require_release_signing!` normally catches
this; if it did not, check that `android/key.properties` exists or that
`LUNO_KEYSTORE_PATH` is set and readable.

**`Version code N has already been used`** — someone uploaded outside CI, or
`LUNO_BUILD_NUMBER` is pinned in the environment. Unset it and let the Fastfile
query Play.

**403 from the Publisher API** — the service account lacks Release manager on
this app, or the grant has not propagated yet.
