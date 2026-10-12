import { FieldCondition } from './models';

/** Espelho de Flow.conditionPasses (backend) para avaliar visibleWhen no preenchimento. */
export function conditionPasses(cond: FieldCondition | null | undefined, data: Record<string, unknown>): boolean {
  if (!cond || !cond.field) return true;
  const op = cond.operator || 'not_empty';
  let actual: unknown = data[cond.field];
  if (typeof actual === 'boolean') actual = actual ? '1' : '0';

  if (op === 'empty') return isEmpty(actual);
  if (op === 'not_empty') return !isEmpty(actual);

  const a = actual == null ? '' : String(actual);
  const expected = cond.value == null ? '' : String(cond.value);

  switch (op) {
    case '==': return looseEq(a, expected);
    case '!=': return !looseEq(a, expected);
    case '>': case '>=': case '<': case '<=': return numCmp(a, expected, op);
    case 'contains': return a.toLowerCase().includes(expected.toLowerCase());
    default: return false;
  }
}

function isEmpty(v: unknown): boolean {
  if (v == null) return true;
  if (Array.isArray(v)) return v.length === 0;
  return String(v).trim() === '';
}

function toNum(s: string): number | null {
  const t = s.trim();
  if (t === '') return null;
  const n = Number(t);
  return Number.isNaN(n) ? null : n;
}

function looseEq(a: string, b: string): boolean {
  const na = toNum(a), nb = toNum(b);
  if (na !== null && nb !== null) return na === nb;
  return a.trim().toLowerCase() === b.trim().toLowerCase();
}

function numCmp(a: string, b: string, op: string): boolean {
  const na = toNum(a), nb = toNum(b);
  if (na === null || nb === null) return false;
  switch (op) {
    case '>': return na > nb;
    case '>=': return na >= nb;
    case '<': return na < nb;
    case '<=': return na <= nb;
    default: return false;
  }
}
