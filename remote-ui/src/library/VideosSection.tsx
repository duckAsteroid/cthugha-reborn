import { useEffect, useState } from 'react';
import { X } from 'lucide-react';
import { getVideos, updateVideo, renameVideo } from '../api';
import type { VideoEntry } from '../types';
import { TagEditor } from '../components/TagEditor';
import { ChapterScrubber } from './ChapterScrubber';
import { useLibraryFilter } from './useLibraryFilter';
import { LibraryFilterBar } from './LibraryFilterBar';

export function VideosSection() {
  const [videos, setVideos] = useState<VideoEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<VideoEntry | null>(null);
  const { search, setSearch, selectedTags, toggleTag, sortDir, cycleSort, allTags, filtered } = useLibraryFilter(
    videos,
    (v) => v.title,
    (v) => v.tags,
  );

  function load() {
    setLoading(true);
    getVideos()
      .then((v) => {
        setVideos(v);
        setError(null);
      })
      .catch(() => setError('Failed to load videos'))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  if (loading) return <p className="text-neutral-500">Loading videos…</p>;
  if (error) return <p className="text-red-400">{error}</p>;
  if (videos.length === 0) return <p className="text-neutral-500">No videos found.</p>;

  return (
    <div>
      <LibraryFilterBar
        search={search}
        onSearchChange={setSearch}
        sortDir={sortDir}
        onCycleSort={cycleSort}
        allTags={allTags}
        selectedTags={selectedTags}
        onToggleTag={toggleTag}
      />

      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
        {filtered.length === 0 && <p className="col-span-full text-sm text-neutral-500">No matches</p>}
        {filtered.map((v) => (
          <button
            key={v.file}
            onClick={() => setEditing(v)}
            className="text-left rounded-lg overflow-hidden border border-neutral-800 bg-neutral-900 hover:border-indigo-500 transition-colors"
          >
            <div className="aspect-video bg-neutral-950">
              <img
                src={`/api/v1/videos/preview/${encodeURIComponent(v.file)}`}
                alt={v.title}
                className="w-full h-full object-cover"
              />
            </div>
            <div className="p-2">
              <p className="text-sm font-medium text-neutral-200 truncate">{v.title}</p>
              <p className="text-xs text-neutral-500 truncate">{v.file}</p>
              {v.tags.length > 0 && (
                <div className="mt-1 flex flex-wrap gap-1">
                  {v.tags.map((t) => (
                    <span key={t} className="px-1.5 py-0.5 rounded-full bg-neutral-800 text-neutral-400 text-[10px]">
                      {t}
                    </span>
                  ))}
                </div>
              )}
            </div>
          </button>
        ))}
      </div>

      {editing && (
        <VideoEditPanel
          video={editing}
          onClose={() => setEditing(null)}
          onLiveUpdate={(updated) => {
            setVideos((prev) => prev.map((v) => (v.file === editing.file ? updated : v)));
            setEditing(updated);
          }}
        />
      )}
    </div>
  );
}

function VideoEditPanel({
  video,
  onClose,
  onLiveUpdate,
}: {
  video: VideoEntry;
  onClose: () => void;
  onLiveUpdate: (updated: VideoEntry) => void;
}) {
  const [title, setTitle] = useState(video.title);
  const [source, setSource] = useState(video.source ?? '');
  const [license, setLicense] = useState(video.license ?? '');
  const [tags, setTags] = useState(video.tags);
  const [filename, setFilename] = useState(video.file);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSave() {
    setSaving(true);
    setError(null);
    try {
      let current = video.file;
      if (filename !== video.file) {
        await renameVideo(current, filename);
        current = filename;
      }
      const updated = await updateVideo(current, { title, source, license, tags });
      onLiveUpdate(updated);
      onClose();
    } catch {
      setError('Save failed — check the filename is available and try again.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4">
      <div className="w-full sm:max-w-2xl max-h-[90vh] overflow-y-auto bg-[#1a1a1a] rounded-2xl border border-neutral-800">
        <div className="sticky top-0 flex items-center justify-between px-4 py-3 border-b border-neutral-800 bg-[#1a1a1a]">
          <h2 className="font-semibold text-neutral-200">Edit video</h2>
          <button onClick={onClose} aria-label="Close" className="p-1 rounded hover:bg-neutral-800 text-neutral-400">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-4 space-y-4">
          <label className="block">
            <span className="block text-xs text-neutral-400 mb-1">Title</span>
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full px-2 py-1.5 rounded bg-neutral-900 border border-neutral-700 text-neutral-200 text-sm outline-none focus:border-indigo-500"
            />
          </label>

          <label className="block">
            <span className="block text-xs text-neutral-400 mb-1">Filename</span>
            <input
              value={filename}
              onChange={(e) => setFilename(e.target.value)}
              className="w-full px-2 py-1.5 rounded bg-neutral-900 border border-neutral-700 text-neutral-200 text-sm outline-none focus:border-indigo-500"
            />
          </label>

          <label className="block">
            <span className="block text-xs text-neutral-400 mb-1">Tags</span>
            <TagEditor tags={tags} onChange={setTags} />
          </label>

          <label className="block">
            <span className="block text-xs text-neutral-400 mb-1">Source</span>
            <input
              value={source}
              onChange={(e) => setSource(e.target.value)}
              className="w-full px-2 py-1.5 rounded bg-neutral-900 border border-neutral-700 text-neutral-200 text-sm outline-none focus:border-indigo-500"
            />
          </label>

          <label className="block">
            <span className="block text-xs text-neutral-400 mb-1">License</span>
            <input
              value={license}
              onChange={(e) => setLicense(e.target.value)}
              className="w-full px-2 py-1.5 rounded bg-neutral-900 border border-neutral-700 text-neutral-200 text-sm outline-none focus:border-indigo-500"
            />
          </label>

          {error && <p className="text-sm text-red-400">{error}</p>}
        </div>

        <div className="flex items-center justify-end gap-2 px-4 py-3 border-t border-neutral-800">
          <button
            onClick={onClose}
            className="px-3 py-1.5 rounded text-sm text-neutral-400 hover:text-neutral-200 hover:bg-neutral-800"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            disabled={saving}
            className="px-3 py-1.5 rounded text-sm bg-indigo-500 text-white hover:bg-indigo-400 disabled:opacity-50"
          >
            {saving ? 'Saving…' : 'Save'}
          </button>
        </div>

        <div className="px-4 pb-4">
          <h3 className="text-xs text-neutral-400 mb-2 mt-2">Chapters</h3>
          <ChapterScrubber video={video} onChange={onLiveUpdate} />
        </div>
      </div>
    </div>
  );
}
