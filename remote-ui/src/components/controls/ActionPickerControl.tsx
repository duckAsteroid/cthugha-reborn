import { useEffect, useState } from 'react';
import * as Collapsible from '@radix-ui/react-collapsible';
import { ChevronDown, ChevronRight, Loader2, Search } from 'lucide-react';
import type { ParamNode } from '../../types';
import { getParams, patchStringParam } from '../../api';
import { NodeIcon } from '../NodeIcon';

interface ActionPickerControlProps {
  path: string;
  value: string;
  disabled?: boolean;
}

interface ActionOption {
  path: string;
  label: string;
}

interface ActionLeafNode {
  kind: 'leaf';
  name: string;
  path: string;
  icon?: string;
}

interface ActionGroupNode {
  kind: 'group';
  name: string;
  path: string;
  icon?: string;
  children: ActionTreeItem[];
}

type ActionTreeItem = ActionLeafNode | ActionGroupNode;

/** Flat {path, label} list across the whole tree, used in search mode. */
function collectActions(node: ParamNode, prefix: string, out: ActionOption[]): void {
  if (node.type === 'ACTION') {
    if (node.uiHints?.['hidden'] === 'true') return;
    out.push({ path: prefix, label: prefix.replace(/\//g, ' › ') });
    return;
  }
  if (node.type === 'CONTAINER') {
    for (const child of node.children) {
      collectActions(child, prefix ? `${prefix}/${child.name}` : child.name, out);
    }
  }
}

/**
 * Prunes the param tree down to containers that have at least one Action descendant, and
 * collapses chains of single-child groups (e.g. "Tab" > "Translate Source" with nothing else
 * in "Tab") into one combined label, mirroring nodeUtils.flattenSoleContainer's spirit for
 * this action-only view.
 */
function buildActionTree(node: ParamNode, prefix: string): ActionTreeItem | null {
  if (node.type === 'ACTION') {
    if (node.uiHints?.['hidden'] === 'true') return null;
    return { kind: 'leaf', name: node.name, path: prefix, icon: node.uiHints?.['icon'] };
  }
  if (node.type !== 'CONTAINER') return null;

  const children: ActionTreeItem[] = [];
  for (const child of node.children) {
    const built = buildActionTree(child, prefix ? `${prefix}/${child.name}` : child.name);
    if (built) children.push(built);
  }
  if (children.length === 0) return null;
  children.sort((a, b) => a.name.localeCompare(b.name));

  if (children.length === 1 && children[0].kind === 'group') {
    const only = children[0];
    return {
      kind: 'group',
      name: `${node.name} › ${only.name}`,
      path: only.path,
      icon: node.uiHints?.['icon'] ?? only.icon,
      children: only.children,
    };
  }
  return { kind: 'group', name: node.name, path: prefix, icon: node.uiHints?.['icon'], children };
}

/** Returns the group paths from root to the leaf at `target`, or null if not found. */
function findAncestorGroupPaths(items: ActionTreeItem[], target: string, trail: string[]): string[] | null {
  for (const item of items) {
    if (item.kind === 'leaf') {
      if (item.path === target) return trail;
    } else {
      const found = findAncestorGroupPaths(item.children, target, [...trail, item.path]);
      if (found) return found;
    }
  }
  return null;
}

function ActionLeafButton({
  leaf,
  selected,
  onSelect,
}: {
  leaf: ActionLeafNode;
  selected: boolean;
  onSelect: (path: string) => void;
}) {
  return (
    <button
      onClick={() => onSelect(leaf.path)}
      className={`w-full shrink-0 flex items-center gap-1.5 text-left px-2 py-1.5 rounded-lg border text-sm truncate transition-colors ${
        selected
          ? 'border-indigo-400 bg-indigo-950/40 text-indigo-300'
          : 'border-transparent bg-neutral-900/50 text-neutral-200 hover:bg-neutral-800'
      }`}
    >
      {leaf.icon && <NodeIcon name={leaf.icon} className="w-3.5 h-3.5 shrink-0" />}
      <span className="truncate">{leaf.name}</span>
    </button>
  );
}

function ActionGroupView({
  group,
  selected,
  openGroups,
  toggleGroup,
  onSelect,
}: {
  group: ActionGroupNode;
  selected: string;
  openGroups: Set<string>;
  toggleGroup: (path: string) => void;
  onSelect: (path: string) => void;
}) {
  const open = openGroups.has(group.path);
  return (
    <Collapsible.Root open={open} onOpenChange={() => toggleGroup(group.path)}>
      <Collapsible.Trigger className="flex items-center gap-1.5 w-full px-2 py-1.5 text-left rounded hover:bg-neutral-800 transition-colors">
        {open ? (
          <ChevronDown className="w-3.5 h-3.5 text-neutral-500 shrink-0" />
        ) : (
          <ChevronRight className="w-3.5 h-3.5 text-neutral-500 shrink-0" />
        )}
        {group.icon && <NodeIcon name={group.icon} className="w-3.5 h-3.5 text-neutral-400 shrink-0" />}
        <span className="text-xs font-semibold text-neutral-300 uppercase tracking-wide truncate">
          {group.name}
        </span>
      </Collapsible.Trigger>
      <Collapsible.Content className="pl-3 mt-0.5 space-y-0.5">
        {group.children.map((child) =>
          child.kind === 'group' ? (
            <ActionGroupView
              key={child.path}
              group={child}
              selected={selected}
              openGroups={openGroups}
              toggleGroup={toggleGroup}
              onSelect={onSelect}
            />
          ) : (
            <ActionLeafButton
              key={child.path}
              leaf={child}
              selected={selected === child.path}
              onSelect={onSelect}
            />
          ),
        )}
      </Collapsible.Content>
    </Collapsible.Root>
  );
}

/**
 * Picker over every Action node currently in the param tree. Used for fields (like an
 * ActionTrigger's `action`) that reference an existing action by path — selection is always
 * made from a live list rather than hand-typed, so the stored path is valid at creation time.
 *
 * Browsing (no search text) shows a nested collapsible tree mirroring the real param-tree
 * hierarchy (chains of single-child groups collapsed for brevity). Typing a search switches to
 * a flat, breadcrumb-labelled list across the whole tree, so lookups don't require drilling
 * down manually.
 */
export function ActionPickerControl({ path, value, disabled }: ActionPickerControlProps) {
  const [options, setOptions] = useState<ActionOption[] | null>(null);
  const [tree, setTree] = useState<ActionTreeItem[] | null>(null);
  const [search, setSearch] = useState('');
  const [committing, setCommitting] = useState(false);
  const [selected, setSelected] = useState(value);
  const [openGroups, setOpenGroups] = useState<Set<string>>(new Set());

  useEffect(() => {
    let cancelled = false;
    getParams().then((root) => {
      if (cancelled) return;
      const flat: ActionOption[] = [];
      collectActions(root, '', flat);
      flat.sort((a, b) => a.label.localeCompare(b.label));
      setOptions(flat);

      const forest = (root.type === 'CONTAINER' ? root.children : [])
        .map((child) => buildActionTree(child, child.name))
        .filter((n): n is ActionTreeItem => n !== null);
      forest.sort((a, b) => a.name.localeCompare(b.name));
      setTree(forest);

      if (value) {
        const ancestors = findAncestorGroupPaths(forest, value, []);
        if (ancestors) setOpenGroups(new Set(ancestors));
      }
    });
    return () => {
      cancelled = true;
    };
  }, [value]);

  const toggleGroup = (groupPath: string) => {
    setOpenGroups((prev) => {
      const next = new Set(prev);
      if (next.has(groupPath)) {
        next.delete(groupPath);
      } else {
        next.add(groupPath);
      }
      return next;
    });
  };

  const commit = async (chosenPath: string) => {
    setCommitting(true);
    try {
      await patchStringParam(path, chosenPath);
      setSelected(chosenPath);
    } catch {
      // error handled by api.ts
    } finally {
      setCommitting(false);
    }
  };

  if (options === null || tree === null) {
    return (
      <div className="flex items-center gap-1.5 px-2 py-1.5 text-xs text-neutral-500">
        <Loader2 className="w-3.5 h-3.5 animate-spin" /> Loading actions…
      </div>
    );
  }

  const searching = search.trim().length > 0;
  const filtered = searching
    ? options.filter((opt) => opt.label.toLowerCase().includes(search.toLowerCase()))
    : [];

  return (
    <div
      className={`flex flex-col gap-1.5 ${disabled || committing ? 'opacity-40 pointer-events-none' : ''}`}
    >
      <div className="flex items-center gap-1.5 px-2 py-1 bg-neutral-800 border border-neutral-600 rounded">
        <Search className="w-3.5 h-3.5 text-neutral-500 shrink-0" />
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search actions…"
          className="w-full bg-transparent text-sm text-neutral-200 placeholder-neutral-500 focus:outline-none"
        />
      </div>

      <div className="max-h-64 overflow-y-auto flex flex-col gap-1">
        {searching ? (
          <>
            {filtered.map((opt) => (
              <button
                key={opt.path}
                onClick={() => commit(opt.path)}
                className={`shrink-0 text-left px-2 py-1.5 rounded-lg border text-sm truncate transition-colors ${
                  opt.path === selected
                    ? 'border-indigo-400 bg-indigo-950/40 text-indigo-300'
                    : 'border-transparent bg-neutral-900/50 text-neutral-200 hover:bg-neutral-800'
                }`}
              >
                {opt.label}
              </button>
            ))}
            {filtered.length === 0 && (
              <p className="text-xs text-neutral-500 px-2 py-1.5">No matches</p>
            )}
          </>
        ) : (
          <>
            {tree.map((item) =>
              item.kind === 'group' ? (
                <ActionGroupView
                  key={item.path}
                  group={item}
                  selected={selected}
                  openGroups={openGroups}
                  toggleGroup={toggleGroup}
                  onSelect={commit}
                />
              ) : (
                <ActionLeafButton
                  key={item.path}
                  leaf={item}
                  selected={selected === item.path}
                  onSelect={commit}
                />
              ),
            )}
            {tree.length === 0 && (
              <p className="text-xs text-neutral-500 px-2 py-1.5">No actions found</p>
            )}
          </>
        )}
      </div>

      {selected && (
        <p className="text-xs text-neutral-500 px-0.5 truncate">
          Selected: <code className="text-indigo-300">{selected}</code>
        </p>
      )}
    </div>
  );
}
