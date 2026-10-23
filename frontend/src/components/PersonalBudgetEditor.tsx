import { useState, type FormEvent } from 'react';
import { expenseApi, expenseError, money as formatMoney, type PersonalBudget } from '../api/expenseApi';
import CurrencySelect from './CurrencySelect';

export default function PersonalBudgetEditor({ tripId, baseCurrency, budget, money, onSaved }: {
  tripId: number; baseCurrency: string; budget: PersonalBudget | null; money: (amount: number) => string; onSaved: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [amount, setAmount] = useState(String(budget?.amount ?? ''));
  const [currency, setCurrency] = useState(budget?.currencyCode ?? baseCurrency);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function save(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true); setError('');
    try { await expenseApi.savePersonalBudget(tripId, amount, currency); setEditing(false); onSaved(); }
    catch (e) { setError(expenseError(e)); }
    finally { setBusy(false); }
  }
  return <div className="expense-card expense-budget expense-personal-budget">
    <span className="expense-label">Personal budget</span>
    {budget ? <><strong className="expense-value">{money(budget.baseAmount)}</strong><p className="expense-card-note">Set as {formatMoney(budget.amount, budget.currencyCode)}</p></> : <p>Set your personal budget</p>}
    {editing ? <form onSubmit={save}>
      <label>Personal budget amount<input aria-label="Personal budget amount" type="number" min="0" max="99999999.99" step="0.01" required value={amount} disabled={busy} onChange={e => setAmount(e.target.value)} /></label>
      <CurrencySelect custom value={currency} onChange={setCurrency} label="Personal budget currency" disabled={busy} />
      <button className="app-button" disabled={busy}>{busy ? 'Saving…' : 'Save personal budget'}</button>
      <button className="app-button" type="button" disabled={busy} onClick={() => setEditing(false)}>Cancel</button>
    </form> : <button className="app-button" onClick={() => { setAmount(String(budget?.amount ?? '')); setCurrency(budget?.currencyCode ?? baseCurrency); setEditing(true); }}>{budget ? 'Edit personal budget' : 'Set Budget'}</button>}
    {error && <p role="alert">{error}</p>}
  </div>;
}
