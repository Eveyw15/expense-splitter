import axiosClient from "./axiosClient";

// Matches the backend's AuthResponse DTO.
export interface AuthResponse {
  token: string;
  userId: number;
  username: string;
  email: string;
  role: string;
}

export function login(email: string, password: string): Promise<AuthResponse> {
  return axiosClient
    .post<AuthResponse>("/auth/login", { email, password })
    .then((res) => res.data);
}

export function register(
  username: string,
  email: string,
  password: string
): Promise<AuthResponse> {
  return axiosClient
    .post<AuthResponse>("/auth/register", { username, email, password })
    .then((res) => res.data);
}
