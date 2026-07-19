import { createContext, useContext, useState, type ReactNode } from 'react';

interface SettingsContextValue {
  open: boolean;
  openSettings: () => void;
  closeSettings: () => void;
}

const SettingsContext = createContext<SettingsContextValue | null>(null);

/**
 * Tracks whether the Settings panel (gear icon in the header) is open. The trigger button lives
 * in App.tsx's header; the panel content (Audio controls, Fullscreen/Notifications toggles) is
 * rendered from TabsContainer, where that data already lives — this context is the only thing
 * connecting the two without prop-drilling through every generic tree-rendering component.
 */
export function SettingsProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  return (
    <SettingsContext.Provider
      value={{ open, openSettings: () => setOpen(true), closeSettings: () => setOpen(false) }}
    >
      {children}
    </SettingsContext.Provider>
  );
}

export function useSettings() {
  const ctx = useContext(SettingsContext);
  if (!ctx) throw new Error('useSettings must be used within SettingsProvider');
  return ctx;
}
