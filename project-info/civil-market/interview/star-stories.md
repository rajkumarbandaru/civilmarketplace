# STAR Stories — Real Bugs, Real Decisions

Every story here is from this codebase, with the file that proves it. Keep the Result short and
concrete; that is the half interviewers remember.

---

## 1. Every booking creation returned a 500

**Situation.** Booking creation failed for every user with a 500. Nothing in the API contract had
changed, and the code looked correct.

**Task.** Find why a straightforward insert was failing, and fix it without breaking the lookup API
that other services used.

**Action.** The logs pointed at a data-truncation error rather than a validation failure. The
generator emits `BK-` plus a 14-digit timestamp plus a dash and four digits — 22 characters — but
`booking_code` was `VARCHAR(20)`. Two options: shorten the code, or widen the column. The code
format was already a public key used by a lookup-by-code endpoint, so shortening it would have
broken callers and invalidated existing references. I widened the column in a **new Flyway
migration** (`V2`) and matched `@Column(length)` on the entity, leaving the applied `V1` untouched.

**Result.** Booking creation worked again — the feature had been entirely broken. The wider lesson I
mention: the entity and the migration are two declarations of one truth, and only the database
enforces it.

---

## 2. auth-service would not start at all

**Situation.** The whole stack was up except auth-service, which exited during startup. With no
auth service, nobody could log in, so nothing else could be tested either.

**Task.** Get the service starting, without removing social login as a future capability.

**Action.** `SecurityConfig` called `.oauth2Login()` unconditionally, but Spring only creates a
`ClientRegistrationRepository` when `spring.security.oauth2.client.registration.*` is configured —
and it never was, because Google and Facebook credentials did not exist in this environment. The lazy
fix would have been deleting the call. Instead I injected
`ObjectProvider<ClientRegistrationRepository>` and applied `oauth2Login()` only when a registration
is actually present, logging which branch was taken at startup.

**Result.** The service starts with password and OTP login regardless, and **social login switches
itself on automatically** the moment real credentials are supplied — no code change, just
`AUTH_PROFILE=docker,social`. I verified the full path afterwards: login → JWT → gateway-injected
`X-User-*` headers → downstream service.

---

## 3. Every failed payment turned into a 500

**Situation.** On a machine with placeholder Razorpay keys, *no payment could be created at all*.
The caller got a 500 and never learned that a payment row existed.

**Task.** Make failure behave like failure — a proper error and a persisted FAILED payment — instead
of an opaque server error.

**Action.** `createPaymentOrder` handled the PSP failure correctly, setting the payment to FAILED,
and then died on the way out: it built its `payment.created` event with `Map.of(...)`, which throws
`NullPointerException` on a null value, and `razorpayOrderId` is null exactly when the Razorpay call
failed. So the only path that could produce a null was the one that always hit it. I replaced it
with a `HashMap` that tolerates the null and also carries the payment status.

**Result.** Failed payments now return a proper error with the payment recorded, and the
`payment.created` event carries the status so consumers can distinguish the two. The lesson worth
saying out loud: `Map.of` is null-hostile, and error paths are where nulls live — so error paths
need tests as much as happy paths.

---

## 4. A repository method that was a time bomb

**Situation.** While adding escrow, I noticed `findByBookingId` returned `Optional<Payment>`.

**Task.** Decide whether it was still safe under the new model, before it failed in production.

**Action.** It was not. Escrow means one booking can carry several payments — one per milestone hold
— so the Optional query would throw `IncorrectResultSizeDataAccessException` as soon as a second
payment existed. I replaced it with `findFirstByBookingIdOrderByCreatedAtDesc` plus a status-scoped
variant, and made escrow funding use a dedicated `createEscrowFundingOrder` that never reuses a
booking's pending payment — because sharing one payment row between holds would fund several holds
off a single capture.

**Result.** A latent production failure removed before it fired, and a money bug — several holds
funded by one payment — prevented. This is my example of the cardinality assumption behind a query
mattering more than the query.

---

## 5. A bare 403 on the login screen of a new tenant

**Situation.** A newly onboarded tenant could not log in. The browser showed a 403 with an empty
body, on the login call, before anyone was authenticated — so it looked like a permissions problem
in the application.

**Task.** Find the real cause of a badly disguised failure.

**Action.** It was CORS at the gateway. Each tenant is served on its own hostname, so
`acme.localhost:3000` and `hostelfee.localhost:3000` are distinct browser origins, and the allow-list
could not enumerate them without a redeploy per tenant. The fix was `setAllowedOriginPatterns` with
wildcard patterns instead of `setAllowedOrigins` — a wildcard in the latter is rejected outright
when `allowCredentials` is true. I also kept both `localhost` and `127.0.0.1` forms, because they are
distinct origins and `127.0.0.1` takes no subdomains.

**Result.** Any new tenant subdomain works with no gateway change. The story lands well because the
symptom ("you do not have permission") pointed at the wrong layer entirely.

---

## 6. Multi-tenancy that survived contact with a browser

**Situation.** The gateway resolves a tenant from the `Host` header. The React bundle was calling
the gateway directly at `http://localhost:8080`.

**Task.** Make tenant resolution work in a browser, not just in curl.

**Action.** A direct call sends `Host: localhost` for every tenant, so all of them collapse onto
whichever fallback tenant was configured. I made the bundle call its own origin and let the
frontend's nginx proxy to the gateway with `proxy_set_header Host $http_host` — keeping the port, not
just the hostname, so the request URL matches the browser's `Origin` exactly and Spring treats these
as same-origin, skipping the CORS check entirely.

**Result.** `acme.localhost:3000` reaches the gateway as `acme` and `hostelfee.localhost:3000` as
`hostelfee`. One nginx directive is the difference between real multi-tenancy and every tenant
seeing one tenant's data.

---

## 7. A deploy that users never saw

**Situation.** After a frontend deploy, browsers kept running the previous version — with no error
anywhere.

**Task.** Explain why, and fix it permanently.

**Action.** `index.html` was being cached. It is the only file with a stable URL, and it names the
content-hashed bundles; a browser holding yesterday's copy keeps requesting yesterday's chunks,
which still exist on disk, so nothing 404s and the app simply stays old — indefinitely and
invisibly. I set `no-cache, must-revalidate` on `index.html` and kept `/assets/` at one year
`immutable`, which is safe precisely because those filenames change on every build.

**Result.** Deploys land on the next page load, at the cost of one small revalidation request. I
also found the old rule was caching `/static/`, a directory Vite has never produced — so the
long-term caching had never worked at all.

---

## 8. Making the platform actually re-themeable

**Situation.** Admins could change the platform's colours through the UI-config API, but several
pages did not change.

**Task.** Find out why re-theming was partial.

**Action.** Those pages had the shipped violet written in as a literal (`#667eea`) or as
`color: 'primary.main'` in places that bypassed the theme, so they were immune to it. I replaced the
literals with reads from the MUI theme (`theme.palette.primary.main`), so the same components
re-colour with the tenant.

**Result.** Theme changes now apply everywhere, and the rule is explicit in the codebase: a
component never writes a brand colour, it reads one.

---

## How to tell these

- **Lead with the impact**, not the stack trace: "every booking failed" beats "there was a column
  width issue".
- **Say what you chose not to do** — shortening the booking code, deleting the `oauth2Login` call.
  Rejected options are what make it sound like judgement rather than luck.
- **End with the generalisation**, one sentence: null-hostile `Map.of` on error paths, cardinality
  assumptions in queries, `index.html` as the only unhashed URL.
