import { Fragment, useState } from 'react';
import { HelpCircle, Minus } from 'lucide-react';
import type { LeafNode, TriggerInfo } from '../types';
import { updateTrigger, deleteTrigger } from '../api';
import { ToggleControl } from './controls/ToggleControl';
import { SliderControl } from './controls/SliderControl';
import { EnumControl } from './controls/EnumControl';
import { SCRIPT_HELP } from '../scriptHelp';

interface TriggerEditorProps {
  path: string;
  trigger: TriggerInfo;
  /** The leaf this trigger targets, so the value control matches its type/min/max/options. Omitted for Action targets, which have nothing to set. */
  valueNode?: LeafNode;
}

/** Renders a control matching the target leaf's own type, so setting the fire-value never requires guessing at a raw number. */
function ValueControl({
  node,
  rawValue,
  onChange,
}: {
  node: LeafNode;
  rawValue: string;
  onChange: (text: string) => void;
}) {
  if (node.type === 'BOOLEAN') {
    const checked = Number(rawValue || '0') !== 0;
    return <ToggleControl value={checked} disabled={false} onChange={(v) => onChange(v ? '1' : '0')} />;
  }

  if (node.type === 'ENUM') {
    const parsed = Math.round(Number(rawValue));
    const idx = Number.isFinite(parsed) ? parsed : 0;
    const count = Math.max(1, Math.round(node.max) + 1);
    const options = node.options ?? Array.from({ length: count }, (_, i) => ({ label: String(i) }));
    return <EnumControl value={idx} options={options} disabled={false} onChange={(v) => onChange(String(v))} />;
  }

  const parsed = Number(rawValue);
  const value = Number.isFinite(parsed) ? parsed : node.min;
  return (
    <SliderControl
      value={value}
      min={node.min}
      max={node.max}
      disabled={false}
      onChange={(v) => onChange(String(v))}
      scale={node.uiHints?.['scale']}
      integer={node.type === 'INTEGER' || node.type === 'LONG'}
    />
  );
}

export function TriggerEditor({ path, trigger, valueNode }: TriggerEditorProps) {
  const [localCondition, setLocalCondition] = useState(trigger.condition);
  const [localCooldown, setLocalCooldown] = useState(trigger.cooldown);
  const [localValue, setLocalValue] = useState(trigger.value ?? '');
  const [compileError, setCompileError] = useState<string | null>(trigger.compileError ?? null);
  const [showHelp, setShowHelp] = useState(false);

  const isDirty =
    localCondition !== trigger.condition ||
    localCooldown !== trigger.cooldown ||
    localValue !== (trigger.value ?? '');

  const commit = async () => {
    try {
      const result = await updateTrigger(path, trigger.name, {
        condition: localCondition,
        cooldown: localCooldown,
        ...(trigger.value !== undefined ? { value: localValue } : {}),
      });
      const updated = result.triggers?.find((t) => t.name === trigger.name);
      setCompileError(updated?.compileError ?? null);
    } catch {
      // error handled by api.ts
    }
  };

  const cancel = () => {
    setLocalCondition(trigger.condition);
    setLocalCooldown(trigger.cooldown);
    setLocalValue(trigger.value ?? '');
    setCompileError(null);
  };

  const remove = async () => {
    try {
      await deleteTrigger(path, trigger.name);
    } catch {
      // error handled by api.ts
    }
  };

  const setEnabled = async (value: boolean) => {
    try {
      await updateTrigger(path, trigger.name, { enabled: value });
    } catch {
      // error handled by api.ts
    }
  };

  return (
    <div className="flex flex-col gap-1.5 pl-2 border-l-2 border-phosphor/40">
      <div className="flex items-center gap-1.5">
        <span className="text-xs text-neutral-500 uppercase tracking-wide font-semibold">Trigger</span>
        <ToggleControl value={trigger.enabled} disabled={false} onChange={setEnabled} />
        <button
          onClick={() => setShowHelp((v) => !v)}
          aria-label="Script reference"
          className={`p-0.5 rounded transition-colors ${
            showHelp ? 'text-phosphor' : 'text-neutral-500 hover:text-neutral-300'
          }`}
        >
          <HelpCircle className="w-3.5 h-3.5" />
        </button>
        <button
          onClick={remove}
          aria-label="Remove trigger"
          className="ml-auto p-0.5 rounded text-neutral-500 hover:text-red-400 transition-colors"
        >
          <Minus className="w-3.5 h-3.5" />
        </button>
      </div>

      <textarea
        value={localCondition}
        onChange={(e) => {
          setLocalCondition(e.target.value);
          if (compileError) setCompileError(null);
        }}
        rows={2}
        spellCheck={false}
        placeholder="e.g. bass() > 0.7"
        className={`w-full bg-neutral-800 rounded px-2 py-1.5 text-sm font-mono text-neutral-200 placeholder-neutral-500 focus:outline-none focus:ring-2 resize-y border ${
          isDirty ? 'border-orange-500 focus:ring-orange-500' : 'border-neutral-600 focus:ring-phosphor'
        }`}
      />

      <div className="flex items-center gap-2">
        <span className="text-xs text-neutral-400 shrink-0">Cooldown (s)</span>
        <input
          type="number"
          min={0}
          max={10}
          step={0.05}
          value={localCooldown}
          onChange={(e) => setLocalCooldown(Number(e.target.value))}
          className="w-20 bg-neutral-800 border border-neutral-600 rounded px-2 py-1 text-sm text-neutral-200 focus:outline-none focus:ring-2 focus:ring-phosphor"
        />
      </div>

      {trigger.value !== undefined && (
        <div className="flex flex-col gap-1.5">
          <span className="text-xs text-neutral-400">Value to set</span>
          {valueNode ? (
            <ValueControl node={valueNode} rawValue={localValue} onChange={setLocalValue} />
          ) : (
            <input
              type="text"
              value={localValue}
              onChange={(e) => setLocalValue(e.target.value)}
              className="w-full bg-neutral-800 border border-neutral-600 rounded px-2 py-1 text-sm text-neutral-200 focus:outline-none focus:ring-2 focus:ring-phosphor"
            />
          )}
        </div>
      )}

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
          onClick={cancel}
          disabled={!isDirty}
          className="px-3 py-1 text-xs rounded border border-neutral-600 text-neutral-300 hover:bg-neutral-700 disabled:opacity-40 disabled:cursor-not-allowed"
        >
          Cancel
        </button>
        <button
          onClick={commit}
          disabled={!isDirty}
          className="px-3 py-1 text-xs rounded bg-phosphor text-void hover:bg-phosphor/90 disabled:opacity-40 disabled:cursor-not-allowed"
        >
          Update
        </button>
      </div>

      {trigger.status !== 'OK' && (
        <p className="text-xs text-neutral-500 px-0.5">{trigger.status}</p>
      )}

      {compileError && (
        <pre className="text-xs text-red-400 bg-red-950/50 border border-red-800 rounded px-2 py-1.5 overflow-x-auto whitespace-pre-wrap break-all">
          {compileError}
        </pre>
      )}
    </div>
  );
}
