import { useState } from 'react';
import { executeAction } from '../api';

/** Shared busy/execute logic for buttons that fire a param-tree Action node. */
export function useActionExecutor(path: string) {
  const [busy, setBusy] = useState(false);

  const trigger = async () => {
    if (busy) return;
    setBusy(true);
    try {
      await executeAction(path);
    } catch {
      // error is handled by api.ts (session-expired event)
    } finally {
      setBusy(false);
    }
  };

  return { busy, trigger };
}
