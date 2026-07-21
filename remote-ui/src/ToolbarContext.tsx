import { createContext, useContext, useState, type ReactNode } from 'react';
import type { ToolbarEntry } from './components/ActionToolbar';

interface ToolbarContextValue {
  actions: ToolbarEntry[];
  setActions: (actions: ToolbarEntry[]) => void;
}

const ToolbarContext = createContext<ToolbarContextValue | null>(null);

/**
 * Carries the General-group toolbar actions (Screenshot, Record, Stop Recording) from
 * TabsContainer, where the param tree data lives, up to the header in App.tsx — same
 * bridging pattern as SettingsContext.
 */
export function ToolbarProvider({ children }: { children: ReactNode }) {
  const [actions, setActions] = useState<ToolbarEntry[]>([]);
  return (
    <ToolbarContext.Provider value={{ actions, setActions }}>
      {children}
    </ToolbarContext.Provider>
  );
}

export function useToolbar() {
  const ctx = useContext(ToolbarContext);
  if (!ctx) throw new Error('useToolbar must be used within ToolbarProvider');
  return ctx;
}
