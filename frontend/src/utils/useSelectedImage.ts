import { useCallback, useEffect, useState } from "react";

/**
 * Holds an image the user has chosen but not uploaded yet, plus a preview URL
 * for it. The preview URL is released when replaced or on unmount.
 */
export function useSelectedImage() {
    const [selected, setSelected] = useState<{ blob: Blob; previewUrl: string } | null>(null);

    useEffect(() => {
        if (!selected) return;
        return () => URL.revokeObjectURL(selected.previewUrl);
    }, [selected]);

    const select = useCallback((blob: Blob | null) => {
        setSelected(blob ? { blob, previewUrl: URL.createObjectURL(blob) } : null);
    }, []);

    return [selected, select] as const;
}
