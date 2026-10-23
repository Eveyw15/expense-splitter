import { useId, useState } from "react";

const BUBBLE_WIDTH = 240;
const EDGE_GAP = 16;

/**
 * Small "i" button that reveals a short explanation on hover, keyboard focus
 * or tap, so helper text doesn't have to sit on the page permanently.
 */
export default function InfoTip({ label, text }: { label: string; text: string }) {
    const tipId = useId();
    const [placement, setPlacement] = useState({ alignRight: false, maxWidth: BUBBLE_WIDTH });

    // Open towards whichever side of the screen has room, so the bubble is never cut off.
    const place = (anchor: HTMLElement) => {
        const rect = anchor.getBoundingClientRect();
        const roomRight = document.documentElement.clientWidth - rect.left;
        const roomLeft = rect.right;
        const alignRight = roomRight < BUBBLE_WIDTH + EDGE_GAP && roomLeft > roomRight;
        const room = (alignRight ? roomLeft : roomRight) - EDGE_GAP;
        setPlacement({ alignRight, maxWidth: Math.max(140, Math.min(BUBBLE_WIDTH, room)) });
    };

    return (
        <span className={`info-tip${placement.alignRight ? " info-tip-right" : ""}`} onMouseEnter={(e) => place(e.currentTarget)}>
            <button
                type="button"
                className="info-tip-button"
                aria-label={`About ${label}`}
                aria-describedby={tipId}
                onFocus={(e) => place(e.currentTarget)}
                onKeyDown={(e) => {
                    if (e.key === "Escape") e.currentTarget.blur();
                }}
            >
                i
            </button>
            <span role="tooltip" id={tipId} className="info-tip-bubble" style={{ maxWidth: placement.maxWidth }}>
                {text}
            </span>
        </span>
    );
}
