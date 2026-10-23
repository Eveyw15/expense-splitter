import PersonalBudgetEditor from '../components/PersonalBudgetEditor';
import { useAuth } from '../context/AuthContext';
import { scopeExpenses, personalSpending, budgetUsage } from '../utils/expenseScope';
import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { fetchUserTrips, fetchTripMembers, type TripResponse, type TripMemberResponse } from '../api/tripApi';
import { expenseApi, expenseError, money as formatMoney, type PersonalBudget, type Budget, type Expense, type ExpenseSummary } from '../api/expenseApi';
import { AppIcon } from '../components/AppLayout';
import ExpenseForm from '../components/ExpenseForm';
import ExpenseDetail from '../components/ExpenseDetail';
import ExpenseBalances from '../components/ExpenseBalances';
import ExpenseAnalytics from '../components/ExpenseAnalytics';
import { EMPTY_EXPENSE_FILTERS, EXPENSE_CATEGORIES, expenseCategory, filterExpenses } from '../utils/expenseHistory';
import '../styles/expenses.css';
import ExpenseAmount from '../components/ExpenseAmount';
import CurrencySelect from '../components/CurrencySelect';
import AppDropdown from '../components/AppDropdown';

const BUDGET_WARNING_PERCENT = 80;

export default function ExpensesPage() {
  const { tripId } = useParams();
  const navigate = useNavigate();
  const [trips, setTrips] = useState<TripResponse[] | null>(null);
  const [selected, setSelected] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    fetchUserTrips().then(items => {
      if (!active) return;
      setTrips(items);
      setSelected(previous => items.some(t => t.tripId === previous) ? previous : items[0]?.tripId ?? null);
    }).catch(e => { if (active) setError(expenseError(e)); });
    return () => { active = false; };
  }, [attempt]);
  const selectedTripId = tripId === undefined ? selected : Number(tripId);
  const trip = trips?.find(t => t.tripId === selectedTripId);
  return <div className="expenses-page">
    <header className="expense-header">
      {trips && trips.length > 1 ? <AppDropdown variant="heading" label="Select trip" value={String(selectedTripId ?? '')} onChange={value => tripId === undefined ? setSelected(Number(value)) : navigate(`/trip/${value}/expenses`)}
        options={trips.map(t => ({ value: String(t.tripId), label: t.name }))} /> : <h1>{trip?.name ?? 'Expenses'}</h1>}
      <p>Expense tracking</p>
    </header>
    {error ? <div role="alert" className="expense-message">{error} <button className="app-button" onClick={() => { setError(''); setAttempt(n => n + 1); }}>Retry</button></div>
      : !trips ? <p role="status" className="expense-message">Loading your trips…</p>
      : !trip && tripId !== undefined ? <p role="alert" className="expense-message">This trip is not available in your active trips.</p>
      : !trip ? <section className="expense-empty">
        <span className="placeholder-icon"><AppIcon name="trip" /></span>
        <h2>No trips yet.</h2><p>Expenses are managed within a trip.<br />Create a trip or join an existing trip to start.</p>
        <Link className="app-button app-button-primary" to="/trip/new">Create a Trip</Link>
      </section> : <TripExpenses key={trip.tripId} trip={trip} />}
  </div>;
}

function TripExpenses({ trip }: { trip: TripResponse }) {
  const { user } = useAuth();
  const [personal, setPersonal] = useState(false);
  const baseCurrency = trip.baseCurrency ?? 'AUD';
  const [displayCurrency, setDisplayCurrency] = useState(baseCurrency);
  const [quote, setQuote] = useState<{ currency: string; rate: number } | null>(null);
  const [rateError, setRateError] = useState('');
  const [failedCurrency, setFailedCurrency] = useState<string | null>(null);
  const [rateAttempt, setRateAttempt] = useState(0);
  const effectiveCurrency = displayCurrency === baseCurrency || quote?.currency !== displayCurrency ? baseCurrency : displayCurrency;
  const displayRate = effectiveCurrency === baseCurrency ? 1 : quote!.rate;
  const money = (amount: number) => formatMoney(amount * displayRate, effectiveCurrency);
  useEffect(() => {
    let active = true;
    if (displayCurrency !== baseCurrency) {
      expenseApi.displayRate(trip.tripId, displayCurrency).then(value => {
        if (active) { setQuote({ currency: displayCurrency, rate: value.rate }); setRateError(''); setFailedCurrency(null); }
      }).catch(e => {
        if (active) { setQuote(null); setFailedCurrency(displayCurrency); setDisplayCurrency(baseCurrency); setRateError(expenseError(e)); }
      });
    }
    return () => { active = false; };
  }, [trip.tripId, baseCurrency, displayCurrency, rateAttempt]);
  const [data, setData] = useState<{ expenses: Expense[]; members: TripMemberResponse[]; budget: Budget; summary: ExpenseSummary; personalBudget: PersonalBudget | null } | null>(null);
  const [error, setError] = useState('');
  const [tab, setTab] = useState('Track');
  const [notice, setNotice] = useState('');
  const [revision, setRevision] = useState(0);
  const [editing, setEditing] = useState<Expense | undefined>();
  const [detail, setDetail] = useState<Expense | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [filters, setFilters] = useState(EMPTY_EXPENSE_FILTERS);
  const scopedExpenses = useMemo(() => scopeExpenses(data?.expenses ?? [], personal, user?.userId), [data, personal, user?.userId]);
  const filteredExpenses = useMemo(() => filterExpenses(scopedExpenses, filters), [scopedExpenses, filters]);
  const analyticsExpenses = useMemo(() => personal ? scopedExpenses.map(e => ({ ...e, amount: e.currentUserShare })) : scopedExpenses, [personal, scopedExpenses]);
  const spending = personal ? personalSpending(scopedExpenses) : data?.summary.totalSpending ?? 0;
  const budgetAmount = personal ? data?.personalBudget?.baseAmount ?? null : data?.summary.budget ?? 0;
  const usage = budgetUsage(spending, budgetAmount);
  const categories = useMemo(() => [...new Set([
    ...EXPENSE_CATEGORIES, ...(data?.expenses.map(expense => expenseCategory(expense.category)) ?? []),
  ])], [data]);
  const payers = useMemo(() => [...new Map([
    // Include historical payers from this trip even if they have since left.
    ...(data?.expenses.map(expense => [expense.payerId, expense.payerName] as const) ?? []),
    ...(data?.members.map(member => [member.userId, member.username] as const) ?? []),
  ])].sort((a, b) => a[1].localeCompare(b[1])), [data]);
  const invalidDateRange = !!filters.from && !!filters.to && filters.from > filters.to;
  useEffect(() => {
    let active = true;
    Promise.all([expenseApi.list(trip.tripId), fetchTripMembers(trip.tripId), expenseApi.budget(trip.tripId), expenseApi.summary(trip.tripId), expenseApi.personalBudget(trip.tripId)])
      .then(([expenses, members, budget, summary, personalBudget]) => { if (active) setData({ expenses, members, budget, summary, personalBudget }); })
      .catch(e => { if (active) setError(expenseError(e)); });
    return () => { active = false; };
  }, [trip.tripId, revision]);
  function reload() { setDetail(null); setData(null); setError(''); setRevision(n => n + 1); }
  async function openDetail(expense: Expense) {
    setDetailLoading(true); setError('');
    try { setDetail(await expenseApi.get(trip.tripId, expense.expenseId)); }
    catch (e) { setError(expenseError(e)); }
    finally { setDetailLoading(false); }
  }
  async function remove(expense: Expense) {
    if (!window.confirm(`Delete “${expense.description}”? This cannot be undone.`)) return;
    setBusy(true); setError('');
    try { await expenseApi.remove(trip.tripId, expense.expenseId); reload(); }
    catch (e) { setError(expenseError(e)); }
    finally { setBusy(false); }
  }
  return <section className="expense-content">
    <div className="expense-currency-toolbar"><span>Accounting currency: {baseCurrency}</span><label>Display in <CurrencySelect custom value={displayCurrency} onChange={code => { setQuote(null); setRateError(''); setFailedCurrency(null); setDisplayCurrency(code); }} label="Display currency" /></label></div>
    {rateError ? <p role="alert" className="expense-card-note">{rateError} Showing {baseCurrency} accounting values. <button className="app-button" onClick={() => { const retry = failedCurrency; setQuote(null); setRateError(''); setRateAttempt(n => n + 1); if (retry) setDisplayCurrency(retry); }}>Retry conversion</button></p>
      : displayCurrency !== effectiveCurrency && <p role="status" className="expense-card-note">Loading display rate… Showing {baseCurrency} accounting values.</p>}
    {effectiveCurrency !== baseCurrency && <p className="expense-card-note">Approximate display values at the latest available rate. Records remain in {baseCurrency}.</p>}
    <nav className="expense-tabs" aria-label="Expense views">{['Track', 'Add Expense', 'Balances'].map(name =>
      <button key={name} aria-current={tab === name ? 'page' : undefined} disabled={busy || detailLoading} onClick={() => { setEditing(undefined); setTab(name); }}>{name}</button>)}</nav>
    {notice && <p role="status" className="expense-message">{notice}</p>}
    {error && <p role="alert" className="expense-message">{error} <button className="app-button" onClick={reload}>Retry</button></p>}
    {!data ? !error && <p role="status">Loading expenses, budget and trip members…</p>
      : tab === 'Balances' ? <ExpenseBalances money={money} baseCurrency={baseCurrency} tripId={trip.tripId} members={data.members} onPaymentSaved={() => { setNotice('Payment recorded.'); reload(); }} />
      : tab === 'Add Expense' ? <ExpenseForm baseCurrency={baseCurrency} key={editing?.expenseId ?? 'new'} tripId={trip.tripId} members={data.members} expense={editing}
        onCancel={() => { setEditing(undefined); setTab('Track'); }} onSaved={() => { setEditing(undefined); setTab('Track'); reload(); }} />
      : <>
        <div className="expense-segment expense-track-scope" role="group" aria-label="Track scope">{[false, true].map(value => <button key={String(value)} aria-pressed={personal === value} onClick={() => setPersonal(value)}>{value ? 'Personal' : 'Trip'}</button>)}</div>
        <div className="expense-summary">
          <SummaryCard money={money} label={personal ? 'My spending' : 'Total spending'} value={spending} note={personal ? 'your allocated shares' : 'all members'} />
          {personal ? <PersonalBudgetEditor tripId={trip.tripId} baseCurrency={baseCurrency} budget={data.personalBudget} money={money} onSaved={reload} />
            : <BudgetEditor money={money} baseCurrency={baseCurrency} tripId={trip.tripId} budget={data.budget} onSaved={reload} />}
          <SummaryCard money={money} label="Remaining" value={usage.remaining} tone={(usage.remaining ?? 0) < 0 ? 'owe' : 'owed'} note={usage.remaining === null ? 'Set your personal budget' : undefined} />
          <SummaryCard money={money} label="You owe" value={data.summary.currentUserOwes} tone="owe" note="outstanding direct debts" />
        </div>
        <div className={`expense-card expense-progress ${(usage.remaining ?? 0) < 0 ? 'exceeded' : (usage.percentage ?? 0) >= BUDGET_WARNING_PERCENT ? 'approaching' : ''}`}>
          <div className="expense-progress-label"><span className="expense-label">{personal ? 'Personal budget used' : 'Budget used'}</span><span>{usage.percentage === null ? '—' : `${usage.percentage}%`}</span></div>
          {budgetAmount === null ? <p>Set your personal budget to track usage.</p> : <>
            <p>{money(spending)} / {money(budgetAmount)}</p>
            <progress aria-label={personal ? 'Personal budget used' : 'Budget used'} max={100} value={Math.min(100, usage.percentage ?? (spending > 0 ? 100 : 0))} />
            {budgetAmount === 0 ? <p>{spending > 0 ? 'Budget exceeded — no budget allocated.' : 'No budget allocated.'}</p>
              : (usage.remaining ?? 0) < 0 ? <p>Budget exceeded by {money(-usage.remaining!)}.</p>
              : (usage.percentage ?? 0) >= BUDGET_WARNING_PERCENT && <p>Approaching budget.</p>}
          </>}
        </div>
        <ExpenseAnalytics money={money} expenses={analyticsExpenses} personal={personal} />
        <h2 className="expense-section-title">{personal ? 'Personal expense history' : 'Expense history'}</h2>
        <p className="expense-card-note">History filters apply to the table. Summary and analytics cover the full {personal ? 'personal' : 'trip'} scope.</p>
        <div className="expense-filters" role="search" aria-label="Search expense history">
          <label>Search expenses<input type="search" placeholder="Search expenses..." value={filters.query} onChange={e => setFilters(previous => ({ ...previous, query: e.target.value }))} /></label>
          <div className="expense-filter-row">
            <div className="expense-field"><span className="expense-field-label">Category</span><AppDropdown label="Filter category" value={filters.category} onChange={category => setFilters(previous => ({ ...previous, category }))} options={[{ value: '', label: 'All' }, ...categories.map(category => ({ value: category, label: category }))]} /></div>
            <div className="expense-field"><span className="expense-field-label">Paid by</span><AppDropdown label="Filter payer" value={filters.payer} onChange={payer => setFilters(previous => ({ ...previous, payer }))} options={[{ value: '', label: 'All' }, ...payers.map(([id, name]) => ({ value: String(id), label: name }))]} /></div>
            <label>From<input type="date" value={filters.from} aria-invalid={invalidDateRange || undefined} aria-describedby={invalidDateRange ? 'expense-date-error' : undefined} onChange={e => setFilters(previous => ({ ...previous, from: e.target.value }))} /></label>
            <label>To<input type="date" value={filters.to} aria-invalid={invalidDateRange || undefined} aria-describedby={invalidDateRange ? 'expense-date-error' : undefined} onChange={e => setFilters(previous => ({ ...previous, to: e.target.value }))} /></label>
            <button className="app-button" onClick={() => setFilters(EMPTY_EXPENSE_FILTERS)}>Clear filters</button>
          </div>
          {invalidDateRange && <p id="expense-date-error" role="alert">From date must be on or before To date.</p>}
        </div>
        {!scopedExpenses.length ? <div className="expense-card expense-message"><h3>{personal ? 'No expenses allocated to you yet.' : 'No expenses yet.'}</h3><p>Add an expense for this trip.</p><button className="app-button" onClick={() => setTab('Add Expense')}>Add Expense</button></div>
          : !filteredExpenses.length ? <div role="status" className="expense-card expense-message"><p>No expenses match your current search or filters.</p><button className="app-button" onClick={() => setFilters(EMPTY_EXPENSE_FILTERS)}>Clear filters</button></div>
          : <div className="expense-table-wrap"><table className="expense-table"><thead><tr><th>Date</th><th>Description</th><th>Payer</th><th>Amount</th><th>Your share</th></tr></thead><tbody>
            {filteredExpenses.map(expense => <tr key={expense.expenseId} onClick={() => { if (!detailLoading) void openDetail(expense); }}>
              <td>{new Date(`${expense.date}T12:00:00`).toLocaleDateString('en-AU', { month: 'short', day: 'numeric' })}</td>
              <td><button disabled={detailLoading} onClick={e => { e.stopPropagation(); void openDetail(expense); }}>{expense.description}</button><small>{expense.category}</small></td>
              <td><span className="expense-person"><span className="expense-avatar" aria-hidden="true">{expense.payerName.slice(0, 1).toUpperCase()}</span>{expense.payerName}</span></td>
              <td className="expense-money"><ExpenseAmount expense={expense} currency={effectiveCurrency} rate={displayRate} /></td><td className="expense-money">{expense.shares.length ? money(expense.currentUserShare) : 'Not split'}</td>
            </tr>)}
          </tbody></table></div>}
      </>}
    {detailLoading && <p role="status">Loading expense details…</p>}
    {detail && <ExpenseDetail expense={detail} busy={busy} error={error} onClose={() => setDetail(null)}
      onEdit={() => { setEditing(detail); setDetail(null); setTab('Add Expense'); }} onDelete={() => remove(detail)} />}
  </section>;
}

function SummaryCard({ label, value, tone, note, money }: { money: (value: number) => string; label: string; value: number | null; tone?: string; note?: string }) {
  return <div className="expense-card"><span className="expense-label">{label}</span><strong className={`expense-value ${tone ?? ''}`}>{value === null ? '—' : money(value)}</strong>{note && <p className="expense-card-note">{note}</p>}</div>;
}

function BudgetEditor({ tripId, budget, onSaved, money, baseCurrency }: { money: (value: number) => string; baseCurrency: string; tripId: number; budget: Budget; onSaved: () => void }) {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(String(budget.plannedBudget));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function save(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try { await expenseApi.updateBudget(tripId, value); setEditing(false); onSaved(); }
    catch (e) { setError(expenseError(e)); }
    finally { setBusy(false); }
  }
  return <div className="expense-card expense-budget"><div><span className="expense-label">Budget</span><strong className="expense-value">{money(budget.plannedBudget)}</strong></div>
    {budget.canEdit && (editing ? <form onSubmit={save}><label>Trip budget ({baseCurrency}) <input aria-label="Trip budget" type="number" min="0" max="99999999.99" step="0.01" required value={value} onChange={e => setValue(e.target.value)} /></label><button className="app-button" disabled={busy}>{busy ? 'Saving…' : 'Save budget'}</button><button className="app-button" type="button" disabled={busy} onClick={() => setEditing(false)}>Cancel</button></form> : <button className="app-button" onClick={() => setEditing(true)}>Edit budget</button>)}
    {error && <p role="alert">{error}</p>}
  </div>;
}
