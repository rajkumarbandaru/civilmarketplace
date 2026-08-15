import React from 'react';
import { Box, Container, Grid, Typography, Link, IconButton, Divider } from '@mui/material';
import { Facebook, Twitter, Instagram, LinkedIn, YouTube } from '@mui/icons-material';

const Footer: React.FC = () => {
  const footerSections = [
    {
      title: 'Services',
      links: [
        'House Planning',
        'Villa Planning',
        'Architecture Design',
        'Elevation Design',
        'Structural Engineering',
        'Earthquake Design',
        'BIM Modeling',
        'Land Survey',
        'Drone Survey',
        'GIS Mapping',
        'Interior Design',
        '3D Modeling',
        'Building Construction',
        'Renovation',
        'Electrical Work',
        'Plumbing Services',
        'Contractor Services',
        'Site Supervision',
        'Project Management',
      ],
    },
    {
      title: 'Marketplace',
      links: [
        'Material Supply',
        'Equipment Rental',
        'Transport & Logistics',
        'Skilled Labour',
        'Daily Wage Labour',
        'Skill & Safety Training',
        'Request a Quote (RFQ)',
      ],
    },
    {
      title: 'For Professionals',
      links: [
        'Register as Worker',
        'Register as Engineer',
        'Register as Architect',
        'Register as Surveyor',
        'Register as Contractor',
        'Register as Material Supplier',
        'Register as Equipment Supplier',
        'Register as Transport Provider',
        'Partner Program',
        'Earnings',
      ],
    },
    {
      title: 'Company',
      links: ['About Us', 'Careers', 'Blog', 'Press', 'Contact Us'],
    },
    {
      title: 'Support',
      links: ['Help Center', 'Raise a Ticket', 'Safety Guidelines', 'Dispute Resolution', 'Terms of Service', 'Privacy Policy', 'Refund Policy'],
    },
  ];

  return (
    <Box
      component="footer"
      sx={{
        background: '#1e293b',
        color: '#cbd5e1',
        pt: 8,
        pb: 4,
        mt: 'auto',
      }}
    >
      <Container maxWidth="xl">
        <Grid container spacing={6}>
          {/* Brand */}
          <Grid item xs={12} md={3}>
            <Typography
              variant="h5"
              sx={{
                fontWeight: 800,
                background: (t) => `linear-gradient(135deg, ${t.palette.primary.main} 0%, ${t.palette.secondary.main} 100%)`,
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
                mb: 2,
                fontFamily: "'Poppins', sans-serif",
              }}
            >
              CivEngMarket
            </Typography>
            <Typography variant="body2" sx={{ mb: 3, lineHeight: 1.7 }}>
              India's #1 platform for booking civil engineering professionals.
              Connecting customers with trusted architects, engineers, surveyors,
              and construction experts.
            </Typography>
            <Box sx={{ display: 'flex', gap: 1 }}>
              {[Facebook, Twitter, Instagram, LinkedIn, YouTube].map((Icon, idx) => (
                <IconButton
                  key={idx}
                  size="small"
                  sx={{
                    color: '#94a3b8',
                    '&:hover': { color: 'primary.main', background: (t) => t.palette.primary.main + '1a' },
                  }}
                >
                  <Icon fontSize="small" />
                </IconButton>
              ))}
            </Box>
          </Grid>

          {/* Link sections */}
          {footerSections.map((section) => (
            <Grid item xs={6} md={2.25} key={section.title}>
              <Typography
                variant="subtitle2"
                sx={{ color: '#fff', fontWeight: 600, mb: 2, textTransform: 'uppercase', letterSpacing: 1 }}
              >
                {section.title}
              </Typography>
              {section.links.map((link) => (
                <Link
                  key={link}
                  href="#"
                  underline="none"
                  sx={{
                    display: 'block',
                    color: '#94a3b8',
                    mb: 1.5,
                    fontSize: '0.875rem',
                    transition: 'color 0.2s',
                    '&:hover': { color: 'primary.main' },
                  }}
                >
                  {link}
                </Link>
              ))}
            </Grid>
          ))}
        </Grid>

        <Divider sx={{ my: 4, borderColor: 'rgba(255,255,255,0.1)' }} />

        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
          <Typography variant="body2" sx={{ color: '#64748b' }}>
            &copy; {new Date().getFullYear()} Civil Engineering Marketplace. All rights reserved.
          </Typography>
          <Typography variant="body2" sx={{ color: '#64748b' }}>
            Made with ❤️ for civil engineering professionals
          </Typography>
        </Box>
      </Container>
    </Box>
  );
};

export default Footer;
