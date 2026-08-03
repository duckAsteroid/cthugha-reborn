import { useState } from 'react';
import { Loader2, Plus } from 'lucide-react';
import type { ActionNode, ContainerNode, LeafNode } from '../types';
import { executeAction } from '../api';
import { ParamContainer } from './ParamContainer';
import { ParamLeaf } from './ParamLeaf';
import { useSSEState } from '../SSEContext';
import { isRenderable } from '../nodeUtils';

interface AddListTabProps {
  node: ContainerNode;
  path: string;
}

/**
 * Renders a {@code control-type=ADD_LIST} tab: some number of item containers (e.g. wave
 * instances) plus exactly one picker leaf and one create action used to add more (e.g.
 * "New Type" + "New Wave"). Items render first; the picker stays hidden behind an "add" button
 * below them until clicked, rather than sitting permanently above the list.
 */
export function AddListTab({ node, path }: AddListTabProps) {
  const [adding, setAdding] = useState(false);
  const [busy, setBusy] = useState(false);
  const sseState = useSSEState();

  const visible = node.children.filter(isRenderable);
  const items = visible.filter((c): c is ContainerNode => c.type === 'CONTAINER');
  const action = visible.find((c): c is ActionNode => c.type === 'ACTION');
  const picker = visible.find(
    (c): c is LeafNode => c.type !== 'CONTAINER' && c.type !== 'ACTION' && c.type !== 'STRING',
  );

  const handleAdd = async () => {
    if (!action || busy) return;
    setBusy(true);
    try {
      await executeAction(`${path}/${action.name}`);
      setAdding(false);
    } catch {
      // error is handled by api.ts (session-expired event)
    } finally {
      setBusy(false);
    }
  };

  if (!action) return null;

  const pickerPath = picker ? `${path}/${picker.name}` : '';
  const pickerLive = sseState.get(pickerPath);

  return (
    <div className="space-y-1">
      {items.map((item) => (
        <ParamContainer key={item.name} node={item} path={`${path}/${item.name}`} />
      ))}

      {adding ? (
        <div className="flex flex-col gap-1.5 py-1.5 px-3">
          {picker && (
            <ParamLeaf
              path={pickerPath}
              node={picker}
              liveValue={pickerLive?.value}
              liveControlled={pickerLive?.controlled}
            />
          )}
          <div className="flex justify-end gap-2">
            <button
              onClick={() => setAdding(false)}
              className="px-3 py-1 text-xs rounded border border-neutral-600 text-neutral-300 hover:bg-neutral-700"
            >
              Cancel
            </button>
            <button
              onClick={handleAdd}
              disabled={busy}
              className="flex items-center gap-1.5 px-3 py-1 text-xs rounded bg-phosphor text-void hover:bg-phosphor/90 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {busy && <Loader2 className="w-3 h-3 animate-spin" />}
              {action.name}
            </button>
          </div>
        </div>
      ) : (
        <div className="py-1.5 px-3">
          <button
            onClick={() => setAdding(true)}
            className="flex items-center gap-2 px-3 py-1.5 border border-dashed border-neutral-600 text-neutral-400 hover:text-neutral-200 hover:border-neutral-400 text-sm font-medium rounded-md transition-colors"
          >
            <Plus className="w-3.5 h-3.5" />
            {action.name}
          </button>
        </div>
      )}
    </div>
  );
}
