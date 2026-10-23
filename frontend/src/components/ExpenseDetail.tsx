import { useEffect, useRef } from 'react';
import { money, expenseError, viewReceipt, type Expense } from '../api/expenseApi';
import { useState } from 'react';

export default function ExpenseDetail({ expense, busy, error, onClose, onEdit, onDelete }: {
  expense: Expense; busy: boolean; error: string; onClose: () => void; onEdit: () => void; onDelete: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const [receiptError, setReceiptError] = useState('');
  const outdatedItemSplit = expense.splitMode === 'BY_ITEM' && expense.items.some(item =>
    Math.round(item.amount * (item.quantity ?? 1) * 100) !== item.allocations.reduce((sum, allocation) => sum + Math.round(allocation.shareAmount * 100), 0));
  useEffect(() => {
    const dialog = ref.current!;
    dialog.showModal();
    return () => dialog.close();
  }, []);
  return <dialog ref={ref} className="expense-dialog" aria-labelledby="expense-detail-title" onCancel={e => { e.preventDefault(); if (!busy) onClose(); }}>
    <div className="expense-detail-header"><h2 id="expense-detail-title">{expense.description}</h2><button className="app-button" disabled={busy} onClick={onClose} autoFocus>Close</button></div>
    <p className="expense-detail-meta">{expense.date} · {expense.category || 'Uncategorised'}</p>
    <strong className="expense-value">{money(expense.originalAmount ?? expense.amount, expense.currencyCode ?? expense.baseCurrency)}</strong>
    {expense.currencyCode !== expense.baseCurrency && <p>{money(expense.amount, expense.baseCurrency)} · rate {expense.exchangeRateToBase} on {expense.exchangeRateDate}</p>}
    <dl className="expense-detail-people"><dt>Paid by</dt><dd>{expense.payerName}</dd><dt>Created by</dt><dd>{expense.createdByName}</dd></dl>
    <h3 className="expense-section-title">Participants</h3>
    {expense.shares.length ? <ul>{expense.shares.map(share => <li className="expense-split-total" key={share.userId}><span>{share.username}</span><strong>{money(share.shareAmount, expense.baseCurrency)}</strong></li>)}</ul>
      : <p>This expense has no split yet. Edit it to allocate shares.</p>}
    {expense.splitMode === 'BY_ITEM' && <section><h3 className="expense-section-title">Item split</h3>{outdatedItemSplit && <p role="status">This expense was saved before quantity was included in item splits. {expense.canEdit ? 'Edit and save it' : 'Ask its creator to edit and save it'} to recalculate the total and shares.</p>}{expense.items.map(item => <div className="expense-detail-item" key={item.id}><div className="expense-split-total"><strong>{item.name}{item.quantity ? ` × ${item.quantity}` : ''}</strong><strong>{money(item.amount * (item.quantity ?? 1), expense.currencyCode)}</strong></div>{item.allocations.map(allocation => <div className="expense-split-total" key={allocation.userId}><span>{allocation.username}</span><span>{money(allocation.shareAmount, expense.currencyCode)}</span></div>)}</div>)}</section>}
    {expense.receipt && <section><h3 className="expense-section-title">Receipt</h3><p>{expense.receipt.fileName} · uploaded by {expense.receipt.uploadedBy}</p><button className="app-button" onClick={() => void viewReceipt(expense).catch(e => setReceiptError(expenseError(e)))}>View receipt</button>{receiptError && <p role="alert">{receiptError}</p>}</section>}
    {expense.canEdit && <div className="page-actions"><button className="app-button" disabled={busy} onClick={onEdit}>Edit expense</button><button className="app-button expense-danger" disabled={busy} onClick={onDelete}>{busy ? 'Deleting…' : 'Delete expense'}</button></div>}
    {error && <p role="alert">{error}</p>}
  </dialog>;
}
