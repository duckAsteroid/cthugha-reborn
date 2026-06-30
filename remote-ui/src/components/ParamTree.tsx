import { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { getParams } from '../api';
import type { ParamNode } from '../types';
import { useSSE } from '../useSSE';
import { ParamContainer } from './ParamContainer';

export function ParamTree() {
  const [root, setRoot] = useState<ParamNode | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // Subscribe to all params (empty path = root subscription)
  const sseState = useSSE(['']);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    getParams()
      .then((data) => {
        if (!cancelled) {
          setRoot(data);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load parameters');
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16">
        <Loader2 className="w-8 h-8 text-indigo-400 animate-spin" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center py-16">
        <p className="text-red-400 text-sm">{error}</p>
      </div>
    );
  }

  if (!root) {
    return null;
  }

  if (root.type !== 'CONTAINER') {
    return (
      <div className="p-4">
        <p className="text-neutral-400 text-sm">Unexpected root node type: {root.type}</p>
      </div>
    );
  }

  return (
    <div className="space-y-1 p-2">
      <ParamContainer
        node={root}
        path=""
        sseState={sseState}
        defaultOpen={true}
      />
    </div>
  );
}
