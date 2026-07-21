import { lazy, Suspense } from 'react';
import dynamicIconImports from 'lucide-react/dynamicIconImports';
import type { LucideProps } from 'lucide-react';

type DynamicIconName = keyof typeof dynamicIconImports;

// Cache lazy components so we don't recreate them on every render.
const iconCache = new Map<string, React.ComponentType<LucideProps>>();

function getLazyIcon(name: string): React.ComponentType<LucideProps> | null {
  if (!(name in dynamicIconImports)) return null;
  if (!iconCache.has(name)) {
    iconCache.set(name, lazy(dynamicIconImports[name as DynamicIconName]));
  }
  return iconCache.get(name)!;
}

interface NodeIconProps {
  name: string;
  className?: string;
}

export function NodeIcon({ name, className = 'w-4 h-4' }: NodeIconProps) {
  const Icon = getLazyIcon(name);
  if (!Icon) return null;
  return (
    <Suspense fallback={null}>
      <Icon className={className} />
    </Suspense>
  );
}
