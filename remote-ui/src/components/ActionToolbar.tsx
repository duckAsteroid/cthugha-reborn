import { useState } from 'react';
import { Loader2 } from 'lucide-react';
import type { ActionNode } from '../types';
import { executeAction } from '../api';
import { NodeIcon } from './NodeIcon';

export interface ToolbarEntry {
  path: string;
  node: ActionNode;
}

function ToolbarButton({ path, node }: ToolbarEntry) {
  const [busy, setBusy] = useState(false);
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
    <button
      onClick={handleClick}
      disabled={busy}
      aria-label={node.name}
      title={node.name}
      className="flex flex-col items-center justify-center gap-0.5 w-14 py-1.5 rounded-md
                 text-neutral-300 hover:bg-neutral-800 disabled:opacity-50 disabled:cursor-not-allowed
                 transition-colors"
    >
      {busy ? (
        <Loader2 className="w-4 h-4 animate-spin" />
      ) : iconName ? (
        <NodeIcon name={iconName} className="w-4 h-4" />
      ) : (
        <span className="w-4 h-4" />
      )}
      <span className="text-[10px] leading-none text-neutral-400 truncate max-w-full px-0.5">
        {node.name}
      </span>
    </button>
  );
}

interface ActionToolbarProps {
  actions: ToolbarEntry[];
}

export function ActionToolbar({ actions }: ActionToolbarProps) {
  if (actions.length === 0) return null;

  return (
    <div className="flex flex-wrap gap-1 px-2 py-2 bg-[#1a1a1a] border-b border-neutral-800 rounded-t-lg">
      {actions.map(({ path, node }) => (
        <ToolbarButton key={path} path={path} node={node} />
      ))}
    </div>
  );
}
