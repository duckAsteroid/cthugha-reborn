/**
 * Cross-component "select this tag" signal: lets a tag chip rendered somewhere else in the tree
 * (e.g. the "Current Video" preview row in ParamContainer, see resolveCurrentPreview) drive the
 * tag filter of a specific GridControl instance, identified by its leaf's full param path, without
 * those two components sharing a parent or any prop-drilled state. A plain window event rather
 * than React context because the two ends are typically siblings mounted from different call
 * sites (ParamContainer's recursive loop vs. ParamLeaf), so there is no single ancestor to host
 * shared state without threading it through everything in between.
 */

export const SELECT_TAG_EVENT = 'cthugha:select-tag';

export interface SelectTagDetail {
  /** Full param path of the target ENUM leaf, e.g. "Videos/Video". */
  path: string;
  tag: string;
}

export function dispatchSelectTag(path: string, tag: string): void {
  window.dispatchEvent(new CustomEvent<SelectTagDetail>(SELECT_TAG_EVENT, { detail: { path, tag } }));
}
