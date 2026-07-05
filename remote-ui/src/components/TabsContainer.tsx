import { useState } from 'react';
import * as RadixTabs from '@radix-ui/react-tabs';
import type { ActionNode, ContainerNode, LeafNode, StringNode, ParamNode } from '../types';
import { ParamContainer } from './ParamContainer';
import { ParamLeaf } from './ParamLeaf';
import { ActionButton } from './ActionButton';
import { StringLeaf } from './StringLeaf';
import { NodeIcon } from './NodeIcon';
import { useSSE } from '../useSSE';
import type { ParamState } from '../useSSE';

interface TabsContainerProps {
  node: ContainerNode;
  path: string;
}

function renderChild(child: ParamNode, basePath: string, sseState: Map<string, ParamState>) {
  const childPath = basePath ? `${basePath}/${child.name}` : child.name;
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

export function TabsContainer({ node, path }: TabsContainerProps) {
  const visibleChildren = node.children.filter(c => c.uiHints?.['hidden'] !== 'true');
  const tabs = visibleChildren.filter(
    c => c.type === 'CONTAINER' && c.uiHints?.['control-type'] !== 'EXPANDER',
  ) as ContainerNode[];
  const expanders = visibleChildren.filter(
    c => c.type === 'CONTAINER' && c.uiHints?.['control-type'] === 'EXPANDER',
  ) as ContainerNode[];

  const [activeTab, setActiveTab] = useState(tabs[0]?.name ?? '');

  const activeTabPath = activeTab ? (path ? `${path}/${activeTab}` : activeTab) : '';

  // Subscribe to the active tab's full subtree so all descendant changes are caught.
  const sseState = useSSE(activeTabPath ? [activeTabPath] : []);

  return (
    <div className="flex flex-col gap-2">
      <RadixTabs.Root value={activeTab} onValueChange={setActiveTab} className="flex flex-col gap-1">
        <RadixTabs.List className="flex gap-1 border-b border-neutral-700 pb-1">
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
          const tabChildren = tab.children.filter(c => c.uiHints?.['hidden'] !== 'true');
          return (
            <RadixTabs.Content key={tab.name} value={tab.name} className="space-y-1 pt-1">
              {tabChildren.map(child => renderChild(child, tabPath, sseState))}
            </RadixTabs.Content>
          );
        })}
      </RadixTabs.Root>

      {expanders.map(exp => {
        const expPath = path ? `${path}/${exp.name}` : exp.name;
        return <ParamContainer key={exp.name} node={exp} path={expPath} />;
      })}
    </div>
  );
}
