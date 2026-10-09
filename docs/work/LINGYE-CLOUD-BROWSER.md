# Lingye cloud-browser QA reachability probe

## Result

Actual UI QA is **blocked, not passed**. The managed cloud Chrome/CDP browser is available, but it cannot reach the isolated frontend fixture started in the command execution environment. No desktop/mobile visual assertions or screenshots were obtained. Source tests and collected browser scenarios are not treated as browser execution evidence.

## Scope and environment

- Probe date: 2026-10-09 UTC
- Repository: `nexus-mainline-r11`, working tree based on `cef2be1` with the concurrent site-presence/static-identity changes still present
- Browser: supported `cua_repl` managed cloud `cdp` browser, not the user's computer
- Frontend: Node 22.23.3, Vite 7.3.6; `VITE_COMPANION_ENABLED=true`; intended isolated API target `http://127.0.0.1:18124`
- No backend, model, or production credentials were used for this reachability probe

## Observed evidence

1. Initial browser navigation before a server was started returned `net::ERR_CONNECTION_REFUSED`; this alone was not taken as an environment conclusion.
2. Starting Vite with `--host 0.0.0.0` exited while resolving display URLs with `uv_interface_addresses` error 1. No network/security settings were changed.
3. Starting Vite normally with `--host 127.0.0.1 --port 5173 --strictPort` succeeded and printed its ready URL. The original command session remained live.
4. A separate execution's normal loopback curl returned connection refused and did not see the Vite process.
5. A fresh owned Vite process plus curl in the **same execution session** conclusively returned `SAME_EXEC_HTTP=200` and `VITE_PID=5`. Vite was kept alive for the browser check.
6. While that session remained live, supported `cua.createBrowserTab('cdp', 'http://127.0.0.1:5173')` again returned `net::ERR_CONNECTION_REFUSED` (tab 5).

The evidence is consistent with command/browser network namespace isolation. It establishes that the managed browser cannot reach this loopback fixture through the supported normal-navigation route; it does not establish a frontend regression or a missing browser.

## Cleanup and limits

Both owned long-running Vite command sessions were interrupted and returned exit code 130. The final shell had an EXIT/INT/TERM trap to kill its owned child. No Redis/backend service was started. Backend fixture owner was told not to start services for this probe.

No alternate browser launch, security/sandbox change, port forwarding, tunnel, or browser script injection was attempted. A failed initial navigation left an internal browser error page; attempting to select that error tab was rejected for its unsupported URL protocol, and it was not bypassed.

## Still unverified in an actual browser

- Default-off site presence, including no presence API activity
- Static/no-blink rendering and public 绫页 naming
- Explicit opt-in DEMO and rehearsal scenarios
- Reply opening ordinary chat without auto-send
- Close/pause behavior and stale-response handling
- Desktop/mobile layout and screenshot review

These require a permitted fixture URL reachable by the managed browser, or another explicitly authorized suitable environment. No full acceptance-matrix claim is made.
