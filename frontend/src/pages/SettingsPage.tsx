import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { fetchTripById, updateVotingThreshold, type TripResponse } from "../api/tripApi";
import { useCurrentTrip } from "../context/CurrentTripContext";

export default function SettingsPage() {
  const { currentTripId } = useCurrentTrip();
  const [trip, setTrip] = useState<TripResponse | null>(null);
  const [threshold, setThreshold] = useState("0.5");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  useEffect(() => { if (currentTripId) void fetchTripById(currentTripId).then((value) => { setTrip(value); setThreshold(String(value.votingThreshold)); }).catch(() => setMessage("Unable to load trip settings.")); }, [currentTripId]);
  async function save(event: FormEvent) {
    event.preventDefault(); if (!trip) return; setBusy(true); setMessage("");
    try { const updated = await updateVotingThreshold(trip.tripId, Number(threshold)); setTrip(updated); setThreshold(String(updated.votingThreshold)); setMessage("Approval threshold saved."); }
    catch { setMessage("Unable to save approval threshold."); } finally { setBusy(false); }
  }
  return <section className="placeholder-panel" aria-labelledby="settings-title"><span className="placeholder-icon">⚙</span><h1 id="settings-title">Settings</h1><p>Manage settings for the selected trip.</p>{trip ? <form className="settings-form" onSubmit={save}><label htmlFor="threshold">Approval threshold</label><input id="threshold" type="number" min="0.01" max="1" step="0.01" value={threshold} onChange={(event) => setThreshold(event.target.value)} disabled={trip.role !== "OWNER" || busy} /><small>{Math.round(Number(threshold) * 100)}% of current trip members must approve.</small>{trip.role === "OWNER" ? <button className="app-button app-button-primary" type="submit" disabled={busy}>{busy ? "Saving…" : "Save threshold"}</button> : <p role="status">Only the trip owner can change this setting.</p>}{message && <p role="status">{message}</p>}</form> : <p>{currentTripId ? message || "Loading…" : "Select a trip first."}</p>}<div className="page-actions"><Link className="app-button" to={currentTripId ? `/trip/${currentTripId}/plan` : "/dashboard"}>Back</Link></div></section>;
}
