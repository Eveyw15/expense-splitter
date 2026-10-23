import { createContext, useContext, useState, useEffect, type ReactNode } from "react";
import * as authApi from "../api/authApi";
import * as userApi from "../api/userApi";

interface CurrentUser {
  userId: number;
  username: string;
  email: string;
  role: string;
}

interface AuthContextValue {
  user: CurrentUser | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  updateUser: (profile: CurrentUser) => void;
  refreshSession: (authResponse: authApi.AuthResponse) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem("token");
    if (!token) {
      setLoading(false);
      return;
    }

    userApi
      .getCurrentUser()
      .then((profile) => setUser(profile))
      .catch(() => {
        localStorage.removeItem("token");
        setUser(null);
      })
      .finally(() => setLoading(false));
  }, []);

  function saveSession(authResponse: authApi.AuthResponse) {
    const { token, ...userInfo } = authResponse;
    localStorage.setItem("token", token);
    setUser(userInfo);
  }

  async function login(email: string, password: string) {
    const authResponse = await authApi.login(email, password);
    saveSession(authResponse);
  }

  async function register(username: string, email: string, password: string) {
    const authResponse = await authApi.register(username, email, password);
    saveSession(authResponse);
  }

  function logout() {
    localStorage.removeItem("token");
    setUser(null);
  }

  function updateUser(profile: CurrentUser) {
    setUser(profile);
  }

  const value: AuthContextValue = {
    user,
    loading,
    login,
    register,
    logout,
    updateUser,
    refreshSession: saveSession,
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
