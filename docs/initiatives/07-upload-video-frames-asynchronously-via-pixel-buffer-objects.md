---
type: perf
status: done
updated: 2026-10-25
tags: [smoothness]
docs_impact:
  - CLAUDE.md
  - docs/RENDERING_PIPELINE.md
---
# Upload video frames asynchronously via pixel buffer objects

## Goal

`VideoPhase` decodes on a background thread into two alternating client-memory `ByteBuffer`s (`display/phase/VideoPhase.java:463`), and `screenRender()` uploads the latest one with a synchronous `glTexSubImage2D` from client memory into the one `videoTex` (`display/phase/VideoPhase.java:543`). The shipped videos are small (426×240 to 712×480, ≤1.4 MB a frame in RGBA), yet JFR showed about as much main-thread native time in that call as in `glfwSwapBuffers` while a video plays: the driver copies the client memory on the GL thread and may stall because the previous frame can still be sampling `videoTex`. Make the upload asynchronous so the GL thread only issues the copy, and measure with `FrameTimeStats` before and after.

## Decisions

- (implementation) **Decode straight into persistently mapped pixel-unpack buffers.** Replace `copyBufA`/`copyBufB` with a small ring (3) of slots in a `GL_PIXEL_UNPACK_BUFFER` created with `glBufferStorage` and mapped persistent + coherent for writing, the same pattern render-core's `PboAudioSink` uses on this GL 3.3 core context with ARB_buffer_storage. The decode thread copies each frame into a free slot and publishes the slot index; the GL thread binds the buffer and calls `glTexSubImage2D` with that slot's offset, so the copy into the texture is a GPU-side transfer and no pixel data passes through the GL thread.
- (implementation) **Fence each slot.** After issuing the upload from a slot, the GL thread inserts a `glFenceSync`; the decode thread does not reuse a slot until its fence has signalled (checked with a zero-timeout `glClientWaitSync` on the GL thread when the slot is handed back), so it never overwrites pixels the GPU has not consumed. When no slot is free the decoder drops that frame, as it effectively does today when the GL thread hasn't taken the previous one.
- (implementation) **Slots are sized up front, and the ring is rebuilt per video.** javacv pads RGBA rows to at most a multiple of 64 pixels (`FFmpegFrameGrabber`'s stride alignment), so a slot of `align64(width) × 4 × height` bytes fits any frame it returns, and the ring is created on the GL thread before the decode thread starts: in `init()`, and on every `loadVideo` (which also drops any frame published for the old video). The old ring is freed only once its decode thread has stopped. A frame that doesn't fit is dropped and logged once.
- (implementation) The slot hand-off is a small lock-free class, `FrameSlots` (`display/phase/`), unit-tested apart from GL: free slots in a queue, the newest published slot in an `AtomicInteger` (a replaced one is recycled), and in-flight slots in GL-thread-only order, released oldest first as their fences signal.

## Benchmark

2026-10-25, fullscreen on eDP-1 (3840×2160@60 Hz), NVIDIA Quadro T2000 via PRIME offload under Xwayland, hands off, 45 s each with the restored session's 1920×1080 30 fps video playing; JFR `profile` recording on the full Corretto 25 JDK. Before: `develop` at 292bcc7; after: this change.

| | Steady-state p95 | p99 | janky per 10 s | Main-thread native samples in `glTexSubImage2D` |
|---|---|---|---|---|
| before (client-memory upload) | 18.9–19.9 ms | 20.0–26.7 ms | 1–6 | 191 of 587 |
| after (pixel-unpack ring) | 17.2 ms | 17.5–17.6 ms | 1 | 1 of 596 |

A further 45 s run switched to a random video every 5 s through the remote API (seven switches between videos of different sizes, from the "medium" encodes up to 2560×1440) with a screenshot every 4 s: no crash, no frame dropped as oversize, and the screenshots show whole, correctly strided video frames. Both builds show ~200 ms frames at each screenshot (read-back and PNG encoding), not at switches.

## Dependencies

_None._

## Open questions

_None._

## Acceptance criteria

- [x] The GL thread makes no `glTexSubImage2D` call with a client-memory pointer for video frames; uploads read from the pixel-unpack buffer.
- [x] Playback looks the same as before (no tearing, no stale or mixed frames) across a video switch and across videos of different sizes.
- [x] A fullscreen benchmark on the built-in panel (eDP-1; DP-4 runs at 24 Hz), hands off, with a video playing shows the main thread's native time in the video upload drop, and frame-time p99/janky no worse than before, recorded in this initiative.
- [x] Project docs (`CLAUDE.md` VideoPhase description, `docs/RENDERING_PIPELINE.md`) describe the upload path.
