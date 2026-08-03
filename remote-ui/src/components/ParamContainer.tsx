import { useState } from 'react';
import * as Collapsible from '@radix-ui/react-collapsible';
import { ChevronDown, ChevronRight, ImageOff } from 'lucide-react';
import type { ActionNode, ContainerNode, LeafNode, StringNode } from '../types';
import { ParamLeaf } from './ParamLeaf';
import { ActionButton } from './ActionButton';
import { StringLeaf } from './StringLeaf';
import { NodeIcon } from './NodeIcon';
import { TabsContainer } from './TabsContainer';
import { XYPadParam } from './XYPadParam';
import { useSSEState } from '../SSEContext';
import { isRenderable, resolveCurrentPreview, type CurrentPreview } from '../nodeUtils';
import { dispatchSelectTag } from '../tagSelection';

interface ParamContainerProps {
  node: ContainerNode;
  path: string;
  defaultOpen?: boolean;
  /** Sibling ENUM's current selection to show as a thumbnail+name+tags row — see resolveCurrentPreview. */
  currentPreview?: CurrentPreview;
}

export function ParamContainer({ node, path, defaultOpen = false, currentPreview }: ParamContainerProps) {
  const [open, setOpen] = useState(defaultOpen);
  const iconName = node.uiHints?.['icon'];

  const visibleChildren = node.children.filter(isRenderable);

  const isTabs = node.uiHints?.['control-type'] === 'TABS';
  const isXYPad = node.uiHints?.['control-type'] === 'XY_PAD';
  // Live state comes from the single app-wide SSE connection (see SSEContext) — no
  // per-container subscription needed.
  const sseState = useSSEState();

  if (isTabs) {
    return <TabsContainer node={node} path={path} />;
  }

  if (isXYPad) {
    return <XYPadParam node={node} path={path} />;
  }

  if (visibleChildren.length === 0) {
    return null;
  }

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
        <span className="text-sm font-semibold text-neutral-200 tracking-wide">
          {node.name}
        </span>
      </Collapsible.Trigger>

      <Collapsible.Content className="pl-4 pr-1 mt-1 space-y-1">
        {currentPreview && (
          <div className="flex items-center gap-3 py-2 px-3 rounded-lg bg-neutral-900/50">
            {currentPreview.option?.preview ? (
              <img
                src={currentPreview.option.preview}
                alt=""
                className="w-24 h-24 rounded-lg object-cover shrink-0"
              />
            ) : (
              <div className="w-24 h-24 rounded-lg bg-neutral-800 flex items-center justify-center shrink-0">
                <ImageOff className="w-6 h-6 text-neutral-500" />
              </div>
            )}
            <div className="flex flex-col gap-1.5 min-w-0">
              <span className="text-sm text-neutral-200 font-medium truncate">
                {currentPreview.option?.label ?? '—'}
              </span>
              {currentPreview.option?.tags && currentPreview.option.tags.length > 0 && (
                <div className="flex flex-wrap gap-1.5">
                  {currentPreview.option.tags.map((tag) => (
                    <button
                      key={tag}
                      onClick={() => dispatchSelectTag(currentPreview.siblingPath, tag)}
                      className="px-2 py-0.5 rounded-full text-xs border border-neutral-600 text-neutral-400 hover:border-indigo-400 hover:text-indigo-300 transition-colors"
                    >
                      {tag}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
        {visibleChildren.map((child) => {
          const childPath = path ? `${path}/${child.name}` : child.name;

          if (child.type === 'CONTAINER') {
            return (
              <ParamContainer
                key={child.name}
                node={child as ContainerNode}
                path={childPath}
                currentPreview={resolveCurrentPreview(child, node.children, path, sseState)}
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
            return <StringLeaf key={child.name} path={childPath} node={child as StringNode} />;
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
