import { useState } from 'react';
import * as Collapsible from '@radix-ui/react-collapsible';
import { ChevronDown, ChevronRight } from 'lucide-react';
import type { ActionNode, ContainerNode, LeafNode, ParamNode as PNode, StringNode } from '../types';
import { ParamContainer } from './ParamContainer';
import { ParamLeaf } from './ParamLeaf';
import { ActionButton } from './ActionButton';
import { StringLeaf } from './StringLeaf';
import { NodeIcon } from './NodeIcon';
import { useSSEState } from '../SSEContext';
import type { ParamState } from '../SSEContext';
import { isRenderable } from '../nodeUtils';

interface GeneratorTabProps {
  children: PNode[];
  path: string;
}

/** Names of the sibling controls that get folded into the active generator's own expander below,
 * rather than rendered in their normal (flat) position. Their tree paths are untouched — only
 * where they're drawn changes — so this is purely a display-time regrouping. */
const SAVE_CONTROL_NAMES = new Set(['Save Name', 'Save']);

function renderNode(child: PNode, childPath: string, sseState: Map<string, ParamState>) {
  if (child.type === 'CONTAINER') {
    return <ParamContainer key={child.name} node={child as ContainerNode} path={childPath} />;
  }
  if (child.type === 'ACTION') {
    return <ActionButton key={child.name} path={childPath} node={child as ActionNode} />;
  }
  if (child.type === 'STRING') {
    return <StringLeaf key={child.name} path={childPath} node={child as StringNode} />;
  }
  const liveState = sseState.get(childPath);
  return (
    <ParamLeaf
      key={child.name}
      path={childPath}
      node={child as LeafNode}
      liveValue={liveState?.value}
      liveControlled={liveState?.controlled}
    />
  );
}

/**
 * Renders a {@code control-type=GENERATOR_TAB} tab (the "Tab" generator tab): identical to the
 * normal flat rendering, except the {@code Save Name}/{@code Save} controls are drawn nested
 * inside the active generator's own expander instead of as siblings above/below it.
 */
export function GeneratorTab({ children, path }: GeneratorTabProps) {
  const sseState = useSSEState();
  const [activeOpen, setActiveOpen] = useState(false);

  const activeGenerator = children.find((c): c is ContainerNode => c.type === 'CONTAINER');
  const saveControls = children.filter((c) => SAVE_CONTROL_NAMES.has(c.name));

  return (
    <div className="space-y-1">
      {children.map((child) => {
        if (activeGenerator && child === activeGenerator) {
          const genPath = `${path}/${child.name}`;
          const iconName = activeGenerator.uiHints?.['icon'];
          return (
            <Collapsible.Root key={child.name} open={activeOpen} onOpenChange={setActiveOpen}>
              <Collapsible.Trigger className="flex items-center gap-2 w-full px-3 py-2 text-left rounded-lg hover:bg-neutral-800 transition-colors group">
                {activeOpen ? (
                  <ChevronDown className="w-4 h-4 text-neutral-400 shrink-0" />
                ) : (
                  <ChevronRight className="w-4 h-4 text-neutral-400 shrink-0" />
                )}
                {iconName && <NodeIcon name={iconName} className="w-4 h-4 text-neutral-400 shrink-0" />}
                <span className="text-sm font-semibold text-neutral-200 tracking-wide">
                  {activeGenerator.name}
                </span>
              </Collapsible.Trigger>
              <Collapsible.Content className="pl-4 pr-1 mt-1 space-y-1">
                {activeGenerator.children
                  .filter(isRenderable)
                  .map((c) => renderNode(c, `${genPath}/${c.name}`, sseState))}
                {saveControls.map((c) => renderNode(c, `${path}/${c.name}`, sseState))}
              </Collapsible.Content>
            </Collapsible.Root>
          );
        }
        if (activeGenerator && SAVE_CONTROL_NAMES.has(child.name)) {
          return null;
        }
        return renderNode(child, `${path}/${child.name}`, sseState);
      })}
    </div>
  );
}
