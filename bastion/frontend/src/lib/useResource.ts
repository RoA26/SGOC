import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "./api";

type State<T> = { data: T | null; error: string | null; loading: boolean };

/** Carga un recurso con estados de carga y error, y expone `reload`. */
export function useResource<T>(load: (signal: AbortSignal) => Promise<T>, deps: unknown[]) {
  const [state, setState] = useState<State<T>>({ data: null, error: null, loading: true });
  const [tick, setTick] = useState(0);
  const loader = useCallback(load, deps);

  useEffect(() => {
    const ctl = new AbortController();
    setState((s) => ({ ...s, loading: true, error: null }));
    loader(ctl.signal)
      .then((data) => setState({ data, error: null, loading: false }))
      .catch((e: unknown) => {
        if (ctl.signal.aborted) return;
        setState({ data: null, error: errorMessage(e), loading: false });
      });
    return () => ctl.abort();
  }, [loader, tick]);

  return { ...state, reload: () => setTick((t) => t + 1) };
}
