import { useEffect, useState, type FormEvent } from "react";
import { useAuth } from "../context/AuthContext";
import * as userApi from "../api/userApi";
import { fetchUserTrips } from "../api/tripApi";
import "../styles/profile.css";

const ERROR_COLOR = "var(--rejected)";
const FALLBACK_ERROR = "Something went wrong. Please try again.";

function errorMessage(err: unknown): string {
  const response = (err as { response?: { data?: { message?: string } } })?.response;
  return response?.data?.message || FALLBACK_ERROR;
}

function initialOf(label: string) {
  return (label.trim().charAt(0) || "?").toUpperCase();
}

function Avatar({ label, size = 28 }: { label: string; size?: number }) {
  return (
    <span
      className="profile-page__avatar"
      style={{ width: size, height: size, background: "var(--accent)", fontSize: size * 0.42 }}
    >
      {initialOf(label)}
    </span>
  );
}

function Button({
  children,
  variant = "primary",
  className = "",
  ...props
}: {
  variant?: "primary" | "ghost" | "outline";
} & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button className={`profile-page__button profile-page__button--${variant} ${className}`} {...props}>
      {children}
    </button>
  );
}

function SectionTitle({ children, right }: { children: React.ReactNode; right?: React.ReactNode }) {
  return (
    <div className="profile-page__section-heading">
      <h2>{children}</h2>
      {right}
    </div>
  );
}

function Stat({ label, value, tone }: { label: string; value: React.ReactNode; tone?: "accent" }) {
  const color = tone === "accent" ? "var(--accent)" : "var(--foreground)";
  return (
    <div className={`profile-page__stat${tone === "accent" ? " profile-page__stat--accent" : ""}`}>
      <span className="profile-page__stat-label">{label}</span>
      <span className="profile-page__stat-value" style={{ color }}>
        {value}
      </span>
    </div>
  );
}

// Small text input styled to match the rest of the page, shared by the
// username/email inline-edit forms below.
function EditInput(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      {...props}
      className="profile-page__input"
    />
  );
}

export default function ProfilePage() {
  const { user, logout, updateUser, refreshSession } = useAuth();
  const username = user?.username?.trim() || "User";
  const email = user?.email || "—";

  // --- Trips ---
  const [tripCount, setTripCount] = useState(0);

  useEffect(() => {
    if (!user) return;
    fetchUserTrips().then((trips) => setTripCount(trips.length)).catch(() => setTripCount(0));
  }, [user]);

  // --- Username ---
  const [editingName, setEditingName] = useState(false);
  const [nameDraft, setNameDraft] = useState(username);
  const [nameSaving, setNameSaving] = useState(false);
  const [nameError, setNameError] = useState("");

  function startEditName() {
    setNameDraft(username);
    setNameError("");
    setEditingName(true);
  }

  function cancelEditName() {
    setEditingName(false);
    setNameError("");
  }

  async function handleSaveName(e: FormEvent) {
    e.preventDefault();
    if (!nameDraft.trim()) {
      setNameError("Username is required");
      return;
    }
    setNameSaving(true);
    setNameError("");
    try {
      const profile = await userApi.updateProfile(nameDraft.trim());
      updateUser(profile);
      setEditingName(false);
    } catch (err) {
      setNameError(errorMessage(err));
    } finally {
      setNameSaving(false);
    }
  }

  // --- Email ---
  const [editingEmail, setEditingEmail] = useState(false);
  const [emailDraft, setEmailDraft] = useState(email);
  const [emailSaving, setEmailSaving] = useState(false);
  const [emailError, setEmailError] = useState("");

  function startEditEmail() {
    setEmailDraft(email);
    setEmailError("");
    setEditingEmail(true);
  }

  function cancelEditEmail() {
    setEditingEmail(false);
    setEmailError("");
  }

  async function handleSaveEmail(e: FormEvent) {
    e.preventDefault();
    setEmailSaving(true);
    setEmailError("");
    try {
      const authResponse = await userApi.changeEmail(emailDraft.trim());
      refreshSession(authResponse);
      setEditingEmail(false);
    } catch (err) {
      setEmailError(errorMessage(err));
    } finally {
      setEmailSaving(false);
    }
  }

  // --- Password ---
  const [editingPassword, setEditingPassword] = useState(false);
  const [currentPasswordDraft, setCurrentPasswordDraft] = useState("");
  const [newPasswordDraft, setNewPasswordDraft] = useState("");
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [passwordError, setPasswordError] = useState("");

  function startEditPassword() {
    setCurrentPasswordDraft("");
    setNewPasswordDraft("");
    setPasswordError("");
    setEditingPassword(true);
  }

  function cancelEditPassword() {
    setEditingPassword(false);
    setPasswordError("");
  }

  async function handleSavePassword(e: FormEvent) {
    e.preventDefault();
    setPasswordSaving(true);
    setPasswordError("");
    try {
      await userApi.changePassword(currentPasswordDraft, newPasswordDraft);
      logout();
    } catch (err) {
      setPasswordError(errorMessage(err));
      setPasswordSaving(false);
    }
  }

  return (
    <div className="profile-page">
      <header className="profile-page__header">
        <Avatar label={username} size={80} />
        <div className="profile-page__identity">
          <p className="profile-page__eyebrow">Your profile</p>
          {editingName ? (
            <form onSubmit={handleSaveName} className="profile-page__edit-form">
              <EditInput
                aria-label="Username"
                value={nameDraft}
                onChange={(e) => setNameDraft(e.target.value)}
                disabled={nameSaving}
                autoFocus
              />
              <Button type="submit" variant="outline" disabled={nameSaving}>
                {nameSaving ? "Saving…" : "Save"}
              </Button>
              <button
                type="button"
                className="profile-page__text-button"
                onClick={cancelEditName}
              >
                Cancel
              </button>
            </form>
          ) : (
            <div className="profile-page__name">
              <h1>{username}</h1>
              <button type="button" className="profile-page__edit-button" onClick={startEditName}>
                edit ✎
              </button>
            </div>
          )}
          {nameError && (
            <p className="profile-page__error" role="alert" style={{ color: ERROR_COLOR }}>
              {nameError}
            </p>
          )}
        </div>
        <Button variant="ghost" onClick={logout}>
          Log out
        </Button>
      </header>

      <div className="profile-page__stats">
        <Stat label="Trips" value={tripCount} />
      </div>

      <div className="profile-page__columns">
        <section className="profile-page__account" aria-label="Account details">
          <SectionTitle>Account details</SectionTitle>
          <div className="profile-page__account-fields">
            {/* Email */}
            <div className="profile-page__field profile-page__email-row">
              <div className="profile-page__field-content">
                <p className="profile-page__label">Email</p>
                {editingEmail ? (
                  <form onSubmit={handleSaveEmail} className="profile-page__edit-form">
                    <EditInput
                      type="email"
                      aria-label="Email"
                      value={emailDraft}
                      onChange={(e) => setEmailDraft(e.target.value)}
                      disabled={emailSaving}
                      autoFocus
                    />
                    <Button type="submit" variant="outline" disabled={emailSaving}>
                      {emailSaving ? "Saving…" : "Save"}
                    </Button>
                    <button
                      type="button"
                      className="profile-page__text-button"
                      onClick={cancelEditEmail}
                    >
                      Cancel
                    </button>
                  </form>
                ) : (
                  <p className="profile-page__value">{email}</p>
                )}
                {emailError && (
                  <p className="profile-page__error" role="alert" style={{ color: ERROR_COLOR }}>
                    {emailError}
                  </p>
                )}
              </div>
              {!editingEmail && (
                <button type="button" className="profile-page__edit-button" onClick={startEditEmail}>
                  Edit ✎
                </button>
              )}
            </div>

            {/* Password */}
            <div className="profile-page__field">
              <div className="profile-page__password-heading">
                <div>
                  <p className="profile-page__label">Password</p>
                  {!editingPassword && <p className="profile-page__value profile-page__password-mask">••••••••</p>}
                </div>
                {!editingPassword && (
                  <button type="button" className="profile-page__edit-button" onClick={startEditPassword}>
                    Edit ✎
                  </button>
                )}
              </div>
              {editingPassword && (
                <form onSubmit={handleSavePassword} className="profile-page__password-form">
                  <EditInput
                    type="password"
                    aria-label="Current password"
                    placeholder="Current password"
                    value={currentPasswordDraft}
                    onChange={(e) => setCurrentPasswordDraft(e.target.value)}
                    disabled={passwordSaving}
                    autoFocus
                  />
                  <EditInput
                    type="password"
                    aria-label="New password"
                    placeholder="New password"
                    value={newPasswordDraft}
                    onChange={(e) => setNewPasswordDraft(e.target.value)}
                    disabled={passwordSaving}
                  />
                  <div className="profile-page__form-actions">
                    <Button type="submit" variant="outline" disabled={passwordSaving}>
                      {passwordSaving ? "Saving…" : "Save"}
                    </Button>
                    <button
                      type="button"
                      className="profile-page__text-button"
                      onClick={cancelEditPassword}
                    >
                      Cancel
                    </button>
                  </div>
                </form>
              )}
              {passwordError && (
                <p className="profile-page__error" role="alert" style={{ color: ERROR_COLOR }}>
                  {passwordError}
                </p>
              )}
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
