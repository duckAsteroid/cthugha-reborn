import { Fragment, useState } from 'react';
import { HelpCircle, Minus } from 'lucide-react';
import type { AnimationInfo } from '../types';
import { updateAnimation, deleteAnimation } from '../api';
import { ToggleControl } from './controls/ToggleControl';
import { SCRIPT_HELP } from '../scriptHelp';

interface AnimationEditorProps {
  path: string;
  animation: AnimationInfo;
}

export function AnimationEditor({ path, animation }: AnimationEditorProps) {
  const [localScript, setLocalScript] = useState(animation.script);
  const [compileError, setCompileError] = useState<string | null>(animation.compileError ?? null);
  const [showHelp, setShowHelp] = useState(false);

  const isDirty = localScript !== animation.script;

  const commit = async () => {
    try {
      const result = await updateAnimation(path, { script: localScript });
      setCompileError(result.animation?.compileError ?? null);
    } catch {
      // error handled by api.ts
    }
  };

  const cancel = () => {
    setLocalScript(animation.script);
    setCompileError(null);
  };

  const remove = async () => {
    try {
      await deleteAnimation(path);
    } catch {
      // error handled by api.ts
    }
  };

  const setEnabled = async (value: boolean) => {
    try {
      await updateAnimation(path, { enabled: value });
    } catch {
      // error handled by api.ts
    }
  };

  return (
    <div className="flex flex-col gap-1.5 pl-2 border-l-2 border-indigo-500/40">
      <div className="flex items-center gap-1.5">
        <span className="text-xs text-neutral-500 uppercase tracking-wide font-semibold">Animation</span>
        <ToggleControl value={animation.enabled} disabled={false} onChange={setEnabled} />
        <button
          onClick={() => setShowHelp((v) => !v)}
          aria-label="Script reference"
          className={`p-0.5 rounded transition-colors ${
            showHelp ? 'text-indigo-400' : 'text-neutral-500 hover:text-neutral-300'
          }`}
        >
          <HelpCircle className="w-3.5 h-3.5" />
        </button>
        <button
          onClick={remove}
          aria-label="Remove animation"
          className="ml-auto p-0.5 rounded text-neutral-500 hover:text-red-400 transition-colors"
        >
          <Minus className="w-3.5 h-3.5" />
        </button>
      </div>

      <textarea
        value={localScript}
        onChange={(e) => {
          setLocalScript(e.target.value);
          if (compileError) setCompileError(null);
        }}
        rows={2}
        spellCheck={false}
        placeholder="e.g. sine(0.05)"
        className={`w-full bg-neutral-800 rounded px-2 py-1.5 text-sm font-mono text-neutral-200 placeholder-neutral-500 focus:outline-none focus:ring-2 resize-y border ${
          isDirty
            ? 'border-orange-500 focus:ring-orange-500'
            : 'border-neutral-600 focus:ring-indigo-500'
        }`}
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
          onClick={cancel}
          disabled={!isDirty}
          className="px-3 py-1 text-xs rounded border border-neutral-600 text-neutral-300 hover:bg-neutral-700 disabled:opacity-40 disabled:cursor-not-allowed"
        >
          Cancel
        </button>
        <button
          onClick={commit}
          disabled={!isDirty}
          className="px-3 py-1 text-xs rounded bg-indigo-600 text-white hover:bg-indigo-500 disabled:opacity-40 disabled:cursor-not-allowed"
        >
          Update
        </button>
      </div>

      {compileError && (
        <pre className="text-xs text-red-400 bg-red-950/50 border border-red-800 rounded px-2 py-1.5 overflow-x-auto whitespace-pre-wrap break-all">
          {compileError}
        </pre>
      )}
    </div>
  );
}
