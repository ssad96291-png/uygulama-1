# Native engine third-party notices

The Android AAR is built from the pinned modules below. Complete upstream
license texts, applicable Go patent grants and gVisor author credits are retained
in `licenses/`. No modifications are made to the vendored module source; the
application provides its own TUN endpoint, flow relay and Android bridge.

| Component | Version | License | Use |
| --- | --- | --- | --- |
| github.com/xjasonlyu/tun2socks/v2 | v2.6.0 | MIT | Stack construction and TCP/UDP forwarding adapters |
| gvisor.dev/gvisor | v0.0.0-20250523182742-eede7a881b20 | Apache-2.0 | IPv4/IPv6 TCP/IP stack |
| github.com/google/btree | v1.1.3 | Apache-2.0 | gVisor data structure |
| golang.org/x/sys | v0.40.0 | BSD-3-Clause | Native descriptor operations |
| golang.org/x/time | v0.11.0 | BSD-3-Clause | gVisor dependency |
| golang.org/x/mobile | v0.0.0-20260203041319-574ceaa2f723 | BSD-3-Clause | gomobile, gobind and generated Java/native bridge support |
| golang.org/x/mod | v0.32.0 | BSD-3-Clause | Build tooling |
| golang.org/x/sync | v0.19.0 | BSD-3-Clause | Build tooling |
| golang.org/x/tools | v0.41.0 | BSD-3-Clause | Build tooling |
| Go runtime and standard library | go1.25.5 | BSD-3-Clause | Runtime and standard library |

Runtime dependency scope can be reproduced with:

```sh
go list -deps -f '{{if .Module}}{{.Module.Path}} {{.Module.Version}}{{end}}' ./mobile | sort -u
```

The full upstream module graph contains optional tools and platform components
that are not imported by this engine. In particular, WireGuard and Wintun are
not imported or linked into the AAR. The compiled dependency set above uses
MIT, Apache-2.0 and BSD-3-Clause licenses and does not include GPL code.

The tun2socks copyright notice is retained in `licenses/tun2socks-MIT.txt`.
The gVisor license and contributor credits are in `licenses/gvisor-Apache-2.0.txt`
and `licenses/gvisor-AUTHORS.txt`. Google B-tree's license is in
`licenses/btree-Apache-2.0.txt`. Each Go subrepository has its own copied license
and patent grant, and the Go distribution license is `licenses/Go-BSD-3-Clause.txt`.
