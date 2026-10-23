import { useId, useState, type CSSProperties } from "react";
import { imageUrl } from "../api/imageApi";

type CoverTrip = { tripId: number; name: string; coverImageId?: string | null };

/**
 * FR-3: A trip's picture: its uploaded cover photo, or a generated landscape
 * illustration when there isn't one (or the photo fails to load).
 * Fills its container, so size it with className/style.
 */
export default function TripCover({ trip, className = "", style }: { trip: CoverTrip; className?: string; style?: CSSProperties }) {
    // Remember which photo failed so a newly uploaded one is still tried.
    const [failedImageId, setFailedImageId] = useState<string | null>(null);
    const photoId = trip.coverImageId && trip.coverImageId !== failedImageId ? trip.coverImageId : null;

    return (
        <div className={`trip-cover ${className}`} style={style}>
            {photoId ? (
                <img
                    src={imageUrl(photoId)}
                    alt={`Cover photo for ${trip.name}`}
                    loading="lazy"
                    onError={() => setFailedImageId(photoId)}
                />
            ) : (
                <TripIllustration seed={trip.tripId} />
            )}
        </div>
    );
}

// Sky top, sky bottom, sun, far hills, near hills.
const PALETTES = [
    ["#f3efe6", "#dfe4d9", "#f2c38b", "#b7c4ae", "#8fa08a"], // sage
    ["#f6e3d6", "#e9c3ae", "#fff4e0", "#c99a82", "#a68a7b"], // terracotta
    ["#e6eff2", "#cfe0e6", "#f7d9a6", "#9fbcc6", "#6f97a3"], // coast
    ["#fbeedc", "#f3d9b8", "#e9a46a", "#d9b48f", "#b98a64"], // desert
    ["#e4e3ef", "#c9c6de", "#f6d6b8", "#9e9bbf", "#736f99"], // dusk
    ["#eef2ea", "#d7e2d0", "#f5e1a4", "#95ad8e", "#5f7d5c"], // forest
];

// Same trip always gets the same picture; neighbouring ids look different.
function TripIllustration({ seed }: { seed: number }) {
    const gradientId = useId();
    const hash = Math.imul(seed, 2654435761) >>> 0;
    const [skyTop, skyBottom, sun, far, near] = PALETTES[hash % PALETTES.length];
    const sunX = 60 + ((hash >>> 3) % 280);
    const sunY = 70 + ((hash >>> 9) % 40);
    const farLift = ((hash >>> 13) % 40) - 20;
    const nearLift = ((hash >>> 17) % 30) - 15;

    return (
        <svg viewBox="0 0 400 240" preserveAspectRatio="xMidYMid slice" aria-hidden="true" focusable="false">
            <defs>
                <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0" stopColor={skyTop} />
                    <stop offset="1" stopColor={skyBottom} />
                </linearGradient>
            </defs>
            <rect width="400" height="240" fill={`url(#${gradientId})`} />
            <circle cx={sunX} cy={sunY} r="26" fill={sun} />
            <path
                d={`M0 ${150 + farLift} Q 70 ${105 + farLift} 140 ${140 + farLift} T 280 ${128 - farLift} T 400 ${140 + farLift} V240 H0 Z`}
                fill={far}
            />
            <path
                d={`M0 ${195 - nearLift} Q 110 ${150 + nearLift} 210 ${188 - nearLift} T 400 ${178 + nearLift} V240 H0 Z`}
                fill={near}
            />
        </svg>
    );
}
