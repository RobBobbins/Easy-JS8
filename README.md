# Easy JS8

by VA3OSO

Easy JS8 is a fork of JS8Android with added features and a new screen layout.

## Status

Early test version. Tested on the Android emulator only, not on a real phone or radio yet.
Test transmitting on a dummy load first.

## Added

- New layout: one scrolling page when the phone is upright; PC-style or swipe screens when it's sideways
- Settings window: speed, frequency, offset, MULTI / AUTO / HB / ACK
- Station window: callsign, grid, radio, audio and the rest, one page at a time
- Acquire GPS for your grid, SWL (Listening only) mode
- Tap the waterfall to set the offset
- JS8 Slow is now decoded
- APRS forms (Saved > APRS EMAIL / TXT / GRID / MSG)
- FT8 decodes in the Message Window
- Press-and-hold callsign menu in Call Activity
- Waterfall Controls: Control, Timing and Display tabs, palette designer
- Sending part by part, sent text crossed out
- API: UDP and TCP, the PC's commands and messages
- WSJT-X protocol, N3FJP and N1MM logging
- Log QSO and ADIF log, worked-before column
- Mailbox: messages held for other stations, QUERY MSGS / QUERY MSG
- Export Options: log and Message Inbox to a folder you choose
- HB and CQ buttons with repeat timers

## Building

Install Docker Desktop, then in Git Bash, in the project folder:

```
docker build -t easyjs8-build build-env
build-env/test/docker-build.sh
```

The first line is needed only once. The APK is written to
`android/app/build/outputs/apk/debug/app-debug.apk`. Details: `build-env/build.cmd.txt`.

## Credits

- JS8Android: [github.com/JS8Call-improved/Android-port](https://github.com/JS8Call-improved/Android-port)
- JS8Call: Jordan Sherer KN4CRD and the JS8Call-improved team,
  [github.com/JS8Call-improved/JS8Call-improved](https://github.com/JS8Call-improved/JS8Call-improved)
- WSJT-X: Joe Taylor K1JT and the WSJT Development Group. JS8 is based on their FT8 mode.
- Fonts: Selawik and Cascadia Mono (SIL Open Font License, see `fonts-licenses/`)

## Notice

Easy JS8 is a fork of JS8Android, which is a port of JS8Call, which is a derivative of WSJT-X.
The WSJT-X, JS8Call and JS8Android developers keep copyright over their original work. Easy JS8
is a derivative work released under the GPLv3.

## Licence

GPLv3, the same as JS8Call and JS8Android. See [LICENSE](LICENSE).
