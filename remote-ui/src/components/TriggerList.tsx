import { Fragment, useState } from 'react';
import { HelpCircle, Plus } from 'lucide-react';
import type { LeafNode, TriggerInfo } from '../types';
import { createTrigger } from '../api';
import { SCRIPT_HELP } from '../scriptHelp';
import { TriggerEditor } from './TriggerEditor';

interface TriggerListProps {
  path: string;
  triggers?: TriggerInfo[];
  /** The leaf this node is, so a new trigger's value control matches its type/min/max/options. Omitted for Action targets, which have nothing to set. */
  valueNode?: LeafNode;
}

/**
 * Renders every existing trigger targeting this node plus a `+` affordance to create another —
 * unlike animations (at most one per leaf), a node can have several triggers, so this is a list
 * rather than a single inline editor. Used by both ParamLeaf (leaf targets, value control shown)
 * and ActionButton (action targets, no value control).
 */
export function TriggerList({ path, triggers, valueNode }: TriggerListProps) {
  const [draftCondition, setDraftCondition] = useState<string | null>(null);
  const [showDraftHelp, setShowDraftHelp] = useState(false);

  const startTrigger = () => setDraftCondition('bass() > 0.7');
  const cancelTrigger = () => setDraftCondition(null);

  const confirmTrigger = async () => {
    try {
      await createTrigger(path, {
        condition: draftCondition ?? 'bass() > 0.7',
        ...(valueNode ? { value: String(valueNode.value) } : {}),
      });
      setDraftCondition(null);
    } catch {
      // error is handled by api.ts (session-expired event)
    }
  };

  return (
    <div className="flex flex-col gap-1.5">
      {(triggers ?? []).map((trigger) => (
        <TriggerEditor key={trigger.name} path={path} trigger={trigger} valueNode={valueNode} />
      ))}

      {draftCondition === null ? (
        <button
          onClick={startTrigger}
          aria-label="Add trigger"
          className="self-start flex items-center gap-1 px-1.5 py-0.5 rounded text-xs text-neutral-500 hover:text-indigo-400 transition-colors"
        >
          <Plus className="w-3.5 h-3.5" /> Trigger
        </button>
      ) : (
        <div className="flex flex-col gap-1.5 pl-2 border-l-2 border-indigo-500/40">
          <div className="flex items-center justify-end">
            <button
              onClick={() => setShowDraftHelp((v) => !v)}
              aria-label="Script reference"
              className={`p-0.5 rounded transition-colors ${
                showDraftHelp ? 'text-indigo-400' : 'text-neutral-500 hover:text-neutral-300'
              }`}
            >
              <HelpCircle className="w-3.5 h-3.5" />
            </button>
          </div>
          <textarea
            value={draftCondition}
            onChange={(e) => setDraftCondition(e.target.value)}
            rows={2}
            spellCheck={false}
            placeholder="e.g. bass() > 0.7"
            autoFocus
            className="w-full bg-neutral-800 rounded px-2 py-1.5 text-sm font-mono text-neutral-200 placeholder-neutral-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-y border border-neutral-600"
          />
          {showDraftHelp && (
            <div className="rounded border border-neutral-700 bg-neutral-800/80 px-3 py-2.5 text-xs space-y-2.5">
              {SCRIPT_HELP.map(({ section, items }) => (
                <div key={section}>
                  <div className="text-neutral-500 uppercase tracking-wide text-[10px] font-semibold mb-1">
                    {section}
                  </div>
                  <div className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-0.5">
                    {items.map(({ name, desc }) => (
                      <Fragment key={name}>
                        <code className="text-indigo-300 font-mono">{name}</code>
                        <span className="text-neutral-400">{desc}</span>
                      </Fragment>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
          <div className="flex justify-end gap-2">
            <button
              onClick={cancelTrigger}
              className="px-3 py-1 text-xs rounded border border-neutral-600 text-neutral-300 hover:bg-neutral-700"
            >
              Cancel
            </button>
            <button
              onClick={confirmTrigger}
              disabled={draftCondition.trim() === ''}
              className="px-3 py-1 text-xs rounded bg-indigo-600 text-white hover:bg-indigo-500 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              Add
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
