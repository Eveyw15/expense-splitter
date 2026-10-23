import { useState, type FormEvent } from "react";
import { useNavigate, Link } from "react-router-dom";
import { createTrip, uploadTripCover, type CreateTripRequest } from "../api/tripApi";
import { useCurrentTrip } from "../context/CurrentTripContext";
import CoverPhotoPicker from "../components/CoverPhotoPicker";
import { imageUploadErrorMessage } from "../utils/prepareImage";
import { useSelectedImage } from "../utils/useSelectedImage";

import CurrencySelect from "../components/CurrencySelect";

export default function CreateTripPage() {
    const navigate = useNavigate();
    const { refreshTrips } = useCurrentTrip();

    const [name, setName] = useState("");
    const [destination, setDestination] = useState("");
    const [startDate, setStartDate] = useState("");
    const [endDate, setEndDate] = useState("");
    const [baseCurrency, setBaseCurrency] = useState("AUD");
    const [plannedBudget, setPlannedBudget] = useState("");

    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    // Optional cover photo, already shrunk; uploaded once the trip exists.
    const [cover, selectCover] = useSelectedImage();
    const [coverPreparing, setCoverPreparing] = useState(false);

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        if (loading || coverPreparing) return;
        setError("");

        // also validate dates before hitting backend
        if (new Date(endDate) < new Date(startDate)) {
            setError("End date cannot be before start date.");
            return;
        }

        setLoading(true);

        try {
            const payload: CreateTripRequest = {
                name: name.trim(),
                destination: destination.trim(),
                startDate,
                endDate,
                plannedBudget: parseFloat(plannedBudget) || 0,
                baseCurrency,
            };

            const created = await createTrip(payload);

            let coverUploadError = "";
            if (cover) {
                try {
                    await uploadTripCover(created.tripId, cover.blob);
                } catch (err: any) {
                    coverUploadError = imageUploadErrorMessage(err, "Please try again.");
                }
            }

            await refreshTrips();

            if (coverUploadError) {
                // The trip exists; take them to it so they can retry the photo there.
                navigate(`/trip/${created.tripId}`, {
                    state: { coverError: `Trip created, but the cover photo couldn't be uploaded: ${coverUploadError} You can add it from Edit Details.` },
                });
            } else {
                // if succesful redirect them back to dashboard to see their new trip
                navigate("/dashboard");
            }
        } catch (err: any) {
            const msg = err.response?.data?.message || "Failed to create trip. Please try again.";
            setError(msg);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{ maxWidth: 640, margin: "0 auto", paddingBottom: 40 }}>
            <header className="dashboard-header" style={{ marginBottom: 24 }}>
                <div>
                    <p className="page-eyebrow">Plan a New Adventure</p>
                    <h1>Create a Trip</h1>
                </div>
            </header>

            {error && (
                <div
                    style={{
                        padding: "12px 16px",
                        marginBottom: 20,
                        borderRadius: 8,
                        background: "#fde8e8",
                        color: "#9b1c1c",
                        fontSize: 14,
                    }}
                >
                    {error}
                </div>
            )}

            <form
                onSubmit={handleSubmit}
                style={{
                    background: "var(--card)",
                    border: "1px solid var(--border)",
                    borderRadius: 12,
                    padding: 28,
                    display: "flex",
                    flexDirection: "column",
                    gap: 20,
                }}
            >
                <div>
                    <span style={{ display: "block", marginBottom: 6, fontWeight: 600, fontSize: 14 }}>
                        Cover Photo (optional)
                    </span>
                    <CoverPhotoPicker
                        photoUrl={cover?.previewUrl ?? null}
                        onPick={selectCover}
                        onRemove={() => selectCover(null)}
                        onPreparingChange={setCoverPreparing}
                    />
                </div>

                <div>
                    <label style={{ display: "block", marginBottom: 6, fontWeight: 600, fontSize: 14 }}>
                        Trip Name *
                    </label>
                    <input
                        type="text"
                        required
                        placeholder="e.g. Summer in Tokyo"
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                        style={{
                            width: "100%",
                            height: 44,
                            padding: "0 14px",
                            borderRadius: 8,
                            border: "1px solid var(--border)",
                            fontSize: 15,
                        }}
                    />
                </div>

                <div>
                    <label style={{ display: "block", marginBottom: 6, fontWeight: 600, fontSize: 14 }}>
                        Destination *
                    </label>
                    <input
                        type="text"
                        required
                        placeholder="e.g. Tokyo, Japan"
                        value={destination}
                        onChange={(e) => setDestination(e.target.value)}
                        style={{
                            width: "100%",
                            height: 44,
                            padding: "0 14px",
                            borderRadius: 8,
                            border: "1px solid var(--border)",
                            fontSize: 15,
                        }}
                    />
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
                    <div>
                        <label style={{ display: "block", marginBottom: 6, fontWeight: 600, fontSize: 14 }}>
                            Start Date *
                        </label>
                        <input
                            type="date"
                            required
                            value={startDate}
                            onChange={(e) => setStartDate(e.target.value)}
                            style={{
                                width: "100%",
                                height: 44,
                                padding: "0 14px",
                                borderRadius: 8,
                                border: "1px solid var(--border)",
                                fontSize: 15,
                            }}
                        />
                    </div>

                    <div>
                        <label style={{ display: "block", marginBottom: 6, fontWeight: 600, fontSize: 14 }}>
                            End Date *
                        </label>
                        <input
                            type="date"
                            required
                            value={endDate}
                            onChange={(e) => setEndDate(e.target.value)}
                            style={{
                                width: "100%",
                                height: 44,
                                padding: "0 14px",
                                borderRadius: 8,
                                border: "1px solid var(--border)",
                                fontSize: 15,
                            }}
                        />
                    </div>
                </div>

                <div>
                    <label style={{ display: "block", marginBottom: 6, fontWeight: 600, fontSize: 14 }}>
                        Planned Budget *
                    </label>
                    <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 120px", gap: 8 }}>
                    <input
                        type="number"
                        min="0"
                        step="10"
                        required
                        aria-label="Planned budget"
                        placeholder="e.g. 3500.00"
                        value={plannedBudget}
                        onChange={(e) => setPlannedBudget(e.target.value)}
                        style={{
                            width: "100%",
                            minWidth: 0,
                            height: 44,
                            padding: "0 14px",
                            borderRadius: 8,
                            border: "1px solid var(--border)",
                            fontSize: 15,
                        }}
                    />
                    <CurrencySelect value={baseCurrency} onChange={setBaseCurrency} label="Trip base currency" />
                    </div>
                </div>

                <div style={{ display: "flex", justifyContent: "flex-end", gap: 12, marginTop: 10 }}>
                    <Link to="/dashboard" className="app-button">
                        Cancel
                    </Link>
                    <button
                        type="submit"
                        disabled={loading || coverPreparing}
                        className="app-button app-button-primary"
                        style={{ minWidth: 140 }}
                    >
                        {loading ? "Creating..." : "Create Trip"}
                    </button>
                </div>
            </form>
        </div>
    );
}