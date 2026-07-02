import { useState } from 'react';
import * as Collapsible from '@radix-ui/react-collapsible';
import { ChevronDown, ChevronRight } from 'lucide-react';
import type { ActionNode, ContainerNode, LeafNode, StringNode } from '../types';
import { ParamLeaf } from './ParamLeaf';
import { ActionButton } from './ActionButton';
import { StringLeaf } from './StringLeaf';
import { NodeIcon } from './NodeIcon';
import type { ParamState } from '../useSSE';

interface ParamContainerProps {
  node: ContainerNode;
  path: string;
  sseState: Map<string, ParamState>;
  defaultOpen?: boolean;
}

export function ParamContainer({ node, path, sseState, defaultOpen = false }: ParamContainerProps) {
  const [open, setOpen] = useState(defaultOpen);
  const iconName = node.uiHints?.['icon'];

  return (
    <Collapsible.Root open={open} onOpenChange={setOpen}>
      <Collapsible.Trigger className="flex items-center gap-2 w-full px-3 py-2 text-left rounded-lg hover:bg-neutral-800 transition-colors group">
        {open ? (
          <ChevronDown className="w-4 h-4 text-neutral-400 shrink-0" />
        ) : (
          <ChevronRight className="w-4 h-4 text-neutral-400 shrink-0" />
        )}
        {iconName && (
          <NodeIcon name={iconName} className="w-4 h-4 text-neutral-400 shrink-0" />
        )}
        <span className="text-sm font-semibold text-neutral-200 uppercase tracking-wide">
          {node.name}
        </span>
      </Collapsible.Trigger>

      <Collapsible.Content className="pl-4 pr-1 mt-1 space-y-1">
        {node.children.filter(child => child.uiHints?.['hidden'] !== 'true').map((child) => {
          const childPath = path ? `${path}/${child.name}` : child.name;

          if (child.type === 'CONTAINER') {
            return (
              <ParamContainer
                key={child.name}
                node={child}
                path={childPath}
                sseState={sseState}
              />
            );
          }

          if (child.type === 'ACTION') {
            return (
              <ActionButton
                key={child.name}
                path={childPath}
                node={child as ActionNode}
              />
            );
          }

          if (child.type === 'STRING') {
            return (
              <StringLeaf
                key={child.name}
                path={childPath}
                node={child as StringNode}
              />
            );
          }

          const leafNode = child as LeafNode;
          const liveState = sseState.get(childPath);

          return (
            <ParamLeaf
              key={child.name}
              path={childPath}
              node={leafNode}
              liveValue={liveState?.value}
              liveControlled={liveState?.controlled}
            />
          );
        })}
      </Collapsible.Content>
    </Collapsible.Root>
  );
}
