import axios from "axios";

// Points at the Spring Boot backend (see trip-planner-backend) on port 8080 of
// whichever host served this page: localhost on your laptop, or your laptop's
// LAN IP when a phone opens an invite QR link. Set VITE_API_URL to override.
const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? (typeof window === "undefined"
    ? "http://localhost:8080/api"
    : `${window.location.protocol}//${window.location.hostname}:8080/api`),
  // Long enough for a free-tier backend to wake from sleep (~1 min).
  timeout: 90_000,
});

// Attach the JWT to every outgoing request, if we have one stored.
axiosClient.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Centralized 401 handling: if an *already-logged-in* request comes back
// unauthenticated (missing/expired token), clear the stale session and
// send the user back to the login page.
axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const isAuthEndpoint = error.config?.url?.includes("/auth/");
    if (error.response?.status === 401 && !isAuthEndpoint) {
      localStorage.removeItem("token");
      window.location.href = "/login";
    }
    return Promise.reject(error);
  }
);

export default axiosClient;
