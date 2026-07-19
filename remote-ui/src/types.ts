export type NodeType = 'CONTAINER' | 'DOUBLE' | 'INTEGER' | 'LONG' | 'BOOLEAN' | 'ENUM' | 'ACTION' | 'STRING';

export interface EnumOption {
  label: string;
  preview?: string;
  group?: string;
}

export interface ContainerNode {
  name: string;
  type: 'CONTAINER';
  children: ParamNode[];
  uiHints?: Record<string, string>;
  description?: string;
}

export interface AnimationInfo {
  script: string;
  enabled: boolean;
  compileError?: string;
}

export interface LeafNode {
  name: string;
  type: Exclude<NodeType, 'CONTAINER' | 'ACTION'>;
  value: number;
  min: number;
  max: number;
  controlled: boolean;
  uiHints?: Record<string, string>;
  options?: EnumOption[];
  description?: string;
  animation?: AnimationInfo;
  /** false when the server excludes this param from animation (e.g. a disruptive "picker" enum). Absent means true. */
  animatable?: boolean;
}

export interface ActionNode {
  name: string;
  type: 'ACTION';
  uiHints?: Record<string, string>;
  description?: string;
}

export interface StringNode {
  name: string;
  type: 'STRING';
  value: string;
  uiHints?: Record<string, string>;
  description?: string;
}

export interface StringPatchResult extends StringNode {
  compileError?: string;
}

export type ParamNode = ContainerNode | LeafNode | ActionNode | StringNode;

export interface SSEParamChangedEvent {
  path: string;
  value: number;
  controlled: boolean;
}

export interface ServerInfo {
  version: string;
}
