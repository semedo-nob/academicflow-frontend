import { useEffect, useState } from 'react';
import { useApp } from '../context/AppContext';

/** Prefer empty lists over mock/other-account data while loading or on error. */
function isolatedSeed<T>(fallback: T): T {
  if (Array.isArray(fallback)) return [] as unknown as T;
  return fallback;
}

/**
 * Loads async data scoped to the current auth session.
 * On login / logout / department switch, previous rows are cleared immediately
 * so another account's data cannot flash on screen.
 */
export function useAsyncData<T>(loader: () => Promise<T>, fallback: T, deps: unknown[] = []) {
  const { sessionKey, authenticated } = useApp();
  const [data, setData] = useState<T>(() => isolatedSeed(fallback));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fromApi, setFromApi] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setFromApi(false);
    setError(null);
    // Drop prior session data before the new request resolves
    setData(isolatedSeed(fallback));

    loader()
      .then((result) => {
        if (!cancelled) {
          setData(result);
          setFromApi(true);
          setError(null);
        }
      })
      .catch((err: Error) => {
        if (!cancelled) {
          // Never paint mock/other-tenant demo rows for an authenticated session
          setData(authenticated ? isolatedSeed(fallback) : fallback);
          setFromApi(false);
          setError(err.message);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
    // sessionKey forces refetch + clear when identity or department changes
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionKey, authenticated, ...deps]);

  return { data, loading, error, fromApi, setData };
}
