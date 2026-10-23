import { useEffect, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { fetchInviteCode, regenerateInviteCode } from "../api/tripApi";
import InfoTip from "./InfoTip";

// FR-4: Share a trip by invite code, link or QR instead of emailing each person.
// Render with key={tripId} so switching trips starts from a clean state.
export default function InviteCodePanel({ tripId, isOwner }: { tripId: string; isOwner: boolean }) {
    const [code, setCode] = useState<string | null>(null);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");
    const [resetting, setResetting] = useState(false);

    useEffect(() => {
        let cancelled = false;

        fetchInviteCode(tripId)
            .then((res) => {
                if (!cancelled) setCode(res.inviteCode);
            })
            .catch((err: any) => {
                if (!cancelled) setError(err.response?.data?.message || "Unable to load the invite code.");
            });

        return () => {
            cancelled = true;
        };
    }, [tripId]);

    const joinUrl = code ? `${window.location.origin}/join/${code}` : "";

    const handleCopy = async () => {
        setError("");
        try {
            await navigator.clipboard.writeText(joinUrl);
            setNotice("Invite link copied.");
        } catch {
            setError("Couldn't copy automatically. Select the link below and copy it instead.");
        }
    };

    const handleReset = async () => {
        if (resetting) return;
        if (!window.confirm("Reset the invite code? The current code, link and QR will stop working.")) return;

        setResetting(true);
        setError("");
        setNotice("");
        try {
            const res = await regenerateInviteCode(tripId);
            setCode(res.inviteCode);
            setNotice("Invite code reset. Share the new code with anyone who still needs to join.");
        } catch (err: any) {
            setError(err.response?.data?.message || "Failed to reset the invite code.");
        } finally {
            setResetting(false);
        }
    };

    return (
        <div className="invite-code-panel">
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 14 }}>
                <h4 style={{ fontSize: 15, fontWeight: 700 }}>Share an Invite Code</h4>
                <InfoTip
                    label="Invite code"
                    text="Anyone with this code, link or QR can join the trip straight away. Owners can reset it to stop the old one working."
                />
            </div>

            {!code && !error && <p style={{ fontSize: 13, color: "var(--dashboard-muted)" }}>Loading invite code...</p>}

            {code && (
                <div className="invite-code-body">
                    <div className="invite-code-qr">
                        <QRCodeSVG value={joinUrl} size={132} level="M" marginSize={2} title={`QR code to join this trip (${code})`} />
                    </div>
                    <div style={{ minWidth: 0 }}>
                        <p className="page-eyebrow" style={{ marginBottom: 4 }}>Invite code</p>
                        <p className="invite-code-value">{code}</p>
                        <p className="invite-code-link">{joinUrl}</p>
                        <div className="page-actions" style={{ marginTop: 12 }}>
                            <button type="button" className="app-button" onClick={handleCopy}>
                                Copy Link
                            </button>
                            {isOwner && (
                                <button type="button" className="app-button" onClick={handleReset} disabled={resetting}>
                                    {resetting ? "Resetting..." : "Reset Code"}
                                </button>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {notice && (
                <p role="status" style={{ color: "#03543f", background: "#def7ec", padding: "8px 12px", borderRadius: 6, marginTop: 12, fontSize: 13 }}>
                    {notice}
                </p>
            )}
            {error && (
                <p role="alert" style={{ color: "#9b1c1c", background: "#fdf2f2", padding: "8px 12px", borderRadius: 6, marginTop: 12, fontSize: 13 }}>
                    {error}
                </p>
            )}
        </div>
    );
}
