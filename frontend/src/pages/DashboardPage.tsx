import { money } from "../utils/currency";
import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
    fetchUserTrips,
    fetchMyInvitations,
    respondToInvitation,
    type TripResponse,
    type TripMemberResponse,
} from "../api/tripApi";
import { useAuth } from "../context/AuthContext";
import { useCurrentTrip } from "../context/CurrentTripContext";
import TripCover from "../components/TripCover";

type InviteAction = "ACCEPT" | "REJECT";

function InvitationList({
    invitations,
    responding,
    onRespond,
}: {
    invitations: TripMemberResponse[];
    responding: { id: number; action: InviteAction } | null;
    onRespond: (invite: TripMemberResponse, action: InviteAction) => void;
}) {
    return (
        <section aria-label="Trip invitations" className="invite-section">
            <h2 className="section-heading">Trip invitations ({invitations.length})</h2>
            <ul className="invite-list">
                {invitations.map((invite) => {
                    const busyAction = responding?.id === invite.tripMemberId ? responding.action : null;
                    return (
                        <li key={invite.tripMemberId} className="invite-card">
                            <div className="invite-details">
                                <p className="invite-trip">{invite.tripName}</p>
                                <p className="invite-meta">
                                    {invite.tripDestination} · Invited {new Date(invite.joinedAt).toLocaleDateString()}
                                </p>
                            </div>
                            <div className="page-actions">
                                <button
                                    type="button"
                                    className="app-button"
                                    disabled={responding !== null}
                                    onClick={() => onRespond(invite, "REJECT")}
                                >
                                    {busyAction === "REJECT" ? "Declining..." : "Decline"}
                                </button>
                                <button
                                    type="button"
                                    className="app-button app-button-primary"
                                    disabled={responding !== null}
                                    onClick={() => onRespond(invite, "ACCEPT")}
                                >
                                    {busyAction === "ACCEPT" ? "Joining..." : "Accept"}
                                </button>
                            </div>
                        </li>
                    );
                })}
            </ul>
        </section>
    );
}

function TripGrid({ trips, label, past }: { trips: TripResponse[]; label: string; past?: boolean }) {
    return (
        <section aria-label={label} className="trip-grid" style={{ marginTop: 24 }}>
            {trips.map((trip) => (
                <article key={trip.tripId} className="trip-card">
                    <Link
                        className="trip-card-link"
                        to={past ? "/reviews" : `/trip/${trip.tripId}`}
                        aria-label={`${past ? "Review" : "View"} ${trip.name}`}
                    >
                        <TripCover trip={trip} className="trip-image" />
                        <div className="trip-info">
                            <div className="trip-heading">
                                <h2>{trip.name}</h2>
                                <span className="trip-status">{trip.status}</span>
                            </div>
                            <p className="trip-dates">
                                {trip.startDate} – {trip.endDate}
                            </p>
                            <p className="trip-destination">{trip.destination}</p>
                            <p style={{ fontSize: 13, color: "var(--dashboard-muted)", marginTop: 8 }}>
                                Budget: {money(trip.plannedBudget, trip.baseCurrency ?? "AUD")} · <strong>{trip.role}</strong>
                                {past && <> · <span style={{ color: "var(--accent)" }}>Review →</span></>}
                            </p>
                        </div>
                    </Link>
                </article>
            ))}
        </section>
    );
}

export default function DashboardPage() {
    const [notice, setNotice] = useState("");
    const [trips, setTrips] = useState<TripResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [invitations, setInvitations] = useState<TripMemberResponse[]>([]);
    const [responding, setResponding] = useState<{ id: number; action: InviteAction } | null>(null);
    const [inviteError, setInviteError] = useState("");

    const [showJoinForm, setShowJoinForm] = useState(false);
    const [joinCode, setJoinCode] = useState("");

    const { user } = useAuth();
    const { refreshTrips } = useCurrentTrip();
    const navigate = useNavigate();
    const username = user?.username?.trim() || "Traveler";

    const handleJoinWithCode = (e: FormEvent) => {
        e.preventDefault();
        const cleaned = joinCode.replace(/[^A-Za-z0-9]/g, "").toUpperCase();
        if (!cleaned) return;
        // Same page a scanned QR opens, so both routes preview the trip first.
        navigate(`/join/${cleaned}`);
    };

    useEffect(() => {
        async function loadTrips() {
            try {
                setLoading(true);
                const data = await fetchUserTrips();
                setTrips(data);
            } catch (err: any) {
                setError("Unable to load your trips. Is the backend running?");
            } finally {
                setLoading(false);
            }
        }

        loadTrips();
        // Invitations are secondary; a failure here shouldn't hide the trips.
        fetchMyInvitations().then(setInvitations).catch(() => setInvitations([]));
    }, []);

    const handleRespond = async (invite: TripMemberResponse, action: InviteAction) => {
        if (responding !== null) return;
        setResponding({ id: invite.tripMemberId, action });
        setInviteError("");
        setNotice("");

        try {
            await respondToInvitation(invite.tripMemberId, action);
            setInvitations((prev) => prev.filter((i) => i.tripMemberId !== invite.tripMemberId));

            if (action === "ACCEPT") {
                const [data] = await Promise.all([fetchUserTrips(), refreshTrips()]);
                setTrips(data);
                setNotice(`You joined ${invite.tripName}.`);
            } else {
                setNotice(`Declined the invitation to ${invite.tripName}.`);
            }
        } catch (err: any) {
            setInviteError(err.response?.data?.message || "Failed to respond to invitation.");
            // The invite may have been cancelled or already answered elsewhere; resync.
            fetchMyInvitations().then(setInvitations).catch(() => {});
        } finally {
            setResponding(null);
        }
    };

    const upcomingTrips = trips.filter((t) => t.status !== "COMPLETED");
    const pastTrips = trips.filter((t) => t.status === "COMPLETED");

    return (
        <>
            <header className="dashboard-header">
                <div>
                    <p className="page-eyebrow">Welcome back, {username}</p>
                    <h1>Your upcoming trip plans</h1>
                </div>
                <div className="page-actions">
                    <button
                        type="button"
                        className="app-button"
                        onClick={() => setNotice("Calendar export is coming soon.")}
                    >
                        Export Calendar
                    </button>
                    <button
                        type="button"
                        className="app-button"
                        aria-expanded={showJoinForm}
                        aria-controls="join-code-form"
                        onClick={() => setShowJoinForm((open) => !open)}
                    >
                        Join with Code
                    </button>
                    <Link className="app-button app-button-primary" to="/trip/new">
                        <span aria-hidden="true">+</span> Add Trip
                    </Link>
                </div>
            </header>

            {showJoinForm && (
                <form id="join-code-form" className="join-code-form" onSubmit={handleJoinWithCode}>
                    <label htmlFor="join-code-input">Invite code</label>
                    <input
                        id="join-code-input"
                        type="text"
                        autoFocus
                        autoComplete="off"
                        spellCheck={false}
                        maxLength={20}
                        placeholder="e.g. K7MQ2XHP"
                        value={joinCode}
                        onChange={(e) => setJoinCode(e.target.value)}
                    />
                    <button type="submit" className="app-button app-button-primary" disabled={!joinCode.trim()}>
                        Continue
                    </button>
                </form>
            )}

            {notice && (
                <p className="dashboard-notice" role="status">
                    {notice}
                </p>
            )}

            {error && (
                <p style={{ color: "#9b1c1c", marginBottom: 20 }}>{error}</p>
            )}

            {inviteError && (
                <p role="alert" style={{ color: "#9b1c1c", marginBottom: 20 }}>{inviteError}</p>
            )}

            {invitations.length > 0 && (
                <InvitationList invitations={invitations} responding={responding} onRespond={handleRespond} />
            )}

            {loading ? (
                <p style={{ color: "var(--dashboard-muted)", marginTop: 40 }}>Loading your trips...</p>
            ) : trips.length === 0 ? (
                <div className="placeholder-panel" style={{ marginTop: 24 }}>
                    <h2>No trips planned yet</h2>
                    <p>Start planning your next adventure by creating a trip!</p>
                    <div className="page-actions" style={{ justifyContent: "center", marginTop: 20 }}>
                        <Link className="app-button app-button-primary" to="/trip/new">
                            <span aria-hidden="true">+</span> Create your first trip
                        </Link>
                    </div>
                </div>
            ) : (
                <>
                    {upcomingTrips.length > 0 && <TripGrid trips={upcomingTrips} label="Your trips" />}

                    {pastTrips.length > 0 && (
                        <div style={{ marginTop: 40 }}>
                            <h2 style={{ fontSize: 18, fontWeight: 700, borderBottom: "2px solid var(--border)", paddingBottom: 8 }}>
                                Past trips
                            </h2>
                            <TripGrid trips={pastTrips} label="Past trips" past />
                        </div>
                    )}
                </>
            )}
        </>
    );
}
