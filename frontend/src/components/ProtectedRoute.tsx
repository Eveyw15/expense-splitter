import { type ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) {
    return (
      <div
        className="flex h-screen w-screen items-center justify-center"
        style={{ background: "var(--background)" }}
      >
        <div
          className="h-10 w-10 animate-spin rounded-full border-4"
          style={{ borderColor: "var(--border)", borderTopColor: "var(--accent)" }}
          role="status"
          aria-label="Loading"
        >
          <span className="sr-only">Loading…</span>
        </div>
      </div>
    );
  }

  if (!user) {
    // Remember where they were headed (e.g. a /join/CODE link) so login can return them there.
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return children;
}
