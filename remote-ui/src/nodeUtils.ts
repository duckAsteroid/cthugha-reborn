import type { ContainerNode, EnumOption, ParamNode } from './types';
import type { ParamState } from './SSEContext';

/**
 * Whether a node should be rendered at all. A hidden node is never renderable;
 * a container is only renderable if at least one of its children is (checked
 * recursively, so a container whose only content is an empty sub-container is
 * also treated as empty).
 */
export function isRenderable(node: ParamNode): boolean {
  if (node.uiHints?.['hidden'] === 'true') return false;
  if (node.type !== 'CONTAINER') return true;
  return node.children.some(isRenderable);
}

/**
 * Collapses a chain of plain containers that each hold nothing but a single
 * child container, so a tab whose only content is one (or a nested run of
 * one) redundant wrapper container renders that wrapper's children directly
 * instead of showing an extra collapsible header. Containers with a special
 * control-type (e.g. TABS) are left alone since they render differently.
 */
export function flattenSoleContainer(
  children: ParamNode[],
  path: string,
): { children: ParamNode[]; path: string } {
  if (children.length === 1 && children[0].type === 'CONTAINER' && !children[0].uiHints?.['control-type']) {
    const only = children[0] as ContainerNode;
    const onlyPath = path ? `${path}/${only.name}` : only.name;
    const onlyVisible = only.children.filter(isRenderable);
    return flattenSoleContainer(onlyVisible, onlyPath);
  }
  return { children, path };
}

export interface CurrentPreview {
  /** Name of the sibling ENUM leaf this mirrors (e.g. {@code "Video"}) — used as a row label. */
  siblingName: string;
  /** Full param path of the sibling ENUM leaf, e.g. {@code "Videos/Video"} — see tagSelection.ts. */
  siblingPath: string;
  /** The sibling's currently-selected option (label/preview/tags), or undefined mid-load. */
  option: EnumOption | undefined;
}

/**
 * Resolves a {@code preview-of} uiHint (see UiHint.java) into the sibling's currently-selected
 * option: finds the named sibling ENUM leaf among {@code siblings}, then reads that leaf's
 * current option (label/preview/tags — the same data already shown in its GRID/CAROUSEL
 * control) using its live SSE value if one has arrived, falling back to the leaf's
 * last-fetched value otherwise.
 */
export function resolveCurrentPreview(
  child: ParamNode,
  siblings: ParamNode[],
  parentPath: string,
  sseState: Map<string, ParamState>,
): CurrentPreview | undefined {
  const siblingName = child.type === 'CONTAINER' ? child.uiHints?.['preview-of'] : undefined;
  if (!siblingName) return undefined;
  const sibling = siblings.find((s) => s.name === siblingName);
  if (!sibling || sibling.type !== 'ENUM') return undefined;
  const siblingPath = parentPath ? `${parentPath}/${sibling.name}` : sibling.name;
  const liveValue = sseState.get(siblingPath)?.value;
  const index = Math.round(liveValue ?? sibling.value);
  return { siblingName, siblingPath, option: sibling.options?.[index] };
}
