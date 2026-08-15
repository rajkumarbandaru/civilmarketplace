import { Google, Apple, Facebook } from '@mui/icons-material';

// Same base the axios client uses — social login is a full-page browser redirect
// through the gateway rather than an XHR, so it cannot go through `api`.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8087';

export type SocialProvider = 'google' | 'apple' | 'facebook';

export interface SocialProviderConfig {
  id: SocialProvider;
  label: string;
  icon: typeof Google;
  color: string;
}

const ALL_PROVIDERS: SocialProviderConfig[] = [
  { id: 'google', label: 'Google', icon: Google, color: '#DB4437' },
  { id: 'facebook', label: 'Facebook', icon: Facebook, color: '#1877F2' },
  { id: 'apple', label: 'Apple', icon: Apple, color: '#000000' },
];

// Only show buttons for providers actually registered on the auth-service, so we
// never render a button that can only fail. Keep this in step with the `social`
// profile in config-repo/auth-service.yml.
const enabled = (import.meta.env.VITE_SOCIAL_PROVIDERS || 'google,facebook')
  .split(',')
  .map((p: string) => p.trim().toLowerCase())
  .filter(Boolean);

export const SOCIAL_PROVIDERS: SocialProviderConfig[] = ALL_PROVIDERS.filter((p) =>
  enabled.includes(p.id)
);

/**
 * Kicks off the OAuth2 authorization-code flow. Spring Security exposes the entry
 * point at /oauth2/authorization/{registrationId}; on success the auth-service
 * redirects the browser back to /oauth2/redirect with the tokens attached.
 */
export const startSocialLogin = (provider: SocialProvider): void => {
  window.location.href = `${API_BASE_URL}/oauth2/authorization/${provider}`;
};
