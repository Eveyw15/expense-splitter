import axiosClient from "./axiosClient";

// matches our backend TripResponse DTO
export interface TripResponse {
    tripId: number;
    name: string;
    destination: string;
    startDate: string;
    endDate: string;
    plannedBudget: number;
    baseCurrency?: string;
    status: string;
    votingThreshold: number;
    role: string;
    createdAt: string;
    // Uploaded cover photo; null means show the default illustration. See imageUrl().
    coverImageId: string | null;
}

// matches our backend CreateTripRequest DTO
export interface CreateTripRequest {
    name: string;
    destination: string;
    startDate: string;
    endDate: string;
    plannedBudget: number;
    baseCurrency?: string;
}

export interface UpdateTripRequest {
    name: string;
    destination: string;
    startDate: string;
    endDate: string;
    plannedBudget: number;
}


export interface TripMemberResponse {
    tripMemberId: number;
    tripId: number;
    tripName: string;
    tripDestination: string;
    userId: number;
    username: string;
    email: string;
    role: "OWNER" | "MEMBER";
    inviteStatus: "JOINED" | "PENDING";
    // For PENDING rows this is when the invite was sent.
    joinedAt: string;
}

export interface InviteMemberRequest {
    email: string;
}

export interface UpdateMemberRoleRequest {
    role: "OWNER" | "MEMBER";
}

export interface InviteCodeResponse {
    inviteCode: string;
}

// What someone sees on the join page before joining with a code.
export interface TripInvitePreviewResponse {
    tripId: number;
    name: string;
    destination: string;
    startDate: string;
    endDate: string;
    status: string;
    memberCount: number;
    alreadyMember: boolean;
}


//FR-3

/**
 * Fetch all trips for the currently logged-in user.
 * Calls: GET http://localhost:8080/api/trips
 */
export async function fetchUserTrips(): Promise<TripResponse[]> {
    const res = await axiosClient.get<TripResponse[]>("/trips");
    return res.data;
}

/**
 * Fetch a single trip overview by its ID.
 * Calls: GET http://localhost:8080/api/trips/{tripId}
 */
export async function fetchTripById(tripId: number | string): Promise<TripResponse> {
    const res = await axiosClient.get<TripResponse>(`/trips/${tripId}`);
    return res.data;
}

/**
 * Create a new trip.
 * Calls: POST http://localhost:8080/api/trips
 */
export async function createTrip(data: CreateTripRequest): Promise<TripResponse> {
    const res = await axiosClient.post<TripResponse>("/trips", data);
    return res.data;
}


/**
 * Update an existing trip (Owner only).
 * Calls: PUT http://localhost:8080/api/trips/{tripId}
 */
export async function updateTrip(tripId: number | string, data: UpdateTripRequest): Promise<TripResponse> {
    const res = await axiosClient.put<TripResponse>(`/trips/${tripId}`, data);
    return res.data;
}

/**
 * Publish a trip (Owner only). PLANNING -> ONGOING.
 * Calls: PUT http://localhost:8080/api/trips/{tripId}/publish
 */
export async function publishTrip(tripId: number | string): Promise<TripResponse> {
    const res = await axiosClient.put<TripResponse>(`/trips/${tripId}/publish`);
    return res.data;
}

/**
 * Archive a trip (Owner only). ONGOING -> COMPLETED. Unlocks post-trip reviews.
 * Calls: PUT http://localhost:8080/api/trips/{tripId}/archive
 */
export async function archiveTrip(tripId: number | string): Promise<TripResponse> {
    const res = await axiosClient.put<TripResponse>(`/trips/${tripId}/archive`);
    return res.data;
}

/**
 * Delete an existing trip (Owner only).
 * Calls: DELETE http://localhost:8080/api/trips/{tripId}
 */
export async function deleteTrip(tripId: number | string): Promise<void> {
    await axiosClient.delete(`/trips/${tripId}`);
}

/**
 * Upload or replace the trip's cover photo (Owner only).
 * Calls: POST /api/trips/{tripId}/cover (multipart field "file")
 */
export async function uploadTripCover(tripId: number | string, image: Blob): Promise<TripResponse> {
    const form = new FormData();
    form.append("file", image, "cover.jpg");
    const res = await axiosClient.post<TripResponse>(`/trips/${tripId}/cover`, form);
    return res.data;
}

/**
 * Remove the trip's cover photo (Owner only).
 * Calls: DELETE /api/trips/{tripId}/cover
 */
export async function removeTripCover(tripId: number | string): Promise<TripResponse> {
    const res = await axiosClient.delete<TripResponse>(`/trips/${tripId}/cover`);
    return res.data;
}


//FR4

/**
 * Fetch all members of a trip.
 * Calls: GET /api/trips/{tripId}/members
 */
export async function fetchTripMembers(tripId: number | string): Promise<TripMemberResponse[]> {
    const res = await axiosClient.get<TripMemberResponse[]>(`/trips/${tripId}/members`);
    return res.data;
}

/**
 * Fetch invitations for a trip that haven't been accepted yet.
 * Calls: GET /api/trips/{tripId}/members/pending
 */
export async function fetchPendingTripInvites(tripId: number | string): Promise<TripMemberResponse[]> {
    const res = await axiosClient.get<TripMemberResponse[]>(`/trips/${tripId}/members/pending`);
    return res.data;
}

/**
 * Invite another user to join the trip by email.
 * Calls: POST /api/trips/{tripId}/members/invite
 */
export async function inviteTripMember(tripId: number | string, data: InviteMemberRequest): Promise<TripMemberResponse> {
    const res = await axiosClient.post<TripMemberResponse>(`/trips/${tripId}/members/invite`, data);
    return res.data;
}

/**
 * Update a member's role (OWNER <-> MEMBER).
 * Calls: PUT /api/trips/{tripId}/members/{memberId}/role
 */
export async function updateTripMemberRole(
    tripId: number | string,
    memberId: number | string,
    data: UpdateMemberRoleRequest
): Promise<TripMemberResponse> {
    const res = await axiosClient.put<TripMemberResponse>(`/trips/${tripId}/members/${memberId}/role`, data);
    return res.data;
}

/**
 * Remove a member from the trip or leave the trip.
 * Calls: DELETE /api/trips/{tripId}/members/{memberId}
 */
export async function removeTripMember(tripId: number | string, memberId: number | string): Promise<void> {
    await axiosClient.delete(`/trips/${tripId}/members/${memberId}`);
}

/**
 * Fetch the logged-in user's pending trip invitations.
 * Calls: GET /api/trips/invitations
 */
export async function fetchMyInvitations(): Promise<TripMemberResponse[]> {
    const res = await axiosClient.get<TripMemberResponse[]>("/trips/invitations");
    return res.data;
}

/**
 * Accept or decline a trip invitation.
 * Calls: POST /api/trips/invitations/{memberId}/respond
 */
export async function respondToInvitation(memberId: number | string, action: "ACCEPT" | "REJECT"): Promise<void> {
    await axiosClient.post(`/trips/invitations/${memberId}/respond`, { action });
}

/**
 * Get the trip's shareable invite code (used for the join link and QR).
 * Calls: GET /api/trips/{tripId}/members/invite-code
 */
export async function fetchInviteCode(tripId: number | string): Promise<InviteCodeResponse> {
    const res = await axiosClient.get<InviteCodeResponse>(`/trips/${tripId}/members/invite-code`);
    return res.data;
}

/**
 * Reset the invite code so the old one stops working (Owner only).
 * Calls: POST /api/trips/{tripId}/members/invite-code/regenerate
 */
export async function regenerateInviteCode(tripId: number | string): Promise<InviteCodeResponse> {
    const res = await axiosClient.post<InviteCodeResponse>(`/trips/${tripId}/members/invite-code/regenerate`);
    return res.data;
}

/**
 * Preview a trip from an invite code before joining.
 * Calls: GET /api/trips/join/{inviteCode}
 */
export async function previewTripByCode(inviteCode: string): Promise<TripInvitePreviewResponse> {
    const res = await axiosClient.get<TripInvitePreviewResponse>(`/trips/join/${encodeURIComponent(inviteCode)}`);
    return res.data;
}

/**
 * Join a trip using its invite code.
 * Calls: POST /api/trips/join/{inviteCode}
 */
export async function joinTripByCode(inviteCode: string): Promise<TripMemberResponse> {
    const res = await axiosClient.post<TripMemberResponse>(`/trips/join/${encodeURIComponent(inviteCode)}`);
    return res.data;
}

export interface UpdateHistoryResponse {
    logId: number;
    tripId: number;
    changedById: number;
    changedByUsername: string;
    changeType: string;
    description: string;
    changedAt: string;
}

export async function fetchTripUpdates(tripId: number | string): Promise<UpdateHistoryResponse[]> {
    const res = await axiosClient.get<UpdateHistoryResponse[]>(`/trips/${tripId}/updates`);
    return res.data;
}

export async function updateVotingThreshold(tripId: number | string, votingThreshold: number): Promise<TripResponse> {
    const res = await axiosClient.put<TripResponse>(`/trips/${tripId}/voting-threshold`, { votingThreshold });
    return res.data;
}
