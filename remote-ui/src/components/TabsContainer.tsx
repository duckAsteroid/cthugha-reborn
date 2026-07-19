import { useState } from 'react';
import * as RadixTabs from '@radix-ui/react-tabs';
import type { ActionNode, ContainerNode, LeafNode, StringNode, ParamNode } from '../types';
import { ParamContainer } from './ParamContainer';
import { ParamLeaf } from './ParamLeaf';
import { ActionButton } from './ActionButton';
import { StringLeaf } from './StringLeaf';
import { NodeIcon } from './NodeIcon';
import { ActionToolbar } from './ActionToolbar';
import type { ToolbarEntry } from './ActionToolbar';
import { useSSEState } from '../SSEContext';
import type { ParamState } from '../SSEContext';
import { isRenderable, flattenSoleContainer } from '../nodeUtils';

interface TabsContainerProps {
  node: ContainerNode;
  path: string;
}

function renderChild(child: ParamNode, basePath: string, sseState: Map<string, ParamState>, siblings: ParamNode[]) {
  const childPath = basePath ? `${basePath}/${child.name}` : child.name;
  if (child.type === 'CONTAINER') {
    return <ParamContainer key={child.name} node={child as ContainerNode} path={childPath} />;
  }
  if (child.type === 'ACTION') {
    return <ActionButton key={child.name} path={childPath} node={child as ActionNode} />;
  }
  if (child.type === 'STRING') {
    const pairedFieldName = child.uiHints?.['paired-value-field'];
    const pairedValueNode = pairedFieldName
      ? (siblings.find((c) => c.name === pairedFieldName && c.type === 'STRING') as StringNode | undefined)
      : undefined;
    return (
      <StringLeaf
        key={child.name}
        path={childPath}
        node={child as StringNode}
        pairedValuePath={pairedValueNode ? `${basePath}/${pairedValueNode.name}` : undefined}
        pairedValueNode={pairedValueNode}
      />
    );
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

export function TabsContainer({ node, path }: TabsContainerProps) {
  const visibleChildren = node.children.filter(isRenderable);
  const tabs = visibleChildren.filter(
    c => c.type === 'CONTAINER' && c.uiHints?.['control-type'] !== 'EXPANDER',
  ) as ContainerNode[];
  const expanders = visibleChildren.filter(
    c => c.type === 'CONTAINER' && c.uiHints?.['control-type'] === 'EXPANDER',
  ) as ContainerNode[];

  const [activeTab, setActiveTab] = useState(tabs[0]?.name ?? '');

  // Live state comes from the single app-wide SSE connection (see SSEContext).
  const sseState = useSSEState();

  // Expander containers (e.g. "General") hold a mix of top-level actions and sub-containers.
  // Actions are pulled out and rendered as an icon toolbar above the tabs; anything else
  // (e.g. the "Remote" sub-container) stays as a collapsible section below the tabs.
  const expanderData = expanders.map(exp => {
    const expPath = path ? `${path}/${exp.name}` : exp.name;
    const toolbarActions: ToolbarEntry[] = exp.children
      .filter((c): c is ActionNode => c.type === 'ACTION' && c.uiHints?.['hidden'] !== 'true')
      .map(c => ({ path: `${expPath}/${c.name}`, node: c }));
    const remainingChildren = exp.children.filter(c => c.type !== 'ACTION');
    return { expPath, toolbarActions, remainingNode: { ...exp, children: remainingChildren } };
  });
  const toolbarActions = expanderData.flatMap(e => e.toolbarActions);
  const remainingExpanders = expanderData.filter(e => isRenderable(e.remainingNode));

  return (
    <div className="flex flex-col gap-2">
      <ActionToolbar actions={toolbarActions} />
      <RadixTabs.Root value={activeTab} onValueChange={setActiveTab} className="flex flex-col gap-1">
        <RadixTabs.List
          className="flex flex-nowrap gap-1 overflow-x-auto border-b border-neutral-700 pb-2
                     [scrollbar-width:thin] [scrollbar-color:#525252_transparent]
                     [&::-webkit-scrollbar]:h-1.5
                     [&::-webkit-scrollbar-track]:bg-transparent
                     [&::-webkit-scrollbar-thumb]:bg-neutral-600
                     [&::-webkit-scrollbar-thumb]:rounded-full"
        >
          {tabs.map(tab => {
            const icon = tab.uiHints?.['icon'];
            return (
              <RadixTabs.Trigger
                key={tab.name}
                value={tab.name}
                className="flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium text-neutral-400 rounded-t
                           hover:text-neutral-200 hover:bg-neutral-800 transition-colors
                           data-[state=active]:text-indigo-300 data-[state=active]:bg-neutral-800
                           data-[state=active]:border-b-2 data-[state=active]:border-indigo-400"
              >
                {icon && <NodeIcon name={icon} className="w-3.5 h-3.5 shrink-0" />}
                {tab.name}
              </RadixTabs.Trigger>
            );
          })}
        </RadixTabs.List>

        {tabs.map(tab => {
          const tabPath = path ? `${path}/${tab.name}` : tab.name;
          const visibleTabChildren = tab.children.filter(isRenderable);
          const { children: tabChildren, path: contentPath } = flattenSoleContainer(visibleTabChildren, tabPath);
          return (
            <RadixTabs.Content key={tab.name} value={tab.name} className="space-y-1 pt-1">
              {tabChildren.map(child => renderChild(child, contentPath, sseState, tab.children))}
            </RadixTabs.Content>
          );
        })}
      </RadixTabs.Root>

      {remainingExpanders.map(({ expPath, remainingNode }) => (
        <ParamContainer key={remainingNode.name} node={remainingNode} path={expPath} />
      ))}
    </div>
  );
}
