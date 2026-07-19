import { useState } from 'react';
import { Lock, Plus } from 'lucide-react';
import type { LeafNode } from '../types';
import { patchParam, createAnimation } from '../api';
import { SliderControl } from './controls/SliderControl';
import { KnobControl } from './controls/KnobControl';
import { ToggleControl } from './controls/ToggleControl';
import { EnumControl } from './controls/EnumControl';
import { CarouselControl } from './controls/CarouselControl';
import { GridControl } from './controls/GridControl';
import { SearchListControl } from './controls/SearchListControl';
import { NodeIcon } from './NodeIcon';
import { InfoButton } from './InfoButton';
import { AnimationEditor } from './AnimationEditor';

interface ParamLeafProps {
  path: string;
  node: LeafNode;
  /** Optional override from SSE updates */
  liveValue?: number;
  liveControlled?: boolean;
}

export function ParamLeaf({ path, node, liveValue, liveControlled }: ParamLeafProps) {
  const value = liveValue ?? node.value;
  const controlled = liveControlled ?? node.controlled;
  const iconName = node.uiHints?.['icon'];
  const [showInfo, setShowInfo] = useState(false);
  const [draftScript, setDraftScript] = useState<string | null>(null);

  const handleChange = async (newValue: number) => {
    try {
      await patchParam(path, newValue);
    } catch {
      // error is handled by api.ts (session-expired event)
    }
  };

  const startAnimation = () => setDraftScript('sine(0.05)');

  const confirmAnimation = async () => {
    try {
      await createAnimation(path, draftScript ?? 'sine(0.05)');
      setDraftScript(null);
    } catch {
      // error is handled by api.ts (session-expired event)
    }
  };

  const cancelAnimation = () => setDraftScript(null);

  const renderControl = () => {
    if (node.type === 'BOOLEAN') {
      return (
        <ToggleControl
          value={value !== 0}
          disabled={controlled}
          onChange={(v) => handleChange(v ? 1 : 0)}
        />
      );
    }

    if (node.type === 'ENUM') {
      const count = Math.max(1, Math.round(node.max) + 1);
      const options = node.options ?? Array.from({ length: count }, (_, i) => ({ label: String(i) }));
      const enumControlType = node.uiHints?.['control-type'];
      if (enumControlType === 'CAROUSEL') {
        return (
          <CarouselControl
            value={Math.round(value)}
            options={options}
            disabled={controlled}
            onChange={handleChange}
          />
        );
      }
      if (enumControlType === 'GRID') {
        return (
          <GridControl
            value={Math.round(value)}
            options={options}
            disabled={controlled}
            onChange={handleChange}
          />
        );
      }
      if (enumControlType === 'LIST') {
        return (
          <SearchListControl
            value={Math.round(value)}
            options={options}
            disabled={controlled}
            onChange={handleChange}
          />
        );
      }
      return (
        <EnumControl
          value={Math.round(value)}
          options={options}
          disabled={controlled}
          onChange={handleChange}
        />
      );
    }

    // DOUBLE, INTEGER, LONG
    if (node.uiHints?.['control-type'] === 'KNOB') {
      return (
        <KnobControl
          value={value}
          min={node.min}
          max={node.max}
          disabled={controlled}
          onChange={handleChange}
        />
      );
    }

    return (
      <SliderControl
        value={value}
        min={node.min}
        max={node.max}
        disabled={controlled}
        onChange={handleChange}
        scale={node.uiHints?.['scale']}
        integer={node.type === 'INTEGER' || node.type === 'LONG'}
      />
    );
  };

  return (
    <div className="flex flex-col gap-1.5 py-2 px-3 rounded-lg bg-neutral-900/50">
      <div className="flex items-center gap-1.5">
        {iconName && <NodeIcon name={iconName} className="w-3.5 h-3.5 text-neutral-400 shrink-0" />}
        <span className="text-sm text-neutral-300 font-medium">{node.name}</span>
        {controlled && (
          <Lock className="w-3 h-3 text-neutral-500 shrink-0" aria-label="Controlled by animator" />
        )}
        {node.description && (
          <InfoButton open={showInfo} onToggle={() => setShowInfo((v) => !v)} />
        )}
        {!node.animation && draftScript === null && (
          <button
            onClick={startAnimation}
            aria-label="Add animation"
            className="ml-auto p-0.5 rounded text-neutral-500 hover:text-indigo-400 transition-colors shrink-0"
          >
            <Plus className="w-3.5 h-3.5" />
          </button>
        )}
      </div>
      {showInfo && node.description && (
        <p className="text-xs text-neutral-400 px-0.5">{node.description}</p>
      )}
      {renderControl()}
      {node.animation && <AnimationEditor path={path} animation={node.animation} />}
      {!node.animation && draftScript !== null && (
        <div className="flex flex-col gap-1.5 pl-2 border-l-2 border-indigo-500/40">
          <textarea
            value={draftScript}
            onChange={(e) => setDraftScript(e.target.value)}
            rows={2}
            spellCheck={false}
            placeholder="e.g. sine(0.05)"
            autoFocus
            className="w-full bg-neutral-800 rounded px-2 py-1.5 text-sm font-mono text-neutral-200 placeholder-neutral-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-y border border-neutral-600"
          />
          <div className="flex justify-end gap-2">
            <button
              onClick={cancelAnimation}
              className="px-3 py-1 text-xs rounded border border-neutral-600 text-neutral-300 hover:bg-neutral-700"
            >
              Cancel
            </button>
            <button
              onClick={confirmAnimation}
              disabled={draftScript.trim() === ''}
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
