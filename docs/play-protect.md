# Play Protect & SMS permissions

## The warning

Installing Luno from a browser, messaging app, or file manager can surface:

> This app can request access to sensitive data. This can increase the risk of
> identity theft or financial fraud.

This is **not a defect in the app** and not a malware detection. It is Google Play
Protect's *enhanced fraud protection*, a policy heuristic that fires on the
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

- `SEND_SMS` is not on the list. Outbound-only builds are unaffected.
- Luno does not declare `READ_SMS`, does not request the default-SMS-handler role,
  and uses no accessibility or notification-listener APIs.

## Build flavors

Because only the receive side trips the check, the app ships in two flavors:

| Flavor | `RECEIVE_SMS` | Inbound SMS | Installs clean from any source |
| --- | --- | --- | --- |
| `full` | declared | yes | no — warns on internet-sideload |
| `sendOnly` | absent | no | yes |

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
