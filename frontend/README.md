# React + TypeScript + Vite

## G’Day Trip application scaffold

Login and registration retain their existing `/dashboard` redirect. All application
routes share `AppLayout` inside the existing `ProtectedRoute`; the authentication
context and API clients are unchanged.

| Route | Page |
| --- | --- |
| `/dashboard` | Dashboard with ongoing, upcoming and past mock trips |
| `/plan` | Planning placeholder |
| `/trip` | Trips placeholder |
| `/trip/new` | Add Trip placeholder |
| `/trip/:tripId` | Mock trip detail placeholder, or trip-not-found state |
| `/expenses` | Expenses placeholder |
| `/profile` | Profile placeholder and existing Log out action |

`src/data/mockTrips.ts` owns the demo trip members, dates, statuses and trips.
The welcome message and sidebar use `AuthContext.user.username`, with `User` as
the fallback for an unavailable or blank username; no additional request is made. Statuses
are deliberately fixed to match the reference. `TripCard` is shared by the two
dashboard sections. Calendar export currently displays a coming-soon message;
trip creation, planning and expenses do not persist data or call trip APIs.

The layout uses the existing theme variables and fonts. Desktop shows two trip
columns; narrower screens show one. On mobile the persistent sidebar becomes an
icon rail with accessible navigation labels. Keyboard focus and reduced-motion
preferences are supported.

The settings icon is the inline [Feather settings SVG](https://github.com/feathericons/feather/blob/main/icons/settings.svg),
under the MIT license (see `public/licenses/feather.txt`). No icon package is required.

### Verification

Run `npm run build` and `npm run lint`. With the existing frontend and backend
running at their documented ports, check:

1. While logged out, open each application route directly: it redirects to login.
2. Log in: the existing flow arrives at `/dashboard` with three mock trip cards.
3. Visit Plan, Trip and Expenses: the sidebar persists and the active link changes.
4. Use Add Trip, each trip card and the profile link; return with Back to Dashboard.
5. Reload a protected route with a valid session, and use browser Back/Forward.
6. Export Calendar displays its placeholder notice. Profile → Log out returns to
   login, and protected routes remain inaccessible after reloading.
7. Check at 1440px and 390px widths: no horizontal overflow; all controls are
   keyboard reachable and traveler names are available to assistive technology.

Trip photos are bundled locally in `public/images/trips` from Unsplash:
[Sydney](https://unsplash.com/photos/a-city-skyline-across-the-water-SF0z_n8eOtc),
[Brisbane](https://unsplash.com/photos/city-skyline-under-clear-blue-sky-during-daytime-RJII3O7CTeo),
and [Shanghai](https://unsplash.com/photos/uKyzXEc2k_s).

This template provides a minimal setup to get React working in Vite with HMR and some Oxlint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the Oxlint configuration

If you are developing a production application, we recommend enabling type-aware lint rules by installing `oxlint-tsgolint` and editing `.oxlintrc.json`:

```json
{
  "$schema": "./node_modules/oxlint/configuration_schema.json",
  "plugins": ["react", "typescript", "oxc"],
  "options": {
    "typeAware": true
  },
  "rules": {
    "react/rules-of-hooks": "error",
    "react/only-export-components": ["warn", { "allowConstantExport": true }]
  }
}
```

See the [Oxlint rules documentation](https://oxc.rs/docs/guide/usage/linter/rules) for the full list of rules and categories.
