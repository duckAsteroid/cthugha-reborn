import { useState } from 'react';
import { Loader2, Plus } from 'lucide-react';
import type { ActionNode } from '../types';
import { executeAction } from '../api';
import { NodeIcon } from './NodeIcon';
import { InfoButton } from './InfoButton';
import { TriggerEditor } from './TriggerEditor';
import { BindingCreatorPanel } from './BindingCreatorPanel';
import { useBindingCreator } from '../hooks/useBindingCreator';

interface ActionButtonProps {
  path: string;
  node: ActionNode;
}

export function ActionButton({ path, node }: ActionButtonProps) {
  const [busy, setBusy] = useState(false);
  const [showInfo, setShowInfo] = useState(false);
  const iconName = node.uiHints?.['icon'];
  const binding = useBindingCreator({ path });

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
          className="flex items-center gap-2 px-3 py-1.5 bg-phosphor hover:bg-phosphor/90 disabled:opacity-50 disabled:cursor-not-allowed text-void text-sm font-medium rounded-md transition-colors"
        >
          {busy ? (
            <Loader2 className="w-3.5 h-3.5 animate-spin" />
          ) : iconName ? (
            <NodeIcon name={iconName} className="w-3.5 h-3.5" />
          ) : null}
          {node.name}
        </button>
        <div className="ml-auto flex items-center gap-1 shrink-0">
          {node.description && (
            <InfoButton open={showInfo} onToggle={() => setShowInfo((v) => !v)} />
          )}
          {!binding.isOpen && (
            <button
              onClick={binding.handleAddClick}
              aria-label="Add trigger"
              className="p-0.5 rounded text-neutral-500 hover:text-phosphor transition-colors shrink-0"
            >
              <Plus className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </div>
      {showInfo && node.description && (
        <p className="text-xs text-neutral-400 px-0.5 mt-1.5">{node.description}</p>
      )}
      <div className="mt-1.5 flex flex-col gap-1.5">
        {(node.triggers ?? []).map((trigger) => (
          <TriggerEditor key={trigger.name} path={path} trigger={trigger} />
        ))}
        <BindingCreatorPanel
          menuOpen={binding.menuOpen}
          draftKind={binding.draftKind}
          draftValue={binding.draftValue}
          setDraftValue={binding.setDraftValue}
          showHelp={binding.showHelp}
          setShowHelp={binding.setShowHelp}
          chooseAnimation={binding.chooseAnimation}
          chooseTrigger={binding.chooseTrigger}
          cancelDraft={binding.cancelDraft}
          confirmDraft={binding.confirmDraft}
        />
      </div>
    </div>
  );
}
