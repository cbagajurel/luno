# Play Protect & SMS permissions

## The warning

Installing Luno from a browser, messaging app, or file manager can surface:

> This app can request access to sensitive data. This can increase the risk of
> identity theft or financial fraud.

This is **not a defect in the app** and not a malware detection. It is Google Play
Protect's _enhanced fraud protection_, a policy heuristic that fires on the
combination of two things:

1. The app declares one of four sensitive permissions — `RECEIVE_SMS`, `READ_SMS`,
   `NOTIFICATION_LISTENER`, `ACCESSIBILITY`; **and**
2. the install came from an "internet-sideloading source" (browser, messaging app,
   file manager).

Luno declares `RECEIVE_SMS` for inbound capture (M11), which is enough to trip it.
Google's stated rationale: over 95% of installs of major fraud-malware families came
from internet-sideloading sources. The heuristic is deliberately crude, so a
legitimate gateway trips it too.

Worth being precise about what does **not** trigger it:

- `SEND_SMS` is not on the list, so it never causes the *install-time* warning.
  This is **not** the same as saying outbound-only builds are unaffected — see
  [Restricted settings](#restricted-settings-a-separate-and-harder-block) below,
  which blocks `SEND_SMS` after install.
- Luno does not declare `READ_SMS`, does not request the default-SMS-handler role,
  and uses no accessibility or notification-listener APIs.

## Restricted settings: a separate, and harder, block

Play Protect's warning is about **installing**. Android's *restricted settings* is a
distinct mechanism about **granting**, and it is the one that stops the gateway from
working at all.

From Android 15, `SEND_SMS` and `RECEIVE_SMS` are **hard-restricted permissions for
any app not installed from an app store**. The permission toggle is greyed out and
the runtime request is auto-denied with no dialog — the user sees "App was denied
access to SMS". Nothing in the APK influences this: it is decided by the install
source, so no manifest flag, `targetSdk`, or code path can opt out of it.

The consequence for the flavor split is blunt: **`sendOnly` installs clean but still
cannot send when sideloaded.** Dropping `RECEIVE_SMS` avoids the install warning and
nothing more.

Recovering on-device, per app: **Settings → Apps → Luno → ⋮ → Allow restricted
settings**, then **Permissions → SMS → Allow**. Or over adb:

```
adb shell cmd appops set com.luno.gateway ACCESS_RESTRICTED_SETTINGS allow
adb shell pm grant com.luno.gateway android.permission.SEND_SMS
```

The app detects this state rather than looping silently: native reports
`PermissionStatus.BLOCKED` (`MainActivity.statusOf`), and the tile explains that
Android refused the last prompt.

`BLOCKED` is treated as a **hint, never a verdict**. Allowing restricted settings
makes the permission grantable again without changing anything the app can observe —
`shouldShowRequestPermissionRationale` still returns false — so a cached blocked
status must never suppress the Grant action, or the user is stranded with no way to
trigger the now-working prompt. `MainActivity.request` therefore always calls
`requestPermissions`, and the UI escalates to the recovery sheet only when a *live*
attempt comes back blocked.

**Play Store installs are exempt**, which is why Play distribution is the supported
path for anything beyond local development.

## Build flavors

Because only the receive side trips the check, the app ships in two flavors:

| Flavor     | `RECEIVE_SMS` | Inbound SMS | Installs clean from any source  |
| ---------- | ------------- | ----------- | ------------------------------- |
| `full`     | declared      | yes         | no — warns on internet-sideload |
| `sendOnly` | absent        | no          | yes                             |

"Installs clean" means exactly that and no more. Neither flavor can be *granted*
`SEND_SMS` when sideloaded on Android 15+ until the user allows restricted settings.

```
flutter build apk --release --flavor full        # complete gateway
flutter build apk --release --flavor sendOnly    # outbound only, installs clean
flutter run --flavor full                        # a flavor is now required
```

`sendOnly` omits both the permission and the `SmsReceiver` registration; the split
lives in `android/app/src/full/AndroidManifest.xml`. Native exposes the choice as
`BuildConfig.RECEIVE_SMS_ENABLED`, surfaced to Dart through the Pigeon call
`isReceiveSmsSupported()` so the settings screen hides a permission it could never
be granted. CI asserts the `sendOnly` APK really has no `RECEIVE_SMS`.

## Install paths that avoid the warning entirely

Enhanced fraud protection only applies to internet-sideloading. These are exempt,
and let you run the `full` flavor without any warning:

- **`adb install`** — the normal path for a self-hosted operator installing on a
  device they control.
- **Managed Google Play / EMM agent (DPC)** — apps installed through an enterprise
  agent, including private apps uploaded to managed Google Play, are not subject to
  it. This is the most reliable route for fleet deployments.
- **Google Play Store** — public Play installs are exempt.

## Play Store distribution

Publishing to Play is the durable fix, but SMS permissions require a **Permissions
Declaration Form** in Play Console and a policy review. Apps that are not the default
SMS handler need an approved exception use case. Two of Google's listed exceptions
plausibly cover Luno:

- **Device automation** — "automation of repetitive actions across OS areas based on
  user-set conditions."
- **Enterprise / CRM** — enterprise messaging systems.

Explicitly **invalid** use cases include account verification via SMS, content
sharing/invites, and social profiling. As of the July 15 2026 policy update, account
verification by phone call is no longer accepted for `READ_CALL_LOG` either; the
Digital Credentials API and SMS Retriever API are the sanctioned alternatives. None
of the invalid cases describe Luno, but a self-hosted gateway is an unusual shape for
review, so budget for iteration.

If a build is flagged and you believe it complies with the Unwanted Software Policy,
Google Play Protect classifications **can be appealed** — see the developer guidance
below.

## References

- [Developer guidance for Google Play Protect warnings](https://developers.google.com/android/play-protect/warning-dev-guidance)
- [Use of SMS or Call Log permission groups](https://support.google.com/googleplay/android-developer/answer/10208820)
- [Permissions and APIs that access sensitive information](https://support.google.com/googleplay/android-developer/answer/16558241)
- [Enhanced fraud protection and enterprise apps](https://bayton.org/android/android-enterprise-faq/enhanced-fraud-protection-enterprise/)
