import { tr } from '../i18n';
/** Stable business codes; labels and legacy aliases stay at the boundary. */
export const AcceptanceDecision = {
  ACCEPT: 'accept',
  REJECT: 'reject',
} as const;
export type AcceptanceDecision = (typeof AcceptanceDecision)[keyof typeof AcceptanceDecision];
export type AcceptanceDecisionValue = AcceptanceDecision | 'return' | '通过' | '打回';
export const acceptanceOptions = [
  { value: AcceptanceDecision.ACCEPT, get label() { return tr('ui.equipment.text1620'); } },
  { value: AcceptanceDecision.REJECT, get label() { return tr('ui.equipment.text1621'); } },
] as const;

export function normalizeAcceptanceDecision(value: unknown): AcceptanceDecision | undefined {
  switch (value) {
    case AcceptanceDecision.ACCEPT:
    case '通过':
      return AcceptanceDecision.ACCEPT;
    case AcceptanceDecision.REJECT:
    case 'return':
    case '打回':
      return AcceptanceDecision.REJECT;
    default:
      return undefined;
  }
}
export function acceptanceDecisionLabel(value: unknown): string {
  const code = normalizeAcceptanceDecision(value);
  return acceptanceOptions.find((option) => option.value === code)?.label ?? String(value ?? '');
}
