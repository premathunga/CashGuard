# CashGuard Roadmap

## Implemented (July 2026)

- **Party Guard** — `guard/PartyGuardManager.kt` + dashboard card in
  `DashboardScreen.kt`. Cap warnings at 50/80%, alarm-channel alert on every
  transaction past the cap, night velocity detection (3+ debits or Rs 15k in
  an hour, 8 PM–5 AM, 30-min alert cool-down), snooze with 30-second
  cool-down dialog, 8 AM morning summary + auto-disarm
  (`guard/MorningSummaryReceiver.kt`).
- **Truecaller-style popup** — `overlay/TransactionPopupWindow.kt`, a
  TYPE_APPLICATION_OVERLAY card with gradient header, swipe-to-dismiss and
  8 s auto-hide; permission flow + toggle in Settings; plain notification
  remains the fallback.

## Still planned

- **App PIN** — required to disarm Party Guard or silence its alarms, so a
  drunk override needs more than one tap. (The snooze cool-down is the
  current, weaker friction.)
- **Card-freeze shortcuts** — on cap blow-out, one-tap deep link into the
  bank's own app, dial the bank hotline, or copy the freeze USSD/SMS
  command. Needs a per-bank action table (BOC hotline 011-2204444, etc.).
  Reality check: a third-party app cannot disable the card itself.
- **Trusted-friend SMS** — optionally text a chosen contact the running
  damage total once the cap is exceeded (needs SEND_SMS permission).
- **Quick-settings tile** — arm/disarm Party Guard from the notification
  shade (`TileService`).
- **Scheduled auto-arm** — arm automatically on chosen nights (Fri/Sat) and
  on salary days (the 10th/25th cycle dates already live in settings).
- **Branded RemoteViews fallback** — give the no-permission notification the
  same gradient design as the overlay card
  (`DecoratedCustomViewStyle` + RemoteViews).
