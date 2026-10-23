import { CURRENCIES } from '../utils/currency';
import AppDropdown from './AppDropdown';

export default function CurrencySelect({ value, onChange, label = 'Currency', disabled = false, custom = false }: {
  value: string; onChange: (code: string) => void; label?: string; disabled?: boolean; custom?: boolean;
}) {
  return custom ? <AppDropdown label={label} value={value} disabled={disabled} onChange={onChange}
    options={CURRENCIES.map(([code, symbol]) => ({ value: code, label: `${code} · ${symbol}` }))} />
    : <select aria-label={label} value={value} disabled={disabled} onChange={event => onChange(event.target.value)}>
      {CURRENCIES.map(([code, symbol]) => <option key={code} value={code}>{code} · {symbol}</option>)}
    </select>;
}
