import { useState } from 'react';
import type { LeafNode } from '../types';
import { createAnimation, createTrigger } from '../api';

export type DraftKind = 'animation' | 'trigger';

interface Options {
  path: string;
  /** Whether this node can host an Animation as well as Triggers (Actions get Triggers only). */
  forValue?: boolean;
  hasAnimation?: boolean;
  /** false when the server excludes this param from animation (e.g. a disruptive "picker" enum). */
  animatable?: boolean;
  valueNode?: LeafNode;
}

const DEFAULT_SCRIPT = 'sine(0.05)';
const DEFAULT_CONDITION = 'bass() > 0.7';

/**
 * Drives the single "+" affordance shared by values and actions: for a value that's still free
 * to animate, it opens a two-way choice (Animation vs Trigger); once an animation already exists,
 * or for an Action (which has no numeric state to animate), it skips straight to a trigger draft.
 */
export function useBindingCreator({ path, forValue, hasAnimation, animatable, valueNode }: Options) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [draftKind, setDraftKind] = useState<DraftKind | null>(null);
  const [draftValue, setDraftValue] = useState('');
  const [showHelp, setShowHelp] = useState(false);

  const animationChoiceAvailable = Boolean(forValue) && animatable !== false && !hasAnimation;

  const openDraft = (kind: DraftKind) => {
    setMenuOpen(false);
    setDraftKind(kind);
    setDraftValue(kind === 'animation' ? DEFAULT_SCRIPT : DEFAULT_CONDITION);
    setShowHelp(false);
  };

  const handleAddClick = () => {
    if (animationChoiceAvailable) {
      setMenuOpen((v) => !v);
    } else {
      openDraft('trigger');
    }
  };

  const cancelDraft = () => {
    setMenuOpen(false);
    setDraftKind(null);
  };

  const confirmDraft = async () => {
    if (draftKind === null || draftValue.trim() === '') return;
    try {
      if (draftKind === 'animation') {
        await createAnimation(path, draftValue);
      } else {
        await createTrigger(path, {
          condition: draftValue,
          ...(valueNode ? { value: String(valueNode.value) } : {}),
        });
      }
      setDraftKind(null);
    } catch {
      // error is handled by api.ts (session-expired event)
    }
  };

  return {
    menuOpen,
    draftKind,
    draftValue,
    setDraftValue,
    showHelp,
    setShowHelp,
    isOpen: menuOpen || draftKind !== null,
    handleAddClick,
    chooseAnimation: () => openDraft('animation'),
    chooseTrigger: () => openDraft('trigger'),
    cancelDraft,
    confirmDraft,
  };
}
