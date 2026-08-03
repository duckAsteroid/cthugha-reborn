import { useEffect, useState } from 'react';
import { X } from 'lucide-react';
import { getImages, updateImage, renameImage } from '../api';
import type { ImageEntry } from '../types';
import { TagEditor } from '../components/TagEditor';
import { useLibraryFilter } from './useLibraryFilter';
import { LibraryFilterBar } from './LibraryFilterBar';

/** Matches RandomImageSource.displayName: filename minus its .PNG extension, upper-cased — the id the read-only /preview/* route expects. */
function previewName(file: string): string {
  const base = file.slice(file.lastIndexOf('/') + 1);
  const upper = base.toUpperCase();
  return upper.endsWith('.PNG') ? upper.slice(0, -4) : upper;
}

export function ImagesSection() {
  const [images, setImages] = useState<ImageEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<ImageEntry | null>(null);
  const { search, setSearch, selectedTags, toggleTag, sortDir, cycleSort, allTags, filtered } = useLibraryFilter(
    images,
    (img) => img.title,
    (img) => img.tags,
  );

  function load() {
    setLoading(true);
    getImages()
      .then((v) => {
        setImages(v);
        setError(null);
      })
      .catch(() => setError('Failed to load images'))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  if (loading) return <p className="text-neutral-500">Loading images…</p>;
  if (error) return <p className="text-red-400">{error}</p>;
  if (images.length === 0) return <p className="text-neutral-500">No images found.</p>;

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

      <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-6 gap-4">
        {filtered.length === 0 && <p className="col-span-full text-sm text-neutral-500">No matches</p>}
        {filtered.map((img) => (
          <button
            key={img.file}
            onClick={() => setEditing(img)}
            className="text-left rounded-lg overflow-hidden border border-line bg-neutral-900 hover:border-phosphor transition-colors"
          >
            <div className="aspect-square bg-neutral-950">
              <img
                src={`/api/v1/images/preview/${encodeURIComponent(previewName(img.file))}`}
                alt={img.title}
                className="w-full h-full object-cover"
              />
            </div>
            <div className="p-2">
              <p className="text-sm font-medium text-neutral-200 truncate">{img.title}</p>
              <p className="text-xs text-neutral-500 truncate">{img.file}</p>
              {img.tags.length > 0 && (
                <div className="mt-1 flex flex-wrap gap-1">
                  {img.tags.map((t) => (
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
        <ImageEditPanel
          image={editing}
          onClose={() => setEditing(null)}
          onSaved={(updated) => {
            setImages((prev) => prev.map((i) => (i.file === editing.file ? updated : i)));
            setEditing(null);
          }}
        />
      )}
    </div>
  );
}

function ImageEditPanel({
  image,
  onClose,
  onSaved,
}: {
  image: ImageEntry;
  onClose: () => void;
  onSaved: (updated: ImageEntry) => void;
}) {
  const [title, setTitle] = useState(image.title);
  const [source, setSource] = useState(image.source ?? '');
  const [license, setLicense] = useState(image.license ?? '');
  const [tags, setTags] = useState(image.tags);
  const filenameOnly = image.file.slice(image.file.lastIndexOf('/') + 1);
  const folder = image.file.slice(0, image.file.length - filenameOnly.length);
  const [filename, setFilename] = useState(filenameOnly);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSave() {
    setSaving(true);
    setError(null);
    try {
      let current = image.file;
      if (filename !== filenameOnly) {
        await renameImage(current, filename);
        current = folder + filename;
      }
      const updated = await updateImage(current, { title, source, license, tags });
      onSaved(updated);
    } catch {
      setError('Save failed — check the filename is available and try again.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4">
      <div className="w-full sm:max-w-md bg-panel rounded-2xl border border-line overflow-hidden">
        <div className="sticky top-0 flex items-center justify-between px-4 py-3 border-b border-line">
          <h2 className="font-semibold text-neutral-200">Edit image</h2>
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
              className="w-full px-2 py-1.5 rounded bg-neutral-900 border border-neutral-700 text-neutral-200 text-sm outline-none focus:border-phosphor"
            />
          </label>

          <label className="block">
            <span className="block text-xs text-neutral-400 mb-1">Filename {folder && <span className="text-neutral-600">(in {folder})</span>}</span>
            <input
              value={filename}
              onChange={(e) => setFilename(e.target.value)}
              className="w-full px-2 py-1.5 rounded bg-neutral-900 border border-neutral-700 text-neutral-200 text-sm outline-none focus:border-phosphor"
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
              className="w-full px-2 py-1.5 rounded bg-neutral-900 border border-neutral-700 text-neutral-200 text-sm outline-none focus:border-phosphor"
            />
          </label>

          <label className="block">
            <span className="block text-xs text-neutral-400 mb-1">License</span>
            <input
              value={license}
              onChange={(e) => setLicense(e.target.value)}
              className="w-full px-2 py-1.5 rounded bg-neutral-900 border border-neutral-700 text-neutral-200 text-sm outline-none focus:border-phosphor"
            />
          </label>

          {error && <p className="text-sm text-red-400">{error}</p>}
        </div>

        <div className="flex items-center justify-end gap-2 px-4 py-3 border-t border-line">
          <button
            onClick={onClose}
            className="px-3 py-1.5 rounded text-sm text-neutral-400 hover:text-neutral-200 hover:bg-neutral-800"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            disabled={saving}
            className="px-3 py-1.5 rounded text-sm bg-phosphor text-void hover:bg-phosphor/90 disabled:opacity-50"
          >
            {saving ? 'Saving…' : 'Save'}
          </button>
        </div>
      </div>
    </div>
  );
}
