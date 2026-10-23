import CurrencySelect from './CurrencySelect';
import AppDropdown from './AppDropdown';
import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import type { TripMemberResponse } from '../api/tripApi';
import { expenseApi, expenseError, money as formatMoney, viewReceipt, type Expense } from '../api/expenseApi';
import { prepareImageForUpload } from '../utils/prepareImage';
import { EXPENSE_CATEGORIES as CATEGORIES } from '../utils/expenseHistory';
import { cents, itemLineCents } from '../utils/expenseItem';
function today() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
type DraftItem = { name: string; amount: string; quantity: string; userIds: number[] };

export default function ExpenseForm({ tripId, members, expense, onSaved, onCancel, baseCurrency, onReceiptPickerOpen, onReceiptPickerDone }: {
  baseCurrency: string; tripId: number; members: TripMemberResponse[]; expense?: Expense; onSaved: () => void; onCancel: () => void;
  onReceiptPickerOpen?: () => void; onReceiptPickerDone?: () => void;
}) {
  const [currencyCode, setCurrencyCode] = useState(expense?.currencyCode ?? baseCurrency);
  const money = (amount: number) => formatMoney(amount, currencyCode);
  const [description, setDescription] = useState(expense?.description ?? '');
  const [category, setCategory] = useState(expense?.category ?? 'Other');
  const [date, setDate] = useState(expense?.date ?? today());
  const [amount, setAmount] = useState(expense ? String(expense.originalAmount ?? expense.amount) : '');
  const [payerId, setPayerId] = useState(members.some(m => m.userId === expense?.payerId) ? String(expense!.payerId) : '');
  // Existing allocations are opened as custom to preserve their exact persisted values.
  const [mode, setMode] = useState<'EQUAL' | 'CUSTOM' | 'BY_ITEM'>(expense?.splitMode === 'BY_ITEM' ? 'BY_ITEM' : expense?.splitMode === 'EQUAL' ? 'EQUAL' : expense?.shares.length ? 'CUSTOM' : 'EQUAL');
  const [selected, setSelected] = useState<number[]>(expense?.shares.length
    ? expense.shares.filter(s => members.some(m => m.userId === s.userId)).map(s => s.userId)
    : members.map(m => m.userId));
  const [custom, setCustom] = useState<Record<number, string>>(() => Object.fromEntries(expense?.shares.map(s => [s.userId, (s.originalShareAmount ?? s.shareAmount).toFixed(2)]) ?? []));
  const [items, setItems] = useState<DraftItem[]>(expense?.items?.map(item => ({ name: item.name, amount: item.amount.toFixed(2), quantity: item.quantity?.toString() ?? '', userIds: item.allocations.map(a => a.userId) })) ?? []);
  const [collapsedItems, setCollapsedItems] = useState<boolean[]>(() => expense?.items?.map(() => false) ?? []);
  const lastItemRef = useRef<HTMLDivElement>(null);
  const [receiptFile, setReceiptFile] = useState<File | null>(null);
  const receiptInputId = useId();
  const receiptInputRef = useRef<HTMLInputElement>(null);
  useEffect(() => {
    const input = receiptInputRef.current;
    if (!input || !onReceiptPickerDone) return;
    input.addEventListener('cancel', onReceiptPickerDone);
    return () => input.removeEventListener('cancel', onReceiptPickerDone);
  }, [onReceiptPickerDone]);
  const [removeReceipt, setRemoveReceipt] = useState(false);
  const [savedId, setSavedId] = useState<number | undefined>(expense?.expenseId);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const itemCents = items.map(item => itemLineCents(item.amount, item.quantity));
  const itemTotal = itemCents.reduce<number>((sum, value) => sum + (value ?? 0), 0);
  const effectiveAmount = mode === 'BY_ITEM' ? (itemTotal / 100).toFixed(2) : amount;
  const totalCents = cents(effectiveAmount);
  const ordered = [...selected].sort((a, b) => a - b);
  const preview = new Map(ordered.map((id, index) => [id, totalCents === null ? null
    : Math.floor(totalCents / ordered.length) + (index < totalCents % ordered.length ? 1 : 0)]));
  const customValues = ordered.map(id => cents(custom[id] ?? ''));
  const customTotal = customValues.reduce<number>((sum, value) => sum + (value ?? 0), 0);
  const customValid = customValues.every(value => value !== null) && customTotal === totalCents;
  const itemValid = items.length > 0 && items.every((item, index) => item.name.trim() && (itemCents[index] ?? 0) > 0 && item.userIds.length > 0 && item.userIds.every(id => selected.includes(id)));
  const itemShares = new Map(ordered.map(id => [id, 0]));
  items.forEach((item, index) => { const ids = [...item.userIds].sort((a, b) => a - b); const value = itemCents[index] ?? 0;
    ids.forEach((id, i) => itemShares.set(id, (itemShares.get(id) ?? 0) + Math.floor(value / ids.length) + (i < value % ids.length ? 1 : 0))); });
  const valid = !!payerId && selected.length > 0 && totalCents !== null && totalCents > 0 && (mode === 'BY_ITEM' ? itemValid : mode === 'EQUAL' || customValid);
  const removedMembers = expense?.shares.some(s => !members.some(m => m.userId === s.userId));

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!valid) { setError('Select participants and make sure shares match the expense amount.'); return; }
    setBusy(true); setError('');
    try {
      const saved = await expenseApi.save(tripId, {
        description, category, date, amount: effectiveAmount, currencyCode, payerId: Number(payerId), splitMode: mode,
        participants: ordered.map(userId => mode === 'CUSTOM' ? { userId, shareAmount: custom[userId] } : { userId }),
        items: mode === 'BY_ITEM' ? items.map(item => ({ name: item.name.trim(), amount: item.amount, quantity: item.quantity ? Number(item.quantity) : undefined, userIds: item.userIds })) : undefined,
      }, savedId);
      setSavedId(saved.expenseId);
      if (receiptFile) await expenseApi.uploadReceipt(tripId, saved.expenseId, await shrinkReceipt(receiptFile));
      else if (removeReceipt) await expenseApi.removeReceipt(tripId, saved.expenseId);
      onSaved();
    } catch (e) { setError(savedId || expense ? expenseError(e) : `${expenseError(e)} If the expense was saved, retry here to finish the receipt.`); }
    finally { setBusy(false); }
  }
  return <form className="expense-form" onSubmit={save}>
    <fieldset disabled={busy} className="expense-form-layout">
      <div className="expense-form-main">
        <h2 className="expense-section-title">{expense ? 'Edit expense' : 'Add expense'}</h2>
        {removedMembers && <p role="status">Some previous participants have left this trip. Choose active members and review the split before saving.</p>}
        <div className="expense-fields">
          <label>Description<input required maxLength={200} value={description} onChange={e => setDescription(e.target.value)} /></label>
          <div className="expense-field"><span className="expense-field-label">Category</span><AppDropdown label="Category" value={category} onChange={setCategory} options={[...new Set([...CATEGORIES, category])].map(c => ({ value: c, label: c }))} /></div>
          <label>Date<input type="date" required value={date} onChange={e => setDate(e.target.value)} /></label>
          <div className="expense-field"><label htmlFor="expense-amount">Amount</label><div className="expense-amount-input"><input id="expense-amount" type="number" min="0.01" max="99999999.99" step="0.01" required readOnly={mode === 'BY_ITEM'} value={effectiveAmount} onChange={e => setAmount(e.target.value)} /><CurrencySelect custom value={currencyCode} onChange={setCurrencyCode} label="Expense currency" /></div><small>{mode === 'BY_ITEM' ? 'Amount is the sum of item rows. ' : ''}Shares are entered in {currencyCode}. Accounting uses {baseCurrency}.</small></div>
          <div className="expense-field"><span className="expense-field-label">Paid by</span><AppDropdown label="Paid by" value={payerId} onChange={setPayerId} options={[{ value: '', label: 'Select a member' }, ...members.map(m => ({ value: String(m.userId), label: m.username }))]} /></div>
          <div><span className="expense-label">Split</span><div className="expense-segment" role="group" aria-label="Split mode">{(['EQUAL', 'CUSTOM', 'BY_ITEM'] as const).map(value =>
            <button type="button" key={value} aria-pressed={mode === value} onClick={() => setMode(value)}>{value === 'BY_ITEM' ? 'By Item' : value === 'EQUAL' ? 'Equal' : 'Custom'}</button>)}</div></div>
          <div className="expense-receipt"><span className="expense-label">Receipt</span>
            <div className="expense-file-control"><input ref={receiptInputRef} id={receiptInputId} className="expense-file-input" type="file" aria-label="Receipt file" accept="image/jpeg,image/png,application/pdf" onClick={onReceiptPickerOpen} onChange={e => { const file = e.target.files?.[0]; if (file) { setReceiptFile(file); setRemoveReceipt(false); } onReceiptPickerDone?.(); }} />
              <label htmlFor={receiptInputId} className="expense-file-button">{receiptFile || expense?.receipt && !removeReceipt ? 'Change' : 'Upload'}</label>
              <span className="expense-file-name" title={receiptFile?.name ?? (!removeReceipt ? expense?.receipt?.fileName : undefined)}>{receiptFile?.name ?? (!removeReceipt ? expense?.receipt?.fileName : null) ?? 'No file selected'}</span>
            </div>
            {expense?.receipt && !removeReceipt && !receiptFile && <div className="expense-receipt-actions"><small>Uploaded by {expense.receipt.uploadedBy}</small><button type="button" className="app-button" onClick={() => void viewReceipt(expense).catch(e => setError(expenseError(e)))}>View</button><button type="button" className="app-button" onClick={() => setRemoveReceipt(true)}>Remove</button></div>}
            {removeReceipt && <small>Receipt will be removed on save.</small>}
          </div>
        </div>
        <div className="page-actions"><button className="app-button app-button-primary" disabled={!valid}>{busy ? 'Saving…' : 'Save Expense'}</button><button className="app-button" type="button" onClick={onCancel}>Cancel</button></div>
        {error && <p role="alert" className="expense-message">{error}</p>}
      </div>
      <aside className="expense-card expense-split" aria-label="Split between">
        <h3 className="expense-label">Split between</h3>
        {members.map(member => <div key={member.userId} className={`expense-split-row ${selected.includes(member.userId) ? 'selected' : ''}`}>
          <label><input type="checkbox" checked={selected.includes(member.userId)} disabled={mode === 'BY_ITEM' && items.some(item => item.userIds.includes(member.userId))} title={mode === 'BY_ITEM' && items.some(item => item.userIds.includes(member.userId)) ? 'Remove this member from assigned items first' : undefined} onChange={e => setSelected(previous => e.target.checked ? [...previous, member.userId] : previous.filter(id => id !== member.userId))} />
            <span className="expense-avatar" aria-hidden="true">{member.username.slice(0, 1).toUpperCase()}</span><span>{member.username}</span></label>
          {selected.includes(member.userId) ? mode === 'CUSTOM'
            ? <input aria-label={`Share for ${member.username}`} type="number" min="0" max="99999999.99" step="0.01" required value={custom[member.userId] ?? ''} onChange={e => setCustom(previous => ({ ...previous, [member.userId]: e.target.value }))} />
            : <span className="expense-money">{mode === 'BY_ITEM' ? money((itemShares.get(member.userId) ?? 0) / 100) : preview.get(member.userId) == null ? '—' : money(preview.get(member.userId)! / 100)}</span>
            : <span>—</span>}
        </div>)}
        {mode === 'BY_ITEM' && <div className="expense-items"><h3 className="expense-label">Items</h3>{items.map((item, index) => <div className="expense-item" key={index} ref={index === items.length - 1 ? lastItemRef : undefined}>
          <div className="expense-item-header"><button type="button" className="expense-item-toggle" aria-expanded={!collapsedItems[index]} onClick={() => setCollapsedItems(previous => previous.map((collapsed, i) => i === index ? !collapsed : collapsed))}>
            <span className="expense-item-index">Item {index + 1}</span>
            {collapsedItems[index] && <><span className="expense-item-summary">{item.name.trim() || 'Untitled item'}{Number(item.quantity) > 1 ? ` ×${item.quantity}` : ''}</span><strong className="expense-item-total">{itemCents[index] === null ? '—' : money(itemCents[index]! / 100)}</strong></>}
            <svg className="expense-item-chevron" width="14" height="14" viewBox="0 0 12 12" fill="none" aria-hidden="true"><path d="M2.5 4.5L6 8L9.5 4.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" /></svg>
          </button><button type="button" className="expense-item-remove expense-danger" aria-label={`Remove item ${index + 1}${item.name.trim() ? `: ${item.name.trim()}` : ''}`} title="Remove item" onClick={() => { setItems(previous => previous.filter((_, i) => i !== index)); setCollapsedItems(previous => previous.filter((_, i) => i !== index)); }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3m3 0-1 13H7L6 7m4 4v6m4-6v6" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" /></svg>
          </button></div>
          {!collapsedItems[index] && <div className="expense-item-fields">
            <label>Item name<input required maxLength={200} value={item.name} onChange={e => setItems(previous => previous.map((old, i) => i === index ? { ...old, name: e.target.value } : old))} /></label>
            <div className="expense-item-price-row"><label>Unit price ({currencyCode})<input required type="number" min="0.01" step="0.01" value={item.amount} onChange={e => setItems(previous => previous.map((old, i) => i === index ? { ...old, amount: e.target.value } : old))} /></label>
              <label>Quantity (optional)<input type="number" min="1" step="1" value={item.quantity} onChange={e => setItems(previous => previous.map((old, i) => i === index ? { ...old, quantity: e.target.value } : old))} /></label></div>
            <fieldset><legend>Split between</legend><div className="expense-item-members">{members.filter(m => selected.includes(m.userId)).map(m => <label key={m.userId}><input type="checkbox" checked={item.userIds.includes(m.userId)} onChange={e => setItems(previous => previous.map((old, i) => i === index ? { ...old, userIds: e.target.checked ? [...old.userIds, m.userId] : old.userIds.filter(id => id !== m.userId) } : old))} />{m.username}</label>)}{item.userIds.filter(id => !members.some(m => m.userId === id)).map(id => <label key={id}><input type="checkbox" checked onChange={() => setItems(previous => previous.map((old, i) => i === index ? { ...old, userIds: old.userIds.filter(userId => userId !== id) } : old))} />Former member #{id} (remove)</label>)}</div></fieldset>
          </div>}
        </div>)}<button type="button" className="app-button" onClick={() => { setItems(previous => [...previous, { name: '', amount: '', quantity: '', userIds: [] }]); setCollapsedItems(previous => [...previous, false]); requestAnimationFrame(() => lastItemRef.current?.scrollIntoView({ block: 'nearest' })); }}>+ Add Item</button>
          <div className="expense-split-total"><span>Allocated · {items.filter(item => !item.userIds.length).length} unassigned items</span><strong>{money([...itemShares.values()].reduce((a, b) => a + b, 0) / 100)} / {money(itemTotal / 100)}</strong></div>
          <p role="status">{itemValid ? 'Balanced' : 'Assign every item to selected participants before saving.'}</p>
        </div>}
        <div className="expense-split-total"><span>{selected.length} people · {mode.toLowerCase()}</span><strong>{money((mode === 'CUSTOM' ? customTotal : mode === 'BY_ITEM' ? itemTotal : selected.length ? totalCents ?? 0 : 0) / 100)}</strong></div>
        {!selected.length && <p role="status">Select at least one participant.</p>}
        {mode === 'CUSTOM' && !customValid && <p role="status">Custom shares must total {totalCents === null ? 'the expense amount' : money(totalCents / 100)} exactly.</p>}
        {mode === 'EQUAL' && selected.length > 0 && <p className="expense-card-note">Any remaining cents go to participants in user ID order.</p>}
      </aside>
    </fieldset>
  </form>;
}

// Photos are re-encoded as ~1600px JPEGs (a few hundred KB) so receipts fit the free database tier; PDFs go up as-is.
async function shrinkReceipt(file: File): Promise<File> {
  if (!file.type.startsWith('image/')) return file;
  return new File([await prepareImageForUpload(file)], file.name.replace(/\.[^.]+$/, '') + '.jpg', { type: 'image/jpeg' });
}
