import React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Container,
  Typography,
  Button,
  Grid,
  Card,
  CardContent,
  Avatar,
  Chip,
  TextField,
  InputAdornment,
} from '@mui/material';
import {
  Search,
  Engineering,
  Home,
  Architecture,
  Map,
  DesignServices,
  Construction,
  Router,
  ElectricalServices,
  AcUnit,
  WaterDrop,
  Inventory,
  Agriculture,
  LocalShipping,
  HandymanOutlined,
  Groups,
  AssignmentTurnedIn,
  SupervisorAccount,
  School,
  Work,
  Star,
  Verified,
  Speed,
  Security,
  People,
} from '@mui/icons-material';
import { motion } from 'framer-motion';

const MotionBox = motion(Box);
const MotionCard = motion(Card);

const services = [
  { icon: <Home />, title: 'House Planning', desc: 'Custom home design', color: '#667eea', category: 'architecture' },
  { icon: <Architecture />, title: 'Architecture', desc: 'Architectural design', color: '#764ba2', category: 'architecture' },
  { icon: <Engineering />, title: 'Structural Eng.', desc: 'Structural analysis', color: '#10b981', category: 'engineering' },
  { icon: <Map />, title: 'Survey Services', desc: 'Land surveying', color: '#f59e0b', category: 'survey' },
  { icon: <DesignServices />, title: 'Interior Design', desc: 'Interior decoration', color: '#ef4444', category: 'design' },
  { icon: <Construction />, title: 'Construction', desc: 'Building services', color: '#8b5cf6', category: 'construction' },
  { icon: <ElectricalServices />, title: 'Electrical', desc: 'Electrical work', color: '#06b6d4', category: 'services' },
  { icon: <WaterDrop />, title: 'Plumbing', desc: 'Plumbing services', color: '#3b82f6', category: 'services' },
  { icon: <Home />, title: 'Villa Planning', desc: 'Luxury villa design', color: '#0ea5e9', category: 'architecture' },
  { icon: <Architecture />, title: 'Elevation Design', desc: 'Facade & elevation', color: '#a855f7', category: 'architecture' },
  { icon: <Engineering />, title: 'BIM Modeling', desc: 'Building information modeling', color: '#14b8a6', category: 'engineering' },
  { icon: <Engineering />, title: 'Earthquake Design', desc: 'Seismic-resistant design', color: '#f97316', category: 'engineering' },
  { icon: <Map />, title: 'Drone Survey', desc: 'Aerial site survey', color: '#eab308', category: 'survey' },
  { icon: <Map />, title: 'GIS Mapping', desc: 'Geospatial mapping', color: '#84cc16', category: 'survey' },
  { icon: <DesignServices />, title: '3D Modeling', desc: '3D visualization', color: '#ec4899', category: 'design' },
  { icon: <Construction />, title: 'Renovation', desc: 'Remodeling & repairs', color: '#6366f1', category: 'construction' },
  { icon: <Inventory />, title: 'Material Supply', desc: 'Cement, steel & aggregates', color: '#d97706', category: 'materials' },
  { icon: <Agriculture />, title: 'Equipment Rental', desc: 'Machinery with operator', color: '#65a30d', category: 'equipment' },
  { icon: <LocalShipping />, title: 'Transport & Logistics', desc: 'Material & worker transport', color: '#0891b2', category: 'logistics' },
  { icon: <HandymanOutlined />, title: 'Skilled Labour', desc: 'Masons, carpenters & fitters', color: '#b45309', category: 'labour' },
  { icon: <Groups />, title: 'Daily Wage Labour', desc: 'Muster-roll site labour', color: '#7c3aed', category: 'labour' },
  { icon: <AssignmentTurnedIn />, title: 'Contractor Services', desc: 'End-to-end execution', color: '#dc2626', category: 'construction' },
  { icon: <SupervisorAccount />, title: 'Site Supervision', desc: 'On-site QA/QC & sign-off', color: '#059669', category: 'engineering' },
  { icon: <Work />, title: 'Project Management', desc: 'Scope, budget & milestones', color: '#4f46e5', category: 'management' },
  { icon: <School />, title: 'Skill & Safety Training', desc: 'Certified worker upskilling', color: '#db2777', category: 'training' },
];

const stats = [
  { icon: <People />, value: '10,000+', label: 'Professionals' },
  { icon: <Verified />, value: '50,000+', label: 'Projects Completed' },
  { icon: <Star />, value: '4.8/5', label: 'Average Rating' },
  { icon: <Speed />, value: '100+', label: 'Cities Covered' },
];

const HomePage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <Box>
      {/* Hero Section */}
      <Box
        sx={{
          minHeight: '90vh',
          display: 'flex',
          alignItems: 'center',
          position: 'relative',
          overflow: 'hidden',
          background: (t) => `linear-gradient(135deg, ${t.palette.primary.main} 0%, ${t.palette.secondary.main} 100%)`,
        }}
      >
        {/* Animated background shapes */}
        {[1, 2, 3].map((i) => (
          <Box
            key={i}
            component="div"
            sx={{
              position: 'absolute',
              width: `${300 + i * 200}px`,
              height: `${300 + i * 200}px`,
              borderRadius: '50%',
              background: 'rgba(255,255,255,0.03)',
              top: `${10 + i * 15}%`,
              right: `${-5 + i * 10}%`,
              animation: `float ${5 + i * 2}s ease-in-out infinite`,
              animationDelay: `${i * 0.5}s`,
            }}
          />
        ))}

        <Container maxWidth="xl" sx={{ position: 'relative', zIndex: 1 }}>
          <Grid container spacing={6} alignItems="center">
            <Grid item xs={12} md={7}>
              <MotionBox
                initial={{ opacity: 0, y: 30 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6 }}
              >
                <Chip
                  label="India's #1 Civil Engineering Platform"
                  sx={{
                    bgcolor: 'rgba(255,255,255,0.15)',
                    color: '#fff',
                    fontWeight: 600,
                    mb: 3,
                    backdropFilter: 'blur(10px)',
                  }}
                />
                <Typography
                  variant="h1"
                  sx={{
                    color: '#fff',
                    mb: 3,
                    fontSize: { xs: '2.5rem', md: '3.5rem', lg: '4rem' },
                    lineHeight: 1.1,
                  }}
                >
                  Book Civil Engineering{' '}
                  <Box component="span" sx={{ color: '#fbbf24' }}>
                    Professionals
                  </Box>{' '}
                  Instantly
                </Typography>
                <Typography
                  variant="h5"
                  sx={{
                    color: 'rgba(255,255,255,0.8)',
                    mb: 5,
                    fontWeight: 400,
                    maxWidth: 600,
                    lineHeight: 1.6,
                  }}
                >
                  From architects and structural engineers to surveyors and contractors
                  — find and book trusted civil engineering experts near you, on demand.
                </Typography>

                {/* Search bar */}
                <Box sx={{ display: 'flex', gap: 2, mb: 4, flexWrap: 'wrap' }}>
                  <TextField
                    placeholder="What service do you need?"
                    variant="outlined"
                    sx={{
                      flex: 1,
                      minWidth: 300,
                      '& .MuiOutlinedInput-root': {
                        bgcolor: '#fff',
                        borderRadius: 3,
                        '&:hover fieldset': { borderColor: 'transparent' },
                        '& fieldset': { borderColor: 'transparent' },
                      },
                    }}
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <Search sx={{ color: 'primary.main' }} />
                        </InputAdornment>
                      ),
                    }}
                  />
                  <Button
                    variant="contained"
                    size="large"
                    onClick={() => navigate('/services')}
                    sx={{
                      px: 5,
                      py: 1.5,
                      borderRadius: 3,
                      fontSize: '1rem',
                      bgcolor: '#fbbf24',
                      color: '#1e293b',
                      '&:hover': { bgcolor: '#f59e0b' },
                    }}
                  >
                    Search
                  </Button>
                </Box>

                {/* Trust badges */}
                <Box sx={{ display: 'flex', gap: 3, flexWrap: 'wrap' }}>
                  {['Verified Professionals', 'Secure Payments', '24/7 Support'].map((text) => (
                    <Box key={text} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <Security sx={{ color: '#34d399', fontSize: 20 }} />
                      <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.7)' }}>
                        {text}
                      </Typography>
                    </Box>
                  ))}
                </Box>
              </MotionBox>
            </Grid>

            <Grid item xs={12} md={5}>
              <MotionBox
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ duration: 0.6, delay: 0.2 }}
              >
                <Box
                  sx={{
                    background: 'rgba(255,255,255,0.1)',
                    backdropFilter: 'blur(20px)',
                    borderRadius: 6,
                    p: 4,
                    border: '1px solid rgba(255,255,255,0.2)',
                  }}
                >
                  <Typography variant="h5" sx={{ color: '#fff', mb: 3, fontWeight: 700 }}>
                    Top Rated Services
                  </Typography>
                  <Grid container spacing={2}>
                    {services.slice(0, 6).map((service, idx) => (
                      <Grid item xs={6} key={idx}>
                        <Box
                          sx={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 1.5,
                            p: 1.5,
                            borderRadius: 2,
                            bgcolor: 'rgba(255,255,255,0.08)',
                            cursor: 'pointer',
                            transition: 'all 0.2s',
                            '&:hover': { bgcolor: 'rgba(255,255,255,0.15)', transform: 'translateX(4px)' },
                          }}
                          onClick={() => navigate(`/services`)}
                        >
                          <Avatar sx={{ bgcolor: service.color, width: 36, height: 36 }}>
                            {service.icon}
                          </Avatar>
                          <Box>
                            <Typography variant="body2" sx={{ color: '#fff', fontWeight: 600 }}>
                              {service.title}
                            </Typography>
                            <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.6)' }}>
                              {service.desc}
                            </Typography>
                          </Box>
                        </Box>
                      </Grid>
                    ))}
                  </Grid>
                </Box>
              </MotionBox>
            </Grid>
          </Grid>
        </Container>
      </Box>

      {/* Stats Section */}
      <Container maxWidth="xl" sx={{ py: 8 }}>
        <Grid container spacing={3}>
          {stats.map((stat, idx) => (
            <Grid item xs={6} md={3} key={idx}>
              <MotionCard
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.4, delay: idx * 0.1 }}
                sx={{
                  textAlign: 'center',
                  p: 4,
                  borderRadius: 4,
                }}
              >
                <Avatar
                  sx={{
                    bgcolor: (t) => t.palette.primary.main + '15',
                    color: 'primary.main',
                    width: 56,
                    height: 56,
                    mx: 'auto',
                    mb: 2,
                  }}
                >
                  {stat.icon}
                </Avatar>
                <Typography variant="h4" sx={{ fontWeight: 800, color: '#1e293b' }}>
                  {stat.value}
                </Typography>
                <Typography variant="body2" sx={{ color: '#64748b', mt: 1 }}>
                  {stat.label}
                </Typography>
              </MotionCard>
            </Grid>
          ))}
        </Grid>
      </Container>

      {/* How It Works */}
      <Box sx={{ bgcolor: 'action.hover', py: 10 }}>
        <Container maxWidth="xl">
          <Typography variant="h2" sx={{ textAlign: 'center', mb: 2 }}>
            How It Works
          </Typography>
          <Typography variant="body1" sx={{ textAlign: 'center', color: '#64748b', mb: 8, maxWidth: 600, mx: 'auto' }}>
            Get your civil engineering work done in three simple steps
          </Typography>

          <Grid container spacing={4}>
            {[
              { step: '01', title: 'Describe Your Project', desc: 'Tell us what you need — from house plans to structural analysis' },
              { step: '02', title: 'Get Matched with Experts', desc: 'We connect you with verified professionals in your area' },
              { step: '03', title: 'Book & Track', desc: 'Book instantly and track progress in real-time' },
            ].map((item, idx) => (
              <Grid item xs={12} md={4} key={idx}>
                <MotionBox
                  initial={{ opacity: 0, y: 30 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.5, delay: idx * 0.15 }}
                  sx={{ textAlign: 'center', px: 3 }}
                >
                  <Box
                    sx={{
                      width: 80,
                      height: 80,
                      borderRadius: '50%',
                      background: (t) => `linear-gradient(135deg, ${t.palette.primary.main} 0%, ${t.palette.secondary.main} 100%)`,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      mx: 'auto',
                      mb: 3,
                      color: '#fff',
                      fontSize: '1.5rem',
                      fontWeight: 800,
                    }}
                  >
                    {item.step}
                  </Box>
                  <Typography variant="h5" sx={{ fontWeight: 700, mb: 2 }}>
                    {item.title}
                  </Typography>
                  <Typography variant="body1" sx={{ color: '#64748b' }}>
                    {item.desc}
                  </Typography>
                </MotionBox>
              </Grid>
            ))}
          </Grid>
        </Container>
      </Box>

      {/* Services Grid */}
      <Container maxWidth="xl" sx={{ py: 10 }}>
        <Typography variant="h2" sx={{ textAlign: 'center', mb: 2 }}>
          Our Services
        </Typography>
        <Typography variant="body1" sx={{ textAlign: 'center', color: '#64748b', mb: 8, maxWidth: 600, mx: 'auto' }}>
          Comprehensive civil engineering services for all your construction needs
        </Typography>

        <Grid container spacing={3}>
          {services.map((service, idx) => (
            <Grid item xs={6} md={3} key={idx}>
              <MotionCard
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ duration: 0.3, delay: idx * 0.05 }}
                className="card-hover"
                sx={{ p: 3, cursor: 'pointer', borderRadius: 4, textAlign: 'center' }}
                // The tile names a category, so it must land on that category rather than the full list.
                onClick={() => navigate(`/services/${service.category}`)}
              >
                <Avatar
                  sx={{
                    bgcolor: `${service.color}15`,
                    color: service.color,
                    width: 56,
                    height: 56,
                    mx: 'auto',
                    mb: 2,
                  }}
                >
                  {service.icon}
                </Avatar>
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>
                  {service.title}
                </Typography>
                <Typography variant="body2" sx={{ color: '#64748b' }}>
                  {service.desc}
                </Typography>
              </MotionCard>
            </Grid>
          ))}
        </Grid>
      </Container>

      {/* CTA Section */}
      <Box
        sx={{
          background: (t) => `linear-gradient(135deg, ${t.palette.primary.main} 0%, ${t.palette.secondary.main} 100%)`,
          py: 10,
          textAlign: 'center',
        }}
      >
        <Container maxWidth="md">
          <MotionBox
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
          >
            <Typography variant="h2" sx={{ color: '#fff', mb: 3, fontWeight: 800 }}>
              Ready to Start Your Project?
            </Typography>
            <Typography variant="h6" sx={{ color: 'rgba(255,255,255,0.8)', mb: 5, fontWeight: 400 }}>
              Join thousands of satisfied customers who found the perfect civil engineering professional
            </Typography>
            <Box sx={{ display: 'flex', gap: 2, justifyContent: 'center', flexWrap: 'wrap' }}>
              <Button
                variant="contained"
                size="large"
                onClick={() => navigate('/register')}
                sx={{
                  px: 6,
                  py: 1.5,
                  borderRadius: 3,
                  bgcolor: '#fff',
                  color: 'primary.main',
                  fontSize: '1.1rem',
                  fontWeight: 700,
                  '&:hover': { bgcolor: '#f1f5f9', boxShadow: '0 8px 25px rgba(0,0,0,0.2)' },
                }}
              >
                Get Started Free
              </Button>
              <Button
                variant="outlined"
                size="large"
                onClick={() => navigate('/services')}
                sx={{
                  px: 6,
                  py: 1.5,
                  borderRadius: 3,
                  borderColor: '#fff',
                  color: '#fff',
                  fontSize: '1.1rem',
                  fontWeight: 600,
                  '&:hover': { borderColor: '#fff', bgcolor: 'rgba(255,255,255,0.1)' },
                }}
              >
                Browse Services
              </Button>
            </Box>
          </MotionBox>
        </Container>
      </Box>
    </Box>
  );
};

export default HomePage;
