import { useEffect } from "react";
import { NavLink, Outlet, useParams } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useCurrentTrip } from "../context/CurrentTripContext";
import "../styles/dashboard.css";

export function AppIcon({
  name,
}: {
  name: "dashboard" | "plan" | "trip" | "expenses" | "review" | "profile" | "community";
}) {
  const paths = {
    community: <><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3c5 5 5 13 0 18-5-5-5-13 0-18Z" /></>,
    dashboard: <><rect x="3" y="3" width="18" height="18" rx="2" /><path d="M9 3v18M9 10h12" /></>,
    plan: <path d="m12 3 2.8 6.2L21 12l-6.2 2.8L12 21l-2.8-6.2L3 12l6.2-2.8Z" />,
    trip: <><path d="m12 3 9 9-9 9-9-9Z" /><path d="m9 15 2-5 4-1-2 5Z" /></>,
    expenses: <><path d="M12 2v20M17 6H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" /></>,
    review: <path d="M12 2l2.9 6.26 6.9.6-5.2 4.52 1.55 6.74L12 17.1 5.85 20.72 7.4 13.98 2.2 9.46l6.9-.6z" />,
    // Feather settings (MIT): https://github.com/feathericons/feather/blob/main/icons/settings.svg
    // License: public/licenses/feather.txt. Uses the surrounding icons' stroke styling.
    profile: <><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" /></>,
  };
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
}

export default function AppLayout() {
  const { user } = useAuth();
  const username = user?.username?.trim() || "User";
  // Picked up from whichever nested route is currently matched, even
  // though AppLayout itself sits above /trip/:tripId in the tree.
  const { tripId } = useParams<{ tripId?: string }>();
  const { currentTripId, setCurrentTripId } = useCurrentTrip();

  // The URL is the source of truth whenever it names a trip (deep links,
  // refreshes); otherwise we keep whatever trip was last selected.
  useEffect(() => {
    if (tripId) setCurrentTripId(Number(tripId));
  }, [tripId, setCurrentTripId]);

  // Sits at the same tier as Dashboard now, always visible - the trip
  // itself is picked separately (via the dropdown on trip pages), not by
  // whether a tripId happens to be in the current URL.
  const navigation = [
    { to: "/dashboard", label: "Dashboard", icon: "dashboard", end: true },
    ...(currentTripId
      ? ([
          // No "Trip" link: it silently opened whichever trip was last viewed.
          // Trips are opened from their Dashboard cards instead.
          { to: `/trip/${currentTripId}/expenses`, label: "Expenses", icon: "expenses", end: false },
        ] as const)
      : []),
  ] as const;

  return (
    <div className="tripsync-app">
      <a className="skip-link" href="#main-content">Skip to content</a>
      <aside className="app-sidebar" aria-label="G’Day Trip sidebar">
        <NavLink to="/dashboard" className="app-brand" aria-label="G’Day Trip home">
          <span className="brand-mark" aria-hidden="true">T</span>
          <span className="brand-name">G’Day Trip</span>
        </NavLink>
        <nav className="app-navigation" aria-label="Main navigation">
          {navigation.map(({ to, label, icon, end }) => (
            <NavLink key={to} to={to} end={end} aria-label={label} title={label} className={({ isActive }) => `nav-link${isActive ? " is-active" : ""}`}>
              <AppIcon name={icon} /><span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-profile">
          <NavLink to="/profile" className={({ isActive }) => `flex min-w-0 flex-1 items-center gap-2${isActive ? " is-active" : ""}`} aria-label={`${username}'s profile`} title={`${username} — Profile`}>
          <span className="profile-avatar" aria-hidden="true">{Array.from(username)[0].toUpperCase()}</span>
          <span className="profile-name">{username}</span>
          </NavLink>
          <NavLink to="/settings" className="rounded-md p-2 hover:bg-muted" aria-label="Site settings" title="Site settings"><AppIcon name="profile" /></NavLink>
        </div>
      </aside>
      <main id="main-content" className="app-main" tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  );
}
