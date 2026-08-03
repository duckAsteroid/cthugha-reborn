import { useEffect, useState } from 'react';
import { initToken } from './token';
import { getParams } from './api';
import { VideosSection } from './library/VideosSection';
import { ImagesSection } from './library/ImagesSection';
import { PaletteGradientEditor } from './library/PaletteGradientEditor';

type Section = 'videos' | 'images' | 'palettes';

const SECTIONS: { id: Section; label: string }[] = [
  { id: 'videos', label: 'Videos' },
  { id: 'images', label: 'Images' },
  { id: 'palettes', label: 'Palettes' },
];

export default function LibraryApp() {
  const [hasToken, setHasToken] = useState(false);
  const [section, setSection] = useState<Section>('videos');
  const [connected, setConnected] = useState<boolean | null>(null);

  useEffect(() => {
    const token = initToken();
    setHasToken(token !== null);
  }, []);

  useEffect(() => {
    if (!hasToken) return;
    getParams()
      .then(() => setConnected(true))
      .catch(() => setConnected(false));
  }, [hasToken]);

  if (!hasToken) {
    return (
      <div className="min-h-screen bg-[#0f0f0f] flex items-center justify-center p-6">
        <div className="text-center space-y-4">
          <h1 className="text-2xl font-bold text-neutral-200">Cthugha Library</h1>
          <p className="text-neutral-400">
            Open this page from the Library link in the Cthugha Remote app so it has a
            valid session token.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#0f0f0f] text-neutral-200">
      <header className="sticky top-0 z-10 bg-[#1a1a1a] border-b border-neutral-800 px-4 py-3 flex items-center gap-3">
        <h1 className="text-lg font-bold text-indigo-400 tracking-tight">Cthugha Library</h1>
        <nav className="ml-6 flex items-center gap-1">
          {SECTIONS.map((s) => (
            <button
              key={s.id}
              onClick={() => setSection(s.id)}
              className={`px-3 py-1.5 rounded text-sm transition-colors ${
                section === s.id
                  ? 'bg-indigo-500/20 text-indigo-300'
                  : 'text-neutral-400 hover:text-neutral-200 hover:bg-neutral-800'
              }`}
            >
              {s.label}
            </button>
          ))}
        </nav>
        <div className="ml-auto text-xs text-neutral-500">
          {connected === null && 'Connecting…'}
          {connected === true && 'Connected'}
          {connected === false && 'Connection failed'}
        </div>
      </header>

      <main className="w-full p-6">
        {section === 'videos' && <VideosSection />}
        {section === 'images' && <ImagesSection />}
        {section === 'palettes' && <PaletteGradientEditor />}
      </main>
    </div>
  );
}
