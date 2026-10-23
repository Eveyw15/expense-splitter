import { money } from "../utils/currency";
import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { useParams, Link, useNavigate, useLocation } from "react-router-dom";
import {
    fetchTripById,
    updateTrip,
    deleteTrip,
    fetchTripMembers,
    fetchPendingTripInvites,
    inviteTripMember,
    updateTripMemberRole,
    removeTripMember,
    uploadTripCover,
    removeTripCover,
    type TripResponse,
    type UpdateTripRequest,
    type TripMemberResponse
} from "../api/tripApi";
import { useAuth } from "../context/AuthContext";
import { useCurrentTrip } from "../context/CurrentTripContext";
import InviteCodePanel from "../components/InviteCodePanel";
import TripCover from "../components/TripCover";
import CoverPhotoPicker from "../components/CoverPhotoPicker";
import InfoTip from "../components/InfoTip";
import { imageUrl } from "../api/imageApi";
import { imageUploadErrorMessage } from "../utils/prepareImage";
import { useSelectedImage } from "../utils/useSelectedImage";

// "2026-09-09" -> local date (new Date("2026-09-09") is UTC and can show the previous day).
function parseLocalDate(value: string) {
    const [year, month, day] = value.split("-").map(Number);
    return new Date(year, month - 1, day);
}

// e.g. "9 Sep – 11 Oct 2026", or "28 Dec 2026 – 3 Jan 2027" across years.
function formatDateRange(start: string, end: string) {
    const startDate = parseLocalDate(start);
    const endDate = parseLocalDate(end);
    const full: Intl.DateTimeFormatOptions = { day: "numeric", month: "short", year: "numeric" };
    const sameYear = startDate.getFullYear() === endDate.getFullYear();
    const startText = startDate.toLocaleDateString("en-AU", sameYear ? { day: "numeric", month: "short" } : full);
    return `${startText} – ${endDate.toLocaleDateString("en-AU", full)}`;
}

function StatCard({ label, info, children }: { label: string; info: string; children: ReactNode }) {
    return (
        <div className="trip-stat-card">
            <div className="trip-stat-label">
                <span className="page-eyebrow">{label}</span>
                <InfoTip label={label} text={info} />
            </div>
            <p className="trip-stat-value">{children}</p>
        </div>
    );
}

export default function TripOverviewPage() {
    const { tripId } = useParams<{ tripId: string }>();
    const navigate = useNavigate();
    const location = useLocation();
    const { user: currentUser } = useAuth();
    const { refreshTrips } = useCurrentTrip();

    const [trip, setTrip] = useState<TripResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    const [isEditing, setIsEditing] = useState(false);
    const [editName, setEditName] = useState("");
    const [editDest, setEditDest] = useState("");
    const [editStart, setEditStart] = useState("");
    const [editEnd, setEditEnd] = useState("");
    const [editBudget, setEditBudget] = useState("");
    const [saving, setSaving] = useState(false);
    const [editError, setEditError] = useState("");

    const [members, setMembers] = useState<TripMemberResponse[]>([]);
    const [pendingInvites, setPendingInvites] = useState<TripMemberResponse[]>([]);
    const [membersLoading, setMembersLoading] = useState(false);
    const [inviteEmail, setInviteEmail] = useState("");
    const [inviting, setInviting] = useState(false);
    const [inviteError, setInviteError] = useState("");
    const [inviteSuccess, setInviteSuccess] = useState("");
    const [memberActionError, setMemberActionError] = useState("");

    // Cover photo changes made in Edit Details; applied on Save, discarded on Cancel.
    const [newCover, selectNewCover] = useSelectedImage();
    const [removeCoverOnSave, setRemoveCoverOnSave] = useState(false);
    const [coverPreparing, setCoverPreparing] = useState(false);
    // CreateTripPage sends a message here if the trip was created but its cover upload failed.
    const [coverError, setCoverError] = useState(
        () => (location.state as { coverError?: string } | null)?.coverError ?? ""
    );

    useEffect(() => {
        async function loadData() {
            if (!tripId) return;
            try {
                setLoading(true);
                const tripData = await fetchTripById(tripId);
                setTrip(tripData);

                setMembersLoading(true);
                const [membersData, pendingData] = await Promise.all([
                    fetchTripMembers(tripId),
                    // Pending invites are supplementary; don't fail the whole page over them.
                    fetchPendingTripInvites(tripId).catch(() => [] as TripMemberResponse[]),
                ]);
                setMembers(membersData);
                setPendingInvites(pendingData);
            } catch (err: any) {
                setError(err.response?.data?.message || "Trip not found or access denied.");
            } finally {
                setLoading(false);
                setMembersLoading(false);
            }
        }

        loadData();
    }, [tripId]);

    const handleStartEdit = () => {
        if (!trip) return;
        setEditName(trip.name);
        setEditDest(trip.destination);
        setEditStart(trip.startDate);
        setEditEnd(trip.endDate);
        setEditBudget(trip.plannedBudget.toString());
        setEditError("");
        selectNewCover(null);
        setRemoveCoverOnSave(false);
        setIsEditing(true);
    };

    // Closes Edit Details and drops any unsaved cover photo change.
    const closeEditor = () => {
        selectNewCover(null);
        setRemoveCoverOnSave(false);
        setIsEditing(false);
    };

    const handleSaveEdit = async (e: FormEvent) => {
        e.preventDefault();
        if (saving || coverPreparing || !tripId) return;

        if (new Date(editEnd) < new Date(editStart)) {
            setEditError("End date cannot be before start date.");
            return;
        }

        setSaving(true);
        setEditError("");

        try {
            const payload: UpdateTripRequest = {
                name: editName.trim(),
                destination: editDest.trim(),
                startDate: editStart,
                endDate: editEnd,
                plannedBudget: parseFloat(editBudget) || 0,
            };

            let updated = await updateTrip(tripId, payload);
            setTrip(updated);

            try {
                if (newCover) {
                    updated = await uploadTripCover(tripId, newCover.blob);
                } else if (removeCoverOnSave && updated.coverImageId) {
                    updated = await removeTripCover(tripId);
                }
            } catch (err: any) {
                // Details are saved; keep the form open so the photo can be retried.
                setEditError(
                    `Trip details saved, but the cover photo couldn't be updated: ${imageUploadErrorMessage(err, "Please try again.")}`
                );
                return;
            }

            setTrip(updated);
            setCoverError("");
            closeEditor();
        } catch (err: any) {
            setEditError(err.response?.data?.message || "Failed to update trip.");
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async () => {
        if (!tripId) return;
        const confirmed = window.confirm("Are you sure you want to delete this trip? All member associations will be removed.");
        if (!confirmed) return;

        try {
            await deleteTrip(tripId);
            await refreshTrips();
            navigate("/dashboard");
        } catch (err: any) {
            alert(err.response?.data?.message || "Failed to delete trip.");
        }
    };

    const handleInvite = async (e: FormEvent) => {
        e.preventDefault();
        if (!tripId || !inviteEmail.trim() || inviting) return;

        setInviting(true);
        setInviteError("");
        setInviteSuccess("");
        setMemberActionError("");

        try {
            const invite = await inviteTripMember(tripId, { email: inviteEmail.trim() });
            setPendingInvites((prev) => [...prev, invite]);
            setInviteSuccess(`Invitation sent to ${invite.username} (${invite.email}). They'll join once they accept.`);
            setInviteEmail("");
        } catch (err: any) {
            setInviteError(err.response?.data?.message || "Failed to invite member.");
        } finally {
            setInviting(false);
        }
    };

    const handleToggleRole = async (targetMember: TripMemberResponse) => {
        if (!tripId) return;
        setMemberActionError("");

        const newRole = targetMember.role === "OWNER" ? "MEMBER" : "OWNER";
        const confirmMsg = newRole === "OWNER"
            ? `Promote ${targetMember.username} to Trip Owner?`
            : `Demote ${targetMember.username} to regular Member?`;

        if (!window.confirm(confirmMsg)) return;

        try {
            const updated = await updateTripMemberRole(tripId, targetMember.tripMemberId, { role: newRole });
            setMembers((prev) =>
                prev.map((m) => (m.tripMemberId === targetMember.tripMemberId ? updated : m))
            );

            if (currentUser && targetMember.userId === currentUser.userId && trip) {
                setTrip({ ...trip, role: newRole });
            }
        } catch (err: any) {
            setMemberActionError(err.response?.data?.message || "Failed to update member role.");
        }
    };

    const handleRemoveMember = async (targetMember: TripMemberResponse) => {
        if (!tripId) return;
        setMemberActionError("");

        const isSelf = currentUser && targetMember.userId === currentUser.userId;
        const confirmMsg = isSelf
            ? "Are you sure you want to leave this trip?"
            : `Are you sure you want to remove ${targetMember.username} from this trip?`;

        if (!window.confirm(confirmMsg)) return;

        try {
            await removeTripMember(tripId, targetMember.tripMemberId);

            if (isSelf) {
                navigate("/dashboard");
            } else {
                setMembers((prev) => prev.filter((m) => m.tripMemberId !== targetMember.tripMemberId));
            }
        } catch (err: any) {
            setMemberActionError(err.response?.data?.message || "Failed to remove member.");
        }
    };

    const handleCancelInvite = async (invite: TripMemberResponse) => {
        if (!tripId) return;
        setMemberActionError("");

        if (!window.confirm(`Cancel the invitation to ${invite.username}?`)) return;

        try {
            await removeTripMember(tripId, invite.tripMemberId);
            setPendingInvites((prev) => prev.filter((i) => i.tripMemberId !== invite.tripMemberId));
        } catch (err: any) {
            setMemberActionError(err.response?.data?.message || "Failed to cancel invitation.");
        }
    };

    const getTripProgress = () => {
        if (!trip) return "";
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        const start = new Date(trip.startDate);
        const end = new Date(trip.endDate);

        const diffDays = Math.ceil((start.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
        const tripLength = Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1;

        if (diffDays > 0) {
            return `Starts in ${diffDays} day${diffDays === 1 ? "" : "s"} · ${tripLength} days duration`;
        } else if (today <= end) {
            const currentDay = Math.ceil((today.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1;
            return `Ongoing · Day ${currentDay} of ${tripLength}`;
        } else {
            return `Trip completed · ${tripLength} days total`;
        }
    };

    if (loading) {
        return <p style={{ color: "var(--dashboard-muted)", marginTop: 40 }}>Loading trip overview...</p>;
    }

    if (error || !trip) {
        return (
            <div className="placeholder-panel" style={{ marginTop: 40 }}>
                <h2>Unable to view trip</h2>
                <p>{error || "This trip does not exist."}</p>
                <div className="page-actions" style={{ justifyContent: "center", marginTop: 20 }}>
                    <Link to="/dashboard" className="app-button app-button-primary">
                        Back to Dashboard
                    </Link>
                </div>
            </div>
        );
    }

    const isOwner = trip.role === "OWNER";
    const tripLengthDays =
        Math.round((parseLocalDate(trip.endDate).getTime() - parseLocalDate(trip.startDate).getTime()) / 86_400_000) + 1;

    return (
        <div style={{ maxWidth: 960, margin: "0 auto", paddingBottom: 60 }}>
            {/* Cover photo is changed from Edit Details. */}
            <div className="trip-cover-banner">
                <TripCover trip={trip} />
            </div>

            {coverError && (
                <p role="alert" style={{ color: "#9b1c1c", background: "#fdf2f2", padding: "8px 12px", borderRadius: 6, marginBottom: 16, fontSize: 13 }}>
                    {coverError}
                </p>
            )}

            <header className="dashboard-header" style={{ marginBottom: 20 }}>
                <div>
                    <p className="page-eyebrow">{trip.destination} · {trip.status}</p>
                    <h1>{trip.name}</h1>
                    <p style={{ color: "var(--sage)", fontWeight: 600, fontSize: 14, marginTop: 4 }}>
                        {getTripProgress()}
                    </p>
                </div>
                <div className="page-actions">
                    <Link to="/dashboard" className="app-button">
                        Back to Trips
                    </Link>
                    {isOwner && (
                        <>
                            <button type="button" className="app-button" onClick={handleStartEdit}>
                                Edit Details
                            </button>
                            <button type="button" className="app-button" onClick={handleDelete} style={{ color: "#9b1c1c" }}>
                                Delete
                            </button>
                        </>
                    )}
                </div>
            </header>

            {isEditing && (
                <div style={{ background: "var(--card)", border: "2px solid var(--accent)", borderRadius: 12, padding: 24, marginBottom: 28 }}>
                    <h3 style={{ fontSize: 18, fontWeight: 700, marginBottom: 16 }}>Edit Trip Details</h3>
                    {editError && <p style={{ color: "#9b1c1c", marginBottom: 14, fontSize: 14 }}>{editError}</p>}
                    <form onSubmit={handleSaveEdit} style={{ display: "flex", flexDirection: "column", gap: 16 }}>
                        <div>
                            <span style={{ display: "block", fontSize: 13, fontWeight: 600, marginBottom: 4 }}>Cover Photo</span>
                            <CoverPhotoPicker
                                photoUrl={
                                    newCover?.previewUrl ??
                                    (!removeCoverOnSave && trip.coverImageId ? imageUrl(trip.coverImageId) : null)
                                }
                                onPick={(image) => {
                                    selectNewCover(image);
                                    setRemoveCoverOnSave(false);
                                }}
                                onRemove={() => {
                                    selectNewCover(null);
                                    setRemoveCoverOnSave(true);
                                }}
                                onPreparingChange={setCoverPreparing}
                            />
                        </div>

                        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
                            <div>
                                <label style={{ display: "block", fontSize: 13, fontWeight: 600, marginBottom: 4 }}>Trip Name</label>
                                <input
                                    type="text"
                                    required
                                    value={editName}
                                    onChange={(e) => setEditName(e.target.value)}
                                    style={{ width: "100%", height: 40, padding: "0 10px", borderRadius: 6, border: "1px solid var(--border)" }}
                                />
                            </div>
                            <div>
                                <label style={{ display: "block", fontSize: 13, fontWeight: 600, marginBottom: 4 }}>Destination</label>
                                <input
                                    type="text"
                                    required
                                    value={editDest}
                                    onChange={(e) => setEditDest(e.target.value)}
                                    style={{ width: "100%", height: 40, padding: "0 10px", borderRadius: 6, border: "1px solid var(--border)" }}
                                />
                            </div>
                        </div>

                        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
                            <div>
                                <label style={{ display: "block", fontSize: 13, fontWeight: 600, marginBottom: 4 }}>Start Date</label>
                                <input
                                    type="date"
                                    required
                                    value={editStart}
                                    onChange={(e) => setEditStart(e.target.value)}
                                    style={{ width: "100%", height: 40, padding: "0 10px", borderRadius: 6, border: "1px solid var(--border)" }}
                                />
                            </div>
                            <div>
                                <label style={{ display: "block", fontSize: 13, fontWeight: 600, marginBottom: 4 }}>End Date</label>
                                <input
                                    type="date"
                                    required
                                    value={editEnd}
                                    onChange={(e) => setEditEnd(e.target.value)}
                                    style={{ width: "100%", height: 40, padding: "0 10px", borderRadius: 6, border: "1px solid var(--border)" }}
                                />
                            </div>
                        </div>

                        <div>
                            <label style={{ display: "block", fontSize: 13, fontWeight: 600, marginBottom: 4 }}>Planned Budget ({trip.baseCurrency ?? "AUD"})</label>
                            <input
                                type="number"
                                min="0"
                                step="0.01"
                                required
                                value={editBudget}
                                onChange={(e) => setEditBudget(e.target.value)}
                                style={{ width: "100%", height: 40, padding: "0 10px", borderRadius: 6, border: "1px solid var(--border)" }}
                            />
                        </div>

                        <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 8 }}>
                            <button type="button" className="app-button" onClick={closeEditor}>Cancel</button>
                            <button type="submit" disabled={saving || coverPreparing} className="app-button app-button-primary">
                                {saving ? "Saving..." : "Save Changes"}
                            </button>
                        </div>
                    </form>
                </div>
            )}

            {/* Trip Stats */}
            <div className="trip-stats">
                <StatCard
                    label="Trip Dates"
                    info={`${tripLengthDays}-day trip · Status: ${trip.status.charAt(0) + trip.status.slice(1).toLowerCase()}`}
                >
                    {formatDateRange(trip.startDate, trip.endDate)}
                </StatCard>

                <StatCard label="Planned Budget" info="The shared group budget set by the trip owner.">
                    <span style={{ color: "var(--accent)" }}>{money(trip.plannedBudget, trip.baseCurrency ?? "AUD")}</span>
                </StatCard>

                <StatCard label="Travelers" info="People who have joined this trip. Pending invitations aren't counted.">
                    {members.length} {members.length === 1 ? "Member" : "Members"}
                </StatCard>

                <StatCard
                    label="Your Role"
                    info={
                        isOwner
                            ? "Full management access: edit trip details, manage members, or delete the trip."
                            : "You can add and split expenses. Owners manage trip settings and members."
                    }
                >
                    {isOwner ? "Owner" : "Collaborator"}
                </StatCard>
            </div>

            <div style={{ marginTop: 32, background: "var(--card)", border: "1px solid var(--border)", borderRadius: 12, padding: 28 }}>
                <div style={{ marginBottom: 20, display: "flex", alignItems: "center", gap: 8 }}>
                    <h2 style={{ fontSize: 20, fontWeight: 700 }}>Trip Members ({members.length})</h2>
                    <InfoTip label="Trip Members" text="Invite people by email or with the invite code, and manage roles." />
                </div>

                {memberActionError && (
                    <div style={{ padding: "10px 14px", background: "#fdf2f2", border: "1px solid #f98080", borderRadius: 8, color: "#9b1c1c", fontSize: 14, marginBottom: 16 }}>
                        {memberActionError}
                    </div>
                )}

                <div style={{ background: "var(--muted)", borderRadius: 10, padding: 18, marginBottom: 24, border: "1px solid var(--border)" }}>
                    <h4 style={{ fontSize: 15, fontWeight: 700, marginBottom: 8 }}>Invite a Friend by Email</h4>
                    <form onSubmit={handleInvite} style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
                        <input
                            type="email"
                            required
                            placeholder="Enter friend's registered email (e.g. friend@example.com)"
                            value={inviteEmail}
                            onChange={(e) => {
                                setInviteEmail(e.target.value);
                                setInviteError("");
                                setInviteSuccess("");
                            }}
                            style={{ flex: "1 1 200px", minWidth: 0, height: 42, padding: "0 12px", borderRadius: 6, border: "1px solid var(--border)", background: "white" }}
                        />
                        <button
                            type="submit"
                            disabled={inviting || !inviteEmail.trim()}
                            className="app-button app-button-primary"
                            style={{ height: 42, padding: "0 20px" }}
                        >
                            {inviting ? "Inviting..." : "+ Invite Member"}
                        </button>
                    </form>

                    {inviteSuccess && (
                        <p style={{ color: "#03543f", background: "#def7ec", padding: "8px 12px", borderRadius: 6, marginTop: 10, fontSize: 13 }}>
                            {inviteSuccess}
                        </p>
                    )}
                    {inviteError && (
                        <p style={{ color: "#9b1c1c", background: "#fdf2f2", padding: "8px 12px", borderRadius: 6, marginTop: 10, fontSize: 13 }}>
                            {inviteError}
                        </p>
                    )}
                </div>

                {tripId && trip.status !== "COMPLETED" && (
                    <InviteCodePanel key={tripId} tripId={tripId} isOwner={isOwner} />
                )}

                {membersLoading ? (
                    <p style={{ color: "var(--dashboard-muted)", fontSize: 14 }}>Loading members...</p>
                ) : (
                    <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                        {members.map((member) => {
                            const isSelf = currentUser && member.userId === currentUser.userId;
                            const memberIsOwner = member.role === "OWNER";

                            return (
                                <div
                                    key={member.tripMemberId}
                                    style={{
                                        display: "flex",
                                        alignItems: "center",
                                        justifyContent: "space-between",
                                        padding: "14px 18px",
                                        borderRadius: 8,
                                        border: "1px solid var(--border)",
                                        background: "white",
                                        gap: 16,
                                        flexWrap: "wrap"
                                    }}
                                >
                                    <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
                                        <button
                                            type="button"
                                            style={{
                                                width: 44,
                                                height: 44,
                                                borderRadius: "50%",
                                                background: memberIsOwner ? "var(--accent)" : "var(--sage)",
                                                color: "white",
                                                display: "flex",
                                                alignItems: "center",
                                                justifyContent: "center",
                                                fontWeight: 700,
                                                fontSize: 18,
                                                textTransform: "uppercase",
                                                border: "none",
                                                cursor: "pointer"
                                            }}
                                        >
                                            {member.username ? member.username[0] : "?"}
                                        </button>

                                        <div>
                                            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                                                <span style={{ fontWeight: 700, fontSize: 15 }}>{member.username}</span>
                                                {isSelf && (
                                                    <span style={{ fontSize: 11, background: "var(--muted)", padding: "2px 6px", borderRadius: 4, color: "var(--dashboard-muted)" }}>
                                                        You
                                                    </span>
                                                )}
                                                <span
                                                    style={{
                                                        fontSize: 11,
                                                        fontWeight: 700,
                                                        padding: "2px 8px",
                                                        borderRadius: 999,
                                                        background: memberIsOwner ? "#fef3c7" : "#e5e7eb",
                                                        color: memberIsOwner ? "#92400e" : "#374151"
                                                    }}
                                                >
                                                    {memberIsOwner ? "OWNER" : "MEMBER"}
                                                </span>
                                            </div>
                                            <p style={{ fontSize: 13, color: "var(--dashboard-muted)", marginTop: 2 }}>
                                                {member.email} · Joined {new Date(member.joinedAt).toLocaleDateString()}
                                            </p>
                                        </div>
                                    </div>

                                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                                        {isOwner && (
                                            <button
                                                type="button"
                                                className="app-button"
                                                onClick={() => handleToggleRole(member)}
                                                style={{ fontSize: 12, padding: "6px 12px" }}
                                                title={memberIsOwner ? "Demote to Member" : "Promote to Owner"}
                                            >
                                                {memberIsOwner ? "Demote to Member" : "Promote to Owner"}
                                            </button>
                                        )}

                                        {(isOwner || isSelf) && (
                                            <button
                                                type="button"
                                                className="app-button"
                                                onClick={() => handleRemoveMember(member)}
                                                style={{
                                                    fontSize: 12,
                                                    padding: "6px 12px",
                                                    color: "#9b1c1c",
                                                    borderColor: "#f98080"
                                                }}
                                            >
                                                {isSelf ? "Leave Trip" : "Remove"}
                                            </button>
                                        )}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}

                {!membersLoading && pendingInvites.length > 0 && (
                    <div style={{ marginTop: 24 }}>
                        <h4 style={{ fontSize: 15, fontWeight: 700, marginBottom: 10 }}>
                            Pending Invitations ({pendingInvites.length})
                        </h4>
                        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                            {pendingInvites.map((invite) => (
                                <div
                                    key={invite.tripMemberId}
                                    style={{
                                        display: "flex",
                                        alignItems: "center",
                                        justifyContent: "space-between",
                                        padding: "14px 18px",
                                        borderRadius: 8,
                                        border: "1px dashed var(--border)",
                                        background: "var(--muted)",
                                        gap: 16,
                                        flexWrap: "wrap"
                                    }}
                                >
                                    <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
                                        <div
                                            aria-hidden="true"
                                            style={{
                                                width: 44,
                                                height: 44,
                                                borderRadius: "50%",
                                                border: "2px dashed var(--dashboard-muted)",
                                                color: "var(--dashboard-muted)",
                                                display: "flex",
                                                alignItems: "center",
                                                justifyContent: "center",
                                                fontWeight: 700,
                                                fontSize: 18,
                                                textTransform: "uppercase"
                                            }}
                                        >
                                            {invite.username ? invite.username[0] : "?"}
                                        </div>

                                        <div>
                                            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                                                <span style={{ fontWeight: 700, fontSize: 15 }}>{invite.username}</span>
                                                <span
                                                    style={{
                                                        fontSize: 11,
                                                        fontWeight: 700,
                                                        padding: "2px 8px",
                                                        borderRadius: 999,
                                                        background: "#e0f2fe",
                                                        color: "#075985"
                                                    }}
                                                >
                                                    PENDING
                                                </span>
                                            </div>
                                            <p style={{ fontSize: 13, color: "var(--dashboard-muted)", marginTop: 2 }}>
                                                {invite.email} · Invited {new Date(invite.joinedAt).toLocaleDateString()}
                                            </p>
                                        </div>
                                    </div>

                                    {isOwner && (
                                        <button
                                            type="button"
                                            className="app-button"
                                            onClick={() => handleCancelInvite(invite)}
                                            style={{
                                                fontSize: 12,
                                                padding: "6px 12px",
                                                color: "#9b1c1c",
                                                borderColor: "#f98080"
                                            }}
                                        >
                                            Cancel Invite
                                        </button>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}
