import { useState } from 'react';
import { Loader2 } from 'lucide-react';
import type { ActionNode } from '../types';
import { executeAction } from '../api';
import { NodeIcon } from './NodeIcon';
import { InfoButton } from './InfoButton';

interface ActionButtonProps {
  path: string;
  node: ActionNode;
}

export function ActionButton({ path, node }: ActionButtonProps) {
  const [busy, setBusy] = useState(false);
  const [showInfo, setShowInfo] = useState(false);
  const iconName = node.uiHints?.['icon'];

  const handleClick = async () => {
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

  return (
    <div className="py-1.5 px-3">
      <div className="flex items-center gap-1.5">
        <button
          onClick={handleClick}
          disabled={busy}
          className="flex items-center gap-2 px-3 py-1.5 bg-indigo-700 hover:bg-indigo-600 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium rounded-md transition-colors"
        >
          {busy ? (
            <Loader2 className="w-3.5 h-3.5 animate-spin" />
          ) : iconName ? (
            <NodeIcon name={iconName} className="w-3.5 h-3.5" />
          ) : null}
          {node.name}
        </button>
        {node.description && (
          <InfoButton className="ml-auto" open={showInfo} onToggle={() => setShowInfo((v) => !v)} />
        )}
      </div>
      {showInfo && node.description && (
        <p className="text-xs text-neutral-400 px-0.5 mt-1.5">{node.description}</p>
      )}
    </div>
  );
}
