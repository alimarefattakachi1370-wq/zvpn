# ZVPN (fork of v2rayNG)

Build:
1. Open the `V2rayNG` folder in Android Studio (Ladybug or newer), let Gradle sync.
2. Build Variants -> `playstoreDebug` (or `fdroidDebug`).
3. Run, or Build > Build APK(s). APKs: V2rayNG/app/build/outputs/apk/

Already included: app/libs/libv2ray.aar (Xray core) and app/libs/<abi>/libhev-socks5-tunnel.so.
Changed: app name (ZVPN), applicationId (ir.zdot.vpn), shortcuts target package.
License: GPL-3.0 (source of derived app must remain available).
