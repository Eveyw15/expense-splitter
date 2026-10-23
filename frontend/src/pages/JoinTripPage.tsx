import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { joinTripByCode, previewTripByCode, type TripInvitePreviewResponse } from "../api/tripApi";
import { useCurrentTrip } from "../context/CurrentTripContext";

// FR-4: Landing page for invite links / QR codes (/join/:code).
export default function JoinTripPage() {
    const { code = "" } = useParams<{ code: string }>();
    // Keyed so a different code starts from a clean state.
    return <JoinTrip key={code} code={code} />;
}

function JoinTrip({ code }: { code: string }) {
    const navigate = useNavigate();
    const { refreshTrips } = useCurrentTrip();

    const [preview, setPreview] = useState<TripInvitePreviewResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [joining, setJoining] = useState(false);

    useEffect(() => {
        let cancelled = false;

        previewTripByCode(code)
            .then((data) => {
                if (!cancelled) setPreview(data);
            })
            .catch((err: any) => {
                if (!cancelled) setError(err.response?.data?.message || "Unable to load this invite.");
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });

        return () => {
            cancelled = true;
        };
    }, [code]);

    const handleJoin = async () => {
        if (!preview || joining) return;
        setJoining(true);
        setError("");

        try {
            const membership = await joinTripByCode(code);
            // Sidebar trip list; a failure here shouldn't block opening the trip.
            await refreshTrips().catch(() => {});
            navigate(`/trip/${membership.tripId}`, { replace: true });
        } catch (err: any) {
            setError(err.response?.data?.message || "Failed to join trip.");
            setJoining(false);
        }
    };

    if (loading) {
        return <p style={{ color: "var(--dashboard-muted)", marginTop: 40 }}>Checking invite code...</p>;
    }

    if (!preview) {
        return (
            <div className="placeholder-panel">
                <h2>Invite code not valid</h2>
                <p>{error}</p>
                <div className="page-actions">
                    <Link to="/dashboard" className="app-button app-button-primary">
                        Back to Dashboard
                    </Link>
                </div>
            </div>
        );
    }

    const completed = preview.status === "COMPLETED";

    return (
        <div className="placeholder-panel">
            <p className="page-eyebrow">You've been invited to join</p>
            <h2>{preview.name}</h2>
            <p>
                {preview.destination}
                <br />
                <span className="placeholder-trip-summary">
                    {preview.startDate} – {preview.endDate} · {preview.memberCount}{" "}
                    {preview.memberCount === 1 ? "member" : "members"}
                </span>
            </p>

            {error && (
                <p role="alert" style={{ color: "#9b1c1c" }}>
                    {error}
                </p>
            )}

            {completed && !preview.alreadyMember && (
                <p>This trip has been completed and can no longer be joined.</p>
            )}

            <div className="page-actions">
                <Link to="/dashboard" className="app-button">
                    Back to Dashboard
                </Link>
                {preview.alreadyMember ? (
                    <Link to={`/trip/${preview.tripId}`} className="app-button app-button-primary">
                        You're a member · Open Trip
                    </Link>
                ) : (
                    !completed && (
                        <button
                            type="button"
                            className="app-button app-button-primary"
                            onClick={handleJoin}
                            disabled={joining}
                        >
                            {joining ? "Joining..." : "Join Trip"}
                        </button>
                    )
                )}
            </div>
        </div>
    );
}
