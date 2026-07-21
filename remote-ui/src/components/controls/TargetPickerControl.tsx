import { useEffect, useState } from 'react';
import * as Collapsible from '@radix-ui/react-collapsible';
import { ChevronDown, ChevronRight, Loader2, Search, SlidersHorizontal, Zap } from 'lucide-react';
import type { EnumOption, NodeType, ParamNode, LeafNode, StringNode } from '../../types';
import { getParams, patchStringParam } from '../../api';
import { NodeIcon } from '../NodeIcon';
import { ToggleControl } from './ToggleControl';
import { SliderControl } from './SliderControl';
import { EnumControl } from './EnumControl';

interface TargetPickerControlProps {
  /** Path of the StringParameter holding the chosen target's path (e.g. an ActionTrigger's `target`). */
  targetPath: string;
  /** Currently stored target path. */
  targetValue: string;
  /** Path of the paired StringParameter holding the raw text to apply when the target is a settable leaf. */
  valuePath: string;
  /** Currently stored raw value text. */
  rawValue: string;
  disabled?: boolean;
}

/** A candidate the picker can select: either an Action to execute, or a settable leaf to set. */
interface TargetOption {
  path: string;
  label: string;
  kind: 'action' | 'value';
  nodeType: NodeType;
  min?: number;
  max?: number;
  currentValue?: number | string;
  options?: EnumOption[];
  scale?: string;
}

interface TargetLeafItem {
  kind: 'leaf';
  name: string;
  path: string;
  icon?: string;
  option: TargetOption;
}

interface TargetGroupItem {
  kind: 'group';
  name: string;
  path: string;
  icon?: string;
  children: TargetTreeItem[];
}

type TargetTreeItem = TargetLeafItem | TargetGroupItem;

function isPickable(node: ParamNode): boolean {
  return node.type === 'ACTION' || node.type !== 'CONTAINER';
}

function toOption(node: ParamNode, path: string): TargetOption {
  if (node.type === 'ACTION') {
    return { path, label: node.name, kind: 'action', nodeType: 'ACTION' };
  }
  if (node.type === 'STRING') {
    return { path, label: node.name, kind: 'value', nodeType: 'STRING', currentValue: (node as StringNode).value };
  }
  const leaf = node as LeafNode;
  return {
    path,
    label: node.name,
    kind: 'value',
    nodeType: leaf.type,
    min: leaf.min,
    max: leaf.max,
    currentValue: leaf.value,
    options: leaf.options,
    scale: leaf.uiHints?.['scale'],
  };
}

/** Flat {path, label, ...} list across the whole tree, used in search mode. Triggers can't target other triggers. */
function collectTargets(node: ParamNode, prefix: string, out: TargetOption[]): void {
  if (prefix === 'Triggers' || prefix.startsWith('Triggers/')) return;
  if (node.uiHints?.['hidden'] === 'true') return;
  if (node.type === 'CONTAINER') {
    for (const child of node.children) {
      collectTargets(child, prefix ? `${prefix}/${child.name}` : child.name, out);
    }
    return;
  }
  if (!isPickable(node)) return;
  const option = toOption(node, prefix);
  out.push({ ...option, label: prefix.replace(/\//g, ' › ') });
}

/**
 * Prunes the param tree down to containers that have at least one pickable descendant, and
 * collapses chains of single-child groups, mirroring nodeUtils.flattenSoleContainer's spirit
 * for this picker-only view.
 */
function buildTargetTree(node: ParamNode, prefix: string): TargetTreeItem | null {
  if (prefix === 'Triggers' || prefix.startsWith('Triggers/')) return null;
  if (node.uiHints?.['hidden'] === 'true') return null;

  if (node.type !== 'CONTAINER') {
    if (!isPickable(node)) return null;
    return { kind: 'leaf', name: node.name, path: prefix, icon: node.uiHints?.['icon'], option: toOption(node, prefix) };
  }

  const children: TargetTreeItem[] = [];
  for (const child of node.children) {
    const built = buildTargetTree(child, prefix ? `${prefix}/${child.name}` : child.name);
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
function findAncestorGroupPaths(items: TargetTreeItem[], target: string, trail: string[]): string[] | null {
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

function TargetIcon({ icon, kind }: { icon?: string; kind: 'action' | 'value' }) {
  if (icon) return <NodeIcon name={icon} className="w-3.5 h-3.5 shrink-0" />;
  return kind === 'action' ? (
    <Zap className="w-3.5 h-3.5 shrink-0 text-neutral-500" />
  ) : (
    <SlidersHorizontal className="w-3.5 h-3.5 shrink-0 text-neutral-500" />
  );
}

function TargetLeafButton({
  leaf,
  selected,
  onSelect,
}: {
  leaf: TargetLeafItem;
  selected: boolean;
  onSelect: (option: TargetOption) => void;
}) {
  return (
    <button
      onClick={() => onSelect(leaf.option)}
      className={`w-full shrink-0 flex items-center gap-1.5 text-left px-2 py-1.5 rounded-lg border text-sm truncate transition-colors ${
        selected
          ? 'border-indigo-400 bg-indigo-950/40 text-indigo-300'
          : 'border-transparent bg-neutral-900/50 text-neutral-200 hover:bg-neutral-800'
      }`}
    >
      <TargetIcon icon={leaf.icon} kind={leaf.option.kind} />
      <span className="truncate">{leaf.name}</span>
    </button>
  );
}

function TargetGroupView({
  group,
  selected,
  openGroups,
  toggleGroup,
  onSelect,
}: {
  group: TargetGroupItem;
  selected: string;
  openGroups: Set<string>;
  toggleGroup: (path: string) => void;
  onSelect: (option: TargetOption) => void;
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
            <TargetGroupView
              key={child.path}
              group={child}
              selected={selected}
              openGroups={openGroups}
              toggleGroup={toggleGroup}
              onSelect={onSelect}
            />
          ) : (
            <TargetLeafButton
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

/** Renders a control matching the picked target's own type/bounds, so setting a value never requires guessing at a raw number. */
function ValueEditor({
  option,
  rawValue,
  onCommit,
}: {
  option: TargetOption;
  rawValue: string;
  onCommit: (text: string) => void;
}) {
  if (option.nodeType === 'BOOLEAN') {
    const checked = Number(rawValue || '0') !== 0;
    return <ToggleControl value={checked} disabled={false} onChange={(v) => onCommit(v ? '1' : '0')} />;
  }

  if (option.nodeType === 'ENUM') {
    const parsed = Math.round(Number(rawValue));
    const idx = Number.isFinite(parsed) ? parsed : 0;
    return (
      <EnumControl
        value={idx}
        options={option.options ?? []}
        disabled={false}
        onChange={(v) => onCommit(String(v))}
      />
    );
  }

  if (option.nodeType === 'DOUBLE' || option.nodeType === 'INTEGER' || option.nodeType === 'LONG') {
    const min = option.min ?? 0;
    const max = option.max ?? 1;
    const parsed = Number(rawValue);
    const value = Number.isFinite(parsed) ? parsed : min;
    return (
      <SliderControl
        value={value}
        min={min}
        max={max}
        disabled={false}
        onChange={(v) => onCommit(String(v))}
        scale={option.scale}
        integer={option.nodeType === 'INTEGER' || option.nodeType === 'LONG'}
      />
    );
  }

  // STRING
  return (
    <input
      type="text"
      defaultValue={rawValue}
      onBlur={(e) => onCommit(e.target.value)}
      onKeyDown={(e) => {
        if (e.key === 'Enter') onCommit((e.target as HTMLInputElement).value);
      }}
      className="w-full bg-neutral-800 border border-neutral-600 rounded px-2 py-1 text-sm text-neutral-200 focus:outline-none focus:ring-2 focus:ring-indigo-500"
    />
  );
}

/**
 * Picker over every Action and settable value leaf currently in the param tree — used by fields
 * (like an ActionTrigger's `target`) that reference an existing node by path rather than
 * hand-typing it. When the picked target is a settable leaf, also renders a control matching its
 * own type/min/max/options (toggle, slider, dropdown, or text) for the paired `value` field named
 * by the `paired-value-field` UI hint; when it's an Action, that control is omitted entirely since
 * there's nothing to set.
 *
 * Browsing (no search text) shows a nested collapsible tree mirroring the real param-tree
 * hierarchy (chains of single-child groups collapsed for brevity). Typing a search switches to a
 * flat, breadcrumb-labelled list across the whole tree.
 */
export function TargetPickerControl({ targetPath, targetValue, valuePath, rawValue, disabled }: TargetPickerControlProps) {
  const [flat, setFlat] = useState<TargetOption[] | null>(null);
  const [tree, setTree] = useState<TargetTreeItem[] | null>(null);
  const [search, setSearch] = useState('');
  const [committing, setCommitting] = useState(false);
  const [selected, setSelected] = useState(targetValue);
  const [localValueText, setLocalValueText] = useState(rawValue);
  const [openGroups, setOpenGroups] = useState<Set<string>>(new Set());

  useEffect(() => setLocalValueText(rawValue), [rawValue]);

  useEffect(() => {
    let cancelled = false;
    getParams().then((root) => {
      if (cancelled) return;
      const list: TargetOption[] = [];
      collectTargets(root, '', list);
      list.sort((a, b) => a.label.localeCompare(b.label));
      setFlat(list);

      const forest = (root.type === 'CONTAINER' ? root.children : [])
        .map((child) => buildTargetTree(child, child.name))
        .filter((n): n is TargetTreeItem => n !== null);
      forest.sort((a, b) => a.name.localeCompare(b.name));
      setTree(forest);

      if (targetValue) {
        const ancestors = findAncestorGroupPaths(forest, targetValue, []);
        if (ancestors) setOpenGroups(new Set(ancestors));
      }
    });
    return () => {
      cancelled = true;
    };
  }, [targetValue]);

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

  const chooseTarget = async (option: TargetOption) => {
    setCommitting(true);
    try {
      await patchStringParam(targetPath, option.path);
      setSelected(option.path);
      const prefill = option.kind === 'value' && option.currentValue !== undefined ? String(option.currentValue) : '';
      await patchStringParam(valuePath, prefill);
      setLocalValueText(prefill);
    } catch {
      // error handled by api.ts
    } finally {
      setCommitting(false);
    }
  };

  const commitValue = async (text: string) => {
    setLocalValueText(text);
    try {
      await patchStringParam(valuePath, text);
    } catch {
      // error handled by api.ts
    }
  };

  if (flat === null || tree === null) {
    return (
      <div className="flex items-center gap-1.5 px-2 py-1.5 text-xs text-neutral-500">
        <Loader2 className="w-3.5 h-3.5 animate-spin" /> Loading targets…
      </div>
    );
  }

  const selectedOption = flat.find((opt) => opt.path === selected) ?? null;
  const searching = search.trim().length > 0;
  const filtered = searching
    ? flat.filter((opt) => opt.label.toLowerCase().includes(search.toLowerCase()))
    : [];

  return (
    <div className={`flex flex-col gap-2 ${disabled || committing ? 'opacity-40 pointer-events-none' : ''}`}>
      <div className="flex items-center gap-1.5 px-2 py-1 bg-neutral-800 border border-neutral-600 rounded">
        <Search className="w-3.5 h-3.5 text-neutral-500 shrink-0" />
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search actions & parameters…"
          className="w-full bg-transparent text-sm text-neutral-200 placeholder-neutral-500 focus:outline-none"
        />
      </div>

      <div className="max-h-64 overflow-y-auto flex flex-col gap-1">
        {searching ? (
          <>
            {filtered.map((opt) => (
              <button
                key={opt.path}
                onClick={() => chooseTarget(opt)}
                className={`shrink-0 flex items-center gap-1.5 text-left px-2 py-1.5 rounded-lg border text-sm truncate transition-colors ${
                  opt.path === selected
                    ? 'border-indigo-400 bg-indigo-950/40 text-indigo-300'
                    : 'border-transparent bg-neutral-900/50 text-neutral-200 hover:bg-neutral-800'
                }`}
              >
                <TargetIcon kind={opt.kind} />
                <span className="truncate">{opt.label}</span>
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
                <TargetGroupView
                  key={item.path}
                  group={item}
                  selected={selected}
                  openGroups={openGroups}
                  toggleGroup={toggleGroup}
                  onSelect={chooseTarget}
                />
              ) : (
                <TargetLeafButton
                  key={item.path}
                  leaf={item}
                  selected={selected === item.path}
                  onSelect={chooseTarget}
                />
              ),
            )}
            {tree.length === 0 && (
              <p className="text-xs text-neutral-500 px-2 py-1.5">No actions or parameters found</p>
            )}
          </>
        )}
      </div>

      {selected && (
        <p className="text-xs text-neutral-500 px-0.5 truncate">
          Selected: <code className="text-indigo-300">{selected}</code>
        </p>
      )}

      {selectedOption && selectedOption.kind === 'value' && (
        <div className="flex flex-col gap-1.5 pl-2 border-l-2 border-indigo-500/40">
          <span className="text-xs text-neutral-400">Value to set</span>
          <ValueEditor option={selectedOption} rawValue={localValueText} onCommit={commitValue} />
        </div>
      )}
    </div>
  );
}
