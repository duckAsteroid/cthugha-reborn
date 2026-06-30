import * as Switch from '@radix-ui/react-switch';

interface ToggleControlProps {
  value: boolean;
  disabled: boolean;
  onChange: (v: boolean) => void;
}

export function ToggleControl({ value, disabled, onChange }: ToggleControlProps) {
  return (
    <div className={disabled ? 'opacity-40 pointer-events-none' : ''}>
      <Switch.Root
        checked={value}
        onCheckedChange={onChange}
        disabled={disabled}
        className="w-10 h-6 bg-neutral-700 rounded-full relative data-[state=checked]:bg-indigo-500 transition-colors cursor-pointer focus:outline-none focus:ring-2 focus:ring-indigo-500"
        aria-label="Toggle"
      >
        <Switch.Thumb className="block w-4 h-4 bg-white rounded-full shadow-md transition-transform translate-x-1 data-[state=checked]:translate-x-5" />
      </Switch.Root>
    </div>
  );
}
