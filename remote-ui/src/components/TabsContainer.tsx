import { useEffect, useState } from 'react';
import * as RadixTabs from '@radix-ui/react-tabs';
import { X } from 'lucide-react';
import type { ActionNode, ContainerNode, LeafNode, StringNode, ParamNode } from '../types';
import { ParamContainer } from './ParamContainer';
import { ParamLeaf } from './ParamLeaf';
import { ActionButton } from './ActionButton';
import { StringLeaf } from './StringLeaf';
import { NodeIcon } from './NodeIcon';
import type { ToolbarEntry } from './ActionToolbar';
import { useSSEState } from '../SSEContext';
import type { ParamState } from '../SSEContext';
import { useSettings } from '../SettingsContext';
import { useToolbar } from '../ToolbarContext';
import { isRenderable, flattenSoleContainer } from '../nodeUtils';

/** Boolean toggles pulled out of the General expander into the Settings panel instead. */
const SETTINGS_CONTROL_NAMES = new Set(['Fullscreen', 'Notifications']);
/** Tabs pulled out of the main tab row into the Settings panel instead. */
const SETTINGS_TAB_NAMES = new Set(['Audio']);

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
  const allTabs = visibleChildren.filter(
    c => c.type === 'CONTAINER' && c.uiHints?.['control-type'] !== 'EXPANDER',
  ) as ContainerNode[];
  const tabs = allTabs.filter(t => !SETTINGS_TAB_NAMES.has(t.name));
  const settingsTabs = allTabs.filter(t => SETTINGS_TAB_NAMES.has(t.name));
  const expanders = visibleChildren.filter(
    c => c.type === 'CONTAINER' && c.uiHints?.['control-type'] === 'EXPANDER',
  ) as ContainerNode[];

  const [activeTab, setActiveTab] = useState(tabs[0]?.name ?? '');

  // Live state comes from the single app-wide SSE connection (see SSEContext).
  const sseState = useSSEState();
  const { open: settingsOpen, closeSettings } = useSettings();

  // Expander containers (e.g. "General") hold a mix of top-level actions, boolean settings
  // toggles, and sub-containers. Actions are pulled out and rendered as an icon toolbar above
  // the tabs; settings toggles are pulled into the Settings panel; anything else (e.g. the
  // "Remote" sub-container) stays as a collapsible section below the tabs.
  const expanderData = expanders.map(exp => {
    const expPath = path ? `${path}/${exp.name}` : exp.name;
    const toolbarActions: ToolbarEntry[] = exp.children
      .filter((c): c is ActionNode => c.type === 'ACTION' && c.uiHints?.['hidden'] !== 'true')
      .map(c => ({ path: `${expPath}/${c.name}`, node: c }));
    const settingsLeaves: { path: string; node: LeafNode }[] = exp.children
      .filter((c): c is LeafNode =>
        c.type !== 'ACTION' && c.type !== 'CONTAINER' && SETTINGS_CONTROL_NAMES.has(c.name) && isRenderable(c))
      .map(c => ({ path: `${expPath}/${c.name}`, node: c }));
    const settingsNames = new Set(settingsLeaves.map(s => s.node.name));
    const remainingChildren = exp.children.filter(c => c.type !== 'ACTION' && !settingsNames.has(c.name));
    return { expPath, toolbarActions, settingsLeaves, remainingNode: { ...exp, children: remainingChildren } };
  });
  const toolbarActions = expanderData.flatMap(e => e.toolbarActions);
  const settingsLeaves = expanderData.flatMap(e => e.settingsLeaves);
  const remainingExpanders = expanderData.filter(e => isRenderable(e.remainingNode));

  // Published to the header (see App.tsx) via ToolbarContext — same bridging pattern as
  // SettingsContext, since the header lives outside this component's subtree.
  const { setActions: setHeaderToolbarActions } = useToolbar();
  const toolbarActionsKey = toolbarActions.map(a => a.path).join('|');
  useEffect(() => {
    setHeaderToolbarActions(toolbarActions);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [toolbarActionsKey]);

  const renderTabContent = (tab: ContainerNode) => {
    const tabPath = path ? `${path}/${tab.name}` : tab.name;
    const visibleTabChildren = tab.children.filter(isRenderable);
    const { children: tabChildren, path: contentPath } = flattenSoleContainer(visibleTabChildren, tabPath);
    return tabChildren.map(child => renderChild(child, contentPath, sseState, tab.children));
  };

  return (
    <div className="flex flex-col gap-2">
      <RadixTabs.Root value={activeTab} onValueChange={setActiveTab} className="flex flex-col gap-1">
        <RadixTabs.List
          className="flex flex-nowrap gap-1 overflow-x-auto border-b border-neutral-700 pb-2
                     [scrollbar-width:none] [-ms-overflow-style:none]
                     [&::-webkit-scrollbar]:hidden"
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

        {tabs.map(tab => (
          <RadixTabs.Content key={tab.name} value={tab.name} className="space-y-1 pt-1">
            {renderTabContent(tab)}
          </RadixTabs.Content>
        ))}
      </RadixTabs.Root>

      {remainingExpanders.map(({ expPath, remainingNode }) => (
        <ParamContainer key={remainingNode.name} node={remainingNode} path={expPath} />
      ))}

      {settingsOpen && (
        <div
          className="fixed inset-0 z-50 bg-black/80 flex items-end sm:items-center justify-center"
          onClick={closeSettings}
        >
          <div
            className="w-full sm:max-w-md sm:mx-6 bg-neutral-900 border border-neutral-700 rounded-t-2xl sm:rounded-2xl max-h-[85vh] overflow-y-auto"
            onClick={e => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-4 py-3 border-b border-neutral-800 sticky top-0 bg-neutral-900">
              <h2 className="text-base font-semibold text-neutral-200">Settings</h2>
              <button
                onClick={closeSettings}
                aria-label="Close settings"
                className="p-1 rounded text-neutral-400 hover:text-neutral-200 hover:bg-neutral-800 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-3 space-y-3">
              {settingsTabs.map(tab => (
                <div key={tab.name} className="space-y-1">
                  {renderTabContent(tab)}
                </div>
              ))}
              {settingsLeaves.length > 0 && (
                <div className="space-y-1 pt-2 border-t border-neutral-800">
                  {settingsLeaves.map(({ path: leafPath, node: leafNode }) => (
                    <ParamLeaf
                      key={leafPath}
                      path={leafPath}
                      node={leafNode}
                      liveValue={sseState.get(leafPath)?.value}
                      liveControlled={sseState.get(leafPath)?.controlled}
                    />
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
