import React, { useState } from 'react';
import {
  Box,
  Container,
  Grid,
  Card,
  CardContent,
  Typography,
  TextField,
  Button,
  Avatar,
  Divider,
  Chip,
  IconButton,
  Tab,
  Tabs,
} from '@mui/material';
import {
  Edit,
  CameraAlt,
  Save,
  LocationOn,
  Phone,
  Email,
  Work,
} from '@mui/icons-material';
import { motion } from 'framer-motion';
import { useAppSelector } from '../../hooks';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index }) => (
  <div role="tabpanel" hidden={value !== index}>
    {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
  </div>
);

const ProfilePage: React.FC = () => {
  const { user } = useAppSelector((state) => state.auth);
  const [tabValue, setTabValue] = useState(0);
  const [isEditing, setIsEditing] = useState(false);

  const profileInfo = [
    { icon: <Email />, label: 'Email', value: user?.email },
    { icon: <Phone />, label: 'Phone', value: user?.phone || 'Not added' },
    { icon: <LocationOn />, label: 'Location', value: 'Mumbai, India' },
    { icon: <Work />, label: 'Role', value: user?.role || 'Customer' },
  ];

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      {/* Profile Header */}
      <Card sx={{ borderRadius: 3, mb: 4, overflow: 'hidden' }}>
        <Box sx={{
          background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
          p: 4,
          position: 'relative',
        }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 3 }}>
            <Box sx={{ position: 'relative' }}>
              <Avatar
                src={user?.profilePicture}
                sx={{
                  width: 100,
                  height: 100,
                  border: '4px solid #fff',
                  bgcolor: '#eef2ff',
                  color: '#667eea',
                  fontSize: '2.5rem',
                }}
              >
                {user?.name?.charAt(0)}
              </Avatar>
              <IconButton
                sx={{
                  position: 'absolute',
                  bottom: 0,
                  right: 0,
                  bgcolor: '#fff',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
                  '&:hover': { bgcolor: '#f1f5f9' },
                }}
                size="small"
              >
                <CameraAlt sx={{ fontSize: 18, color: '#667eea' }} />
              </IconButton>
            </Box>
            <Box sx={{ color: '#fff' }}>
              <Typography variant="h4" sx={{ fontWeight: 800 }}>
                {user?.name}
              </Typography>
              <Typography variant="body1" sx={{ opacity: 0.8 }}>
                {user?.role} • Member since 2026
              </Typography>
              <Box sx={{ display: 'flex', gap: 1, mt: 1 }}>
                <Chip
                  label="Verified"
                  size="small"
                  sx={{ bgcolor: 'rgba(255,255,255,0.2)', color: '#fff', fontWeight: 600 }}
                />
                <Chip
                  label={user?.emailVerified ? 'Email Verified' : 'Verify Email'}
                  size="small"
                  sx={{ bgcolor: 'rgba(255,255,255,0.2)', color: '#fff', fontWeight: 600 }}
                />
              </Box>
            </Box>
          </Box>

          <Button
            variant="contained"
            startIcon={<Edit />}
            onClick={() => setIsEditing(!isEditing)}
            sx={{
              position: 'absolute',
              top: 24,
              right: 24,
              bgcolor: '#fff',
              color: '#667eea',
              '&:hover': { bgcolor: '#f1f5f9' },
            }}
          >
            {isEditing ? 'Cancel' : 'Edit Profile'}
          </Button>
        </Box>

        {/* Profile Info Cards */}
        <CardContent sx={{ p: 3 }}>
          <Grid container spacing={2}>
            {profileInfo.map((info, idx) => (
              <Grid item xs={6} md={3} key={idx}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, p: 2, bgcolor: '#f8fafc', borderRadius: 2 }}>
                  <Box sx={{ color: '#667eea' }}>{info.icon}</Box>
                  <Box>
                    <Typography variant="caption" sx={{ color: '#94a3b8' }}>
                      {info.label}
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {info.value}
                    </Typography>
                  </Box>
                </Box>
              </Grid>
            ))}
          </Grid>
        </CardContent>
      </Card>

      {/* Tabs Section */}
      <Card sx={{ borderRadius: 3 }}>
        <Box sx={{ borderBottom: 1, borderColor: 'divider', px: 3 }}>
          <Tabs value={tabValue} onChange={(_, val) => setTabValue(val)}>
            <Tab label="Personal Info" />
            <Tab label="Addresses" />
            <Tab label="Account Settings" />
          </Tabs>
        </Box>

        <CardContent sx={{ p: 4 }}>
          <TabPanel value={tabValue} index={0}>
            <Typography variant="h6" sx={{ fontWeight: 700, mb: 3 }}>
              Personal Information
            </Typography>
            <Grid container spacing={3}>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Full Name"
                  defaultValue={user?.name}
                  disabled={!isEditing}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Email"
                  defaultValue={user?.email}
                  disabled={!isEditing}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Phone"
                  defaultValue={user?.phone}
                  disabled={!isEditing}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Date of Birth"
                  type="date"
                  InputLabelProps={{ shrink: true }}
                  disabled={!isEditing}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  label="Bio"
                  multiline
                  rows={3}
                  placeholder="Tell us about yourself..."
                  disabled={!isEditing}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  label="Languages"
                  placeholder="English, Hindi, Marathi..."
                  disabled={!isEditing}
                />
              </Grid>
            </Grid>

            {isEditing && (
              <Button
                variant="contained"
                startIcon={<Save />}
                sx={{ mt: 3, borderRadius: 2 }}
              >
                Save Changes
              </Button>
            )}
          </TabPanel>

          <TabPanel value={tabValue} index={1}>
            <Typography variant="h6" sx={{ fontWeight: 700, mb: 3 }}>
              Saved Addresses
            </Typography>

            {[1, 2].map((addr) => (
              <Box
                key={addr}
                sx={{
                  p: 2,
                  bgcolor: '#f8fafc',
                  borderRadius: 2,
                  mb: 2,
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <LocationOn sx={{ color: '#667eea' }} />
                  <Box>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {addr === 1 ? 'Home' : 'Office'}
                    </Typography>
                    <Typography variant="body2" sx={{ color: '#64748b' }}>
                      {addr === 1
                        ? '123, Main Street, Andheri West, Mumbai - 400053'
                        : '456, Business Park, BKC, Mumbai - 400051'}
                    </Typography>
                  </Box>
                </Box>
                <Chip
                  label={addr === 1 ? 'Default' : ''}
                  size="small"
                  color="primary"
                  variant="outlined"
                />
              </Box>
            ))}

            <Button variant="outlined" startIcon={<LocationOn />} sx={{ borderRadius: 2 }}>
              Add New Address
            </Button>
          </TabPanel>

          <TabPanel value={tabValue} index={2}>
            <Typography variant="h6" sx={{ fontWeight: 700, mb: 3 }}>
              Account Settings
            </Typography>

            <Box sx={{ maxWidth: 400 }}>
              <TextField
                fullWidth
                label="Current Password"
                type="password"
                sx={{ mb: 2 }}
              />
              <TextField
                fullWidth
                label="New Password"
                type="password"
                sx={{ mb: 2 }}
              />
              <TextField
                fullWidth
                label="Confirm New Password"
                type="password"
                sx={{ mb: 3 }}
              />
              <Button variant="contained" sx={{ borderRadius: 2 }}>
                Update Password
              </Button>
            </Box>
          </TabPanel>
        </CardContent>
      </Card>
    </Container>
  );
};

export default ProfilePage;
