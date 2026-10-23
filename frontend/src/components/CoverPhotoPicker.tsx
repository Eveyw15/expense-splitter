import { useRef, useState, type ChangeEvent } from "react";
import { IMAGE_ACCEPT, imageUploadErrorMessage, prepareImageForUpload } from "../utils/prepareImage";

/**
 * FR-3: Choose a trip cover photo in a form. Shows `photoUrl` (the current or
 * newly chosen photo) with Change/Remove, or a "Choose a Photo" button.
 * The chosen file is shrunk for upload before being passed to `onPick`;
 * the parent decides when to actually upload it (e.g. on Save).
 */
export default function CoverPhotoPicker({
    photoUrl,
    onPick,
    onRemove,
    onPreparingChange,
}: {
    photoUrl: string | null;
    onPick: (image: Blob) => void;
    onRemove: () => void;
    onPreparingChange?: (preparing: boolean) => void;
}) {
    const inputRef = useRef<HTMLInputElement>(null);
    const [preparing, setPreparing] = useState(false);
    const [error, setError] = useState("");

    const setBusy = (busy: boolean) => {
        setPreparing(busy);
        onPreparingChange?.(busy);
    };

    const handleSelected = async (e: ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        e.target.value = ""; // so choosing the same file again still triggers a change
        if (!file) return;

        setBusy(true);
        setError("");
        try {
            onPick(await prepareImageForUpload(file));
        } catch (err: any) {
            setError(imageUploadErrorMessage(err, "That image couldn't be used."));
        } finally {
            setBusy(false);
        }
    };

    const chooseButton = (label: string) => (
        <button type="button" className="app-button" onClick={() => inputRef.current?.click()} disabled={preparing}>
            {preparing ? "Preparing..." : label}
        </button>
    );

    return (
        <div>
            <input ref={inputRef} type="file" accept={IMAGE_ACCEPT} hidden onChange={handleSelected} />
            <div className="cover-picker">
                {photoUrl ? (
                    <>
                        <img src={photoUrl} alt="Cover photo preview" />
                        <div className="trip-cover-actions">
                            {chooseButton("Change")}
                            <button
                                type="button"
                                className="app-button"
                                onClick={() => {
                                    setError("");
                                    onRemove();
                                }}
                                disabled={preparing}
                            >
                                Remove
                            </button>
                        </div>
                    </>
                ) : (
                    <div className="cover-picker-empty">{chooseButton("Choose a Photo")}</div>
                )}
            </div>
            <p style={{ marginTop: 6, fontSize: 12, color: "var(--dashboard-muted)" }}>
                JPEG, PNG or WebP. Without one, the trip gets a generated picture.
            </p>
            {error && (
                <p role="alert" style={{ marginTop: 6, fontSize: 13, color: "#9b1c1c" }}>
                    {error}
                </p>
            )}
        </div>
    );
}
