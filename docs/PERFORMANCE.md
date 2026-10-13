# Rendering measurements

The 1.4 renderer reuses its ARGB canvas and pixel arrays, caches its gradient background, converts pixels on the worker, and copies the upload buffer in bulk. GUI surfaces share a cached antialiased texture atlas. Text components and fitted labels use bounded caches.

## Local allocation experiment

The 1.5 animation update adds a cached light texture at the physical display resolution. Moving light uses direct pixel blits instead of resampling a large translucent image on every frame. A 2026-10-13 run of the same 1050 × 762 benchmark with ambient drift and the artwork halo enabled measured **10.635 ms/frame and 221,111 Java bytes/frame** with reused buffers, versus **12.698 ms/frame and 6,623,127 bytes/frame** with fresh buffers. This is a raster-only measurement, not Minecraft FPS. Existing update/density limits remain in effect.

On 2026-10-11, the optional `RenderingPerformanceTest.reportRasterAllocationAndTime` compared allocating a fresh surface against reusing one in the same renderer. It renders a 1050 × 762 Lyric Lounge card at density 3 with original example lyrics, warms up the renderer, and measures 60 frames per path using the JVM's thread allocation counter.

- Fresh surface: 6,616,092 allocated bytes per frame; 7.942 ms per frame.
- Reused surface: 212,827 allocated bytes per frame; 7.194 ms per frame.
- Reduction in measured Java allocation: approximately 96.8%.

These measurements isolate Java2D raster work on this development machine. They exclude Minecraft world rendering, texture upload, GPU time, native allocations, and media-bridge work. Timing varies with hardware, warmup, and other activity. They do not establish an in-game FPS improvement or a universal frame-time target.

To repeat the opt-in measurement in PowerShell:

```powershell
$env:BETTERMUSIC_BENCHMARK = '1'
.\gradlew.bat :mc12111:test --tests '*RenderingPerformanceTest' --rerun-tasks
Remove-Item Env:BETTERMUSIC_BENCHMARK
```

Regular builds skip the benchmark. Rendering presets cap overlay updates at 30 / 20 / 10 fps and raster density at 4 / 3 / 2 respectively. With Motion disabled, the renderer checks at 5 Hz and skips unchanged cards. Balanced is the default. Choose Quality for high GUI scales; choose Saver to reduce the overlay's raster and upload workload.
