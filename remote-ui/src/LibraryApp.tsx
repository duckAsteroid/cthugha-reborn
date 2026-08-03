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
      <div className="min-h-screen bg-void flex items-center justify-center p-6">
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
    <div className="min-h-screen bg-void text-neutral-200">
      <header className="sticky top-0 z-10 bg-panel border-b border-line px-3.5 py-2.5 flex items-center gap-3">
        <img src="/cthugha-icon.png" alt="" className="w-7 h-7 rounded-md shrink-0" />
        <div className="flex flex-col leading-tight">
          <h1 className="font-mono font-bold text-base tracking-wide text-phosphor text-glow">CTHUGHA</h1>
          <span className="hidden min-[400px]:block font-mono text-[9px] tracking-[0.2em] text-neutral-600">
            LIBRARY&nbsp;MANAGER
          </span>
        </div>
        <nav className="ml-4 flex items-center gap-1">
          {SECTIONS.map((s) => (
            <button
              key={s.id}
              onClick={() => setSection(s.id)}
              className={`font-mono uppercase tracking-wide px-3 py-1.5 rounded text-xs transition-colors ${
                section === s.id
                  ? 'bg-phosphor text-void font-bold shadow-[0_0_16px_rgba(61,255,122,0.35)]'
                  : 'text-neutral-400 hover:text-neutral-200 hover:bg-panel-hi'
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
