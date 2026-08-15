import React, { useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Box, CircularProgress, Typography } from '@mui/material';
import { useAppDispatch } from '../../hooks';
import { setSocialCredentials } from '../../store/slices/authSlice';
import { landingPathFor } from '../../components/AdminRoute';

/**
 * Landing page for the OAuth2 success redirect.
 *
 * The auth-service sends the browser here with the freshly minted tokens on the
 * query string; we move them into the store (and this tab's session storage) and then replace
 * the history entry so the tokens do not sit in the address bar or in history.
 */
const OAuth2RedirectPage: React.FC = () => {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const handled = useRef(false);

  useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    const error = params.get('error');
    const accessToken = params.get('accessToken');
    const refreshToken = params.get('refreshToken');

    if (error || !accessToken || !refreshToken) {
      navigate(`/login?error=${encodeURIComponent(error || 'Social login failed')}`, {
        replace: true,
      });
      return;
    }

    const user = {
      id: Number(params.get('userId')) || 0,
      name: params.get('name') || '',
      email: params.get('email') || '',
      phone: '',
      profilePicture: params.get('picture') || '',
      role: params.get('role') || 'CUSTOMER',
      emailVerified: true,
      phoneVerified: false,
      status: 'ACTIVE',
      provider: params.get('provider') || undefined,
    };

    dispatch(setSocialCredentials({ user, accessToken, refreshToken }));
    navigate(landingPathFor(user.role), { replace: true });
  }, [params, dispatch, navigate]);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2, py: 8 }}>
      <CircularProgress />
      <Typography variant="body2" sx={{ color: '#64748b' }}>
        Signing you in…
      </Typography>
    </Box>
  );
};

export default OAuth2RedirectPage;
