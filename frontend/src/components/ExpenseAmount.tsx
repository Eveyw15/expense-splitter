import type { Expense } from '../api/expenseApi';
import { historyAmounts } from '../utils/currency';

export default function ExpenseAmount({ expense, currency, rate }: { expense: Expense; currency: string; rate: number }) {
  const { original, converted } = historyAmounts(expense, currency, rate);
  return <>{original}{converted && <small>({converted})</small>}</>;
}
