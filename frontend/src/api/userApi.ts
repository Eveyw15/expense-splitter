import axiosClient from "./axiosClient";
import type { AuthResponse } from "./authApi";

// Matches the backend's UserProfileResponse DTO.
export interface UserProfile {
  userId: number;
  username: string;
  email: string;
  role: string;
}

export function getCurrentUser(): Promise<UserProfile> {
  return axiosClient.get<UserProfile>("/users/me").then((res) => res.data);
}


export function updateProfile(username: string): Promise<UserProfile> {
  return axiosClient
    .put<UserProfile>("/users/me", { username })
    .then((res) => res.data);
}


export function changePassword(
  currentPassword: string,
  newPassword: string
): Promise<void> {
  return axiosClient
    .put("/users/me/password", { currentPassword, newPassword })
    .then(() => undefined);
}


export function changeEmail(newEmail: string): Promise<AuthResponse> {
  return axiosClient
    .put<AuthResponse>("/users/me/email", { newEmail })
    .then((res) => res.data);
}
