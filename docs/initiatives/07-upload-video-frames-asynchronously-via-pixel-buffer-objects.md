---
type: perf
status: designed
updated: 2026-10-25
tags: [smoothness]
---
# Upload video frames asynchronously via pixel buffer objects

## Goal

`VideoPhase` decodes on a background thread into two alternating client-memory `ByteBuffer`s (`display/phase/VideoPhase.java:463`), and `screenRender()` uploads the latest one with a synchronous `glTexSubImage2D` from client memory into the one `videoTex` (`display/phase/VideoPhase.java:543`). The shipped videos are small (426×240 to 712×480, ≤1.4 MB a frame in RGBA), yet JFR showed about as much main-thread native time in that call as in `glfwSwapBuffers` while a video plays: the driver copies the client memory on the GL thread and may stall because the previous frame can still be sampling `videoTex`. Make the upload asynchronous so the GL thread only issues the copy, and measure with `FrameTimeStats` before and after.

## Decisions

- (implementation) **Decode straight into persistently mapped pixel-unpack buffers.** Replace `copyBufA`/`copyBufB` with a small ring (3) of slots in a `GL_PIXEL_UNPACK_BUFFER` created with `glBufferStorage` and mapped persistent + coherent for writing, the same pattern render-core's `PboAudioSink` uses on this GL 3.3 core context with ARB_buffer_storage. The decode thread copies each frame into a free slot and publishes the slot index; the GL thread binds the buffer and calls `glTexSubImage2D` with that slot's offset, so the copy into the texture is a GPU-side transfer and no pixel data passes through the GL thread.
- (implementation) **Fence each slot.** After issuing the upload from a slot, the GL thread inserts a `glFenceSync`; the decode thread does not reuse a slot until its fence has signalled (checked with a zero-timeout `glClientWaitSync` on the GL thread when the slot is handed back), so it never overwrites pixels the GPU has not consumed. When no slot is free the decoder drops that frame, as it effectively does today when the GL thread hasn't taken the previous one.
- (implementation) The ring is (re)allocated when the stream's stride or height changes, where `copyBufA`/`copyBufB` are sized today (`display/phase/VideoPhase.java:461`).

## Dependencies

_None._

## Open questions

_None._

## Acceptance criteria

- [ ] The GL thread makes no `glTexSubImage2D` call with a client-memory pointer for video frames; uploads read from the pixel-unpack buffer.
- [ ] Playback looks the same as before (no tearing, no stale or mixed frames) across a video switch and across videos of different sizes.
- [ ] A fullscreen benchmark on DP-4 (unlocked session) with a video playing shows the main thread's native time in the video upload drop, and frame-time p99/janky no worse than before, recorded in this initiative.
- [ ] Project docs (`CLAUDE.md` VideoPhase description) describe the upload path.
