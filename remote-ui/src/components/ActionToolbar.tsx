import { Loader2 } from 'lucide-react';
import type { ActionNode } from '../types';
import { useActionExecutor } from '../hooks/useActionExecutor';
import { NodeIcon } from './NodeIcon';

export interface ToolbarEntry {
  path: string;
  node: ActionNode;
}

/** Compact icon-only button, styled to match the header's Settings gear button. */
function ToolbarButton({ path, node }: ToolbarEntry) {
  const { busy, trigger } = useActionExecutor(path);
  const iconName = node.uiHints?.['icon'];

  return (
    <button
      onClick={trigger}
      disabled={busy}
      aria-label={node.name}
      title={node.name}
      className="p-1.5 rounded text-neutral-400 hover:text-neutral-200 hover:bg-neutral-800
                 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
    >
      {busy ? (
        <Loader2 className="w-5 h-5 animate-spin" />
      ) : iconName ? (
        <NodeIcon name={iconName} className="w-5 h-5" />
      ) : (
        <span className="w-5 h-5" />
      )}
    </button>
  );
}

interface ActionToolbarProps {
  actions: ToolbarEntry[];
}

/** Renders the General-group action buttons (Screenshot, Record, Stop Recording) in the header. */
export function ActionToolbar({ actions }: ActionToolbarProps) {
  if (actions.length === 0) return null;

  return (
    <div className="flex items-center gap-1">
      {actions.map(({ path, node }) => (
        <ToolbarButton key={path} path={path} node={node} />
      ))}
    </div>
  );
}
