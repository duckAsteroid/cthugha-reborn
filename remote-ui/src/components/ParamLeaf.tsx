import { Lock } from 'lucide-react';
import type { LeafNode } from '../types';
import { patchParam } from '../api';
import { SliderControl } from './controls/SliderControl';
import { KnobControl } from './controls/KnobControl';
import { ToggleControl } from './controls/ToggleControl';
import { EnumControl } from './controls/EnumControl';
import { CarouselControl } from './controls/CarouselControl';
import { NodeIcon } from './NodeIcon';

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

  const handleChange = async (newValue: number) => {
    try {
      await patchParam(path, newValue);
    } catch {
      // error is handled by api.ts (session-expired event)
    }
  };

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
      if (node.uiHints?.['control-type'] === 'CAROUSEL') {
        return (
          <CarouselControl
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
      </div>
      {renderControl()}
    </div>
  );
}
