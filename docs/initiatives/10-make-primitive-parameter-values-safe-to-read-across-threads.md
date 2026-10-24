---
type: bug
status: draft
updated: 2026-10-24
---
# Make primitive parameter values safe to read across threads

## Goal

DoubleParameter/IntegerParameter/BooleanParameter `value` fields are plain (non-volatile) but written from Javalin HTTP threads and the render thread and read on the render thread's hot path, so there is no happens-before guarantee the render thread sees a remote edit (PERFORMANCE_REVIEW.md #16; StringParameter and ScriptParameter already use volatile). Mark them volatile, and add the missing primitive BooleanParameter.setNormalisedValue override (#15).

## Dependencies

_None._

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
