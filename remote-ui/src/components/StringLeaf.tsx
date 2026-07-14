import { Fragment, useState } from 'react';
import { HelpCircle } from 'lucide-react';
import type { StringNode } from '../types';
import { patchStringParam } from '../api';
import { InfoButton } from './InfoButton';

interface StringLeafProps {
  path: string;
  node: StringNode;
}

const SCRIPT_HELP: { section: string; items: { name: string; desc: string }[] }[] = [
  {
    section: 'Variables',
    items: [
      { name: 't',       desc: 'elapsed seconds since start' },
      { name: 'TWO_PI',  desc: '2π ≈ 6.283' },
    ],
  },
  {
    section: 'Wave helpers — return [0, 1]',
    items: [
      { name: 'sine(hz)',         desc: 'smooth sine wave' },
      { name: 'cosine(hz)',       desc: 'smooth cosine wave' },
      { name: 'saw(hz)',          desc: 'sawtooth ramp 0→1' },
      { name: 'tri(hz)',          desc: 'triangle 0→1→0' },
      { name: 'pulse(hz, duty)',  desc: 'square wave; duty 0–1 e.g. 0.5' },
      { name: 'phase(hz)',        desc: 'raw angular phase in radians' },
    ],
  },
  {
    section: 'Also in scope',
    items: [
      { name: 'Math.*', desc: 'sin, cos, abs, min, max, pow, sqrt…' },
    ],
  },
];

export function StringLeaf({ path, node }: StringLeafProps) {
  const [localValue, setLocalValue] = useState(node.value);
  const [committedValue, setCommittedValue] = useState(node.value);
  const [compileError, setCompileError] = useState<string | null>(null);
  const [showHelp, setShowHelp] = useState(false);
  const [showInfo, setShowInfo] = useState(false);

  const isDirty = localValue !== committedValue;

  const commit = async (value: string) => {
    try {
      const result = await patchStringParam(path, value);
      setCommittedValue(value);
      setCompileError(result.compileError ?? null);
    } catch {
      // error handled by api.ts
    }
  };

  const cancel = () => {
    setLocalValue(committedValue);
    setCompileError(null);
  };

  if (node.uiHints?.['control-type'] === 'CODE_EDITOR') {
    return (
      <div className="flex flex-col gap-1.5 py-2 px-3 rounded-lg bg-neutral-900/50">

        {/* Label row */}
        <div className="flex items-center gap-1.5">
          <span className="text-sm text-neutral-300 font-medium">{node.name}</span>
          {isDirty && (
            <span className="w-2 h-2 rounded-full bg-orange-400 shrink-0" aria-label="unsaved changes" />
          )}
          <div className="ml-auto flex items-center gap-1">
            {node.description && (
              <InfoButton open={showInfo} onToggle={() => setShowInfo(v => !v)} />
            )}
            <button
              onClick={() => setShowHelp(v => !v)}
              aria-label="Script reference"
              className={`p-0.5 rounded transition-colors ${
                showHelp ? 'text-indigo-400' : 'text-neutral-500 hover:text-neutral-300'
              }`}
            >
              <HelpCircle className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        {/* Description */}
        {showInfo && node.description && (
          <p className="text-xs text-neutral-400 px-0.5">{node.description}</p>
        )}

        {/* Editor */}
        <textarea
          value={localValue}
          onChange={(e) => {
            setLocalValue(e.target.value);
            if (compileError) setCompileError(null);
          }}
          rows={3}
          spellCheck={false}
          placeholder="e.g. sine(10)  or  0.5*sine(1)+0.5*cosine(3)  or  saw(0.5)"
          className={`w-full bg-neutral-800 rounded px-2 py-1.5 text-sm font-mono text-neutral-200 placeholder-neutral-500 focus:outline-none focus:ring-2 resize-y border ${
            isDirty
              ? 'border-orange-500 focus:ring-orange-500'
              : 'border-neutral-600 focus:ring-indigo-500'
          }`}
        />

        {/* Help panel */}
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
                      <code className="text-indigo-300 font-mono">{name}</code>
                      <span className="text-neutral-400">{desc}</span>
                    </Fragment>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Buttons */}
        <div className="flex justify-end gap-2">
          <button
            onClick={cancel}
            disabled={!isDirty}
            className="px-3 py-1 text-xs rounded border border-neutral-600 text-neutral-300 hover:bg-neutral-700 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            Cancel
          </button>
          <button
            onClick={() => commit(localValue)}
            disabled={!isDirty}
            className="px-3 py-1 text-xs rounded bg-indigo-600 text-white hover:bg-indigo-500 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            Update
          </button>
        </div>

        {/* Compile error */}
        {compileError && (
          <pre className="text-xs text-red-400 bg-red-950/50 border border-red-800 rounded px-2 py-1.5 overflow-x-auto whitespace-pre-wrap break-all">
            {compileError}
          </pre>
        )}

      </div>
    );
  }

  return (
    <div className="flex flex-col gap-1.5 py-2 px-3 rounded-lg bg-neutral-900/50">
      <div className="flex items-center gap-1.5">
        <span className="text-sm text-neutral-300 font-medium">{node.name}</span>
        {node.description && (
          <InfoButton open={showInfo} onToggle={() => setShowInfo(v => !v)} />
        )}
      </div>
      {showInfo && node.description && (
        <p className="text-xs text-neutral-400 px-0.5">{node.description}</p>
      )}
      <input
        type="text"
        value={localValue}
        placeholder="leave blank for auto name"
        onChange={(e) => setLocalValue(e.target.value)}
        onBlur={() => commit(localValue)}
        onKeyDown={(e) => { if (e.key === 'Enter') commit(localValue); }}
        className="w-full bg-neutral-800 border border-neutral-600 rounded px-2 py-1 text-sm text-neutral-200 placeholder-neutral-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
      />
    </div>
  );
}
