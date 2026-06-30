export type NodeType = 'CONTAINER' | 'DOUBLE' | 'INTEGER' | 'LONG' | 'BOOLEAN' | 'ENUM';
export type UiHint = 'SLIDER' | 'KNOB';

export interface ContainerNode {
  name: string;
  type: 'CONTAINER';
  children: ParamNode[];
}

export interface LeafNode {
  name: string;
  type: Exclude<NodeType, 'CONTAINER'>;
  value: number;
  min: number;
  max: number;
  controlled: boolean;
  uiHint: UiHint;
}

export type ParamNode = ContainerNode | LeafNode;

export interface SSEParamChangedEvent {
  path: string;
  value: number;
  controlled: boolean;
}

export interface ServerInfo {
  version: string;
}
