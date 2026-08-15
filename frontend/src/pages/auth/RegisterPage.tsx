import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Box,
  Card,
  CardContent,
  Typography,
  TextField,
  Button,
  IconButton,
  InputAdornment,
  Alert,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  ListSubheader,
  ToggleButton,
  ToggleButtonGroup,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Visibility, VisibilityOff } from '@mui/icons-material';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { CountryCode, isValidPhoneNumber, parsePhoneNumber } from 'libphonenumber-js';
import { motion } from 'framer-motion';
import { useAppDispatch, useAppSelector } from '../../hooks';
import {
  register as registerUser,
  sendOtp,
  verifyOtp,
  clearError,
} from '../../store/slices/authSlice';
import PhoneNumberField from '../../components/form/PhoneNumberField';
import PasswordStrengthMeter from '../../components/form/PasswordStrengthMeter';
import { SIGNUP_ROLES, SIGNUP_ROLE_GROUPS } from '../../constants/roles';

/** Marketplace is India-first, so default the dialling code accordingly. */
const DEFAULT_COUNTRY: CountryCode = 'IN';

/** Where the account-verification code is sent. */
type VerificationChannel = 'EMAIL' | 'SMS' | 'WHATSAPP';

const CHANNEL_LABELS: Record<VerificationChannel, string> = {
  EMAIL: 'by email',
  SMS: 'by SMS',
  WHATSAPP: 'on WhatsApp',
};

// Resend cooldown enforced by the backend (app.otp.resend-cooldown-seconds, default 30).
const RESEND_COOLDOWN_SECONDS = 30;

const schema = yup.object({
  name: yup
    .string()
    .trim()
    .min(2, 'Min 2 characters')
    .max(60, 'Max 60 characters')
    .required('Name is required'),
  email: yup.string().email('Invalid email').required('Email is required'),
  password: yup
    .string()
    .min(8, 'Min 8 characters')
    .matches(/^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).*$/,
      'Must include uppercase, lowercase, number & special char')
    .required('Password is required'),
  confirmPassword: yup.string()
    .oneOf([yup.ref('password')], 'Passwords must match')
    .required('Confirm password is required'),
  // Phone is mandatory. It is validated against the selected country rather than a
  // single regex, so a valid local number is never rejected for its formatting.
  phone: yup
    .string()
    .required('Phone number is required')
    .test('valid-phone', 'Enter a valid phone number for the selected country',
      function validate(value) {
        const country = this.options.context?.country as CountryCode | undefined;
        if (!value || !country) return false;
        return isValidPhoneNumber(value, country);
      }),
});

const RegisterPage: React.FC = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { loading, error } = useAppSelector((state) => state.auth);
  const [showPassword, setShowPassword] = useState(false);
  const [role, setRole] = useState('CUSTOMER');
  const [channel, setChannel] = useState<VerificationChannel>('EMAIL');
  // Registration already returns a session, so this second step verifies the account
  // rather than gating access — hence the "Skip for now" escape below.
  const [verifying, setVerifying] = useState<{ email?: string; phone?: string } | null>(null);
  const [code, setCode] = useState('');
  const [codeError, setCodeError] = useState<string | null>(null);
  const [cooldown, setCooldown] = useState(RESEND_COOLDOWN_SECONDS);

  // Drop any stale auth error left over from a previous attempt or page
  useEffect(() => {
    dispatch(clearError());
  }, [dispatch]);

  const [country, setCountry] = useState<CountryCode>(DEFAULT_COUNTRY);

  const {
    register,
    control,
    watch,
    handleSubmit,
    trigger,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(schema),
    // The phone rule needs the selected country to validate against.
    context: { country },
  });

  const password = watch('password') || '';
  const phone = watch('phone');

  // Re-check the number when the country changes: the same digits can be valid in
  // one country and invalid in another.
  useEffect(() => {
    if (phone) trigger('phone');
  }, [country, phone, trigger]);

  // Tick the resend cooldown down to zero.
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown((sec) => sec - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  const onSubmit = async (data: any) => {
    // Submit in E.164 — the display formatting is for the user, not the API.
    const e164 = parsePhoneNumber(data.phone, country).number;

    const result = await dispatch(registerUser({
      name: data.name.trim(),
      email: data.email,
      password: data.password,
      phone: e164,
      role,
      verificationChannel: channel,
    }));
    if (result.meta.requestStatus === 'fulfilled') {
      // The backend sends the verification code as part of registration; this step only
      // collects it. The identifier must match the channel it was sent to.
      setVerifying(channel === 'EMAIL' ? { email: data.email } : { phone: e164 });
      setCooldown(RESEND_COOLDOWN_SECONDS);
    }
  };

  const submitCode = async () => {
    if (!verifying) return;
    setCodeError(null);
    if (!/^\d{6}$/.test(code)) {
      setCodeError('Enter the 6-digit code');
      return;
    }
    const result = await dispatch(verifyOtp({ ...verifying, otp: code }));
    if (result.meta.requestStatus === 'fulfilled') {
      navigate('/dashboard');
    }
  };

  const resendCode = () => {
    if (cooldown > 0 || !verifying) return;
    dispatch(sendOtp({ ...verifying, channel }));
    setCooldown(RESEND_COOLDOWN_SECONDS);
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
            Create Account
          </Typography>
          <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.7)', mt: 1 }}>
            Join India's #1 civil engineering marketplace
          </Typography>
        </Box>

        <CardContent sx={{ p: 4 }}>
          {error && <Alert severity="error" sx={{ mb: 3, borderRadius: 2 }}>{error}</Alert>}

          {verifying ? (
            <Box>
              <Alert severity="success" sx={{ mb: 3, borderRadius: 2 }}>
                Account created. We sent a 6-digit code {CHANNEL_LABELS[channel]} to{' '}
                {verifying.email || verifying.phone}. It expires in 5 minutes.
              </Alert>

              <TextField
                fullWidth
                autoFocus
                label="Enter verification code"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                error={!!codeError}
                helperText={codeError}
                inputProps={{ inputMode: 'numeric', maxLength: 6, autoComplete: 'one-time-code' }}
                sx={{ mb: 2 }}
              />

              <Button
                variant="contained"
                fullWidth
                size="large"
                disabled={loading}
                onClick={submitCode}
                sx={{ py: 1.5, mb: 1 }}
              >
                {loading ? 'Verifying...' : 'Verify Account'}
              </Button>

              <Button fullWidth disabled={cooldown > 0} onClick={resendCode} sx={{ mb: 1 }}>
                {cooldown > 0 ? `Resend code in ${cooldown}s` : 'Resend code'}
              </Button>

              {/* Registration already signed the user in, so verification is not a gate —
                  it can be completed later from the profile. */}
              <Button fullWidth onClick={() => navigate('/dashboard')}>
                Skip for now
              </Button>
            </Box>
          ) : (
          <form onSubmit={handleSubmit(onSubmit)}>
            <TextField
              fullWidth
              label="Full Name"
              margin="normal"
              {...register('name')}
              error={!!errors.name}
              helperText={errors.name?.message}
              sx={{ mb: 2 }}
            />

            <TextField
              fullWidth
              label="Email"
              margin="normal"
              {...register('email')}
              error={!!errors.email}
              helperText={errors.email?.message}
              sx={{ mb: 2 }}
            />

            <Box sx={{ mb: 2 }}>
              <Controller
                name="phone"
                control={control}
                defaultValue=""
                render={({ field }) => (
                  <PhoneNumberField
                    required
                    label="Phone"
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

            <FormControl fullWidth sx={{ mb: 2 }}>
              <InputLabel>I am a</InputLabel>
              <Select
                value={role}
                label="I am a"
                onChange={(e) => setRole(e.target.value)}
              >
                {SIGNUP_ROLE_GROUPS.flatMap((group) => [
                  <ListSubheader key={group}>{group}</ListSubheader>,
                  ...SIGNUP_ROLES.filter((r) => r.group === group).map((r) => (
                    <MenuItem key={r.value} value={r.value}>{r.label}</MenuItem>
                  )),
                ])}
              </Select>
            </FormControl>

            <TextField
              fullWidth
              label="Password"
              type={showPassword ? 'text' : 'password'}
              margin="normal"
              {...register('password')}
              error={!!errors.password}
              helperText={errors.password?.message}
              sx={{ mb: 1.5 }}
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

            <PasswordStrengthMeter password={password} />

            <TextField
              fullWidth
              label="Confirm Password"
              type="password"
              margin="normal"
              {...register('confirmPassword')}
              error={!!errors.confirmPassword}
              helperText={errors.confirmPassword?.message}
              sx={{ mb: 3 }}
            />

            <Typography variant="body2" sx={{ mt: 3, mb: 1, color: '#64748b' }}>
              Send my verification code
            </Typography>
            <ToggleButtonGroup
              value={channel}
              exclusive
              onChange={(_, val) => { if (val) setChannel(val); }}
              size="small"
              fullWidth
              sx={{ mb: 3 }}
            >
              <ToggleButton value="EMAIL">Email</ToggleButton>
              <ToggleButton value="SMS">SMS</ToggleButton>
              <ToggleButton value="WHATSAPP">WhatsApp</ToggleButton>
            </ToggleButtonGroup>

            <Button
              type="submit"
              variant="contained"
              fullWidth
              size="large"
              disabled={loading}
              sx={{ py: 1.5, mb: 2 }}
            >
              {loading ? 'Creating Account...' : 'Create Account'}
            </Button>
          </form>
          )}

          <Typography variant="body2" sx={{ textAlign: 'center', mt: 2, color: '#64748b' }}>
            Already have an account?{' '}
            <Link to="/login" style={{ color: theme.palette.primary.main, fontWeight: 600, textDecoration: 'none' }}>
              Sign In
            </Link>
          </Typography>
        </CardContent>
      </Card>
    </motion.div>
  );
};

export default RegisterPage;
