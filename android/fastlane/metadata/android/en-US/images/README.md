# Store graphics

`fastlane metadata` uploads everything in this directory. supply matches on
filename, not content, so the names below are exact.

| File                          | Size (px)   | Required | Status                                         |
| ----------------------------- | ----------- | -------- | ---------------------------------------------- |
| `icon.png`                    | 512 × 512   | yes      | present (from `assets/brand/`)                 |
| `featureGraphic.png`          | 1024 × 500  | yes      | **missing** — Play will not publish without it |
| `phoneScreenshots/*.png`      | 1080 × 1920 | yes, ≥2  | present (4)                                    |
| `sevenInchScreenshots/*.png`  | tablet      | no       | deliberately absent — see below                |
| `tenInchScreenshots/*.png`    | tablet      | no       | deliberately absent — see below                |

## No tablet screenshots, on purpose

Play requires only phone screenshots. Tablet sets become worth supplying when an
app targets large screens — Luno does the opposite. Declaring `SEND_SMS` makes
the platform imply a hardware requirement, which `aapt2 dump badging` confirms
on the built APK:

```
uses-feature: name='android.hardware.telephony'
uses-implied-feature: reason='requested a telephony permission'
```

Play filters the listing off any device without telephony hardware, so a
Wi-Fi-only tablet never sees Luno at all. Tablet screenshots would advertise to
an audience that cannot install the app, while implying a form factor it does
not support. Add them only if Luno ever ships a genuine large-screen layout.

## Screenshots are generated, not hand-placed

Don't copy captures in here directly. Put raw device captures in
`assets/screenshots/` named `pair.png`, `dashboard.png`, `messages.png`,
`settings.png`, then run:

```
tool/prepare_screenshots.sh
```

It rewrites this directory from scratch every time, so re-capturing one screen
and re-running is the whole update workflow.

### Why the conversion is not optional

Play rejects any screenshot whose long side exceeds twice its short side. Modern
phones are 20:9 — a raw capture is 2.22 and is **always** rejected. The script
letterboxes each capture onto a 1080 × 1920 canvas (Play's recommended size,
ratio 1.78) padded with the app's own background `#0E1113`, so the bars are
invisible. It also forces PNG24 to drop the alpha channel Play refuses, and
strips metadata that would otherwise leak capture device and timestamps.

Upload order is filename order, and the first screenshot is the one shoppers
actually see. That order is the `ORDER` array in the script, not alphabetical.

### What Play rejects on content

Screenshots must show the real app. Mockups, marketing frames containing UI the
app does not have, and AI-generated interfaces are all grounds for rejection.
Device frames, backgrounds and caption text placed *around* a genuine capture
are fine.

Two things to check before capturing:

- **Show the app working.** A dashboard reading "Disconnected · Agent stopped ·
  Permissions needed" is a truthful capture and a terrible listing. Pair the
  device, start the agent and grant permissions first.
- **No real phone numbers.** Use test recipients; the messages list is the one
  screen that can leak someone's number.

## featureGraphic.png

1024 × 500, no alpha, no transparency. It sits at the top of the listing and is
also used in Play's promotional surfaces. Text near the edges gets cropped on
some layouts — keep the wordmark central.

## icon.png

Regenerate the source with `python3 tool/generate_brand_assets.py`, then copy
`assets/brand/play_store_icon_512.png` here. Play rejects icons with an alpha
channel; the generator already flattens it.
