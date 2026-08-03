import { Fragment } from 'react';
import { HelpCircle } from 'lucide-react';
import { SCRIPT_HELP } from '../scriptHelp';
import type { DraftKind } from '../hooks/useBindingCreator';

interface BindingCreatorPanelProps {
  menuOpen: boolean;
  draftKind: DraftKind | null;
  draftValue: string;
  setDraftValue: (v: string) => void;
  showHelp: boolean;
  setShowHelp: (fn: (v: boolean) => boolean) => void;
  chooseAnimation: () => void;
  chooseTrigger: () => void;
  cancelDraft: () => void;
  confirmDraft: () => void;
}

/** The body of the unified "+" affordance: the Animation-vs-Trigger choice menu, or the draft form for whichever was picked. Renders nothing once neither is open. */
export function BindingCreatorPanel({
  menuOpen,
  draftKind,
  draftValue,
  setDraftValue,
  showHelp,
  setShowHelp,
  chooseAnimation,
  chooseTrigger,
  cancelDraft,
  confirmDraft,
}: BindingCreatorPanelProps) {
  if (menuOpen) {
    return (
      <div className="flex gap-2 pl-2 border-l-2 border-phosphor/40">
        <button
          onClick={chooseAnimation}
          className="px-3 py-1 text-xs rounded border border-neutral-600 text-neutral-300 hover:bg-neutral-700"
        >
          Animation
        </button>
        <button
          onClick={chooseTrigger}
          className="px-3 py-1 text-xs rounded border border-neutral-600 text-neutral-300 hover:bg-neutral-700"
        >
          Trigger
        </button>
      </div>
    );
  }

  if (draftKind === null) return null;

  return (
    <div className="flex flex-col gap-1.5 pl-2 border-l-2 border-phosphor/40">
      <div className="flex items-center justify-between">
        <span className="text-xs text-neutral-500 uppercase tracking-wide font-semibold">
          {draftKind === 'animation' ? 'Animation' : 'Trigger'}
        </span>
        <button
          onClick={() => setShowHelp((v) => !v)}
          aria-label="Script reference"
          className={`p-0.5 rounded transition-colors ${
            showHelp ? 'text-phosphor' : 'text-neutral-500 hover:text-neutral-300'
          }`}
        >
          <HelpCircle className="w-3.5 h-3.5" />
        </button>
      </div>
      <textarea
        value={draftValue}
        onChange={(e) => setDraftValue(e.target.value)}
        rows={2}
        spellCheck={false}
        placeholder={draftKind === 'animation' ? 'e.g. sine(0.05)' : 'e.g. bass() > 0.7'}
        autoFocus
        className="w-full bg-neutral-800 rounded px-2 py-1.5 text-sm font-mono text-neutral-200 placeholder-neutral-500 focus:outline-none focus:ring-2 focus:ring-phosphor resize-y border border-neutral-600"
      />
      {showHelp && (
        <div className="rounded border border-neutral-700 bg-neutral-800/80 px-3 py-2.5 text-xs space-y-2.5">
          {SCRIPT_HELP.map(({ section, items }) => (
            <div key={section}>
              <div className="text-neutral-500 uppercase tracking-wide text-[10px] font-semibold mb-1">
                {section}
              </div>
              <div className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-0.5">
                {items.map(({ name, desc }) => (
                  <Fragment key={name}>
                    <code className="text-phosphor font-mono">{name}</code>
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
          onClick={cancelDraft}
          className="px-3 py-1 text-xs rounded border border-neutral-600 text-neutral-300 hover:bg-neutral-700"
        >
          Cancel
        </button>
        <button
          onClick={confirmDraft}
          disabled={draftValue.trim() === ''}
          className="px-3 py-1 text-xs rounded bg-phosphor text-void hover:bg-phosphor/90 disabled:opacity-40 disabled:cursor-not-allowed"
        >
          Add
        </button>
      </div>
    </div>
  );
}
