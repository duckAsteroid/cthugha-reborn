---
type: perf
status: draft
updated: 2026-10-24
---
# Upload video frames asynchronously via pixel buffer objects

## Goal

VideoPhase.screenRender() uploads each decoded frame with a synchronous glTexSubImage2D from client memory into a texture the previous frame may still be drawing; JFR showed about as much main-thread native time there as in glfwSwapBuffers while a video overlay plays. Stream uploads through alternating pixel-unpack buffer objects (PBOs) so the copy is asynchronous, and measure with FrameTimeStats before/after.

## Dependencies

_None._

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
