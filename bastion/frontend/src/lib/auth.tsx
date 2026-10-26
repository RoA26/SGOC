import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { SESSION_EXPIRED, api, tokenStore } from "./api";
import type { Me, Rol } from "./types";

type Status = "loading" | "anonymous" | "authenticated";
type AuthValue = {
  status: Status;
  me: Me | null;
  expired: boolean;
  /** true tras un cierre de sesión explícito: no se recuerda la página del usuario anterior. */
  signedOut: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  hasRole: (...roles: Rol[]) => boolean;
};

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [status, setStatus] = useState<Status>(tokenStore.get() ? "loading" : "anonymous");
  const [expired, setExpired] = useState(false);
  const [signedOut, setSignedOut] = useState(false);

  useEffect(() => {
    if (!tokenStore.get()) return;
    api<Me>("/auth/me")
      .then((m) => {
        setMe(m);
        setStatus("authenticated");
      })
      .catch(() => {
        tokenStore.clear();
        setStatus("anonymous");
      });
  }, []);

  useEffect(() => {
    const onExpired = () => {
      setMe(null);
      setStatus("anonymous");
      setExpired(true);
    };
    window.addEventListener(SESSION_EXPIRED, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED, onExpired);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const t = await api<{ access_token: string }>("/auth/login", { method: "POST", body: { email, password } });
    tokenStore.set(t.access_token);
    const m = await api<Me>("/auth/me");
    setMe(m);
    setExpired(false);
    setSignedOut(false);
    setStatus("authenticated");
  }, []);

  const logout = useCallback(async () => {
    try {
      await api("/auth/logout", { method: "POST" });
    } finally {
      tokenStore.clear();
      setMe(null);
      setSignedOut(true);
      setStatus("anonymous");
    }
  }, []);

  const value = useMemo<AuthValue>(
    () => ({ status, me, expired, signedOut, login, logout, hasRole: (...r) => !!me && r.includes(me.role) }),
    [status, me, expired, signedOut, login, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const v = useContext(AuthContext);
  if (!v) throw new Error("useAuth fuera de AuthProvider");
  return v;
}
