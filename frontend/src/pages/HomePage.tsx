import React, { useMemo } from 'react';
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
import { Search } from '@mui/icons-material';
import { motion } from 'framer-motion';
import DynamicIcon from '../components/DynamicIcon';
import ServiceMedia from '../components/ServiceMedia';
import { useCatalogue } from '../hooks/useCatalogue';
import { useSection } from '../hooks/useSiteContent';
import { resolveMediaUrl } from '../services/siteContentApi';

const MotionBox = motion(Box);
const MotionCard = motion(Card);

/** How many catalogue items the landing grid shows before sending people to the full list. */
const HOME_SERVICE_LIMIT = 24;

/**
 * The tile palette, cycled by position.
 *
 * The grid used to be a hand-written list of twenty-five tiles, each with its own colour, which is
 * why the landing page could advertise services the catalogue no longer had (and never showed ones
 * an admin added). The tiles now come from the catalogue, so the colour has to come from somewhere
 * other than the data — cycling keeps the row-to-row variety the hand-picked colours gave it.
 */
const TILE_COLORS = [
  '#667eea', '#764ba2', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6',
  '#06b6d4', '#3b82f6', '#0ea5e9', '#a855f7', '#14b8a6', '#f97316',
];

/**
 * Renders a headline, painting the segment an admin wrapped in `**asterisks**` in the accent
 * colour. The highlight used to be a hardcoded `<Box component="span">` around one word, so
 * moving it — or removing it — was a code change.
 */
const Headline: React.FC<{ text: string }> = ({ text }) => (
  <>
    {text.split(/(\*\*[^*]+\*\*)/g).map((part, idx) =>
      part.startsWith('**') && part.endsWith('**') ? (
        <Box component="span" key={idx} sx={{ color: '#fbbf24' }}>
          {part.slice(2, -2)}
        </Box>
      ) : (
        <React.Fragment key={idx}>{part}</React.Fragment>
      )
    )}
  </>
);

const HomePage: React.FC = () => {
  const navigate = useNavigate();
  const { services: catalogue } = useCatalogue();

  // Every heading, paragraph, badge and button below is a row a Super Admin edits in the console.
  // A section switched off there returns null here and its block is skipped entirely.
  const hero = useSection('home.hero');
  const statsSection = useSection('home.stats');
  const howItWorks = useSection('home.how_it_works');
  const servicesSection = useSection('home.services');
  const cta = useSection('home.cta');

  /**
   * The same order the services page opens in — its "Top Rated" default — truncated.
   *
   * The two used to sort differently (most-reviewed here, top-rated there), so "Our Services" and
   * the services list read as two unrelated catalogues even though both came from the same data:
   * nothing a visitor saw on the landing page was near the top of the page it linked to. A landing
   * grid is a preview of that list, so it has to be the front of it.
   */
  const featured = useMemo(
    () =>
      [...catalogue]
        .sort((a, b) => b.rating - a.rating || b.reviews - a.reviews || a.title.localeCompare(b.title))
        .slice(0, HOME_SERVICE_LIMIT),
    [catalogue]
  );

  /** The hero panel's shortlist: the head of the same ordering, so the two never contradict. */
  const topRated = useMemo(() => featured.slice(0, 6), [featured]);

  return (
    <Box>
      {/* Hero Section */}
      {hero && (
      <Box
        sx={{
          minHeight: '90vh',
          display: 'flex',
          alignItems: 'center',
          position: 'relative',
          overflow: 'hidden',
          // An uploaded hero image sits over the theme gradient, which stays as the backdrop for
          // the (usual) case of no image and as the fallback while the image loads.
          background: (t) => `linear-gradient(135deg, ${t.palette.primary.main} 0%, ${t.palette.secondary.main} 100%)`,
          ...(resolveMediaUrl(hero.imageUrl)
            ? {
                backgroundImage: `linear-gradient(135deg, rgba(15,23,42,0.75), rgba(15,23,42,0.55)), url(${resolveMediaUrl(hero.imageUrl)})`,
                backgroundSize: 'cover',
                backgroundPosition: 'center',
              }
            : {}),
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
                {hero.body && (
                <Chip
                  label={hero.body}
                  sx={{
                    bgcolor: 'rgba(255,255,255,0.15)',
                    color: '#fff',
                    fontWeight: 600,
                    mb: 3,
                    backdropFilter: 'blur(10px)',
                  }}
                />
                )}
                <Typography
                  variant="h1"
                  sx={{
                    color: '#fff',
                    mb: 3,
                    fontSize: { xs: '2.5rem', md: '3.5rem', lg: '4rem' },
                    lineHeight: 1.1,
                  }}
                >
                  <Headline text={hero.title ?? ''} />
                </Typography>
                {hero.subtitle && (
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
                  {hero.subtitle}
                </Typography>
                )}

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
                    onClick={() => navigate(hero.linkUrl || '/services')}
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
                    {hero.linkLabel || 'Search'}
                  </Button>
                </Box>

                {/* Trust badges */}
                <Box sx={{ display: 'flex', gap: 3, flexWrap: 'wrap' }}>
                  {hero.items.map((badge) => (
                    <Box key={badge.id} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <DynamicIcon name={badge.icon ?? 'Security'} sx={{ color: '#34d399', fontSize: 20 }} />
                      <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.7)' }}>
                        {badge.title}
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
                    {topRated.map((service, idx) => (
                      <Grid item xs={6} key={service.slug}>
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
                          onClick={() => navigate(`/book/${service.slug}`)}
                        >
                          <Avatar
                            sx={{
                              bgcolor: TILE_COLORS[idx % TILE_COLORS.length],
                              width: 36,
                              height: 36,
                            }}
                          >
                            <DynamicIcon name={service.icon} />
                          </Avatar>
                          <Box>
                            <Typography variant="body2" sx={{ color: '#fff', fontWeight: 600 }}>
                              {service.title}
                            </Typography>
                            <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.6)' }}>
                              {service.category}
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
      )}

      {/* Stats Section */}
      {statsSection && (
      <Container maxWidth="xl" sx={{ py: 8 }}>
        <Grid container spacing={3}>
          {statsSection.items.map((stat, idx) => (
            <Grid item xs={6} md={3} key={stat.id}>
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
                  <DynamicIcon name={stat.icon ?? 'Insights'} />
                </Avatar>
                <Typography variant="h4" sx={{ fontWeight: 800, color: '#1e293b' }}>
                  {stat.title}
                </Typography>
                <Typography variant="body2" sx={{ color: '#64748b', mt: 1 }}>
                  {stat.subtitle}
                </Typography>
              </MotionCard>
            </Grid>
          ))}
        </Grid>
      </Container>
      )}

      {/* How It Works */}
      {howItWorks && (
      <Box sx={{ bgcolor: 'action.hover', py: 10 }}>
        <Container maxWidth="xl">
          <Typography variant="h2" sx={{ textAlign: 'center', mb: 2 }}>
            {howItWorks.title}
          </Typography>
          {howItWorks.subtitle && (
            <Typography variant="body1" sx={{ textAlign: 'center', color: '#64748b', mb: 8, maxWidth: 600, mx: 'auto' }}>
              {howItWorks.subtitle}
            </Typography>
          )}

          <Grid container spacing={4}>
            {howItWorks.items.map((item, idx) => (
              <Grid item xs={12} md={4} key={item.id}>
                <MotionBox
                  initial={{ opacity: 0, y: 30 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.5, delay: idx * 0.15 }}
                  sx={{ textAlign: 'center', px: 3 }}
                >
                  {/* An uploaded illustration replaces the numbered circle when there is one. */}
                  {resolveMediaUrl(item.imageUrl) ? (
                    <Box
                      component="img"
                      src={resolveMediaUrl(item.imageUrl)}
                      alt={item.title ?? ''}
                      sx={{ width: 120, height: 120, objectFit: 'cover', borderRadius: '50%', mx: 'auto', mb: 3, display: 'block' }}
                    />
                  ) : (
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
                    {item.badge}
                  </Box>
                  )}
                  <Typography variant="h5" sx={{ fontWeight: 700, mb: 2 }}>
                    {item.title}
                  </Typography>
                  <Typography variant="body1" sx={{ color: '#64748b' }}>
                    {item.body}
                  </Typography>
                </MotionBox>
              </Grid>
            ))}
          </Grid>
        </Container>
      </Box>
      )}

      {/* Services Grid */}
      {servicesSection && (
      <Container maxWidth="xl" sx={{ py: 10 }}>
        <Typography variant="h2" sx={{ textAlign: 'center', mb: 2 }}>
          {servicesSection.title}
        </Typography>
        <Typography variant="body1" sx={{ textAlign: 'center', color: '#64748b', mb: 8, maxWidth: 600, mx: 'auto' }}>
          {/* Says outright that this is the top of a longer list, so a visitor who does not find
              what they need here knows there is more rather than assuming this is everything. The
              admin's subtitle is what shows when the whole catalogue already fits on the page. */}
          {catalogue.length > HOME_SERVICE_LIMIT
            ? `The top ${HOME_SERVICE_LIMIT} of ${catalogue.length} services, materials, machines and vehicles`
            : servicesSection.subtitle}
        </Typography>

        <Grid container spacing={3}>
          {featured.map((service, idx) => (
            <Grid item xs={6} md={3} key={service.slug}>
              <MotionCard
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ duration: 0.3, delay: idx * 0.05 }}
                className="card-hover"
                sx={{ p: 3, cursor: 'pointer', borderRadius: 4, textAlign: 'center' }}
                // The tile names one service, so it opens that service's booking page; the chip-level
                // "browse the category" route is still a click away on the services page itself.
                onClick={() => navigate(`/book/${service.slug}`)}
              >
                <ServiceMedia
                  mediaUrl={service.mediaUrl}
                  mediaType={service.mediaType}
                  title={service.title}
                  height={120}
                />
                <Avatar
                  sx={{
                    bgcolor: `${TILE_COLORS[idx % TILE_COLORS.length]}15`,
                    color: TILE_COLORS[idx % TILE_COLORS.length],
                    width: 56,
                    height: 56,
                    mx: 'auto',
                    mb: 2,
                  }}
                >
                  <DynamicIcon name={service.icon} />
                </Avatar>
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>
                  {service.title}
                </Typography>
                <Typography variant="body2" sx={{ color: '#64748b' }}>
                  {service.category}{service.price ? ` · ${service.price}` : ''}
                </Typography>
              </MotionCard>
            </Grid>
          ))}
        </Grid>

        {catalogue.length > HOME_SERVICE_LIMIT && (
          <Box sx={{ textAlign: 'center', mt: 5 }}>
            <Button
              variant="outlined"
              size="large"
              onClick={() => navigate('/services')}
              sx={{ borderRadius: 3, px: 4, textTransform: 'none', fontWeight: 600 }}
            >
              Browse all {catalogue.length} services
            </Button>
          </Box>
        )}
      </Container>
      )}

      {/* CTA Section */}
      {cta && (
      <Box
        sx={{
          background: (t) => `linear-gradient(135deg, ${t.palette.primary.main} 0%, ${t.palette.secondary.main} 100%)`,
          py: 10,
          textAlign: 'center',
          ...(resolveMediaUrl(cta.imageUrl)
            ? {
                backgroundImage: `linear-gradient(135deg, rgba(15,23,42,0.75), rgba(15,23,42,0.55)), url(${resolveMediaUrl(cta.imageUrl)})`,
                backgroundSize: 'cover',
                backgroundPosition: 'center',
              }
            : {}),
        }}
      >
        <Container maxWidth="md">
          <MotionBox
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
          >
            <Typography variant="h2" sx={{ color: '#fff', mb: 3, fontWeight: 800 }}>
              <Headline text={cta.title ?? ''} />
            </Typography>
            {cta.subtitle && (
              <Typography variant="h6" sx={{ color: 'rgba(255,255,255,0.8)', mb: 5, fontWeight: 400 }}>
                {cta.subtitle}
              </Typography>
            )}
            {/* The first button is the filled one and the rest are outlined, so an admin sets the
                primary action by ordering rather than by picking a style. */}
            <Box sx={{ display: 'flex', gap: 2, justifyContent: 'center', flexWrap: 'wrap' }}>
              {cta.items.map((button, idx) =>
                idx === 0 ? (
                  <Button
                    key={button.id}
                    variant="contained"
                    size="large"
                    onClick={() => navigate(button.linkUrl || '/register')}
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
                    {button.title}
                  </Button>
                ) : (
                  <Button
                    key={button.id}
                    variant="outlined"
                    size="large"
                    onClick={() => navigate(button.linkUrl || '/services')}
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
                    {button.title}
                  </Button>
                )
              )}
            </Box>
          </MotionBox>
        </Container>
      </Box>
      )}
    </Box>
  );
};

export default HomePage;
