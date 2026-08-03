import { useRef, useState } from 'react';
import { Play, Star, Trash2, Pencil } from 'lucide-react';
import { createChapter, updateChapter, deleteChapter, updateVideo, videoStreamUrl } from '../api';
import type { VideoEntry } from '../types';

function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, '0')}`;
}

const buttonClass =
  'px-2 py-1 rounded text-xs bg-neutral-800 text-neutral-300 hover:bg-neutral-700 disabled:opacity-40 disabled:cursor-not-allowed';

export function ChapterScrubber({ video, onChange }: { video: VideoEntry; onChange: (v: VideoEntry) => void }) {
  const chapters = video.chapters ?? [];
  const videoRef = useRef<HTMLVideoElement>(null);
  const [markStart, setMarkStart] = useState<number | null>(null);
  const [markEnd, setMarkEnd] = useState<number | null>(null);
  const [newName, setNewName] = useState('');
  const [editing, setEditing] = useState<string | null>(null);
  const [editName, setEditName] = useState('');
  const [editStart, setEditStart] = useState(0);
  const [editEnd, setEditEnd] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function currentTime(): number {
    return videoRef.current?.currentTime ?? 0;
  }

  function seekTo(t: number) {
    if (videoRef.current) videoRef.current.currentTime = t;
  }

  async function withBusy(fn: () => Promise<VideoEntry>, failMessage: string) {
    setBusy(true);
    setError(null);
    try {
      onChange(await fn());
    } catch {
      setError(failMessage);
    } finally {
      setBusy(false);
    }
  }

  async function handleAdd() {
    if (markStart == null || markEnd == null || !newName.trim()) return;
    await withBusy(
      () =>
        createChapter(video.file, {
          name: newName.trim(),
          start: Math.min(markStart, markEnd),
          end: Math.max(markStart, markEnd),
        }),
      'Failed to add chapter',
    );
    setNewName('');
    setMarkStart(null);
    setMarkEnd(null);
  }

  function startEdit(name: string, start: number, end: number) {
    setEditing(name);
    setEditName(name);
    setEditStart(start);
    setEditEnd(end);
  }

  async function saveEdit(originalName: string) {
    await withBusy(
      () => updateChapter(video.file, originalName, { name: editName.trim(), start: editStart, end: editEnd }),
      'Failed to update chapter',
    );
    setEditing(null);
  }

  return (
    <div className="space-y-3">
      <video ref={videoRef} src={videoStreamUrl(video.file)} controls className="w-full rounded bg-black" />

      <div className="flex flex-wrap items-center gap-2">
        <button type="button" className={buttonClass} onClick={() => setMarkStart(currentTime())}>
          Mark Start{markStart != null ? ` (${formatTime(markStart)})` : ''}
        </button>
        <button type="button" className={buttonClass} onClick={() => setMarkEnd(currentTime())}>
          Mark End{markEnd != null ? ` (${formatTime(markEnd)})` : ''}
        </button>
        <input
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          placeholder="Chapter name"
          className="flex-1 min-w-[8rem] px-2 py-1 rounded bg-neutral-900 border border-neutral-700 text-neutral-200 text-sm outline-none focus:border-phosphor"
        />
        <button
          type="button"
          disabled={busy || markStart == null || markEnd == null || !newName.trim()}
          onClick={handleAdd}
          className={buttonClass}
        >
          Add Chapter
        </button>
      </div>

      {error && <p className="text-sm text-red-400">{error}</p>}

      <ul className="divide-y divide-neutral-800 border border-neutral-800 rounded">
        {chapters.length === 0 && <li className="p-2 text-sm text-neutral-500">No chapters yet.</li>}
        {chapters.map((c) =>
          editing === c.name ? (
            <li key={c.name} className="flex flex-wrap items-center gap-2 p-2 text-sm bg-neutral-900">
              <input
                value={editName}
                onChange={(e) => setEditName(e.target.value)}
                className="px-2 py-1 rounded bg-neutral-950 border border-neutral-700 text-neutral-200 text-sm w-32"
              />
              <button type="button" className={buttonClass} onClick={() => setEditStart(currentTime())}>
                Start: {formatTime(editStart)}
              </button>
              <button type="button" className={buttonClass} onClick={() => setEditEnd(currentTime())}>
                End: {formatTime(editEnd)}
              </button>
              <button type="button" disabled={busy} className={buttonClass} onClick={() => saveEdit(c.name)}>
                Save
              </button>
              <button type="button" className={buttonClass} onClick={() => setEditing(null)}>
                Cancel
              </button>
            </li>
          ) : (
            <li key={c.name} className="flex items-center gap-2 p-2 text-sm">
              <button
                type="button"
                onClick={() => seekTo(c.start)}
                aria-label={`Play from ${c.name}`}
                className="text-neutral-400 hover:text-phosphor"
              >
                <Play className="w-3.5 h-3.5" />
              </button>
              <span className="flex-1 truncate text-neutral-200">{c.name}</span>
              <span className="text-neutral-500 text-xs">
                {formatTime(c.start)}–{formatTime(c.end)}
              </span>
              <button
                type="button"
                disabled={busy}
                onClick={() => withBusy(() => updateVideo(video.file, { defaultChapter: video.defaultChapter === c.name ? null : c.name }), 'Failed to set default chapter')}
                aria-label="Set as default chapter"
                title="Play this chapter by default when the video is selected"
                className={video.defaultChapter === c.name ? 'text-yellow-400' : 'text-neutral-600 hover:text-neutral-300'}
              >
                <Star className="w-3.5 h-3.5" fill={video.defaultChapter === c.name ? 'currentColor' : 'none'} />
              </button>
              <button
                type="button"
                onClick={() => startEdit(c.name, c.start, c.end)}
                aria-label={`Edit ${c.name}`}
                className="text-neutral-600 hover:text-neutral-300"
              >
                <Pencil className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                disabled={busy}
                onClick={() => withBusy(() => deleteChapter(video.file, c.name), 'Failed to delete chapter')}
                aria-label={`Delete ${c.name}`}
                className="text-neutral-600 hover:text-red-400"
              >
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            </li>
          ),
        )}
      </ul>
    </div>
  );
}
