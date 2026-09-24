import React, { useEffect, useState } from 'react';
import QRCode from 'qrcode';
import {
  Alert, Box, Button, CircularProgress, Link, Stack, TextField, Typography,
} from '@mui/material';
import { Security } from '@mui/icons-material';
import { useAppDispatch, useAppSelector } from '../../hooks';
import { cancelMfa, clearRecoveryCodes, enableMfa, verifyMfa } from '../../store/slices/authSlice';
import { apiErrorMessage } from '../../services/apiError';
import { MfaSetup, groupSecret, startMfaSetup } from '../../services/mfaApi';

/**
 * The second sign-in step, shown in place of the login form while a sign-in owes a code.
 *
 * Two shapes: enter a code (or a recovery code) for an account that has an authenticator; or,
 * for one that must have one and does not yet (a Super Admin's first sign-in), scan a QR code,
 * confirm with a first code, and write down the recovery codes before going on.
 */
const MfaStep: React.FC<{ onSignedIn: (role?: string) => void }> = ({ onSignedIn }) => {
  const dispatch = useAppDispatch();
  const { mfa, loading, error, recoveryCodes, user } = useAppSelector((s) => s.auth);
  const [code, setCode] = useState('');
  const [useRecovery, setUseRecovery] = useState(false);
  const [setup, setSetup] = useState<MfaSetup | null>(null);
  const [qr, setQr] = useState<string | null>(null);
  const [setupError, setSetupError] = useState<string | null>(null);

  useEffect(() => {
    if (!mfa?.setup) return;
    let live = true;
    startMfaSetup(mfa.token)
      .then(async (s) => {
        if (!live) return;
        setSetup(s);
        setQr(await QRCode.toDataURL(s.otpauthUri, { margin: 1, width: 200 }));
      })
      .catch((e) => live && setSetupError(apiErrorMessage(e, 'Could not start setup. Sign in again.')));
    return () => { live = false; };
  }, [mfa?.setup, mfa?.token]);

  // Enrolment finished: the recovery codes are shown once, then the user goes on.
  if (recoveryCodes) {
    return (
      <Box data-testid="mfa-recovery-codes">
        <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>Save your recovery codes</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          If you lose your phone, each of these signs you in once. Store them somewhere safe —
          they will not be shown again.
        </Typography>
        <Box component="pre" sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 2, fontFamily: 'monospace',
          display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 1, m: 0, mb: 2 }}>
          {recoveryCodes.map((c) => <span key={c}>{c}</span>)}
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={() => navigator.clipboard?.writeText(recoveryCodes.join('\n'))}>
            Copy
          </Button>
          <Button variant="contained" onClick={() => {
            dispatch(clearRecoveryCodes());
            onSignedIn(user?.role);
          }}>
            I have saved them
          </Button>
        </Stack>
      </Box>
    );
  }

  if (!mfa) return null;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const result = await dispatch(mfa.setup ? enableMfa(code) : verifyMfa(code));
    if (result.meta.requestStatus === 'fulfilled' && !mfa.setup) {
      onSignedIn((result.payload as any)?.user?.role);
    }
    if (result.meta.requestStatus === 'rejected') setCode('');
  };

  return (
    <Box component="form" onSubmit={submit} data-testid="mfa-step">
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
        <Security color="primary" />
        <Typography variant="h6" sx={{ fontWeight: 700 }}>
          {mfa.setup ? 'Set up two-step sign-in' : 'Two-step sign-in'}
        </Typography>
      </Stack>

      {mfa.setup ? (
        <>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Your account needs an authenticator app (Google Authenticator, Microsoft Authenticator,
            1Password…). Scan this code with it, then enter the 6-digit code it shows.
          </Typography>
          {setupError && <Alert severity="error" sx={{ mb: 2 }}>{setupError}</Alert>}
          {!setup && !setupError && <CircularProgress size={24} sx={{ mb: 2 }} />}
          {setup && (
            <Box sx={{ textAlign: 'center', mb: 2 }}>
              {qr && <Box component="img" src={qr} alt="QR code for your authenticator app" sx={{ width: 200, height: 200 }} />}
              <Typography variant="caption" color="text.secondary" component="div">Can't scan? Enter this key:</Typography>
              <Typography sx={{ fontFamily: 'monospace', fontWeight: 600, letterSpacing: 1 }} data-testid="mfa-secret">
                {groupSecret(setup.secret)}
              </Typography>
            </Box>
          )}
        </>
      ) : (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {useRecovery
            ? 'Enter one of the recovery codes you saved when you set up two-step sign-in.'
            : 'Enter the 6-digit code from your authenticator app.'}
        </Typography>
      )}

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <TextField
        fullWidth
        autoFocus
        label={useRecovery ? 'Recovery code' : 'Authentication code'}
        value={code}
        onChange={(e) => setCode(useRecovery ? e.target.value : e.target.value.replace(/\D/g, '').slice(0, 6))}
        inputProps={useRecovery
          ? { autoComplete: 'off', maxLength: 11 }
          : { inputMode: 'numeric', autoComplete: 'one-time-code' }}
        sx={{ mb: 2 }}
      />
      <Button type="submit" fullWidth variant="contained" size="large"
        disabled={loading || (useRecovery ? code.trim().length < 10 : code.length !== 6) || (mfa.setup && !setup)}>
        {loading ? 'Checking…' : mfa.setup ? 'Turn on and sign in' : 'Verify'}
      </Button>

      <Stack direction="row" justifyContent="space-between" sx={{ mt: 2 }}>
        <Link component="button" type="button" variant="body2" onClick={() => dispatch(cancelMfa())}>
          Back to sign in
        </Link>
        {!mfa.setup && (
          <Link component="button" type="button" variant="body2" onClick={() => { setUseRecovery(!useRecovery); setCode(''); }}>
            {useRecovery ? 'Use authenticator code' : 'Use a recovery code'}
          </Link>
        )}
      </Stack>
    </Box>
  );
};

export default MfaStep;
