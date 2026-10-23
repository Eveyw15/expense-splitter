import { useState, type FormEvent } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import logoUrl from "@/imports/logo.png";
import { useAuth } from "@/context/AuthContext";

type AuthMode = "login" | "signup";
interface AuthProps { initialMode: AuthMode; }

const JKS = "'Plus Jakarta Sans', system-ui, sans-serif";

// ─── Brand palette ────────────────────────────────────────────────────────────
const NAVY = "#14306e";
const NAVY_SOFT = "#4a5c86";
const YELLOW = "#f7b81c";
const OFFWHITE = "#fbf8f1";

// ─── Feature icons ────────────────────────────────────────────────────────────
function VoteIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 10.5V16a1 1 0 0 0 1 1h1.5a1 1 0 0 0 1-1v-5.5a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1Z" />
      <path d="M6.5 10.5l2.6-5.9a1.8 1.8 0 0 1 3.4.9V8h3.1a1.6 1.6 0 0 1 1.57 1.96l-1.1 5A1.6 1.6 0 0 1 14.5 16.2H6.5" />
    </svg>
  );
}
function SplitIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="10" cy="10" r="7.2" />
      <path d="M10 6v8M8 8.2c0-.9.9-1.4 2-1.4s2 .4 2 1.3c0 1.9-4 1-4 2.9 0 .9.9 1.4 2 1.4s2-.5 2-1.4" />
    </svg>
  );
}

// ─── Eye icons ────────────────────────────────────────────────────────────────
const EyeOn = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
    <circle cx="12" cy="12" r="3" />
  </svg>
);
const EyeOff = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
    <line x1="1" y1="1" x2="23" y2="23" />
  </svg>
);
const Spinner = () => (
  <svg className="animate-spin" width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden>
    <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="3" opacity="0.25" />
    <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
  </svg>
);

const features = [
  { Icon: VoteIcon, label: "Vote on plans" },
  { Icon: SplitIcon, label: "Split expenses" },
];

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// ─── Field ───────────────────────────────────────────────────────────────────
interface FieldProps {
  id: string;
  label: string;
  error?: string;
  children: React.ReactNode;
  helper?: string;
}
function Field({ id, label, error, children, helper }: FieldProps) {
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={id} className="text-[13.5px] font-semibold" style={{ color: NAVY, fontFamily: JKS }}>
        {label}
      </label>
      {children}
      {helper && !error && (
        <p className="text-[12.5px]" style={{ color: NAVY_SOFT, fontFamily: JKS }}>{helper}</p>
      )}
      {error && (
        <p id={`${id}-error`} className="text-[12.5px] font-medium" style={{ color: "#d0463b", fontFamily: JKS }}>
          {error}
        </p>
      )}
    </div>
  );
}

// ─── Component ────────────────────────────────────────────────────────────────
export default function Auth({ initialMode }: AuthProps) {
  const [mode, setMode] = useState<AuthMode>(initialMode);
  const [showPw, setShowPw] = useState(false);
  const [loading, setLoading] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPw] = useState("");
  const [username, setUser] = useState("");
  const [errors, setErrors] = useState<{ username?: string; email?: string; password?: string; form?: string }>({});

  const { login, register } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  // Set by ProtectedRoute when a logged-out user opened a protected page (e.g. an invite link).
  const redirectTo = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname || "/dashboard";

  const switchMode = (m: AuthMode) => {
    setMode(m);
    setShowPw(false);
    setErrors({});
    // Keep the redirect when switching between login and sign-up.
    navigate(m === "login" ? "/login" : "/register", { state: location.state });
  };

  const inputStyle = (hasError?: boolean): React.CSSProperties => ({
    fontFamily: JKS,
    borderColor: hasError ? "#e0938c" : "#dcd7cb",
  });
  const inputCls =
    "h-[50px] w-full rounded-xl border bg-white px-4 text-[14.5px] text-[#14306e] placeholder:text-[#9a9488] outline-none transition-[border-color,box-shadow] duration-150 focus:border-[#f7b81c] focus:ring-2 focus:ring-[#f7b81c]/40 disabled:cursor-not-allowed disabled:bg-[#f4f2ec] disabled:text-[#9a9488]";

  // The only real integration point: build a request, call the backend
  // through AuthContext, and translate its response into UI state.
  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const next: typeof errors = {};
    if (mode === "signup" && !username.trim()) next.username = "Username is required";
    if (!EMAIL_RE.test(email.trim())) next.email = "Enter a valid email address";
    if (password.length < 8 || password.length > 72) next.password = "Password must be between 8 and 72 characters";

    setErrors(next);
    if (Object.keys(next).length > 0) return;

    setLoading(true);
    try {
      if (mode === "login") {
        await login(email.trim(), password);
      } else {
        await register(username.trim(), email.trim(), password);
      }
      navigate(redirectTo, { replace: true });
    } catch (err: any) {
      const message: string = err.response?.data?.message || "Something went wrong. Please try again.";
      // FR-1: duplicate email on register surfaces on the email field;
      // everything else (bad credentials, validation) shows as a form-level banner.
      if (mode === "signup" && message.toLowerCase().includes("email")) {
        setErrors({ email: message });
      } else {
        setErrors({ form: message });
      }
    } finally {
      setLoading(false);
    }
  };

  const isLogin = mode === "login";

  return (
    <div className="grid min-h-screen lg:grid-cols-[1fr_1fr]" style={{ background: OFFWHITE, fontFamily: JKS }}>

      {/* ═══ LEFT — brand panel ═══ */}
      <div className="relative hidden flex-col justify-between overflow-hidden px-14 py-16 lg:flex xl:px-20">
        {/* subtle abstract travel shape */}
        <svg className="pointer-events-none absolute -bottom-24 -right-24 h-[440px] w-[440px]" viewBox="0 0 400 400" fill="none" aria-hidden>
          <circle cx="200" cy="200" r="170" fill={YELLOW} opacity="0.12" />
          <path d="M60 300 Q 180 120 340 200" stroke={NAVY} strokeWidth="2" strokeDasharray="3 12" strokeLinecap="round" opacity="0.18" />
        </svg>

        <img src={logoUrl} alt="G’Day Trip" className="h-11 w-auto self-start" />

        <div className="relative max-w-[440px]">
          <h1 className="text-[46px] font-extrabold leading-[1.05] tracking-[-0.03em]" style={{ color: NAVY }}>
            Plan together.<br />Travel better.
          </h1>

          <ul className="mt-10 flex flex-col gap-4">
            {features.map(({ Icon, label }) => (
              <li key={label} className="flex items-center gap-3.5">
                <span
                  className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-xl"
                  style={{ background: YELLOW, color: NAVY }}
                >
                  <Icon />
                </span>
                <span className="text-[17px] font-bold" style={{ color: NAVY }}>{label}</span>
              </li>
            ))}
          </ul>
        </div>

        <div />
      </div>

      {/* ═══ RIGHT — form panel ═══ */}
      <div className="flex min-h-screen flex-col items-center justify-center px-6 py-12 sm:px-10">
        <div className="w-full max-w-[460px]">

          {/* Mobile-only logo + tagline */}
          <div className="mb-10 flex flex-col items-center lg:hidden">
            <img src={logoUrl} alt="G’Day Trip" className="h-9 w-auto" />
            <p className="mt-3 text-[14px] font-semibold" style={{ color: NAVY_SOFT }}>
              Plan together. Travel better.
            </p>
          </div>

          {/* Heading */}
          <div className="mb-8">
            <h2 className="text-[30px] font-extrabold tracking-[-0.02em]" style={{ color: NAVY }}>
              {isLogin ? "Welcome back" : "Start planning together"}
            </h2>
            <p className="mt-2 text-[15px]" style={{ color: NAVY_SOFT }}>
              {isLogin
                ? "Log in to continue planning with your group."
                : "Create your G’Day Trip account."}
            </p>
          </div>

          <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-5">
            {/* Username (signup only) */}
            {!isLogin && (
              <Field id="username" label="Username" error={errors.username}>
                <input
                  id="username"
                  className={inputCls}
                  style={inputStyle(!!errors.username)}
                  type="text"
                  placeholder="Choose a username"
                  value={username}
                  onChange={(e) => { setUser(e.target.value); setErrors((p) => ({ ...p, username: undefined })); }}
                  disabled={loading}
                  autoComplete="username"
                  autoFocus
                  aria-invalid={!!errors.username}
                  aria-describedby={errors.username ? "username-error" : undefined}
                />
              </Field>
            )}

            {/* Email */}
            <Field id="email" label="Email" error={errors.email}>
              <input
                id="email"
                className={inputCls}
                style={inputStyle(!!errors.email)}
                type="email"
                placeholder="name@example.com"
                value={email}
                onChange={(e) => { setEmail(e.target.value); setErrors((p) => ({ ...p, email: undefined })); }}
                disabled={loading}
                autoComplete="email"
                autoFocus={isLogin}
                aria-invalid={!!errors.email}
                aria-describedby={errors.email ? "email-error" : undefined}
              />
            </Field>

            {/* Password */}
            <Field
              id="password"
              label="Password"
              error={errors.password}
              helper={isLogin ? undefined : "8–72 characters"}
            >
              <div className="relative">
                <input
                  id="password"
                  className={inputCls + " pr-12"}
                  style={inputStyle(!!errors.password)}
                  type={showPw ? "text" : "password"}
                  placeholder={isLogin ? "Enter your password" : "Create a password"}
                  value={password}
                  onChange={(e) => { setPw(e.target.value); setErrors((p) => ({ ...p, password: undefined })); }}
                  disabled={loading}
                  autoComplete={isLogin ? "current-password" : "new-password"}
                  aria-invalid={!!errors.password}
                  aria-describedby={errors.password ? "password-error" : undefined}
                />
                <button
                  type="button"
                  onClick={() => setShowPw((v) => !v)}
                  className="absolute right-3 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-lg text-[#7d8199] transition-colors hover:text-[#14306e] disabled:opacity-40"
                  disabled={loading}
                  aria-label={showPw ? "Hide password" : "Show password"}
                >
                  {showPw ? <EyeOff /> : <EyeOn />}
                </button>
              </div>
            </Field>

            {/* Form-level error */}
            {errors.form && (
              <div className="rounded-xl border border-[#e9bab4] bg-[#fdf1ef] px-4 py-3 text-[13.5px] font-medium" style={{ color: "#c23d33" }}>
                {errors.form}
              </div>
            )}

            {/* Submit */}
            <button
              type="submit"
              disabled={loading}
              className="mt-1 flex h-[52px] w-full items-center justify-center gap-2 rounded-xl text-[15.5px] font-bold tracking-[-0.01em] transition-[transform,filter] hover:brightness-[0.97] active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-70"
              style={{ background: YELLOW, color: NAVY }}
            >
              {loading ? <Spinner /> : null}
              {loading
                ? (isLogin ? "Logging in…" : "Creating account…")
                : (isLogin ? "Log in" : "Create account")}
            </button>
          </form>

          {/* Footer switch */}
          <p className="mt-8 text-center text-[14px]" style={{ color: NAVY_SOFT }}>
            {isLogin ? "New to G’Day Trip? " : "Already have an account? "}
            <button
              onClick={() => switchMode(isLogin ? "signup" : "login")}
              className="font-bold underline decoration-2 underline-offset-2 transition-colors hover:opacity-80"
              style={{ color: NAVY }}
            >
              {isLogin ? "Create an account" : "Log in"}
            </button>
          </p>
        </div>
      </div>
    </div>
  );
}
