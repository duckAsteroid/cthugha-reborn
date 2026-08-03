import { useEffect, useState } from 'react';
import { Settings, LibraryBig } from 'lucide-react';
import { initToken, getToken } from './token';
import { getInfo } from './api';
import { ParamTree } from './components/ParamTree';
import { ActionToolbar } from './components/ActionToolbar';
import { SSEProvider } from './SSEContext';
import { SettingsProvider, useSettings } from './SettingsContext';
import { ToolbarProvider, useToolbar } from './ToolbarContext';

function SettingsButton() {
  const { openSettings } = useSettings();
  return (
    <button
      onClick={openSettings}
      aria-label="Settings"
      className="p-1.5 rounded text-neutral-400 hover:text-phosphor hover:bg-panel-hi transition-colors"
    >
      <Settings className="w-5 h-5" />
    </button>
  );
}

function LibraryButton() {
  const token = getToken();
  return (
    <a
      href={`/library.html?token=${encodeURIComponent(token ?? '')}`}
      target="_blank"
      rel="noopener noreferrer"
      aria-label="Library manager"
      className="p-1.5 rounded text-neutral-400 hover:text-phosphor hover:bg-panel-hi transition-colors"
    >
      <LibraryBig className="w-5 h-5" />
    </a>
  );
}

function HeaderControls() {
  const { actions } = useToolbar();
  const [libraryManagerEnabled, setLibraryManagerEnabled] = useState(false);

  useEffect(() => {
    getInfo()
      .then(info => setLibraryManagerEnabled(info.libraryManagerEnabled))
      .catch(() => setLibraryManagerEnabled(false));
  }, []);

  return (
    <div className="ml-auto flex items-center gap-1">
      <ActionToolbar actions={actions} />
      {libraryManagerEnabled && <LibraryButton />}
      <SettingsButton />
    </div>
  );
}

export default function App() {
  const [hasToken, setHasToken] = useState(false);
  const [sessionExpired, setSessionExpired] = useState(false);
  const [connectionLost, setConnectionLost] = useState(false);

  useEffect(() => {
    const token = initToken();
    setHasToken(token !== null);
  }, []);

  useEffect(() => {
    const handleExpired = () => {
      setSessionExpired(true);
      setHasToken(false);
    };
    window.addEventListener('session-expired', handleExpired);
    return () => {
      window.removeEventListener('session-expired', handleExpired);
    };
  }, []);

  useEffect(() => {
    const handleLost = () => setConnectionLost(true);
    const handleRestored = () => setConnectionLost(false);
    window.addEventListener('connection-lost', handleLost);
    window.addEventListener('connection-restored', handleRestored);
    return () => {
      window.removeEventListener('connection-lost', handleLost);
      window.removeEventListener('connection-restored', handleRestored);
    };
  }, []);

  // No token at all — show QR code prompt
  if (!hasToken && !sessionExpired) {
    return (
      <div className="min-h-screen bg-void flex items-center justify-center p-6">
        <div className="text-center space-y-4">
          <h1 className="text-2xl font-bold text-neutral-200">Cthugha Remote</h1>
          <p className="text-neutral-400">
            Scan the QR code displayed on screen to connect.
          </p>
        </div>
      </div>
    );
  }

  return (
    <SSEProvider>
      <SettingsProvider>
        <ToolbarProvider>
          <div className="min-h-screen bg-void text-neutral-200">
            {/* Connection lost overlay */}
            {connectionLost && (
              <div className="fixed inset-0 z-50 bg-black/95 flex items-center justify-center p-6">
                <div className="text-center space-y-4">
                  <h2 className="text-xl font-bold text-red-400">Connection lost</h2>
                  <p className="text-neutral-400">
                    The app is unreachable. Scan the QR code on screen to reconnect.
                  </p>
                </div>
              </div>
            )}

            {/* Session expired overlay */}
            {!connectionLost && sessionExpired && (
              <div className="fixed inset-0 z-50 bg-black/90 flex items-center justify-center p-6">
                <div className="text-center space-y-4">
                  <h2 className="text-xl font-bold text-neutral-200">Session ended</h2>
                  <p className="text-neutral-400">
                    Scan the QR code to reconnect.
                  </p>
                </div>
              </div>
            )}

            {/* Title bar */}
            <header className="sticky top-0 z-10 bg-panel border-b border-line px-3.5 py-2.5 flex items-center gap-3">
              <img src="/cthugha-icon.png" alt="" className="w-7 h-7 rounded-md shrink-0" />
              <div className="flex flex-col leading-tight">
                <h1 className="font-mono font-bold text-base tracking-wide text-phosphor text-glow">CTHUGHA</h1>
                <span className="hidden min-[400px]:block font-mono text-[9px] tracking-[0.2em] text-neutral-600">
                  REMOTE&nbsp;CONTROL
                </span>
              </div>
              <HeaderControls />
            </header>

            {/* Parameter tree */}
            <main className="w-full pb-8">
              <ParamTree />
            </main>
          </div>
        </ToolbarProvider>
      </SettingsProvider>
    </SSEProvider>
  );
}
