import { Info } from 'lucide-react';

interface InfoButtonProps {
  open: boolean;
  onToggle: () => void;
  /** Extra classes, e.g. "ml-auto" when this is the only trailing icon in its row. */
  className?: string;
}

/**
 * Tap-to-toggle info icon for a parameter's description. Deliberately not a hover tooltip —
 * the primary client is a phone, where hover doesn't exist.
 */
export function InfoButton({ open, onToggle, className = '' }: InfoButtonProps) {
  return (
    <button
      onClick={(e) => {
        e.stopPropagation();
        onToggle();
      }}
      aria-label="Parameter description"
      aria-expanded={open}
      className={`p-0.5 rounded transition-colors shrink-0 ${className} ${
        open ? 'text-indigo-400' : 'text-neutral-500 hover:text-neutral-300'
      }`}
    >
      <Info className="w-3.5 h-3.5" />
    </button>
  );
}
