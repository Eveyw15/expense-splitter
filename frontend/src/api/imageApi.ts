import axiosClient from "./axiosClient";

/**
 * URL for an uploaded image (e.g. a trip cover).
 * Served publicly at GET /api/images/{imageId} so <img> tags can load it
 * without the JWT; ids are random and only given to people who can see them.
 */
export function imageUrl(imageId: string): string {
    return `${axiosClient.defaults.baseURL}/images/${encodeURIComponent(imageId)}`;
}
