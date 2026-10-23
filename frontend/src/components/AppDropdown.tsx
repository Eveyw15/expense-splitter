import { useEffect, useId, useRef, useState } from 'react';
import '../styles/dropdown.css';

export type DropdownOption = { value: string; label: string };

export default function AppDropdown({ value, options, onChange, label, variant = 'field', disabled = false, autoFocus = false }: {
  value: string; options: DropdownOption[]; onChange: (value: string) => void; label: string; variant?: 'field' | 'heading'; disabled?: boolean; autoFocus?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const optionRefs = useRef<(HTMLButtonElement | null)[]>([]);
  const listId = useId();
  const selectedIndex = options.findIndex(option => option.value === value);
  const selected = options[selectedIndex];

  useEffect(() => {
    if (!open) return;
    const outside = (event: MouseEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false); };
    document.addEventListener('mousedown', outside);
    return () => document.removeEventListener('mousedown', outside);
  }, [open]);

  function move(index: number) {
    if (!options.length) return;
    optionRefs.current[(index + options.length) % options.length]?.focus();
  }

  return <div ref={root} className={`app-dropdown app-dropdown-${variant}`}>
    <button ref={trigger} type="button" disabled={disabled} autoFocus={autoFocus} className="app-dropdown-trigger" aria-label={label} aria-haspopup="listbox" aria-controls={listId} aria-expanded={open}
      title={selected?.label} onClick={() => setOpen(current => !current)} onKeyDown={event => {
        if (event.key === 'Escape' && open) { event.preventDefault(); event.stopPropagation(); setOpen(false); }
        else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
          event.preventDefault(); setOpen(true);
          requestAnimationFrame(() => move(selectedIndex < 0 ? 0 : selectedIndex));
        }
      }}>
      {variant === 'heading' ? <h1 className="app-dropdown-value">{selected?.label ?? label}</h1> : <span className="app-dropdown-value">{selected?.label ?? label}</span>}
      <svg width="14" height="14" viewBox="0 0 12 12" fill="none" className="app-dropdown-chevron" style={{ transform: open ? 'rotate(180deg)' : undefined }} aria-hidden="true"><path d="M2.5 4.5L6 8L9.5 4.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" /></svg>
    </button>
    {open && <div id={listId} role="listbox" aria-label={label} className="app-dropdown-menu" onKeyDown={event => {
      const current = optionRefs.current.indexOf(document.activeElement as HTMLButtonElement);
      if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); setOpen(false); trigger.current?.focus(); }
      else if (event.key === 'ArrowDown') { event.preventDefault(); move(current + 1); }
      else if (event.key === 'ArrowUp') { event.preventDefault(); move(current - 1); }
      else if (event.key === 'Home') { event.preventDefault(); move(0); }
      else if (event.key === 'End') { event.preventDefault(); move(options.length - 1); }
      else if (event.key === 'Tab') setOpen(false);
    }}>
      {options.map((option, index) => <button key={option.value} ref={element => { optionRefs.current[index] = element; }} type="button" role="option" aria-selected={option.value === value} title={option.label}
        className="app-dropdown-option" onClick={() => { onChange(option.value); setOpen(false); trigger.current?.focus(); }}>
        <strong>{option.label}</strong>{option.value === value && <svg width="13" height="13" viewBox="0 0 12 12" fill="none" aria-hidden="true"><path d="M2 6.5L4.8 9L10 3" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" /></svg>}
      </button>)}
      {!options.length && <p className="app-dropdown-empty">No options yet.</p>}
    </div>}
  </div>;
}
