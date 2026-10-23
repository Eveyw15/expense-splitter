import { createContext, useContext, useState, useEffect, type ReactNode } from "react";
import { fetchUserTrips, type TripResponse } from "../api/tripApi";
import { useAuth } from "./AuthContext";

interface CurrentTripContextValue {
  trips: TripResponse[];
  currentTripId: number | null;
  setCurrentTripId: (id: number) => void;
  refreshTrips: () => Promise<void>;
}

const CurrentTripContext = createContext<CurrentTripContextValue | null>(null);

export function CurrentTripProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [trips, setTrips] = useState<TripResponse[]>([]);
  const [currentTripId, setCurrentTripId] = useState<number | null>(null);

  async function refreshTrips() {
    const data = await fetchUserTrips();
    setTrips(data);
    // Keep whatever trip is already selected (e.g. from the URL) as long as
    // it still exists; otherwise (first load, or that trip got deleted)
    // fall back to the first one in the list.
    setCurrentTripId((prev) => {
      if (prev && data.some((t) => t.tripId === prev)) return prev;
      return data[0]?.tripId ?? null;
    });
  }

  useEffect(() => {
    if (!user) {
      setTrips([]);
      setCurrentTripId(null);
      return;
    }
    refreshTrips().catch(() => setTrips([]));
  }, [user]);

  const value: CurrentTripContextValue = { trips, currentTripId, setCurrentTripId, refreshTrips };
  return <CurrentTripContext.Provider value={value}>{children}</CurrentTripContext.Provider>;
}

export function useCurrentTrip() {
  const context = useContext(CurrentTripContext);
  if (!context) {
    throw new Error("useCurrentTrip must be used within a CurrentTripProvider");
  }
  return context;
}
