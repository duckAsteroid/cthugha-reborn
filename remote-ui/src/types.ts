export type NodeType = 'CONTAINER' | 'DOUBLE' | 'INTEGER' | 'LONG' | 'BOOLEAN' | 'ENUM' | 'ACTION' | 'STRING';
export type UiHint = 'SLIDER' | 'KNOB' | 'CAROUSEL';

export interface EnumOption {
  label: string;
  preview?: string;
}

export interface ContainerNode {
  name: string;
  type: 'CONTAINER';
  children: ParamNode[];
}

export interface LeafNode {
  name: string;
  type: Exclude<NodeType, 'CONTAINER' | 'ACTION'>;
  value: number;
  min: number;
  max: number;
  controlled: boolean;
  uiHint: UiHint;
  options?: EnumOption[];
}

export interface ActionNode {
  name: string;
  type: 'ACTION';
}

export interface StringNode {
  name: string;
  type: 'STRING';
  value: string;
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
