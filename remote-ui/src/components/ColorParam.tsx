import { useState } from 'react';
import { Lock } from 'lucide-react';
import type { ContainerNode, LeafNode } from '../types';
import { patchParam } from '../api';
import { ColorControl } from './controls/ColorControl';
import { NodeIcon } from './NodeIcon';
import { InfoButton } from './InfoButton';
import { useSSEState } from '../SSEContext';

interface ColorParamProps {
  node: ContainerNode;
  path: string;
}

/**
 * Renders a ColorParam container (an "R"/"G"/"B" child triple — see ColorParam.withColorControl())
 * as a single native colour-swatch picker instead of three separate sliders.
 */
export function ColorParam({ node, path }: ColorParamProps) {
  const iconName = node.uiHints?.['icon'];
  const [showInfo, setShowInfo] = useState(false);

  const rNode = node.children.find((c) => c.name === 'R') as LeafNode | undefined;
  const gNode = node.children.find((c) => c.name === 'G') as LeafNode | undefined;
  const bNode = node.children.find((c) => c.name === 'B') as LeafNode | undefined;

  const sseState = useSSEState();

  if (!rNode || !gNode || !bNode) return null;

  const rPath = `${path}/R`;
  const gPath = `${path}/G`;
  const bPath = `${path}/B`;

  const rLive = sseState.get(rPath);
  const gLive = sseState.get(gPath);
  const bLive = sseState.get(bPath);
  const r = rLive?.value ?? rNode.value;
  const g = gLive?.value ?? gNode.value;
  const b = bLive?.value ?? bNode.value;
  const controlled =
    (rLive?.controlled ?? rNode.controlled) ||
    (gLive?.controlled ?? gNode.controlled) ||
    (bLive?.controlled ?? bNode.controlled);

  const handleChange = async (nr: number, ng: number, nb: number) => {
    try {
      await Promise.all([patchParam(rPath, nr), patchParam(gPath, ng), patchParam(bPath, nb)]);
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
          <InfoButton className="ml-auto" open={showInfo} onToggle={() => setShowInfo((v) => !v)} />
        )}
      </div>
      {showInfo && node.description && (
        <p className="text-xs text-neutral-400 px-0.5">{node.description}</p>
      )}
      <ColorControl r={r} g={g} b={b} disabled={controlled} onChange={handleChange} />
    </div>
  );
}
