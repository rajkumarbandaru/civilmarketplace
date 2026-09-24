import html, json

OUT = '/home/aryagami/RAJKUMAR/project-info/civil-market/overview/action-flows.html'

P = {  # alias: (display name, layer)
    'U': ('User', 'user'), 'UI': ('React UI', 'ui'), 'NX': ('Frontend nginx :3000', 'edge'),
    'GW': ('API Gateway :8080', 'gw'), 'EU': ('Eureka', 'plat'),
    'AUTH': ('auth-service', 'svc'), 'USER': ('user-service', 'svc'), 'BOOK': ('booking-service', 'svc'),
    'PAY': ('payment-service', 'svc'), 'NOTI': ('notification-service', 'svc'), 'ADM': ('admin-service', 'svc'),
    'REV': ('review-service', 'svc'), 'SUP': ('support-service', 'svc'), 'AUD': ('audit-service', 'svc'),
    'TEN': ('tenant-service', 'svc'), 'MSG': ('messaging-service', 'svc'), 'PROJ': ('project-service', 'svc'),
    'SRCH': ('search-service', 'svc'), 'ALL': ('every tenanted service', 'svc'),
    'DB': ('MySQL', 'data'), 'RD': ('Redis', 'data'), 'ES': ('Elasticsearch', 'data'),
    'KF': ('Kafka', 'bus'), 'RZP': ('Razorpay', 'ext'), 'GEM': ('Google Gemini', 'ext'),
    'GOO': ('Google OAuth', 'ext'), 'MAIL': ('Email/SMS provider', 'ext'), 'SS': ('sessionStorage', 'ui'),
}

def gw(path, svc, public=False, method='GET'):
    """Common edge path: UI -> nginx -> gateway -> service."""
    check = ('Tenant from Host, public route (no JWT)' if public else
             'Tenant from Host, verify JWT + tenant claim, add X-User-* headers')
    return [('UI', 'NX', f'{method} /api/v1{path}'),
            ('NX', 'GW', 'proxy, Host header kept'),
            ('GW', 'GW', check),
            ('GW', svc, f'lb://{P[svc][0]} (via Eureka)')]

A = []
def act(group, id, title, who, page, trigger, steps, result, errors=(), notes=()):
    A.append(dict(group=group, id=id, title=title, who=who, page=page, trigger=trigger,
                  steps=steps, result=result, errors=list(errors), notes=list(notes)))

# ---------------- Visitor ----------------
act('Visitor (not logged in)', 'landing', 'Open the landing page', 'Anyone',
    '/  (pages/HomePage.tsx)', 'Browser opens http://localhost:3000 or http://<tenant>.localhost:3000',
    [('U', 'NX', 'GET /  (index.html + hashed /assets/*.js)'),
     ('NX', 'UI', 'React boots: main.tsx, providers, router'),
     *gw('/tenant-resolution/...', 'TEN', True),
     ('TEN', 'UI', 'tenant name + branding'),
     *gw('/content/site', 'ADM', True),
     ('ADM', 'DB', 'read site sections / items / media (admin_db_<tenant>)'),
     ('ADM', 'UI', 'hero text, footer, images'),
     *gw('/catalogue', 'BOOK', True),
     ('BOOK', 'DB', 'active categories + services'),
     ('BOOK', 'UI', 'service catalogue')],
    'Landing page renders with this tenant\'s branding, editable copy and service list, no login needed.',
    ['Blank sections: admin-service or booking-service is down, or not yet in Eureka (gateway 503).'])

act('Visitor (not logged in)', 'browse', 'Browse services by category', 'Anyone',
    '/services, /services/:category', 'Click "Services" or a category card',
    [*gw('/catalogue', 'BOOK', True), ('BOOK', 'DB', 'categories + service offerings'),
     ('BOOK', 'UI', 'list'), ('UI', 'UI', 'filter by :category on the client, show cards with price')],
    'Category page lists services. "Book" on a card goes to /book/:serviceId (asks to log in first).')

act('Visitor (not logged in)', 'register', 'Register a new account', 'New customer / worker / supplier',
    '/register  (pages/auth/RegisterPage.tsx)', 'Fill form and submit',
    [*gw('/geo/countries', 'USER', True),
     ('USER', 'UI', 'countries, then /geo/countries/{code}/states and /cities for the address pickers'),
     ('U', 'UI', 'submit form (validated with React Hook Form + Yup)'),
     ('UI', 'UI', 'authSlice.register thunk'),
     *gw('/auth/register', 'AUTH', True, 'POST'),
     ('AUTH', 'DB', 'check email unique, BCrypt password, INSERT user (civil_engineer_auth_<tenant>)'),
     ('AUTH', 'KF', 'publish user.registered'),
     ('AUTH', 'UI', 'tokens / success'),
     ('KF', 'NOTI', 'consume user.registered'),
     ('NOTI', 'MAIL', 'welcome email (or just a log line when EMAIL_PROVIDER=log)')],
    'Account created in the current tenant. The same email can exist separately in another tenant.',
    ['400 email already registered', '400 validation error (GlobalExceptionHandler)'])

act('Visitor (not logged in)', 'login', 'Log in with email + password', 'Any user',
    '/login  (pages/auth/LoginPage.tsx)', 'Submit email + password',
    [('U', 'UI', 'submit'), ('UI', 'UI', 'authSlice.login thunk'),
     *gw('/auth/login', 'AUTH', True, 'POST'),
     ('AUTH', 'DB', 'load user in tenant schema, check password + lockout counter'),
     ('AUTH', 'AUTH', 'sign access JWT (sub, email, role, name, tenant) + refresh token with JWT_SECRET'),
     ('AUTH', 'UI', '200 accessToken + refreshToken + user'),
     ('UI', 'SS', 'save tokens (sessionStorage = per tab, so two accounts can be open in two tabs)'),
     *gw('/ui-config/me', 'ADM'),
     ('ADM', 'UI', 'effective theme + menu for this role/workspace'),
     ('UI', 'U', 'redirect: admins to /admin, others to /dashboard')],
    'User is signed in. Every later request carries Authorization: Bearer <accessToken>.',
    ['401 wrong password (no refresh attempted on /auth/* endpoints)', 'Account locked after repeated failures',
     '403 empty body: origin not allowed by CORS or tenant mismatch'])

act('Visitor (not logged in)', 'otp', 'Log in with OTP', 'Any user',
    '/login/otp', 'Enter phone/email, then the code',
    [*gw('/auth/otp/send', 'AUTH', True, 'POST'),
     ('AUTH', 'RD', 'store OTP with expiry'), ('AUTH', 'KF', 'publish otp.sent'),
     ('AUTH', 'UI', '200 OTP sent'),
     ('KF', 'NOTI', 'consume otp.sent'),
     ('alt', 'provider = log'), ('NOTI', 'NOTI', 'write OTP to the service log'),
     ('else', 'real provider'), ('NOTI', 'MAIL', 'send email / SMS / WhatsApp'), ('end',),
     ('U', 'UI', 'type the code'),
     *gw('/auth/otp/verify', 'AUTH', True, 'POST'),
     ('AUTH', 'RD', 'compare + delete OTP'),
     ('AUTH', 'UI', 'JWT tokens (same as password login from here)')],
    'Signed in without a password.',
    ['400 wrong / expired OTP'],
    ['Locally, read the code with: docker compose logs --tail=50 notification-service'])

act('Visitor (not logged in)', 'social', 'Log in with Google / Facebook', 'Any user',
    '/login buttons, then /oauth2/redirect', 'Click "Continue with Google"',
    [('UI', 'GW', 'browser navigates to /oauth2/authorization/google'),
     ('GW', 'AUTH', 'route auth-service (/oauth2/**, public)'),
     ('AUTH', 'GOO', 'redirect to Google consent'),
     ('GOO', 'GW', 'callback /login/oauth2/code/google'),
     ('GW', 'AUTH', 'exchange code, find or create user'),
     ('AUTH', 'UI', 'redirect to /oauth2/redirect with tokens'),
     ('UI', 'SS', 'save tokens')],
    'Signed in with a social account.',
    ['Buttons do nothing: needs AUTH_PROFILE=docker,social and real client id/secret in docker/.env'],
    ['Without the social profile, auth-service starts with oauth2Login switched off (by design).'])

# ---------------- Customer ----------------
act('Customer', 'book', 'Book a service', 'Customer',
    '/book/:serviceId', 'Choose date, address, notes, then "Confirm booking"',
    [('U', 'UI', 'submit booking form'), ('UI', 'UI', 'bookingSlice.createBooking'),
     *gw('/bookings', 'BOOK', method='POST'),
     ('BOOK', 'BOOK', 'read X-User-Id as the customer, generate booking code BK-<timestamp>-<4 digits>'),
     ('BOOK', 'DB', 'INSERT booking (civil_engineer_bookings_<tenant>), status PENDING / QUOTATION_PENDING'),
     ('BOOK', 'UI', '201 booking'),
     ('UI', 'U', 'go to payment: /bookings/:bookingId/pay')],
    'Booking exists and waits for payment and worker assignment.',
    ['401 not logged in', '400 invalid date / missing fields'],
    ['notification-service has a listener for booking.created, but no service publishes that topic, so no "booking created" message is sent today.'])

act('Customer', 'mybookings', 'View my bookings', 'Customer',
    '/bookings', 'Open "My bookings"',
    [*gw('/bookings/customer?page=0&size=..', 'BOOK'),
     ('BOOK', 'DB', 'SELECT bookings WHERE customer_id = X-User-Id'),
     ('BOOK', 'UI', 'page of bookings'),
     ('U', 'UI', 'open one'), *gw('/bookings/{id}', 'BOOK'), ('BOOK', 'UI', 'booking detail / invoice')],
    'List with status (PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED...) and payment status.')

act('Customer', 'pay', 'Pay for a booking (Razorpay)', 'Customer',
    '/bookings/:bookingId/pay  (services/paymentApi.ts, razorpayCheckout.ts)', 'Click "Pay now"',
    [*gw('/payments/create-order', 'PAY', method='POST'),
     ('PAY', 'RZP', 'create order (RAZORPAY_KEY_ID / SECRET)'),
     ('PAY', 'DB', 'INSERT payment PENDING + razorpayOrderId'),
     ('PAY', 'KF', 'publish payment.created'),
     ('PAY', 'UI', 'order id + amount'),
     ('UI', 'RZP', 'open Razorpay Checkout popup (VITE_RAZORPAY_KEY_ID, public key)'),
     ('RZP', 'UI', 'payment id + signature'),
     *gw('/payments/verify', 'PAY', method='POST'),
     ('PAY', 'PAY', 'verify HMAC signature with the key secret'),
     ('PAY', 'DB', 'payment COMPLETED'),
     ('PAY', 'KF', 'publish payment.completed'),
     ('PAY', 'UI', '200 paid'),
     ('KF', 'BOOK', 'PaymentEventConsumer: booking paymentStatus = PAID, QUOTATION_PENDING becomes PENDING'),
     ('BOOK', 'KF', 'publish booking.paid'),
     ('KF', 'NOTI', 'payment.completed + booking.paid: receipt / in-app notification')],
    'Booking is marked PAID and joins the assignment queue. Customer gets a receipt.',
    ['Razorpay popup fails: test keys in docker/.env are placeholders on this machine',
     '400 signature mismatch', 'Duplicate Kafka events are ignored (booking already PAID)'],
    ['Razorpay also calls /webhooks/** (public route) on payment-service as a server-side confirmation.'])

act('Customer', 'track', 'Track the worker live', 'Customer',
    '/track/:bookingId  (pages/tracking/LiveTrackingPage.tsx)', 'Open "Track" on an assigned booking',
    [*gw('/bookings/{id}/tracking', 'BOOK'),
     ('BOOK', 'DB', 'latest worker location + ETA'),
     ('BOOK', 'UI', 'tracking snapshot (map, ETA, distance)'),
     ('UI', 'UI', 'poll again every few seconds')],
    'Map shows the worker moving and the ETA. See "Share live location" (worker) for the other side.')

act('Customer', 'review', 'Review a completed booking', 'Customer (or worker, reviews are two-way)',
    '/bookings/:bookingId/review  (services/reviewApi.ts)', 'Give stars + comment, submit',
    [*gw('/bookings/{id}/reviews', 'REV', method='POST'),
     ('GW', 'GW', 'route booking-reviews (matched before the general /bookings/** route)'),
     ('REV', 'BOOK', 'Feign: get booking {id}'),
     ('alt', 'booking COMPLETED and caller is customer or worker'),
     ('REV', 'DB', 'INSERT review (one per booking per reviewer)'),
     ('REV', 'DB', 'recompute RatingSummary average'),
     ('REV', 'UI', '201 review'),
     ('else', 'not completed / not a party / duplicate'),
     ('REV', 'UI', '400 or 403'), ('end',)],
    'Rating shows on the worker\'s profile. The reviewed party can post one response.')

act('Customer', 'notifications', 'Notifications bell', 'Any logged-in user',
    'Navbar bell  (services/notificationApi.ts)', 'Page load / click the bell',
    [*gw('/notifications/unread-count', 'NOTI'), ('NOTI', 'DB', 'count unread for X-User-Id'),
     ('NOTI', 'UI', 'badge number'),
     ('U', 'UI', 'open bell'), *gw('/notifications', 'NOTI'), ('NOTI', 'UI', 'list'),
     ('U', 'UI', 'click one'), ('UI', 'NOTI', 'PUT /notifications/{id}/read  (or /read-all, DELETE /{id})')],
    'Badge clears. Notifications come from Kafka events and admin announcements.')

act('Customer', 'ticket', 'Raise a support ticket', 'Any logged-in user',
    '/support  (services/supportApi.ts)', 'Create ticket, then chat in its thread',
    [*gw('/support/tickets', 'SUP', method='POST'),
     ('SUP', 'DB', 'INSERT ticket OPEN (civil_engineer_support_<tenant>)'),
     ('SUP', 'KF', 'publish audit.events (ticket created)'),
     ('SUP', 'UI', 'ticket'),
     ('U', 'UI', 'reply'), ('UI', 'SUP', 'POST /support/tickets/{id}/messages'),
     ('UI', 'SUP', 'GET /support/tickets/{id}/messages (thread)')],
    'Ticket shows in the admin support queue. Reporter cannot resolve it themselves.',
    ['403 not your ticket', '400 reply on a RESOLVED/CLOSED ticket'])

act('Customer', 'askai', 'Ask AI assistant', 'Any logged-in user',
    'Floating chat widget  (components/SupportChatWidget.tsx, services/aiApi.ts)', 'Open widget, type a question',
    [*gw('/support/ai/status', 'SUP'), ('SUP', 'UI', 'enabled? (GEMINI_API_KEY set and AI_ASSISTANT_ENABLED)'),
     ('U', 'UI', 'ask question'), *gw('/support/ai/chat', 'SUP', method='POST'),
     ('SUP', 'GEM', 'prompt to GEMINI_MODEL'),
     ('alt', '503 high demand / 404 retired model'),
     ('SUP', 'GEM', 'retry with GEMINI_FALLBACK_MODELS in order'), ('end',),
     ('SUP', 'UI', 'answer')],
    'Chat answer. If the key is missing, the widget says the assistant is unavailable and tickets still work.',
    notes=['The Gemini key stays on the server (support-service). It is never a VITE_ variable.'])

act('Customer', 'appearance', 'Change my appearance', 'Any logged-in user',
    '/settings/appearance', 'Pick colours / density / mode, save',
    [*gw('/ui-config/me/appearance', 'ADM', method='PUT'),
     ('ADM', 'DB', 'save personal overrides'), ('ADM', 'UI', 'new appearance'),
     ('UI', 'UI', 'rebuild MUI theme, UI re-colours'),
     ('U', 'UI', '"Reset"'), ('UI', 'ADM', 'DELETE /ui-config/me/appearance, back to the workspace theme')],
    'Only this user\'s view changes. Admin/platform theme is unaffected.')

# ---------------- Worker / provider ----------------
act('Worker / provider', 'jobs', 'See my assigned jobs', 'Worker, engineer, architect, contractor...',
    '/dashboard, /track', 'Open dashboard / tracking',
    [*gw('/bookings/worker?page=0&size=50', 'BOOK'),
     ('BOOK', 'DB', 'SELECT bookings WHERE worker_id = X-User-Id'),
     ('BOOK', 'UI', 'assigned jobs')],
    'Worker sees jobs that an admin (or the flow) assigned with POST /bookings/{id}/assign/{workerId}.')

act('Worker / provider', 'location', 'Share live location', 'Assigned worker',
    '/track/:bookingId  (services/trackingApi.ts)', 'Worker\'s browser sends GPS position while travelling',
    [*gw('/bookings/{id}/tracking', 'BOOK', method='PUT'),
     ('BOOK', 'BOOK', 'store position, compute ETA + distance (traffic-aware if configured)'),
     ('BOOK', 'DB', 'save tracking point'),
     ('alt', 'worker is close'),
     ('BOOK', 'KF', 'publish booking.arriving'),
     ('KF', 'NOTI', 'notify customer "your professional is arriving"'), ('end',),
     ('BOOK', 'UI', '200')],
    'Customer\'s tracking page updates on its next poll.')

act('Worker / provider', 'status', 'Update job status / complete', 'Worker or admin',
    'API (booking-service)', 'Start work, then mark complete',
    [*gw('/bookings/{id}/status/IN_PROGRESS', 'BOOK', method='PUT'),
     ('BOOK', 'DB', 'status IN_PROGRESS'),
     *gw('/bookings/{id}/complete', 'BOOK', method='POST'),
     ('BOOK', 'DB', 'status COMPLETED'),
     ('BOOK', 'KF', 'publish booking.completed'),
     ('KF', 'NOTI', 'notify customer, ask for a review'),
     ('BOOK', 'UI', '200')],
    'Booking COMPLETED. Both sides can now leave a review. Cancel: POST /bookings/{id}/cancel.')

act('Worker / provider', 'materials', 'Supplier: publish material prices', 'Material supplier',
    '/material-prices  (services/materialApi.ts)', 'Add / edit / delete a price',
    [*gw('/users/materials/catalogue', 'USER'), ('USER', 'UI', 'material list'),
     *gw('/users/materials/my-prices', 'USER', method='POST'),
     ('USER', 'DB', 'save supplier price (PUT / DELETE /my-prices/{id} to change)'),
     ('USER', 'UI', 'saved'),
     ('UI', 'USER', 'GET /users/materials/rates (market min / max ranges)')],
    'Supplier prices feed the market rate ranges shown to users (and the AI site-rates context).')

# ---------------- Admin ----------------
act('Admin', 'admindash', 'Admin dashboard', 'ADMIN / SUPER_ADMIN',
    '/admin  (pages/admin/AdminDashboard.tsx, services/adminApi.ts)', 'Open /admin',
    [*gw('/admin/dashboard/stats', 'ADM'),
     ('ADM', 'ADM', 'check X-User-Role is an admin role'),
     ('ADM', 'AUTH', 'Feign: /api/v1/auth/admin/stats (user counts)'),
     ('ADM', 'BOOK', 'Feign: /api/v1/bookings/admin/stats'),
     ('ADM', 'PAY', 'Feign: /api/v1/payments/admin/revenue/summary'),
     ('ADM', 'UI', 'stats, /dashboard/activity, /dashboard/cities')],
    'KPI cards and charts. admin-service is an aggregator; the data lives in the other services.',
    ['403 role is not admin'],
    ['The internal paths (/api/v1/auth/admin, /api/v1/bookings/admin) are blocked at the gateway by InternalOnlyPathFilter, so only admin-service can call them, from inside Docker.'])

act('Admin', 'users', 'Manage users', 'ADMIN / SUPER_ADMIN',
    '/admin/users  (UserManagement)', 'Search, edit, suspend or delete a user',
    [*gw('/admin/users?search=..', 'ADM'),
     ('ADM', 'AUTH', 'Feign: GET /api/v1/auth/admin/users'), ('AUTH', 'DB', 'query users'),
     ('ADM', 'UI', 'user table'),
     ('U', 'UI', 'suspend'), *gw('/admin/users/{id}/status', 'ADM', method='PUT'),
     ('ADM', 'AUTH', 'Feign: PUT /api/v1/auth/admin/users/{id}/status'),
     ('AUTH', 'DB', 'status SUSPENDED'), ('ADM', 'UI', 'updated row')],
    'Suspended users can no longer log in.')

act('Admin', 'catalogue', 'Manage categories and services', 'ADMIN',
    '/admin/categories  (CategoryManagement)', 'Create / edit / toggle a category or service',
    [*gw('/admin/categories', 'ADM', method='POST'),
     ('ADM', 'BOOK', 'Feign: POST /api/v1/bookings/admin/categories'),
     ('BOOK', 'DB', 'save category'), ('ADM', 'UI', 'saved'),
     ('UI', 'ADM', 'POST /admin/services, PUT /admin/services/{id}/status'),
     ('ADM', 'BOOK', 'Feign: /api/v1/bookings/admin/services')],
    'Public catalogue (/catalogue) shows the change immediately.')

act('Admin', 'adminbookings', 'Manage bookings', 'ADMIN',
    '/admin/bookings  (BookingManagement)', 'Filter, change status, complete or cancel',
    [*gw('/admin/bookings?status=..', 'ADM'),
     ('ADM', 'BOOK', 'Feign: GET /api/v1/bookings/admin/all'),
     ('ADM', 'UI', 'bookings table'),
     ('UI', 'ADM', 'PUT /admin/bookings/{id}/status, POST .../complete, POST .../cancel'),
     ('ADM', 'BOOK', 'Feign: same under /api/v1/bookings/admin/..'),
     ('BOOK', 'KF', 'booking.completed when completed')],
    'Booking changes are visible to customer and worker.')

act('Admin', 'revenue', 'Revenue, analytics, reports, invoices', 'ADMIN',
    '/admin/revenue, /admin/analytics, /admin/reports, /admin/invoices', 'Open page / export',
    [*gw('/admin/analytics', 'ADM'),
     ('ADM', 'PAY', 'Feign: /api/v1/payments/admin/revenue/(summary|monthly|breakdown|transactions)'),
     ('ADM', 'BOOK', 'Feign: booking stats'),
     ('ADM', 'UI', 'charts'),
     ('U', 'UI', 'export report'), ('UI', 'ADM', 'GET /admin/reports/{key}/export (file download, Blob)')],
    'Charts and downloadable reports.')

act('Admin', 'theme', 'Change platform theme / workspaces', 'SUPER_ADMIN',
    '/admin/theme, /admin/workspaces  (services/uiConfigApi.ts)', 'Edit colours, layout, menu; save',
    [*gw('/admin/theme', 'ADM', method='PUT'),
     ('ADM', 'DB', 'save theme (version + 1)'),
     ('ADM', 'KF', 'publish audit.events (theme changed)'),
     ('ADM', 'UI', 'resolved theme'),
     ('UI', 'ADM', 'per role: PUT /admin/workspaces/{role}/theme and /menu'),
     ('note', 'UI', 'Members pick it up on next GET /ui-config/me')],
    'Every member (or that workspace role) sees the new look and menu.')

act('Admin', 'content', 'Edit landing page content', 'ADMIN',
    '/admin/content  (services/siteContentApi.ts)', 'Edit sections / items / upload images',
    [*gw('/admin/content/sections', 'ADM', method='POST'),
     ('ADM', 'DB', 'save section / item'),
     ('UI', 'ADM', 'POST /admin/content/media (image upload)'),
     ('ADM', 'UI', 'saved'),
     ('note', 'UI', 'Visitors read it via public GET /content/site')],
    'Landing page and footer change without a redeploy.')

act('Admin', 'announce', 'Send an announcement', 'ADMIN',
    '/admin/alerts  (AlertsPage, services/notificationApi.ts)', 'Write message, choose audience, send',
    [*gw('/admin/announcements', 'NOTI', method='POST'),
     ('GW', 'GW', 'route notification-service (matched before the /admin/** catch-all)'),
     ('NOTI', 'DB', 'save Announcement'),
     ('NOTI', 'DB', 'one Notification row per recipient ("*" = all ACTIVE users)'),
     ('NOTI', 'KF', 'publish audit.events'),
     ('NOTI', 'UI', 'announcement')],
    'Everyone in the audience sees it in their bell. Cancel: POST /admin/announcements/{id}/cancel.')

act('Admin', 'emails', 'Email templates and email log', 'ADMIN',
    '/admin/email-templates, /admin/emails  (services/emailApi.ts)', 'Edit template, preview, test-send',
    [*gw('/admin/notifications/email-templates/{key}', 'NOTI', method='PUT'),
     ('NOTI', 'DB', 'save template'),
     ('UI', 'NOTI', 'POST .../{key}/preview, POST .../{key}/test-send'),
     ('NOTI', 'MAIL', 'send test email'),
     ('UI', 'NOTI', 'GET /admin/notifications/emails (delivery log + summary)'),
     ('MAIL', 'NOTI', 'Brevo delivery callback to /api/v1/notifications/webhooks/** (public, shared secret)')],
    'Templates used for all future emails. The log shows delivered / bounced status.')

act('Admin', 'supportq', 'Work the support queue', 'ADMIN',
    '/admin/support  (SupportQueuePage)', 'Assign, reply, resolve',
    [*gw('/admin/support/tickets', 'SUP'), ('SUP', 'UI', 'queue'),
     ('UI', 'SUP', 'PATCH /admin/support/tickets/{id}/assign  (auto moves to IN_PROGRESS)'),
     ('UI', 'SUP', 'POST /support/tickets/{id}/messages (reply)'),
     ('UI', 'SUP', 'PATCH /support/tickets/{id}/status RESOLVED + reason'),
     ('SUP', 'KF', 'audit.events for create / assign / resolve')],
    'Ticket closed; reporter sees the resolution; no more replies allowed.')

act('Admin', 'audit', 'Audit log and integrity check', 'ADMIN / SUPER_ADMIN',
    '/admin/activity  (services/auditApi.ts)', 'Open activity, filter, verify, export',
    [*gw('/admin/audit/events?entityType=..', 'AUD'),
     ('AUD', 'DB', 'read append-only events (civil_engineer_audit_<tenant>)'),
     ('AUD', 'UI', 'events'),
     ('UI', 'AUD', 'GET /admin/audit/integrity: re-walk the hash chain'),
     ('UI', 'AUD', 'GET /admin/audit/anomalies, /admin/audit/export')],
    'Shows who did what. Integrity check reports a break if any row was altered.')

act('Admin', 'tenants', 'Onboard a new tenant (workspace)', 'SUPER_ADMIN of the operator tenant',
    '/admin/tenants  (TenantManagement, services/tenantApi.ts)', 'Create tenant, set branding / modules / navigation',
    [*gw('/tenants', 'TEN', method='POST'),
     ('TEN', 'TEN', 'require SUPER_ADMIN of the operator tenant'),
     ('TEN', 'DB', 'INSERT tenant (key, host, modules, branding)'),
     ('TEN', 'KF', 'publish tenant.events'),
     ('TEN', 'UI', 'tenant'),
     ('KF', 'ALL', 'TenantProvisioningListener (tenant-common) in every service'),
     ('ALL', 'DB', 'CREATE SCHEMA <service_db>_<tenantKey> + run Flyway migrations'),
     ('note', 'ALL', 'No restart needed'),
     ('UI', 'TEN', 'PUT /tenants/{key}/branding, /modules, /navigation, PATCH /status')],
    'New tenant is live at http://<tenantKey>.localhost:3000 with empty, fully migrated schemas.',
    ['403 caller is not operator SUPER_ADMIN', 'Disabled module: its routes return 404 for that tenant'])

# ---------------- System / background ----------------
act('System (background)', 'refresh', 'Automatic token refresh', 'Frontend (Axios interceptor)',
    'services/api.ts', 'Any API call returns 401 (access token expired)',
    [('UI', 'GW', 'GET /api/v1/... with expired token'), ('GW', 'UI', '401'),
     ('UI', 'UI', 'interceptor: skip if /auth/login|register|refresh|otp, else try once'),
     *gw('/auth/refresh', 'AUTH', True, 'POST'),
     ('alt', 'refresh token valid'),
     ('AUTH', 'UI', 'new access + refresh token'), ('UI', 'SS', 'store (setCredentials)'),
     ('UI', 'GW', 'retry the original request'),
     ('else', 'refresh fails'), ('UI', 'U', 'log out, go to /login'), ('end',)],
    'The user does not notice token expiry unless the refresh token is also expired.')

act('System (background)', 'auditpipe', 'Audit event pipeline', 'audit-common in producer services',
    'user, payment, project, notification, support, admin services', 'Any audited action',
    [('ADM', 'KF', 'audit-common publishes audit.events (actor, action, entity, tenant)'),
     ('KF', 'AUD', 'AuditIngestService consumes'),
     ('AUD', 'AUD', 'hash = SHA(previous hash + event)'),
     ('AUD', 'DB', 'append row (never updated or deleted)')],
    'Tamper-evident history per tenant.')

act('System (background)', 'boot', 'A service starts up', 'Docker',
    'docker compose up', 'Container starts: java -jar app.jar',
    [('ALL', 'ALL', 'bootstrap: fetch <service>.yml from config-server:8888'),
     ('ALL', 'DB', 'read tenant registry, run Flyway for every tenant schema'),
     ('ALL', 'KF', 'join consumer groups (if it listens)'),
     ('ALL', 'EU', 'register under spring.application.name'),
     ('GW', 'EU', 'refresh registry: new instance routable')],
    'Service is UP on /actuator/health and reachable through the gateway.',
    ['Gateway 503 for ~30 s after start until Eureka has the instance'])

# ---------------- Backend only ----------------
act('Backend only (no screen yet)', 'escrow', 'Escrow: fund, release, dispute', 'Customer (payer), worker (payee), admin',
    'API only: /api/v1/escrow/**, /api/v1/admin/escrow/**', 'Call the API (curl / Swagger)',
    [*gw('/escrow', 'PAY', method='POST'),
     ('PAY', 'DB', 'EscrowHold PENDING_FUNDING'),
     ('PAY', 'RZP', 'funding order'), ('RZP', 'PAY', 'payment completed'),
     ('PAY', 'DB', 'hold HELD'), ('PAY', 'KF', 'escrow.held'),
     ('alt', 'payer releases or auto-release after 7 days'),
     ('PAY', 'DB', '5% commission frozen, payee Wallet credited + ledger line'),
     ('PAY', 'KF', 'escrow.released + audit.events'),
     ('else', 'dispute'),
     ('PAY', 'DB', 'DISPUTED (release blocked)'), ('PAY', 'KF', 'escrow.disputed'), ('end',)],
    'Money moves to the provider\'s wallet only after a real, confirmed payment.',
    notes=['No UI screen yet. Withdrawals/payouts are not built: wallets are read-only.'])

act('Backend only (no screen yet)', 'kyc', 'KYC verification', 'Provider + admin',
    'API only: user-service', 'Submit document, admin approves',
    [*gw('/users/kyc/...', 'USER', method='POST'),
     ('USER', 'DB', 'KycDocument PENDING'), ('USER', 'KF', 'audit.events'),
     ('note', 'USER', 'Admin approves / rejects (403 without admin role)'),
     ('USER', 'DB', 'approve: UserProfile.isVerified = true')],
    'Verified badge; search can filter on "verified".',
    notes=['Exact paths: see user-service KYC controller / Swagger at http://localhost:8094/swagger-ui.html'])

act('Backend only (no screen yet)', 'messaging', 'Booking chat', 'Customer + assigned worker',
    'API only: /api/v1/bookings/{id}/messages, /api/v1/threads/**', 'Send a message',
    [*gw('/bookings/{id}/messages', 'MSG', method='POST'),
     ('MSG', 'BOOK', 'check worker is assigned and caller is a party'),
     ('MSG', 'DB', 'save Message, bump other party\'s unread counter'),
     ('MSG', 'KF', 'publish message.sent'),
     ('KF', 'NOTI', 'in-app notification for the recipient'),
     ('MSG', 'UI', '201')],
    'One thread per booking.', ['400 before a worker is assigned', '403 not a party'])

act('Backend only (no screen yet)', 'projects', 'Projects and milestones', 'Customer / contractor',
    'API only: /api/v1/projects/**', 'Create project, milestones, view summary',
    [*gw('/projects', 'PROJ', method='POST'), ('PROJ', 'DB', 'Project + Milestones'),
     ('UI', 'PROJ', 'GET /projects/{id}/summary'),
     ('PROJ', 'BOOK', 'Feign: bookings for project'),
     ('PROJ', 'PAY', 'Feign: /escrow/project/{id}'),
     ('PROJ', 'UI', 'budget vs actual + escrow held/released')],
    'Budget rollup across bookings and escrow.')

act('Backend only (no screen yet)', 'search', 'Search professionals', 'Any logged-in user',
    'API only: /api/v1/search/**', 'Query with filters',
    [*gw('/search/profiles?q=..&city=..&minRating=..', 'SRCH'),
     ('SRCH', 'ES', 'fuzzy full-text + filters on the tenant\'s profiles index'),
     ('SRCH', 'UI', 'ranked results'),
     ('note', 'SRCH', 'Admin reindex: POST /admin/search/... pulls from auth, user, review services')],
    'Only ACTIVE provider accounts appear.',
    notes=['Index freshness depends on reindexing (see MODULE_STATUS.md).'])

# ---------------- Journey ----------------
JOURNEY = [
    ('landing', 'Visitor opens the site'), ('register', 'Registers'), ('login', 'Logs in'),
    ('browse', 'Browses services'), ('book', 'Books a service'), ('pay', 'Pays with Razorpay'),
    ('jobs', 'Worker gets the job'), ('location', 'Worker travels, shares location'),
    ('track', 'Customer tracks'), ('status', 'Worker completes'), ('review', 'Customer reviews'),
    ('ticket', 'Raises a ticket if needed'), ('supportq', 'Admin resolves'),
]

# ---------------- render ----------------
def esc(s): return html.escape(str(s))

def mm(s):  # make text safe for mermaid sequence messages
    return (str(s).replace(';', ',').replace('#', 'no.').replace('<', '‹').replace('>', '›')
            .replace('"', "'"))

def seq(steps):
    used = []
    for st in steps:
        if st[0] in ('alt', 'else', 'end'): continue
        if st[0] == 'note':
            if st[1] not in used: used.append(st[1])
            continue
        for p in st[:2]:
            if p not in used: used.append(p)
    lines = ['sequenceDiagram', '    autonumber']
    for p in used:
        name, _ = P[p]
        kind = 'actor' if p == 'U' else 'participant'
        lines.append(f'    {kind} {p} as {mm(name)}')
    for st in steps:
        if st[0] == 'alt': lines.append(f'    alt {mm(st[1])}')
        elif st[0] == 'else': lines.append(f'    else {mm(st[1])}')
        elif st[0] == 'end': lines.append('    end')
        elif st[0] == 'note': lines.append(f'    Note over {st[1]}: {mm(st[2])}')
        else:
            a, b, msg = st
            arrow = '-->>' if P[b][1] == 'ui' and a != 'U' and b != 'SS' else '->>'
            lines.append(f'    {a}{arrow}{b}: {mm(msg)}')
    return '\n'.join(lines)

def steps_table(steps):
    rows, n, branch = [], 0, ''
    for st in steps:
        if st[0] == 'alt': branch = f'if {st[1]}'; continue
        if st[0] == 'else': branch = f'else: {st[1]}'; continue
        if st[0] == 'end': branch = ''; continue
        if st[0] == 'note':
            rows.append(f'<tr class="note"><td></td><td colspan="2"><span class="tag {P[st[1]][1]}">{esc(P[st[1]][0])}</span></td><td>{esc(st[2])}</td></tr>')
            continue
        n += 1
        a, b, msg = st
        br = f'<span class="br">{esc(branch)}</span> ' if branch else ''
        arrow = '↺' if a == b else '→'
        rows.append(f'<tr><td class="n">{n}</td><td><span class="tag {P[a][1]}">{esc(P[a][0])}</span></td>'
                    f'<td><span class="arr">{arrow}</span> <span class="tag {P[b][1]}">{esc(P[b][0])}</span></td>'
                    f'<td>{br}{esc(msg)}</td></tr>')
    return ('<div class="tw"><table><thead><tr><th>#</th><th>From</th><th>To</th><th>What happens</th></tr></thead>'
            f'<tbody>{"".join(rows)}</tbody></table></div>')

groups = []
for a in A:
    if a['group'] not in groups: groups.append(a['group'])

nav = []
for g in groups:
    items = ''.join(f'<li><a href="#{a["id"]}" data-id="{a["id"]}">{esc(a["title"])}</a></li>' for a in A if a['group'] == g)
    nav.append(f'<div class="ng"><div class="ngt">{esc(g)}</div><ul>{items}</ul></div>')

cards = []
for g in groups:
    cards.append(f'<h2 class="gh" data-group="{esc(g)}">{esc(g)}</h2>')
    for a in A:
        if a['group'] != g: continue
        errs = ''.join(f'<li>{esc(e)}</li>' for e in a['errors'])
        notes = ''.join(f'<li>{esc(e)}</li>' for e in a['notes'])
        text = ' '.join([a['title'], a['who'], a['page'], a['trigger'], a['result'],
                         *[' '.join(map(str, s)) for s in a['steps']]]).lower()
        cards.append(f'''
<article class="card" id="{a['id']}" data-text="{esc(text)}">
  <header><h3>{esc(a['title'])}</h3><span class="who">{esc(a['who'])}</span></header>
  <dl class="meta">
    <div><dt>Screen</dt><dd><code>{esc(a['page'])}</code></dd></div>
    <div><dt>Trigger</dt><dd>{esc(a['trigger'])}</dd></div>
  </dl>
  <div class="tabs"><button class="on" data-v="steps">Steps</button><button data-v="diagram">Diagram</button></div>
  <div class="view steps">{steps_table(a['steps'])}</div>
  <div class="view diagram" hidden><pre class="mermaid">{esc(seq(a['steps']))}</pre></div>
  <div class="res"><b>Result:</b> {esc(a['result'])}</div>
  {f'<div class="err"><b>What can go wrong</b><ul>{errs}</ul></div>' if errs else ''}
  {f'<div class="nt"><b>Notes</b><ul>{notes}</ul></div>' if notes else ''}
</article>''')

title_of = {a['id']: a['title'] for a in A}
journey = ''.join(f'<a class="js" href="#{i}"><span class="jn">{k+1}</span><span>{esc(t)}</span></a>'
                  for k, (i, t) in enumerate(JOURNEY))

legend = ''.join(f'<span class="tag {c}">{esc(l)}</span>' for c, l in
                 [('user', 'User'), ('ui', 'Browser / React'), ('edge', 'nginx'), ('gw', 'Gateway'),
                  ('plat', 'Eureka / Config'), ('svc', 'Microservice'), ('data', 'Database / cache'),
                  ('bus', 'Kafka'), ('ext', 'External')])

page = f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Action Flows</title>
<style>
:root{{--bg:#f5f6f8;--fg:#1d2430;--mut:#5d6878;--card:#fff;--line:#e2e6ec;--acc:#4f46e5;--code:#f1f3f7;
 --user:#fde68a;--ui:#bfdbfe;--edge:#c7d2fe;--gw:#ddd6fe;--plat:#e5e7eb;--svc:#bbf7d0;--data:#fed7aa;--bus:#fbcfe8;--ext:#fecaca}}
@media (prefers-color-scheme:dark){{:root{{--bg:#0f141b;--fg:#e6e9ef;--mut:#9aa4b2;--card:#171d26;--line:#2a3340;--acc:#8b86ff;--code:#1f2733;
 --user:#6b5a1a;--ui:#1e3a5f;--edge:#2e3470;--gw:#3f2f6b;--plat:#374151;--svc:#1f4d33;--data:#6b3d17;--bus:#6b2346;--ext:#6b2323}}}}
*{{box-sizing:border-box}}
body{{margin:0;font:14.5px/1.55 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:var(--bg);color:var(--fg)}}
.top{{padding:22px 20px 16px;border-bottom:1px solid var(--line);background:var(--card)}}
.top h1{{margin:0;font-size:22px}} .top p{{margin:6px 0 0;color:var(--mut);max-width:900px}}
.wrap{{display:grid;grid-template-columns:270px 1fr;gap:0;max-width:1400px;margin:0 auto}}
aside{{position:sticky;top:0;align-self:start;height:100vh;overflow:auto;padding:16px;border-right:1px solid var(--line)}}
aside input{{width:100%;padding:8px 10px;border:1px solid var(--line);border-radius:8px;background:var(--card);color:var(--fg);font:inherit}}
.ng{{margin-top:14px}} .ngt{{font-size:11.5px;text-transform:uppercase;letter-spacing:.05em;color:var(--mut);font-weight:600}}
aside ul{{list-style:none;margin:6px 0 0;padding:0}} aside li a{{display:block;padding:4px 8px;border-radius:6px;color:var(--fg);text-decoration:none;font-size:13.5px}}
aside li a:hover,aside li a.act{{background:var(--code);color:var(--acc)}}
main{{padding:18px 22px 80px;min-width:0}}
.legend{{display:flex;flex-wrap:wrap;gap:6px;margin:4px 0 18px}}
.tag{{display:inline-block;padding:1px 8px;border-radius:999px;font-size:12px;white-space:nowrap;color:var(--fg)}}
.tag.user{{background:var(--user)}}.tag.ui{{background:var(--ui)}}.tag.edge{{background:var(--edge)}}.tag.gw{{background:var(--gw)}}
.tag.plat{{background:var(--plat)}}.tag.svc{{background:var(--svc)}}.tag.data{{background:var(--data)}}.tag.bus{{background:var(--bus)}}.tag.ext{{background:var(--ext)}}
.journey{{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:14px 16px;margin-bottom:8px}}
.journey h2{{margin:0 0 10px;font-size:16px}}
.jr{{display:flex;flex-wrap:wrap;gap:8px}}
.js{{display:flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid var(--line);border-radius:999px;text-decoration:none;color:var(--fg);font-size:13px;background:var(--bg)}}
.js:hover{{border-color:var(--acc)}} .jn{{background:var(--acc);color:#fff;border-radius:999px;min-width:20px;height:20px;display:inline-grid;place-items:center;font-size:11.5px}}
.gh{{font-size:17px;margin:30px 0 10px;padding-top:6px}}
.card{{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:16px 18px;margin:0 0 16px;scroll-margin-top:12px}}
.card header{{display:flex;flex-wrap:wrap;align-items:baseline;gap:10px;justify-content:space-between}}
.card h3{{margin:0;font-size:17px}} .who{{color:var(--mut);font-size:13px}}
.meta{{display:grid;grid-template-columns:1fr 1fr;gap:6px 18px;margin:10px 0}}
.meta dt{{font-size:11.5px;text-transform:uppercase;color:var(--mut);letter-spacing:.04em}} .meta dd{{margin:0}}
code{{background:var(--code);padding:1px 6px;border-radius:5px;font-size:12.5px;word-break:break-word}}
.tabs{{display:flex;gap:4px;margin:8px 0}}
.tabs button{{border:1px solid var(--line);background:var(--bg);color:var(--fg);padding:4px 12px;border-radius:7px;cursor:pointer;font:inherit;font-size:13px}}
.tabs button.on{{background:var(--acc);border-color:var(--acc);color:#fff}}
.tw{{overflow-x:auto}} table{{border-collapse:collapse;width:100%;font-size:13.5px}}
th{{text-align:left;font-size:11.5px;text-transform:uppercase;color:var(--mut);font-weight:600;border-bottom:1px solid var(--line);padding:6px 8px}}
td{{border-bottom:1px solid var(--line);padding:6px 8px;vertical-align:top}}
td.n{{color:var(--mut);width:28px}} tr.note td{{background:var(--code);font-style:italic}}
.arr{{color:var(--mut)}} .br{{font-size:11.5px;background:var(--code);border-radius:5px;padding:0 5px;color:var(--acc)}}
.diagram{{overflow-x:auto;padding:6px 0}} .mermaid{{margin:0;background:transparent}}
.res{{margin-top:10px;padding:8px 12px;border-left:3px solid #16a34a;background:var(--code);border-radius:0 8px 8px 0}}
.err,.nt{{margin-top:8px;font-size:13.5px}} .err ul,.nt ul{{margin:4px 0 0;padding-left:20px}}
.err b{{color:#dc2626}} .nt b{{color:var(--acc)}}
.hide{{display:none!important}}
@media (max-width:860px){{.wrap{{grid-template-columns:1fr}} aside{{position:static;height:auto;border-right:0;border-bottom:1px solid var(--line)}}
 .meta{{grid-template-columns:1fr}} main{{padding:14px 16px 60px}}}}
</style></head><body>
<div class="top"><h1>Action Flows: Civil Engineering Marketplace</h1>
<p>Every user action, traced end to end: which screen, which API call, how the gateway handles it, which service and database it touches, which Kafka events fire, and what the user sees. Built from the frontend API modules, the gateway routes and the backend controllers/Kafka listeners. Each card has a step table and a sequence diagram.</p></div>
<div class="wrap">
<aside><input id="q" type="search" placeholder="Filter actions (e.g. kafka, payment, 403)">{''.join(nav)}</aside>
<main>
<div class="legend">{legend}</div>
<section class="journey"><h2>Complete customer journey (click a step)</h2><div class="jr">{journey}</div></section>
<p style="color:var(--mut);font-size:13px;margin:10px 0 0">Every "API Gateway" step does the same checks: tenant from the Host subdomain (X-Tenant-Id), block internal-only paths, and on protected routes verify the JWT, match its tenant claim, add X-User-Id / Email / Role / Name, apply the Redis rate limit.</p>
{''.join(cards)}
</main></div>
<script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.1/dist/mermaid.min.js"></script>
<script>
const dark = matchMedia('(prefers-color-scheme: dark)').matches;
mermaid.initialize({{startOnLoad:false, theme: dark ? 'dark' : 'default', sequence:{{useMaxWidth:false}}}});
document.querySelectorAll('.card').forEach(card => {{
  card.querySelectorAll('.tabs button').forEach(b => b.addEventListener('click', async () => {{
    card.querySelectorAll('.tabs button').forEach(x => x.classList.toggle('on', x === b));
    card.querySelector('.steps').hidden = b.dataset.v !== 'steps';
    const d = card.querySelector('.diagram'); d.hidden = b.dataset.v !== 'diagram';
    const pre = d.querySelector('.mermaid');
    if (!d.hidden && !pre.dataset.processed) {{ await mermaid.run({{nodes:[pre]}}); }}
  }}));
}});
const q = document.getElementById('q');
q.addEventListener('input', () => {{
  const t = q.value.trim().toLowerCase();
  document.querySelectorAll('.card').forEach(c => c.classList.toggle('hide', t && !c.dataset.text.includes(t)));
  document.querySelectorAll('aside li a').forEach(a => a.parentElement.classList.toggle('hide', t && document.getElementById(a.dataset.id).classList.contains('hide')));
  document.querySelectorAll('.gh').forEach(h => {{
    let n = h.nextElementSibling, any = false;
    while (n && n.classList.contains('card')) {{ if (!n.classList.contains('hide')) any = true; n = n.nextElementSibling; }}
    h.classList.toggle('hide', !any);
  }});
}});
const links = [...document.querySelectorAll('aside li a')];
const io = new IntersectionObserver(es => es.forEach(e => {{ if (e.isIntersecting) links.forEach(l => l.classList.toggle('act', l.dataset.id === e.target.id)); }}), {{rootMargin:'-20% 0px -70% 0px'}});
document.querySelectorAll('.card').forEach(c => io.observe(c));
// ?all=1 renders every diagram up front (used for checking / printing)
if (new URLSearchParams(location.search).get('all')) {{
  (async () => {{
    for (const d of document.querySelectorAll('.diagram')) {{
      d.hidden = false;
      await mermaid.run({{nodes:[d.querySelector('.mermaid')]}});
    }}
    document.body.dataset.done = '1';
  }})();
}}
</script>
</body></html>'''
open(OUT, 'w').write(page)
print(len(A), 'actions written to', OUT)
