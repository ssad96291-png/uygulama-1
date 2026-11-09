# Android packet engine

This module terminates the VPN's TCP and UDP flows in gVisor and forwards their
unchanged payloads through sockets protected and bound by `VpnService`. IPv4 and
IPv6 use the same quota, pacing, ownership and socket-protection path. TLS is
carried as opaque bytes. The virtual DNS endpoint `10.111.0.1:53` forwards to the
current underlying network's numeric DNS server, preserving the original tuple
for Android application-owner lookup.

Build with Go 1.25.5, JDK 21, Android SDK platform 36 and NDK 28.2.13676358:

```sh
source /workspace/toolchains/android-env.sh # supplied cloud workspace only
./engine/build.sh
```

On another machine, configure `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_NDK_HOME`
and the Go executable on `PATH` before running the script. It installs the exact
gomobile/gobind module version in `go.mod`, runs native race tests and builds an
API 29 AAR for `arm64-v8a`, `armeabi-v7a`, and `x86_64`. Output is copied to
`app/libs/engine.aar`; Gradle consumes this local artifact. Java bindings are in
`dev.akiskontrol.engine.mobile`; Go `int` binds to Java `long`.
Dependency notices are bundled as AAR assets and included in the APK.

`Start` duplicates the caller's TUN descriptor and owns only the duplicate.
Android must supply a nonblocking TUN and keep it nonblocking while active.
`Stop` cancels pending reservations, joins flow workers and closes the duplicate;
Go's poller interrupts outstanding descriptor reads and writes. The Android
service remains responsible for closing its original descriptor. Unknown owners,
denied policies, failed protection, and invalid underlying DNS fail closed.

Tests use two independent IP stacks and local TCP/UDP servers to exercise IPv4,
IPv6, original owner tuples, protected sockets before connect, DNS remapping,
exact quota settlement, atomic datagrams and cancellable pacing. Socketpair tests
exercise TUN descriptor ownership, packet storage lifetime and blocked I/O.
Physical-device VPN and OEM lifecycle behavior still requires device testing.

See `THIRD_PARTY_NOTICES.md` and `licenses/` for bundled dependency licensing.
