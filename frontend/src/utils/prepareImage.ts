const ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];
const MAX_INPUT_BYTES = 25 * 1024 * 1024;

// For <input type="file" accept={IMAGE_ACCEPT}>
export const IMAGE_ACCEPT = ACCEPTED_TYPES.join(",");

/**
 * Shrink a chosen photo to at most `maxSize` px on its longest side and
 * re-encode it as JPEG before upload. Keeps uploads well under the server's
 * 5 MB limit and drops photo metadata such as GPS location.
 */
export async function prepareImageForUpload(file: File, maxSize = 1600): Promise<Blob> {
    if (!ACCEPTED_TYPES.includes(file.type)) {
        throw new Error("Please choose a JPEG, PNG or WebP image.");
    }
    if (file.size > MAX_INPUT_BYTES) {
        throw new Error("That image is too large. Please choose one under 25 MB.");
    }

    let bitmap: ImageBitmap;
    try {
        // Applies the photo's EXIF rotation, so phone pictures aren't sideways.
        bitmap = await createImageBitmap(file);
    } catch {
        throw new Error("That image couldn't be read. Please try a different file.");
    }

    const scale = Math.min(1, maxSize / Math.max(bitmap.width, bitmap.height));
    const width = Math.max(1, Math.round(bitmap.width * scale));
    const height = Math.max(1, Math.round(bitmap.height * scale));

    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext("2d");
    if (!context) {
        bitmap.close();
        throw new Error("Your browser couldn't process this image.");
    }

    // JPEG has no transparency; paint white so transparent PNG areas aren't black.
    context.fillStyle = "#ffffff";
    context.fillRect(0, 0, width, height);
    context.drawImage(bitmap, 0, 0, width, height);
    bitmap.close();

    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.85));
    if (!blob) {
        throw new Error("Your browser couldn't process this image.");
    }
    return blob;
}

// Server message if there is one, our own validation message otherwise.
export function imageUploadErrorMessage(err: any, fallback: string): string {
    return err?.response?.data?.message || (err?.isAxiosError ? fallback : err?.message) || fallback;
}
