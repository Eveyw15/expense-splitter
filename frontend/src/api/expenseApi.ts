import axios from 'axios';
import client from './axiosClient';

export interface Expense {
  expenseId: number; tripId: number;
  payerId: number; payerName: string;
  createdById: number; createdByName: string;
  description: string; category: string | null; amount: number; date: string;
  createdAt: string; updatedAt: string; canEdit: boolean;
  originalAmount: number; currencyCode: string; baseCurrency: string; exchangeRateToBase: number; exchangeRateDate: string;
  currentUserShare: number; shares: ExpenseShare[];
  splitMode?: 'EQUAL' | 'CUSTOM' | 'BY_ITEM'; items: ExpenseItem[]; receipt: ExpenseReceipt | null;
}
export interface ExpenseItem { id?: number; name: string; quantity: number | null; amount: number; allocations: { userId: number; username: string; shareAmount: number }[] }
export interface ExpenseReceipt { fileName: string; contentType: string; uploadedBy: string; uploadedAt: string }
export interface ExpenseShare { userId: number; username: string; shareAmount: number; originalShareAmount: number }
export interface ExpenseSummary {
  totalSpending: number; budget: number; remaining: number; currentUserOwes: number; budgetUsedPercentage: number;
}
export interface ExpenseInput {
  splitMode: "EQUAL" | "CUSTOM" | "BY_ITEM"; participants: { userId: number; shareAmount?: string }[];
  items?: { name: string; quantity?: number; amount: string; userIds: number[] }[];
  currencyCode: string;
  payerId: number; description: string; category: string; amount: string; date: string;
}
export interface Obligation { userId: number; name: string; amount: number }
export interface Balances {
  netBalance: number; totalYouOwe: number; totalYouAreOwed: number;
  youOwe: Obligation[]; youAreOwed: Obligation[];
}
export interface Settlement {
  settlementId: number; payerId: number; payerName: string; payeeId: number; payeeName: string;
  amount: number; settledAt: string;
}
export interface SimplifiedDebts {
  currency: string; originalPairwiseRelationships: number;
  transfers: { fromUserId: number; fromName: string; toUserId: number; toName: string; amount: number }[];
}
export interface PersonalBudget { amount: number; currencyCode: string; exchangeRateToBase: number; baseAmount: number; exchangeRateDate: string; updatedAt: string }
export interface Budget { plannedBudget: number; canEdit: boolean }
export const expenseApi = {
  simplify: async (tripId: number) => (await client.get<SimplifiedDebts>(`/trips/${tripId}/balances/simplified`)).data,
  personalBudget: async (tripId: number): Promise<PersonalBudget | null> => {
    const response = await client.get<PersonalBudget>(`/trips/${tripId}/personal-budget`);
    return response.status === 204 ? null : response.data;
  },
  savePersonalBudget: async (tripId: number, amount: string, currencyCode: string) =>
    (await client.put<PersonalBudget>(`/trips/${tripId}/personal-budget`, { amount, currencyCode })).data,
  displayRate: async (tripId: number, to: string) => (await client.get<{ rate: number; date: string }>(`/trips/${tripId}/currency-rate`, { params: { to } })).data,
  balances: async (tripId: number) => (await client.get<Balances>(`/trips/${tripId}/balances`)).data,
  settlements: async (tripId: number) => (await client.get<Settlement[]>(`/trips/${tripId}/settlements`)).data,
  recordPayment: async (tripId: number, payeeId: number, amount: string) =>
    (await client.post<Settlement>(`/trips/${tripId}/settlements`, { payeeId, amount })).data,
  summary: async (tripId: number) => (await client.get<ExpenseSummary>(`/trips/${tripId}/expenses/summary`)).data,
  list: async (tripId: number) => (await client.get<Expense[]>(`/trips/${tripId}/expenses`)).data,
  get: async (tripId: number, id: number) => (await client.get<Expense>(`/trips/${tripId}/expenses/${id}`)).data,
  save: async (tripId: number, input: ExpenseInput, id?: number) => id
    ? (await client.put<Expense>(`/trips/${tripId}/expenses/${id}`, input)).data
    : (await client.post<Expense>(`/trips/${tripId}/expenses`, input)).data,
  remove: async (tripId: number, id: number) => { await client.delete(`/trips/${tripId}/expenses/${id}`); },
  uploadReceipt: async (tripId: number, id: number, file: File) => {
    const form = new FormData(); form.append('file', file);
    return (await client.post<Expense>(`/trips/${tripId}/expenses/${id}/receipt`, form)).data;
  },
  removeReceipt: async (tripId: number, id: number) => { await client.delete(`/trips/${tripId}/expenses/${id}/receipt`); },
  receipt: async (tripId: number, id: number) => (await client.get<Blob>(`/trips/${tripId}/expenses/${id}/receipt`, { responseType: 'blob' })).data,
  budget: async (tripId: number) => (await client.get<Budget>(`/trips/${tripId}/budget`)).data,
  updateBudget: async (tripId: number, plannedBudget: string) =>
    (await client.put<Budget>(`/trips/${tripId}/budget`, { plannedBudget })).data,
};
export async function viewReceipt(expense: Expense) {
  const blob = await expenseApi.receipt(expense.tripId, expense.expenseId);
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a'); link.href = url; link.target = '_blank'; link.rel = 'noopener';
  link.click(); setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
export function expenseError(error: unknown): string {
  return axios.isAxiosError(error) && typeof error.response?.data?.message === 'string'
    ? error.response.data.message : 'Unable to load or save expenses. Please try again.';
}
export { money } from '../utils/currency';
