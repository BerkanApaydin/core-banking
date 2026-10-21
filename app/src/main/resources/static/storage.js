/* exported safeStorage */
// --- Storage Safety Wrapper ---
// localStorage is not guaranteed to be usable: blocked third-party storage,
// private browsing, sandboxed frames and quota exhaustion can make even a read
// throw SecurityError/QuotaExceededError. A single uncaught access in a
// top-level script would abort the rest of the UI, and idempotency state must
// never prevent a money-movement form from rendering.
//
// The wrapper probes the native store once, never throws, and falls back to an
// in-memory Map (hydrated from the native store when a later write fails) so
// the current page keeps working. Memory-only state is intentionally not
// persisted across reloads in that environment.
const safeStorage = (function () {
    const memory = new Map();
    let native = null;

    try {
        if (typeof localStorage !== 'undefined' && localStorage) {
            const probe = '__bank_storage_probe__';
            localStorage.setItem(probe, '1');
            localStorage.removeItem(probe);
            native = localStorage;
        }
    } catch (e) {
        native = null;
    }

    function useMemory() {
        if (!native) return;
        try {
            for (let i = 0; i < native.length; i++) {
                const name = native.key(i);
                if (name === null) continue;
                const value = native.getItem(name);
                if (value !== null) memory.set(name, value);
            }
        } catch (e) { /* hydration is best-effort; keep what we already have */ }
        native = null;
    }

    function getItem(key) {
        const name = String(key);
        if (native) {
            try {
                return native.getItem(name);
            } catch (e) {
                useMemory();
            }
        }
        return memory.has(name) ? memory.get(name) : null;
    }

    function setItem(key, value) {
        const name = String(key);
        const text = String(value);
        if (native) {
            try {
                native.setItem(name, text);
                return;
            } catch (e) {
                useMemory();
            }
        }
        memory.set(name, text);
    }

    function removeItem(key) {
        const name = String(key);
        if (native) {
            try {
                native.removeItem(name);
                return;
            } catch (e) {
                useMemory();
            }
        }
        memory.delete(name);
    }

    function key(index) {
        const position = Number(index);
        if (native) {
            try {
                return native.key(position);
            } catch (e) {
                useMemory();
            }
        }
        return Array.from(memory.keys())[position] ?? null;
    }

    function clear() {
        if (native) {
            try {
                native.clear();
                return;
            } catch (e) {
                useMemory();
            }
        }
        memory.clear();
    }

    return {
        getItem,
        setItem,
        removeItem,
        key,
        clear,
        get length() {
            if (native) {
                try {
                    return native.length;
                } catch (e) {
                    useMemory();
                }
            }
            return memory.size;
        }
    };
})();
