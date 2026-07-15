import { useState } from 'react';
import { Lock } from 'lucide-react';
import type { ContainerNode, LeafNode } from '../types';
import { patchParam } from '../api';
import { XYPadControl } from './controls/XYPadControl';
import { NodeIcon } from './NodeIcon';
import { InfoButton } from './InfoButton';
import { useSSE } from '../useSSE';

interface XYPadParamProps {
  node: ContainerNode;
  path: string;
}

/**
 * Renders an XYParam container (an "X"/"Y" child pair — see XYParam.withPadControl()) as a
 * single draggable point on a 2-D pad instead of two separate sliders.
 */
export function XYPadParam({ node, path }: XYPadParamProps) {
  const iconName = node.uiHints?.['icon'];
  const [showInfo, setShowInfo] = useState(false);

  const xNode = node.children.find((c) => c.name === 'X') as LeafNode | undefined;
  const yNode = node.children.find((c) => c.name === 'Y') as LeafNode | undefined;

  const sseState = useSSE([path]);

  if (!xNode || !yNode) return null;

  const xPath = `${path}/X`;
  const yPath = `${path}/Y`;

  const xLive = sseState.get(xPath);
  const yLive = sseState.get(yPath);
  const x = xLive?.value ?? xNode.value;
  const y = yLive?.value ?? yNode.value;
  const controlled = (xLive?.controlled ?? xNode.controlled) || (yLive?.controlled ?? yNode.controlled);

  const handleChange = async (nx: number, ny: number) => {
    try {
      await Promise.all([patchParam(xPath, nx), patchParam(yPath, ny)]);
    } catch {
      // error is handled by api.ts (session-expired event)
    }
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
      </div>
      {showInfo && node.description && (
        <p className="text-xs text-neutral-400 px-0.5">{node.description}</p>
      )}
      <XYPadControl
        x={x}
        y={y}
        minX={xNode.min}
        maxX={xNode.max}
        minY={yNode.min}
        maxY={yNode.max}
        disabled={controlled}
        onChange={handleChange}
      />
    </div>
  );
}
