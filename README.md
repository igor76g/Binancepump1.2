# Binance Pump Alerts (Android, Kotlin)

Monitors all Binance USDT-M perpetual futures and sends a phone notification with the coin ticker when:

- 24h quote volume >= **30 million USDT** (editable in the app), AND
- price growth >= **4%** within **5 minutes** (editable in the app).

Uses only PUBLIC Binance Futures endpoints (`/fapi/v1/exchangeInfo`, `/fapi/v1/ticker/24hr`,
`/fapi/v1/ticker/price`). No API key is needed.

## Build

1. Open the `BinancePump` folder in Android Studio (Koala / 2024.1 or newer).
2. Let Gradle sync finish (Android Studio uses its own Gradle and downloads dependencies).
3. Connect a phone (USB debugging on) or start an emulator and press Run.
   To get an APK: Build > Build Bundle(s) / APK(s) > Build APK(s).

## Use

1. Open the app, adjust the settings, tap **Save settings**.
2. Tap **Start monitoring** and allow notifications.
3. The service keeps running in the background (a permanent status notification is shown).
   Tap **Stop monitoring** to stop it.

Settings are re-read on every check, so saved changes apply immediately without restarting.

## Settings

| Setting | Default | Meaning |
|---|---|---|
| Minimum 24h volume (million USDT) | 30 | Volume filter |
| Volume list refresh interval (s) | 60 | How often the list of qualifying coins is refreshed |
| Minimum price growth (%) | 4 | Pump threshold |
| Time window (minutes) | 5 | Window used to measure growth |
| Price check interval (s) | 15 | How often prices are polled |
| Alert cooldown per coin (min) | 15 | No repeated alerts for the same coin within this time |

## Tips for reliable background work

- In Android settings, set the app's battery usage to **Unrestricted**, otherwise some phones
  (Xiaomi, Huawei, Samsung, etc.) may kill the service when the screen is off.
- Binance public endpoints may be blocked by some networks/countries: if the status line shows
  HTTP 451/403 errors, use a different network or a VPN.

## Alerts with the screen off (v1.1)

The service now holds a CPU + Wi-Fi wake lock while monitoring, and on Start the app asks to be excluded
from battery optimization - tap **Allow**. If alerts still stop with the screen off, set
Settings > Apps > Binance Pump > Battery > **Unrestricted** (and on Xiaomi/Huawei/Samsung/OnePlus also enable
"Autostart" / disable "Put app to sleep"). The Wi-Fi sleep policy of the phone should be "Keep Wi-Fi on during sleep".
Alert notifications are also shown on the lock screen.

## Mirror mode for shorts (v1.2)

New setting **Alert on**: *Growth (+)*, *Drop (-), mirror mode for shorts*, or *Growth and drop* (default).
One threshold ("Minimum price move") is used for both directions. A drop alert looks like
"🔻 BTCUSDT -4.35%" / "DUMP (short idea): price fell 4.35% in 5 min"; growth alerts are "🚀 ... PUMP (long idea)".
The cooldown is kept separately for growth and drop of the same coin.
