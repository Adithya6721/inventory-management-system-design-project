// Preserve a creation key after a failed/lost response, including a page reload.
// Clear only after acknowledgement, so a later intentional order gets a new key.
const memory = new Map();
const storageKey = scope => `inventory:pending-create:${scope}`;

function read(scope) {
  try {
    const saved = globalThis.sessionStorage?.getItem(storageKey(scope));
    if (saved) return JSON.parse(saved);
  } catch { /* Storage may be unavailable; retain the in-memory fallback. */ }
  return memory.get(scope);
}

export function pendingCreate(scope, payload) {
  const fingerprint = JSON.stringify(payload);
  let pending = read(scope);
  if (!pending || pending.fingerprint !== fingerprint) {
    const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16));
    pending = { fingerprint, key: Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('') };
  }
  memory.set(scope, pending);
  try { globalThis.sessionStorage?.setItem(storageKey(scope), JSON.stringify(pending)); } catch { /* fallback above */ }
  return { ...payload, idempotencyKey: pending.key };
}

export function completeCreate(scope, key) {
  if (read(scope)?.key !== key) return;
  memory.delete(scope);
  try { globalThis.sessionStorage?.removeItem(storageKey(scope)); } catch { /* no persistent storage */ }
}
