import api from './api';

/** A new authenticator secret: `otpauthUri` for the QR code, `secret` to type in by hand. */
export interface MfaSetup {
  secret: string;
  otpauthUri: string;
}

/** Starts enrolment for a sign-in that must set up an authenticator (auth-service MfaController). */
export const startMfaSetup = async (mfaToken: string): Promise<MfaSetup> => {
  const { data } = await api.post<MfaSetup>('/auth/mfa/setup', { mfaToken });
  return data;
};

/** "JBSWY3DPEHPK3PXP" → "JBSW Y3DP EHPK 3PXP": easier to copy into an app by hand. */
export const groupSecret = (secret: string) => secret.replace(/(.{4})/g, '$1 ').trim();
