import { useState } from 'react';
import * as Collapsible from '@radix-ui/react-collapsible';
import { ChevronDown, ChevronRight, ImageOff, Pause, Play } from 'lucide-react';
import type { ActionNode, ContainerNode, LeafNode, StringNode } from '../types';
import { ParamLeaf } from './ParamLeaf';
import { ActionButton } from './ActionButton';
import { StringLeaf } from './StringLeaf';
import { NodeIcon } from './NodeIcon';
import { TabsContainer } from './TabsContainer';
import { XYPadParam } from './XYPadParam';
import { useSSEState } from '../SSEContext';
import {
  isRenderable,
  resolveCurrentPreview,
  resolvePauseControl,
  resolveChapterControl,
  resolvePositionOf,
  type CurrentPreview,
} from '../nodeUtils';
import { dispatchSelectTag } from '../tagSelection';
import { patchParam } from '../api';
import { TimelineControl } from './controls/TimelineControl';

interface ParamContainerProps {
  node: ContainerNode;
  path: string;
  defaultOpen?: boolean;
  /** Sibling ENUM's current selection to show as a thumbnail+name+tags row — see resolveCurrentPreview. */
  currentPreview?: CurrentPreview;
}

export function ParamContainer({ node, path, defaultOpen, currentPreview }: ParamContainerProps) {
  const [open, setOpen] = useState(defaultOpen ?? node.uiHints?.['default-open'] === 'true');
  const iconName = node.uiHints?.['icon'];

  const visibleChildren = node.children.filter(isRenderable);

  const isTabs = node.uiHints?.['control-type'] === 'TABS';
  const isXYPad = node.uiHints?.['control-type'] === 'XY_PAD';
  // Live state comes from the single app-wide SSE connection (see SSEContext) — no
  // per-container subscription needed.
  const sseState = useSSEState();
  const pauseControl = resolvePauseControl(node, path, sseState);
  const chapterControl = resolveChapterControl(node, path, sseState);
  const position = resolvePositionOf(node, path, sseState);

  const togglePause = () => {
    if (!pauseControl) return;
    patchParam(pauseControl.path, pauseControl.paused ? 0 : 1).catch(() => {
      // error is handled by api.ts (session-expired event)
    });
  };

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
            <div className="relative w-24 h-24 rounded-lg shrink-0">
              {currentPreview.option?.preview ? (
                <img
                  src={currentPreview.option.preview}
                  alt=""
                  className="w-full h-full rounded-lg object-cover"
                />
              ) : (
                <div className="w-full h-full rounded-lg bg-neutral-800 flex items-center justify-center">
                  <ImageOff className="w-6 h-6 text-neutral-500" />
                </div>
              )}
              {pauseControl && (
                <button
                  onClick={togglePause}
                  aria-label={pauseControl.paused ? 'Resume playback' : 'Pause playback'}
                  className="absolute inset-0 rounded-lg flex items-end justify-end p-1"
                >
                  <span className="p-1 rounded-full bg-black/60 text-white flex items-center justify-center">
                    {pauseControl.paused ? (
                      <Play className="w-3.5 h-3.5" fill="currentColor" />
                    ) : (
                      <Pause className="w-3.5 h-3.5" fill="currentColor" />
                    )}
                  </span>
                </button>
              )}
            </div>
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
                      className="px-2 py-0.5 rounded-full text-xs border border-neutral-600 text-neutral-400 hover:border-phosphor hover:text-phosphor transition-colors"
                    >
                      {tag}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
        {chapterControl && currentPreview?.option?.chapters && currentPreview.option.chapters.length > 0 && (
          <TimelineControl
            chapters={currentPreview.option.chapters}
            duration={currentPreview.option.duration}
            position={position}
            chapterControl={chapterControl}
          />
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
