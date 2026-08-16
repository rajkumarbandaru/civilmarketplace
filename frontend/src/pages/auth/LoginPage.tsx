import React, { useState, useEffect } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Box,
  Card,
  CardContent,
  Typography,
  TextField,
  Button,
  Divider,
  Alert,
  ToggleButtonGroup,
  ToggleButton,
  Checkbox,
  IconButton,
  InputAdornment,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import {
  Visibility,
  VisibilityOff,
  Send,
  StorefrontOutlined,
} from '@mui/icons-material';
import { useForm, Controller, Resolver } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { CountryCode, isValidPhoneNumber, parsePhoneNumber } from 'libphonenumber-js';
import { motion } from 'framer-motion';
import { useAppDispatch, useAppSelector } from '../../hooks';
import {
  login,
  sendOtp,
  verifyOtp,
  clearError,
  setRememberMe as setRememberMeAction,
  OtpIdentifier,
} from '../../store/slices/authSlice';
import PhoneNumberField from '../../components/form/PhoneNumberField';
import { landingPathFor } from '../../components/AdminRoute';
import { SOCIAL_PROVIDERS, startSocialLogin } from '../../services/socialAuth';
import { stashRememberIntent } from '../../services/authStorage';

const emailField = yup.string().email('Invalid email').required('Email is required');

const phoneField = yup
  .string()
  .required('Mobile number is required')
  .test('valid-phone', 'Enter a valid mobile number for the selected country',
    function validate(value) {
      const country = this.options.context?.country as CountryCode | undefined;
      return !!value && !!country && isValidPhoneNumber(value, country);
    });

const otpField = yup
  .string()
  .matches(/^\d{6}$/, 'OTP must be 6 digits')
  .required('OTP is required');

const passwordSchema = yup.object({
  email: emailField,
  password: yup.string().min(6, 'Min 6 chars').required('Password is required'),
});

// Before the OTP is sent we only collect the identifier; afterwards the code too.
const otpEmailRequestSchema = yup.object({ email: emailField });
const otpEmailVerifySchema = yup.object({ email: emailField, otp: otpField });
const otpPhoneRequestSchema = yup.object({ phone: phoneField });
const otpPhoneVerifySchema = yup.object({ phone: phoneField, otp: otpField });

/** How the OTP is delivered. `sms` and `whatsapp` both go to the mobile number. */
type OtpChannel = 'email' | 'sms' | 'whatsapp';

const usesPhone = (channel: OtpChannel) => channel !== 'email';

/** How each channel is described to the user. One source so notices cannot word it differently. */
const CHANNEL_PHRASE: Record<OtpChannel, string> = {
  email: 'by email',
  sms: 'by SMS',
  whatsapp: 'on WhatsApp',
};

/** Human label for the channel buttons. */
const CHANNEL_LABEL: Record<OtpChannel, string> = {
  email: 'Email',
  sms: 'SMS',
  whatsapp: 'WhatsApp',
};

/** Wire value the backend's OtpRequest.channel expects. */
const channelParam = (channel: OtpChannel) =>
  channel.toUpperCase() as 'EMAIL' | 'SMS' | 'WHATSAPP';

interface LoginFormValues {
  email?: string;
  phone?: string;
  password?: string;
  otp?: string;
}

/** Marketplace is India-first, so default the dialling code accordingly. */
const DEFAULT_COUNTRY: CountryCode = 'IN';

// Resend cooldown enforced by the backend (app.otp.resend-cooldown-seconds, default 30).
const RESEND_COOLDOWN_SECONDS = 30;

const LoginPage: React.FC = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { loading, error } = useAppSelector((state) => state.auth);
  const [loginMode, setLoginMode] = useState<'password' | 'otp'>('password');
  const [showPassword, setShowPassword] = useState(false);
  // Applies to every route in: password, OTP and social all end in the same place.
  const [rememberMe, setRememberMe] = useState(false);
  const [otpSent, setOtpSent] = useState(false);
  const [otpChannel, setOtpChannel] = useState<OtpChannel>('email');
  const [country, setCountry] = useState<CountryCode>(DEFAULT_COUNTRY);
  const [notice, setNotice] = useState<string | null>(null);
  const [cooldown, setCooldown] = useState(0);
  const [searchParams, setSearchParams] = useSearchParams();

  // A failed social login bounces back here as ?error=...; latch it into state and
  // strip it from the URL, so it stays visible but a refresh does not resurrect it.
  const [socialError, setSocialError] = useState<string | null>(null);
  useEffect(() => {
    const err = searchParams.get('error');
    if (!err) return;
    setSocialError(err);
    setSearchParams({}, { replace: true });
  }, [searchParams, setSearchParams]);

  // Drop any stale auth error left over from a previous attempt or page, and reset the OTP flow
  // when the tab changes.
  //
  // Deliberately not keyed on otpChannel any more: switching delivery method after a code has been
  // sent is now a supported action that keeps the user on the code step, and resetting here would
  // wipe `otpSent` out from under it the moment the channel state changed. Channel changes do
  // their own resetting, where the two cases (same number vs new identifier) can be told apart.
  useEffect(() => {
    dispatch(clearError());
    setOtpSent(false);
    setNotice(null);
    setCooldown(0);
    setSocialError(null);
  }, [dispatch, loginMode]);

  // Tick the resend cooldown down to zero.
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  // The four OTP schemas have different shapes, so the union is widened here and
  // re-typed at the resolver rather than fought field by field.
  const activeSchema = ((): yup.ObjectSchema<any> => {
    if (loginMode === 'password') return passwordSchema;
    if (usesPhone(otpChannel)) return otpSent ? otpPhoneVerifySchema : otpPhoneRequestSchema;
    return otpSent ? otpEmailVerifySchema : otpEmailRequestSchema;
  })();

  const {
    register,
    control,
    handleSubmit,
    getValues,
    setValue,
    setFocus,
    formState: { errors },
  } = useForm<LoginFormValues>({
    // The active schema changes with the step, so the resolver is cast to the
    // widest shape the form can hold rather than the current step's shape.
    resolver: yupResolver(activeSchema) as Resolver<LoginFormValues>,
    // The mobile-number rule validates against the selected country.
    context: { country },
  });

  // Put the cursor in the code box as soon as the OTP step appears.
  useEffect(() => {
    if (otpSent) setFocus('otp');
  }, [otpSent, setFocus]);

  /** Builds the identifier payload the OTP endpoints expect. */
  const otpIdentifier = (values: LoginFormValues): OtpIdentifier =>
    usesPhone(otpChannel)
      // The API takes E.164; the field holds the prettified national form.
      ? { phone: parsePhoneNumber(values.phone || '', country).number }
      : { email: values.email };

  const requestOtp = async (values: LoginFormValues) => {
    setNotice(null);
    const identifier = otpIdentifier(values);
    // Only the send call carries the channel; verification is keyed on the identifier,
    // so a code sent over WhatsApp verifies through the same phone-number path as SMS.
    const result = await dispatch(sendOtp({ ...identifier, channel: channelParam(otpChannel) }));
    // Only advance to the code step if the OTP actually went out — otherwise the
    // user is stranded on a "Verify OTP" screen for a code that was never sent.
    if (result.meta.requestStatus === 'fulfilled') {
      setOtpSent(true);
      setCooldown(RESEND_COOLDOWN_SECONDS);
      setNotice(
        `We sent a 6-digit code ${CHANNEL_PHRASE[otpChannel]} to ${identifier.phone || identifier.email}. ` +
          'It expires in 5 minutes.'
      );
    }
  };

  const onSubmit = async (data: any) => {
    if (loginMode === 'password') {
      const result = await dispatch(login(data));
      if (result.meta.requestStatus === 'fulfilled') {
        // After the tokens are in the store, so the reducer has a refresh token to remember.
        dispatch(setRememberMeAction(rememberMe));
        navigate(landingPathFor((result.payload as any)?.user?.role));
      }
      return;
    }

    if (!otpSent) {
      await requestOtp(data);
    } else {
      const result = await dispatch(verifyOtp({ ...otpIdentifier(data), otp: data.otp }));
      if (result.meta.requestStatus === 'fulfilled') {
        dispatch(setRememberMeAction(rememberMe));
        navigate(landingPathFor((result.payload as any)?.user?.role));
      }
    }
  };

  const handleResend = () => {
    if (cooldown > 0) return;
    requestOtp(getValues());
  };

  const handleChangeIdentifier = () => {
    dispatch(clearError());
    setOtpSent(false);
    setNotice(null);
    setCooldown(0);
  };

  /**
   * Switches how the code is delivered after one has already been sent.
   *
   * Two different journeys behind one control, because the identifier decides which:
   *
   * - SMS ↔ WhatsApp keep the same mobile number, so the code can go straight back out and the
   *   user stays on the code step. Sending them back to re-type a number they just entered would
   *   be pure friction — nothing about it changed.
   * - Anything involving email changes the identifier itself, so it returns to the request step
   *   with the field editable. Silently reusing the phone number as an email address is not
   *   possible, and guessing an address the user never gave is worse.
   *
   * The resend cooldown deliberately does *not* gate this. It exists to stop repeat sends to the
   * same destination; a user switching channel is telling us the first one is not reaching them,
   * and making them wait on a channel that is not working is the opposite of helpful.
   */
  const handleChangeChannel = async (next: OtpChannel) => {
    if (next === otpChannel) return;

    dispatch(clearError());
    setNotice(null);
    setValue('otp', '');
    setOtpChannel(next);

    const sameMedium = usesPhone(next) === usesPhone(otpChannel);
    if (!sameMedium) {
      setOtpSent(false);
      setCooldown(0);
      return;
    }

    // Send on the new channel straight away. requestOtp reads `otpChannel` from state, which has
    // not re-rendered yet, so the channel is passed explicitly rather than read back.
    const values = getValues();
    const identifier = otpIdentifier(values);
    const result = await dispatch(sendOtp({ ...identifier, channel: channelParam(next) }));
    if (result.meta.requestStatus === 'fulfilled') {
      setOtpSent(true);
      setCooldown(RESEND_COOLDOWN_SECONDS);
      setNotice(
        `We sent a new 6-digit code ${CHANNEL_PHRASE[next]} to ${identifier.phone}. ` +
          'It expires in 5 minutes.'
      );
    } else {
      // The switch failed, so the user is still waiting on the original code — say so rather than
      // leaving them on a screen that claims a channel nothing was sent through.
      setOtpChannel(otpChannel);
    }
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4 }}
    >
      <Card sx={{ borderRadius: 4, overflow: 'hidden' }}>
        <Box sx={{
          background: (t) => `linear-gradient(135deg, ${t.palette.primary.main} 0%, ${t.palette.secondary.main} 100%)`,
          p: 4,
          textAlign: 'center',
        }}>
          <Typography variant="h4" sx={{ color: '#fff', fontWeight: 800, fontFamily: "'Poppins', sans-serif" }}>
            Welcome Back
          </Typography>
          <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.7)', mt: 1 }}>
            Sign in to your account
          </Typography>
        </Box>

        <CardContent sx={{ p: 4 }}>
          {(error || socialError) && (
            <Alert severity="error" sx={{ mb: 3, borderRadius: 2 }}>{error || socialError}</Alert>
          )}
          {!error && !socialError && notice && (
            <Alert severity="success" sx={{ mb: 3, borderRadius: 2 }}>{notice}</Alert>
          )}

          <ToggleButtonGroup
            value={loginMode}
            exclusive
            onChange={(_, val) => { if (val) { setLoginMode(val); setOtpSent(false); } }}
            size="small"
            fullWidth
            sx={{ mb: 3 }}
          >
            <ToggleButton value="password" sx={{ borderRadius: '8px 0 0 8px' }}>Password</ToggleButton>
            <ToggleButton value="otp" sx={{ borderRadius: '0 8px 8px 0' }}>OTP Login</ToggleButton>
          </ToggleButtonGroup>

          <form onSubmit={handleSubmit(onSubmit)}>
            {loginMode === 'otp' && (
              <ToggleButtonGroup
                value={otpChannel}
                exclusive
                onChange={(_, val) => { if (val) setOtpChannel(val); }}
                size="small"
                fullWidth
                disabled={otpSent}
                sx={{ mb: 2 }}
              >
                {(['email', 'sms', 'whatsapp'] as OtpChannel[]).map((channel) => (
                  <ToggleButton key={channel} value={channel}>
                    {CHANNEL_LABEL[channel]}
                  </ToggleButton>
                ))}
              </ToggleButtonGroup>
            )}

            {loginMode === 'otp' && usesPhone(otpChannel) ? (
              <Box sx={{ mb: 2 }}>
                <Controller
                  name="phone"
                  control={control}
                  defaultValue=""
                  render={({ field }) => (
                    <PhoneNumberField
                      label="Mobile number"
                      country={country}
                      onCountryChange={setCountry}
                      value={field.value || ''}
                      onChange={field.onChange}
                      onBlur={field.onBlur}
                      error={!!errors.phone}
                      helperText={errors.phone?.message}
                    />
                  )}
                />
              </Box>
            ) : (
              <TextField
                fullWidth
                label="Email"
                margin="normal"
                {...register('email')}
                error={!!errors.email}
                helperText={errors.email?.message}
                // Locked once the code is out, so the address the OTP was sent to
                // always matches the address we verify against. readOnly rather than
                // disabled so the value still reaches the submit handler.
                InputProps={{ readOnly: loginMode === 'otp' && otpSent }}
                sx={{ mb: 2 }}
              />
            )}

            {loginMode === 'password' && (
              <TextField
                fullWidth
                label="Password"
                type={showPassword ? 'text' : 'password'}
                margin="normal"
                {...register('password')}
                error={!!errors.password}
                helperText={errors.password?.message}
                sx={{ mb: 3 }}
                InputProps={{
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton onClick={() => setShowPassword(!showPassword)} edge="end">
                        {showPassword ? <VisibilityOff /> : <Visibility />}
                      </IconButton>
                    </InputAdornment>
                  ),
                }}
              />
            )}
            {loginMode === 'otp' && otpSent && (
              <TextField
                fullWidth
                label="Enter OTP"
                margin="normal"
                {...register('otp')}
                error={!!errors.otp}
                helperText={errors.otp?.message}
                sx={{ mb: 1 }}
                inputProps={{ inputMode: 'numeric', maxLength: 6, autoComplete: 'one-time-code' }}
                InputProps={{
                  startAdornment: <InputAdornment position="start"><Send /></InputAdornment>,
                }}
              />
            )}
            {loginMode === 'otp' && otpSent && (
              <>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                  <Button
                    size="small"
                    onClick={handleChangeIdentifier}
                    sx={{ textTransform: 'none' }}
                  >
                    {usesPhone(otpChannel) ? 'Change number' : 'Change email'}
                  </Button>
                  <Button
                    size="small"
                    onClick={handleResend}
                    disabled={cooldown > 0 || loading}
                    sx={{ textTransform: 'none' }}
                  >
                    {cooldown > 0 ? `Resend in ${cooldown}s` : 'Resend code'}
                  </Button>
                </Box>

                {/*
                  Changing the delivery method, alongside changing the destination. Resending on a
                  channel that is not arriving just repeats the failure — an SMS that never lands
                  because the number is roaming or the operator is dropping it will not land on the
                  second attempt either, and until now the only way out was to abandon the flow.
                */}
                <Box sx={{ mb: 3 }}>
                  <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                    Not arriving? Get the code another way:
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 1, mt: 0.5, flexWrap: 'wrap' }}>
                    {(['sms', 'whatsapp', 'email'] as OtpChannel[])
                      .filter((channel) => channel !== otpChannel)
                      .map((channel) => (
                        <Button
                          key={channel}
                          size="small"
                          variant="outlined"
                          disabled={loading}
                          onClick={() => handleChangeChannel(channel)}
                          sx={{ textTransform: 'none' }}
                        >
                          {/* Names what will happen: the same number needs no re-entry, a switch
                              between phone and email does. */}
                          {usesPhone(channel) === usesPhone(otpChannel)
                            ? `Send ${CHANNEL_PHRASE[channel]}`
                            : `Use ${CHANNEL_LABEL[channel].toLowerCase()} instead`}
                        </Button>
                      ))}
                  </Box>
                </Box>
              </>
            )}

            {/*
              Shown on the password step and on the OTP entry step — the two points where a sign-in
              is about to complete. Hidden while an OTP is merely being requested, where there is
              no session yet for the choice to apply to.
            */}
            {(loginMode === 'password' || otpSent) && (
              // Laid out by hand rather than with FormControlLabel: the box and its text sit on one
              // baseline, and only the box itself toggles — clicking the wording does nothing, so a
              // stray click near the text cannot silently change how long the session is kept.
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                <Checkbox
                  checked={rememberMe}
                  onChange={(event) => setRememberMe(event.target.checked)}
                  size="small"
                  inputProps={{ 'aria-label': 'Keep me signed in on this device' }}
                  sx={{ p: 0.5 }}
                />
                <Typography variant="body2">
                  Keep me signed in on this device
                </Typography>
              </Box>
            )}

            <Button
              type="submit"
              variant="contained"
              fullWidth
              size="large"
              disabled={loading}
              sx={{ py: 1.5, mb: 2 }}
            >
              {loading ? 'Loading...' : loginMode === 'password' ? 'Sign In' : otpSent ? 'Verify OTP' : 'Send OTP'}
            </Button>
          </form>

          <Divider sx={{ my: 3 }}>
            <Typography variant="body2" sx={{ color: '#94a3b8', px: 2 }}>OR</Typography>
          </Divider>

          <Box sx={{ display: 'flex', gap: 1.5 }}>
            {SOCIAL_PROVIDERS.map(({ id, label, icon: Icon, color }) => (
              <Button
                key={id}
                variant="outlined"
                fullWidth
                onClick={() => {
                  // Stashed before leaving the page — the redirect destroys component state.
                  stashRememberIntent(rememberMe);
                  startSocialLogin(id);
                }}
                startIcon={<Icon sx={{ color }} />}
                sx={{ py: 1.5, borderColor: '#e2e8f0', color: '#475569' }}
              >
                {label}
              </Button>
            ))}
          </Box>

          <Typography variant="body2" sx={{ textAlign: 'center', mt: 3, color: '#64748b' }}>
            Don't have an account?{' '}
            <Link to="/register" style={{ color: theme.palette.primary.main, fontWeight: 600, textDecoration: 'none' }}>
              Register
            </Link>
          </Typography>

          {/*
            An escape hatch for visitors who landed here but only want to look around: most of the
            marketplace is browsable signed-out, so make that reachable instead of dead-ending them
            at a form they have no credentials for.
          */}
          <Button
            component={Link}
            to="/"
            fullWidth
            startIcon={<StorefrontOutlined />}
            sx={{ mt: 1.5, textTransform: 'none', color: 'text.secondary' }}
          >
            Continue browsing without signing in
          </Button>
        </CardContent>
      </Card>
    </motion.div>
  );
};

export default LoginPage;
