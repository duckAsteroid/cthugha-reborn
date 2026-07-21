import type { ContainerNode, ParamNode } from './types';

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
