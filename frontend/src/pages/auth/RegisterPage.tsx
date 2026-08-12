import React, { useState } from 'react';
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
} from '@mui/material';
import { Visibility, VisibilityOff } from '@mui/icons-material';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { motion } from 'framer-motion';
import { useAppDispatch, useAppSelector } from '../../hooks';
import { register as registerUser } from '../../store/slices/authSlice';

const schema = yup.object({
  name: yup.string().min(2, 'Min 2 characters').required('Name is required'),
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
  phone: yup.string().matches(/^\+?[1-9]\d{1,14}$/, 'Invalid phone number'),
});

const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { loading, error } = useAppSelector((state) => state.auth);
  const [showPassword, setShowPassword] = useState(false);
  const [role, setRole] = useState('CUSTOMER');

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({ resolver: yupResolver(schema) });

  const onSubmit = async (data: any) => {
    const result = await dispatch(registerUser({
      name: data.name,
      email: data.email,
      password: data.password,
      phone: data.phone,
      role,
    }));
    if (result.meta.requestStatus === 'fulfilled') {
      navigate('/dashboard');
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
            Create Account
          </Typography>
          <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.7)', mt: 1 }}>
            Join India's #1 civil engineering marketplace
          </Typography>
        </Box>

        <CardContent sx={{ p: 4 }}>
          {error && <Alert severity="error" sx={{ mb: 3, borderRadius: 2 }}>{error}</Alert>}

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

            <TextField
              fullWidth
              label="Phone (optional)"
              margin="normal"
              {...register('phone')}
              error={!!errors.phone}
              helperText={errors.phone?.message}
              sx={{ mb: 2 }}
            />

            <FormControl fullWidth sx={{ mb: 2 }}>
              <InputLabel>I am a</InputLabel>
              <Select
                value={role}
                label="I am a"
                onChange={(e) => setRole(e.target.value)}
              >
                <MenuItem value="CUSTOMER">Customer</MenuItem>
                <MenuItem value="WORKER">Worker</MenuItem>
                <MenuItem value="CIVIL_ENGINEER">Civil Engineer</MenuItem>
                <MenuItem value="ARCHITECT">Architect</MenuItem>
                <MenuItem value="SURVEYOR">Surveyor</MenuItem>
                <MenuItem value="LABOUR_CONTRACTOR">Labour Contractor</MenuItem>
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
              sx={{ mb: 2 }}
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

          <Typography variant="body2" sx={{ textAlign: 'center', mt: 2, color: '#64748b' }}>
            Already have an account?{' '}
            <Link to="/login" style={{ color: '#667eea', fontWeight: 600, textDecoration: 'none' }}>
              Sign In
            </Link>
          </Typography>
        </CardContent>
      </Card>
    </motion.div>
  );
};

export default RegisterPage;
