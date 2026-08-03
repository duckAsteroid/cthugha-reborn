import { useEffect, useRef, useState } from 'react';
import { Trash2 } from 'lucide-react';
import { getMaps, saveMap } from '../api';
import { sampleGradient, sampleGradientAt, type Curve, type GradientStop } from './gradient';

const CURVES: { id: Curve; label: string }[] = [
  { id: 'linear', label: 'Linear' },
  { id: 'ease-in', label: 'Ease in' },
  { id: 'ease-out', label: 'Ease out' },
  { id: 'ease-in-out', label: 'Ease in/out' },
];

function newId(): string {
  return Math.random().toString(36).slice(2);
}

const DEFAULT_STOPS: GradientStop[] = [
  { id: newId(), position: 0, color: '#000000', curve: 'linear' },
  { id: newId(), position: 1, color: '#ffffff', curve: 'linear' },
];

export function PaletteGradientEditor() {
  const [stops, setStops] = useState<GradientStop[]>(DEFAULT_STOPS);
  const [selectedId, setSelectedId] = useState<string>(DEFAULT_STOPS[0].id);
  const [sampleCount, setSampleCount] = useState(256);
  const [name, setName] = useState('');
  const [existingNames, setExistingNames] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [status, setStatus] = useState<{ kind: 'ok' | 'error'; message: string } | null>(null);
  const barRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    getMaps()
      .then(setExistingNames)
      .catch(() => setExistingNames([]));
  }, []);

  const sorted = [...stops].sort((a, b) => a.position - b.position);
  const selected = stops.find((s) => s.id === selectedId) ?? null;
  const selectedIsLast = selected != null && sorted[sorted.length - 1].id === selected.id;

  const previewSamples = sampleGradient(stops, Math.min(sampleCount, 180));
  const previewBackground = `linear-gradient(to right, ${previewSamples.join(', ')})`;

  function updateStop(id: string, patch: Partial<GradientStop>) {
    setStops((prev) => prev.map((s) => (s.id === id ? { ...s, ...patch } : s)));
  }

  function deleteStop(id: string) {
    if (stops.length <= 2) return;
    setStops((prev) => prev.filter((s) => s.id !== id));
    if (selectedId === id) setSelectedId(sorted[0].id === id ? sorted[1].id : sorted[0].id);
  }

  function positionFromEvent(clientX: number): number {
    const rect = barRef.current?.getBoundingClientRect();
    if (!rect) return 0;
    return Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
  }

  function handleBarClick(e: React.MouseEvent) {
    const pos = positionFromEvent(e.clientX);
    const id = newId();
    setStops((prev) => [...prev, { id, position: pos, color: sampleGradientAt(prev, pos), curve: 'linear' }]);
    setSelectedId(id);
  }

  function handleHandlePointerDown(e: React.PointerEvent, id: string) {
    e.stopPropagation();
    setSelectedId(id);
    const onMove = (ev: PointerEvent) => updateStop(id, { position: positionFromEvent(ev.clientX) });
    const onUp = () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
    };
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
  }

  async function handleSave() {
    if (!name.trim()) return;
    setSaving(true);
    setStatus(null);
    try {
      const colors = sampleGradient(stops, sampleCount);
      const result = await saveMap(name.trim(), colors);
      setStatus({ kind: 'ok', message: `Saved ${result.name}.MAP with ${result.size} colours.` });
      setExistingNames((prev) => (prev.includes(name.trim().toUpperCase()) ? prev : [...prev, name.trim().toUpperCase()]));
    } catch {
      setStatus({ kind: 'error', message: 'Save failed.' });
    } finally {
      setSaving(false);
    }
  }

  const nameCollides = name.trim() !== '' && existingNames.includes(name.trim().toUpperCase());

  return (
    <div className="max-w-2xl space-y-4">
      <div className="h-16 rounded-lg border border-neutral-800" style={{ background: previewBackground }} />

      <div
        ref={barRef}
        onClick={handleBarClick}
        className="relative h-10 rounded-lg border border-neutral-800 cursor-copy"
        style={{ background: previewBackground }}
        title="Click to add a colour stop"
      >
        {stops.map((s) => (
          <button
            key={s.id}
            type="button"
            onPointerDown={(e) => handleHandlePointerDown(e, s.id)}
            onClick={(e) => e.stopPropagation()}
            aria-label={`Stop at ${(s.position * 100).toFixed(0)}%`}
            className={`absolute top-1/2 -translate-x-1/2 -translate-y-1/2 w-4 h-4 rounded-full border-2 shadow ${
              s.id === selectedId ? 'border-phosphor z-10' : 'border-white'
            }`}
            style={{ left: `${s.position * 100}%`, backgroundColor: s.color }}
          />
        ))}
      </div>

      {selected && (
        <div className="flex flex-wrap items-end gap-3 p-3 rounded-lg border border-neutral-800 bg-neutral-900">
          <label className="block">
            <span className="block text-xs text-neutral-400 mb-1">Color</span>
            <input
              type="color"
              value={selected.color}
              onChange={(e) => updateStop(selected.id, { color: e.target.value })}
              className="w-16 h-8 rounded bg-transparent border border-neutral-700"
            />
          </label>

          <label className="block">
            <span className="block text-xs text-neutral-400 mb-1">Position %</span>
            <input
              type="number"
              min={0}
              max={100}
              value={Math.round(selected.position * 100)}
              onChange={(e) => updateStop(selected.id, { position: Math.min(1, Math.max(0, Number(e.target.value) / 100)) })}
              className="w-20 px-2 py-1.5 rounded bg-neutral-950 border border-neutral-700 text-neutral-200 text-sm outline-none focus:border-phosphor"
            />
          </label>

          {!selectedIsLast && (
            <label className="block">
              <span className="block text-xs text-neutral-400 mb-1">Curve to next stop</span>
              <select
                value={selected.curve}
                onChange={(e) => updateStop(selected.id, { curve: e.target.value as Curve })}
                className="px-2 py-1.5 rounded bg-neutral-950 border border-neutral-700 text-neutral-200 text-sm outline-none focus:border-phosphor"
              >
                {CURVES.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.label}
                  </option>
                ))}
              </select>
            </label>
          )}

          <button
            type="button"
            disabled={stops.length <= 2}
            onClick={() => deleteStop(selected.id)}
            aria-label="Delete stop"
            className="p-1.5 rounded text-neutral-500 hover:text-red-400 hover:bg-neutral-800 disabled:opacity-30 disabled:cursor-not-allowed"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      )}

      <div className="flex flex-wrap items-end gap-3">
        <label className="block">
          <span className="block text-xs text-neutral-400 mb-1">Colour steps</span>
          <input
            type="number"
            min={2}
            max={4096}
            value={sampleCount}
            onChange={(e) => setSampleCount(Math.min(4096, Math.max(2, Number(e.target.value))))}
            className="w-24 px-2 py-1.5 rounded bg-neutral-900 border border-neutral-700 text-neutral-200 text-sm outline-none focus:border-phosphor"
          />
        </label>

        <label className="block flex-1 min-w-[10rem]">
          <span className="block text-xs text-neutral-400 mb-1">Save as</span>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Palette name"
            className="w-full px-2 py-1.5 rounded bg-neutral-900 border border-neutral-700 text-neutral-200 text-sm outline-none focus:border-phosphor"
          />
        </label>

        <button
          type="button"
          disabled={saving || !name.trim()}
          onClick={handleSave}
          className="px-3 py-1.5 rounded text-sm bg-phosphor text-void hover:bg-phosphor/90 disabled:opacity-50"
        >
          {saving ? 'Saving…' : 'Save Palette'}
        </button>
      </div>

      {nameCollides && <p className="text-sm text-yellow-400">A palette named "{name.trim().toUpperCase()}" already exists — saving will overwrite it.</p>}
      {status && <p className={`text-sm ${status.kind === 'ok' ? 'text-green-400' : 'text-red-400'}`}>{status.message}</p>}
    </div>
  );
}
