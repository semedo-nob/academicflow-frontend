import { useId } from 'react';

type SuggestOption = { value: string; label: string };

type SuggestInputProps = {
  id?: string;
  value: string;
  onChange: (value: string) => void;
  /** When set, called on blur if the value differs from `value` at focus time — useful for table inline edits */
  onCommit?: (value: string) => void;
  options: SuggestOption[];
  placeholder?: string;
  disabled?: boolean;
  /** Shown under the field — clarifies free typing is allowed */
  hint?: string;
};

/**
 * Free-text field with optional suggestions (datalist).
 * Users can type any value; suggestions are not a closed list.
 */
export function SuggestInput({
  id,
  value,
  onChange,
  onCommit,
  options,
  placeholder,
  disabled,
  hint = 'Type freely — suggestions appear if available',
}: SuggestInputProps) {
  const autoId = useId();
  const listId = id ? `${id}-suggestions` : `${autoId}-suggestions`;

  return (
    <div className="suggest-input">
      <input
        id={id}
        list={listId}
        value={value}
        disabled={disabled}
        placeholder={placeholder}
        autoComplete="off"
        onChange={(e) => onChange(e.target.value)}
        onBlur={(e) => {
          if (onCommit) onCommit(e.target.value);
        }}
      />
      <datalist id={listId}>
        {options.map((o) => (
          <option key={o.value} value={o.label} />
        ))}
      </datalist>
      {hint ? <p className="field-hint">{hint}</p> : null}
    </div>
  );
}

/** Match typed text to an option by label or value (case-insensitive). */
export function matchSuggestOption(typed: string, options: SuggestOption[]): SuggestOption | undefined {
  const t = typed.trim().toLowerCase();
  if (!t) return undefined;
  return (
    options.find((o) => o.value.toLowerCase() === t) ||
    options.find((o) => o.label.toLowerCase() === t) ||
    options.find((o) => o.label.toLowerCase().startsWith(t))
  );
}
