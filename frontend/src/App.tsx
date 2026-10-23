import { BrowserRouter, Routes, Route, Navigate, Outlet } from "react-router-dom";
import { AuthProvider, useAuth } from "./context/AuthContext";
import { CurrentTripProvider } from "./context/CurrentTripContext";
import ProtectedRoute from "./components/ProtectedRoute";
import Auth from "./pages/Auth";
import DashboardPage from "./pages/DashboardPage";
import AppLayout from "./components/AppLayout";
import ProfilePage from "./pages/ProfilePage";
import CreateTripPage from "./pages/CreateTripPage";
import TripOverviewPage from "./pages/TripOverviewPage";
import ExpensesPage from "./pages/ExpensesPage";
import SettingsPage from "./pages/SettingsPage";
import JoinTripPage from "./pages/JoinTripPage";

function AppRoutes() {
  const { user } = useAuth();

  return (
    <Routes>
      <Route path="/login" element={<Auth initialMode="login" />} />
      <Route path="/register" element={<Auth initialMode="signup" />} />
      <Route
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/trip/new" element={<CreateTripPage />} />
        <Route path="/join/:code" element={<JoinTripPage />} />
        <Route path="/trip/:tripId" element={<Outlet />}>
          <Route index element={<TripOverviewPage />} />
          <Route path="expenses" element={<ExpensesPage />} />
        </Route>
        <Route path="/expenses" element={<ExpensesPage />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to={user ? "/dashboard" : "/login"} replace />} />
    </Routes>
  );
}

function App() {
  return (
    <AuthProvider>
      <CurrentTripProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </CurrentTripProvider>
    </AuthProvider>
  );
}

export default App;
