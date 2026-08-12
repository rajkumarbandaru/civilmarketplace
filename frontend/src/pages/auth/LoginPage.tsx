import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
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
  IconButton,
  InputAdornment,
} from '@mui/material';
import {
  Google,
  Apple,
  Visibility,
  VisibilityOff,
  Send,
} from '@mui/icons-material';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { motion } from 'framer-motion';
import { useAppDispatch, useAppSelector } from '../../hooks';
import { login, sendOtp, verifyOtp } from '../../store/slices/authSlice';

const loginSchema = yup.object({
  email: yup.string().email('Invalid email').required('Email is required'),
  password: yup.string().min(6, 'Min 6 chars'),
  otp: yup.string().length(6, 'OTP must be 6 digits'),
});

const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { loading, error } = useAppSelector((state) => state.auth);
  const [loginMode, setLoginMode] = useState<'password' | 'otp'>('password');
  const [showPassword, setShowPassword] = useState(false);
  const [otpSent, setOtpSent] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(loginSchema),
  });

  const onSubmit = async (data: any) => {
    if (loginMode === 'password') {
      const result = await dispatch(login(data));
      if (result.meta.requestStatus === 'fulfilled') {
        navigate('/dashboard');
      }
    } else {
      if (!otpSent) {
        await dispatch(sendOtp({ email: data.email }));
        setOtpSent(true);
      } else {
        const result = await dispatch(verifyOtp(data));
        if (result.meta.requestStatus === 'fulfilled') {
          navigate('/dashboard');
        }
      }
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
          background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
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
          {error && <Alert severity="error" sx={{ mb: 3, borderRadius: 2 }}>{error}</Alert>}

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
            <TextField
              fullWidth
              label="Email"
              margin="normal"
              {...register('email')}
              error={!!errors.email}
              helperText={errors.email?.message}
              sx={{ mb: 2 }}
            />

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
                sx={{ mb: 3 }}
                InputProps={{
                  startAdornment: <InputAdornment position="start"><Send /></InputAdornment>,
                }}
              />
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

          <Box sx={{ display: 'flex', gap: 2 }}>
            <Button
              variant="outlined"
              fullWidth
              startIcon={<Google />}
              sx={{ py: 1.5, borderColor: '#e2e8f0', color: '#475569' }}
            >
              Google
            </Button>
            <Button
              variant="outlined"
              fullWidth
              startIcon={<Apple />}
              sx={{ py: 1.5, borderColor: '#e2e8f0', color: '#475569' }}
            >
              Apple
            </Button>
          </Box>

          <Typography variant="body2" sx={{ textAlign: 'center', mt: 3, color: '#64748b' }}>
            Don't have an account?{' '}
            <Link to="/register" style={{ color: '#667eea', fontWeight: 600, textDecoration: 'none' }}>
              Register
            </Link>
          </Typography>
        </CardContent>
      </Card>
    </motion.div>
  );
};

export default LoginPage;
