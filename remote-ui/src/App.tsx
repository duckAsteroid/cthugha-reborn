import { useEffect, useState } from 'react';
import { initToken, getToken } from './token';
import { ParamTree } from './components/ParamTree';
import { SSEProvider } from './SSEContext';

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
      <div className="min-h-screen bg-[#0f0f0f] flex items-center justify-center p-6">
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
      <div className="min-h-screen bg-[#0f0f0f] text-neutral-200">
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
        <header className="sticky top-0 z-10 bg-[#1a1a1a] border-b border-neutral-800 px-4 py-3 flex items-center gap-3">
          <h1 className="text-lg font-bold text-indigo-400 tracking-tight">Cthugha Remote</h1>
          <span className={`text-xs ml-auto ${connectionLost ? 'text-red-500' : 'text-neutral-600'}`}>
            {connectionLost ? 'Disconnected' : getToken() ? 'Connected' : 'No session'}
          </span>
        </header>

        {/* Parameter tree */}
        <main className="max-w-lg mx-auto pb-8">
          <ParamTree />
        </main>
      </div>
    </SSEProvider>
  );
}
