import type { EnumChapter } from '../../types';
import type { ChapterControl } from '../../nodeUtils';
import { patchParam } from '../../api';

interface TimelineControlProps {
  chapters: EnumChapter[];
  duration?: number;
  position?: number;
  chapterControl: ChapterControl;
}

/**
 * Chapter timeline shown under a video's preview thumbnail (see ParamContainer's use of
 * resolveChapterControl/resolvePositionOf). Chapter segments are sized proportionally to the
 * video's duration; tapping a segment selects it (tapping the already-selected one, or the
 * "Whole video" button, deselects back to unclamped playback). The thin vertical marker tracks
 * live playback position.
 */
export function TimelineControl({ chapters, duration, position, chapterControl }: TimelineControlProps) {
  const effectiveDuration = duration ?? Math.max(...chapters.map((c) => c.end));
  if (!(effectiveDuration > 0)) return null;

  const toPct = (seconds: number) => Math.min(100, Math.max(0, (seconds / effectiveDuration) * 100));

  const select = (index: number) => {
    const next = chapterControl.index === index ? -1 : index;
    patchParam(chapterControl.path, next).catch(() => {
      // error is handled by api.ts (session-expired event)
    });
  };

  const reset = () => {
    patchParam(chapterControl.path, -1).catch(() => {
      // error is handled by api.ts (session-expired event)
    });
  };

  return (
    <div className="flex flex-col gap-1">
      <div className="relative h-6 mx-0.5">
        <div className="absolute inset-x-0 top-1/2 -translate-y-1/2 h-1 rounded-full bg-neutral-700" />
        {chapters.map((ch, i) => {
          const selected = chapterControl.index === i;
          const leftPct = toPct(ch.start);
          const widthPct = Math.max(toPct(ch.end) - leftPct, 2);
          return (
            <button
              key={`${ch.name}-${ch.start}`}
              onClick={() => select(i)}
              aria-pressed={selected}
              title={ch.name}
              className={`absolute top-0 h-6 rounded px-1 flex items-center justify-center overflow-hidden text-[10px] leading-none font-medium transition-colors ${
                selected
                  ? 'bg-indigo-500 text-white'
                  : 'bg-neutral-700/70 text-neutral-300 hover:bg-neutral-600'
              }`}
              style={{ left: `${leftPct}%`, width: `${widthPct}%` }}
            >
              <span className="truncate">{ch.name}</span>
            </button>
          );
        })}
        {position !== undefined && (
          <div
            className="absolute -top-0.5 h-7 w-0.5 bg-white/90 rounded-full pointer-events-none"
            style={{ left: `${toPct(position)}%` }}
          />
        )}
      </div>
      {chapterControl.index >= 0 && (
        <button
          onClick={reset}
          className="self-end text-[10px] text-neutral-500 hover:text-indigo-300 transition-colors"
        >
          Whole video
        </button>
      )}
    </div>
  );
}
