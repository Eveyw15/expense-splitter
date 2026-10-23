import { useEffect, useRef, useState, type FormEvent } from 'react';
import { expenseApi, expenseError, money as formatMoney, type SimplifiedDebts, type Balances, type Obligation, type Settlement } from '../api/expenseApi';
import type { TripMemberResponse } from '../api/tripApi';
import { useAuth } from '../context/AuthContext';
import AppDropdown from './AppDropdown';

// TripExpenses is keyed by tripId: switching trips unmounts this view and its payment dialog.
export default function ExpenseBalances({ tripId, members, onPaymentSaved, money, baseCurrency }: {
  money: (amount: number) => string; baseCurrency: string;
  tripId: number; members: TripMemberResponse[]; onPaymentSaved: () => void;
}) {
  const [plan, setPlan] = useState<SimplifiedDebts | null>(null);
  const [simplifying, setSimplifying] = useState(false);
  const [planError, setPlanError] = useState('');
  async function simplify() {
    setSimplifying(true); setPlanError('');
    try { setPlan(await expenseApi.simplify(tripId)); }
    catch (e) { setPlanError(expenseError(e)); }
    finally { setSimplifying(false); }
  }
  const [balances, setBalances] = useState<Balances | null>(null);
  const [history, setHistory] = useState<Settlement[] | null>(null);
  const [balanceError, setBalanceError] = useState('');
  const [historyError, setHistoryError] = useState('');
  const [revision, setRevision] = useState(0);
  const [paying, setPaying] = useState(false);
  useEffect(() => {
    let active = true;
    expenseApi.balances(tripId).then(value => { if (active) setBalances(value); })
      .catch(e => { if (active) setBalanceError(expenseError(e)); });
    expenseApi.settlements(tripId).then(value => { if (active) setHistory(value); })
      .catch(e => { if (active) setHistoryError(expenseError(e)); });
    return () => { active = false; };
  }, [tripId, revision]);
  function refresh() {
    setBalances(null); setHistory(null); setBalanceError(''); setHistoryError('');
    setRevision(n => n + 1);
  }
  const creditors = balances?.youOwe.filter(o => members.some(m => m.userId === o.userId)) ?? [];
  return <div className="expense-balances">
    {balanceError ? <p role="alert" className="expense-message">{balanceError} <button className="app-button" onClick={refresh}>Retry</button></p>
      : !balances ? <p role="status" className="expense-message">Loading balances…</p>
      : <>
        <div className="expense-balance-columns">
          <Obligations money={money} title="You owe" rows={balances.youOwe} total={balances.totalYouOwe} empty="You’re all settled up." tone="owe" />
          <Obligations money={money} title="You are owed" rows={balances.youAreOwed} total={balances.totalYouAreOwed} empty="Nobody owes you a payment." tone="owed" />
        </div>
        <section className="expense-card expense-net">
          <div><h2 className="expense-label">Net balance</h2>
            <strong className={`expense-value ${balances.netBalance < 0 ? 'owe' : balances.netBalance > 0 ? 'owed' : ''}`}>{money(balances.netBalance)}</strong>
            <p className="expense-card-note">{balances.netBalance > 0 ? 'You are owed more overall.' : balances.netBalance < 0 ? 'You owe more overall.' : 'Your net balance is zero.'}
              {balances.netBalance === 0 && balances.youOwe.length > 0 && ' Direct payments are still outstanding.'}</p>
          </div>
          <button className="app-button app-button-primary" disabled={!creditors.length} onClick={() => setPaying(true)}>Record Payment</button>
        </section>
        {balances.youOwe.length > creditors.length && <p className="expense-card-note">Payments can only be recorded to active trip members.</p>}
      </>}
    <section className="expense-card">
      <button className="app-button" disabled={simplifying} onClick={simplify}>{simplifying ? 'Calculating…' : 'Simplify Debts'}</button>
      {planError && <p role="alert">{planError}</p>}
      {plan && <div>
        <h2 className="expense-section-title">Suggested settlement plan</h2>
        <p className="expense-card-note">Suggestions only. No payments or debts have been changed. Record Payment continues to use direct outstanding debts.</p>
        {!plan.transfers.length ? <p>Everyone is settled up. No simplified transfers are required.</p>
          : <ul className="expense-obligations">{plan.transfers.map((transfer, index) => <li key={index}><span>{transfer.fromName} → {transfer.toName}</span><strong>{money(transfer.amount)}</strong></li>)}</ul>}
        <p className="expense-card-note">Original pairwise relationships: {plan.originalPairwiseRelationships} · Suggested transfers: {plan.transfers.length} · Accounting: {plan.currency}</p>
        <button className="app-button" onClick={() => setPlan(null)}>Close suggestions</button>
      </div>}
    </section>
    <h2 className="expense-section-title">Settlement History</h2>
    {historyError ? <p role="alert" className="expense-message">{historyError} <button className="app-button" onClick={refresh}>Retry</button></p>
      : !history ? <p role="status" className="expense-message">Loading settlement history…</p>
      : !history.length ? <p className="expense-card expense-message">No payments recorded for this trip yet.</p>
      : <div className="expense-table-wrap"><table className="expense-table settlement-table"><thead><tr><th>Date</th><th>From</th><th>To</th><th>Amount</th></tr></thead>
        <tbody>{history.map(payment => <tr key={payment.settlementId}>
          <td>{new Date(payment.settledAt).toLocaleDateString('en-AU', { day: 'numeric', month: 'short', year: 'numeric' })}</td>
          <td>{payment.payerName}</td><td>{payment.payeeName}</td><td className="expense-money">{money(payment.amount)}</td>
        </tr>)}</tbody></table></div>}
    {paying && <RecordPayment baseCurrency={baseCurrency} tripId={tripId} creditors={creditors} onClose={() => setPaying(false)} onSaved={() => {
      setPaying(false); onPaymentSaved();
    }} />}
  </div>;
}

function Obligations({ title, rows, total, empty, tone, money }: {
  money: (amount: number) => string;
  title: string; rows: Obligation[]; total: number; empty: string; tone: string;
}) {
  return <section className="expense-card"><h2 className="expense-label">{title}</h2><strong className={`expense-value ${tone}`}>{money(total)}</strong>
    {!rows.length ? <p className="expense-card-note">{empty}</p> : <ul className="expense-obligations">{rows.map(row => <li key={row.userId}>
      <span className="expense-person"><span className="expense-avatar" aria-hidden="true">{row.name.slice(0, 1).toUpperCase()}</span>{row.name}</span>
      <strong className="expense-money">{money(row.amount)}</strong>
    </li>)}</ul>}
  </section>;
}

function RecordPayment({ tripId, creditors, onClose, onSaved, baseCurrency }: {
  baseCurrency: string;
  tripId: number; creditors: Obligation[]; onClose: () => void; onSaved: () => void;
}) {
  const money = (amount: number) => formatMoney(amount, baseCurrency);
  const { user } = useAuth();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const submitting = useRef(false);
  const [payeeId, setPayeeId] = useState(creditors[0]?.userId ?? 0);
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const payee = creditors.find(o => o.userId === payeeId);
  const cents = /^\d+(?:\.\d{1,2})?$/.test(amount) ? Math.round(Number(amount) * 100) : 0;
  const valid = !!payee && cents > 0 && cents <= Math.round(payee.amount * 100);
  useEffect(() => {
    const dialog = dialogRef.current!;
    dialog.showModal();
    return () => dialog.close();
  }, []);
  async function save(event: FormEvent) {
    event.preventDefault();
    if (submitting.current || !valid) return;
    submitting.current = true; setBusy(true); setError('');
    try { await expenseApi.recordPayment(tripId, payeeId, amount); onSaved(); }
    catch (e) { setError(expenseError(e)); }
    finally { submitting.current = false; setBusy(false); }
  }
  return <dialog ref={dialogRef} className="expense-dialog" aria-labelledby="record-payment-title"
    onCancel={e => { e.preventDefault(); if (!submitting.current) onClose(); }}>
    <h2 id="record-payment-title" className="expense-section-title">Record Payment</h2>
    <p className="expense-card-note">Record a payment you have made to another trip member.</p>
    <form onSubmit={save} className="expense-payment-form">
      <label>From<input value={user?.username ?? 'You'} readOnly /></label>
      <div className="expense-field"><span className="expense-field-label">To</span><AppDropdown label="Payment recipient" value={String(payeeId)} disabled={busy} autoFocus
        onChange={value => { setPayeeId(Number(value)); setAmount(''); setError(''); }} options={creditors.map(o => ({ value: String(o.userId), label: o.name }))} /></div>
      {payee && <p className="expense-card-note">Outstanding: {money(payee.amount)}</p>}
      <label>Amount ({baseCurrency})<input type="number" min="0.01" max={payee?.amount ?? 0} step="0.01" required value={amount}
        disabled={busy} onChange={e => setAmount(e.target.value)} aria-describedby="payment-amount-help" /></label>
      <p id="payment-amount-help" className="expense-card-note">Enter a positive amount up to the outstanding debt, with at most two decimal places.</p>
      {error && <p role="alert">{error}</p>}
      <div className="page-actions"><button type="button" className="app-button" disabled={busy} onClick={onClose}>Cancel</button>
        <button type="submit" className="app-button app-button-primary" disabled={busy || !valid}>{busy ? 'Recording…' : 'Record Payment'}</button></div>
    </form>
  </dialog>;
}
