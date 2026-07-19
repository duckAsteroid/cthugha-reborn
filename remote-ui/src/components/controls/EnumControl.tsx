import * as Select from '@radix-ui/react-select';
import { ChevronDown, ChevronUp } from 'lucide-react';
import type { EnumOption } from '../../types';

interface EnumControlProps {
  value: number;
  options: EnumOption[];
  disabled: boolean;
  onChange: (v: number) => void;
}

export function EnumControl({ value, options, disabled, onChange }: EnumControlProps) {
  const handleChange = (strVal: string) => {
    const idx = parseInt(strVal, 10);
    if (!isNaN(idx)) {
      onChange(idx);
    }
  };

  return (
    <div className={disabled ? 'opacity-40 pointer-events-none' : ''}>
      <Select.Root value={String(value)} onValueChange={handleChange} disabled={disabled}>
        <Select.Trigger
          className="inline-flex items-center justify-between gap-2 px-3 py-1.5 bg-neutral-800 border border-neutral-600 rounded text-neutral-200 text-sm min-w-24 hover:border-neutral-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 data-[disabled]:opacity-40"
          aria-label="Select option"
        >
          <span className="truncate min-w-0 flex-1 text-left">
            <Select.Value />
          </span>
          <Select.Icon>
            <ChevronDown className="w-4 h-4 text-neutral-400" />
          </Select.Icon>
        </Select.Trigger>

        <Select.Portal>
          <Select.Content className="bg-neutral-800 border border-neutral-600 rounded shadow-xl z-50 overflow-hidden">
            <Select.ScrollUpButton className="flex items-center justify-center h-6 text-neutral-400">
              <ChevronUp className="w-4 h-4" />
            </Select.ScrollUpButton>
            <Select.Viewport className="p-1">
              {options.map((opt, idx) => (
                <Select.Item
                  key={idx}
                  value={String(idx)}
                  className="flex items-center px-3 py-1.5 text-sm text-neutral-200 rounded cursor-pointer hover:bg-indigo-600 focus:bg-indigo-600 focus:outline-none data-[state=checked]:text-indigo-300"
                >
                  <Select.ItemText>{opt.label}</Select.ItemText>
                </Select.Item>
              ))}
            </Select.Viewport>
            <Select.ScrollDownButton className="flex items-center justify-center h-6 text-neutral-400">
              <ChevronDown className="w-4 h-4" />
            </Select.ScrollDownButton>
          </Select.Content>
        </Select.Portal>
      </Select.Root>
    </div>
  );
}
