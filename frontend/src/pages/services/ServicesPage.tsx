import React, { useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  Box,
  Container,
  Typography,
  Grid,
  Card,
  CardActionArea,
  Avatar,
  Chip,
  TextField,
  InputAdornment,
  Slider,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Rating,
  Button,
} from '@mui/material';
import { Search, Verified, Clear } from '@mui/icons-material';
import { motion } from 'framer-motion';
import DynamicIcon from '../../components/DynamicIcon';
import {
  ServiceCategory,
  categoryFromSlug,
  numericPrice,
  priceCeiling,
  searchServices,
  slugify,
} from '../../constants/serviceCatalogue';
import { useCatalogue } from '../../hooks/useCatalogue';
import ServiceMedia from '../../components/ServiceMedia';

const ServicesPage: React.FC = () => {
  const navigate = useNavigate();
  // The route is `/services/:category`. Reading it is what makes a category link actually filter —
  // the page used to hold the selection in local state only, so every deep link opened unfiltered.
  const { category: categoryParam } = useParams<{ category?: string }>();

  // The catalogue is admin-managed and arrives from the API; the list the site shipped with is only
  // a fallback for a failed request.
  const { services, categories, loading } = useCatalogue();

  // Resolved against the categories actually in the catalogue, so a link to a category an admin has
  // since removed falls back to "everything" rather than filtering to nothing.
  const selectedCategory = categoryFromSlug(categoryParam, categories);

  // `?q=` lets a link name one service exactly — the footer uses it so "Cement" opens Cement
  // rather than the whole Materials category. Typing in the box writes back to the URL, so the
  // narrowed view stays shareable and survives a refresh.
  const [searchParams, setSearchParams] = useSearchParams();
  const searchQuery = searchParams.get('q') ?? '';
  const setSearchQuery = (value: string) => {
    const next = new URLSearchParams(searchParams);
    if (value) next.set('q', value);
    else next.delete('q');
    // Replace, not push: every keystroke would otherwise become its own history entry and Back
    // would walk the user back through the word they just typed.
    setSearchParams(next, { replace: true });
  };

  const ceiling = useMemo(() => priceCeiling(services), [services]);
  // `null` means "the user has not touched the slider", which is what lets the range follow the
  // catalogue: pinning it to the ceiling at mount would freeze it at whatever the fallback list
  // topped out at, and every item above that would be filtered away the moment the API answered.
  const [priceRange, setPriceRange] = useState<number[] | null>(null);
  const effectiveRange = priceRange ?? [0, ceiling];
  const [sortBy, setSortBy] = useState('rating');

  // Navigating rather than setting state keeps the URL the single source of truth: the filter
  // survives a refresh, is shareable, and Back steps through the categories the user visited.
  const selectCategory = (category: ServiceCategory | null) => {
    navigate(category ? `/services/${slugify(category)}` : '/services');
  };

  const filteredServices = useMemo(() => {
    const query = searchQuery.trim();
    // The same matcher the header search uses, so a query that found something up there cannot
    // come back empty down here — and "tmt" or "jcb" resolve through the alias list either way.
    const base = query ? searchServices(query, services, services.length) : services;

    return base
      .filter((service) => !selectedCategory || service.category === selectedCategory)
      .filter((service) => {
        const price = numericPrice(service.price);
        if (price === null) return true;
        return price >= effectiveRange[0] && price <= effectiveRange[1];
      })
      .sort((a, b) => {
        if (sortBy === 'rating') return b.rating - a.rating;
        if (sortBy === 'reviews') return b.reviews - a.reviews;
        return a.title.localeCompare(b.title);
      });
  }, [services, selectedCategory, searchQuery, effectiveRange, sortBy]);

  const heading = selectedCategory ?? 'Find Your Service';

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h3" sx={{ fontWeight: 800, mb: 1 }}>
          {heading}
        </Typography>
        <Typography variant="body1" sx={{ color: 'text.secondary' }}>
          {selectedCategory
            ? `Showing ${selectedCategory.toLowerCase()} only`
            : 'Browse through our comprehensive range of civil engineering services and materials'}
        </Typography>
      </Box>

      <Card sx={{ borderRadius: 3, p: 3, mb: 4 }}>
        <Grid container spacing={3} alignItems="center">
          <Grid item xs={12} md={4}>
            <TextField
              fullWidth
              placeholder="Search services and materials…"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <Search sx={{ color: 'primary.main' }} />
                  </InputAdornment>
                ),
              }}
              sx={{ '& .MuiOutlinedInput-root': { borderRadius: 3 } }}
            />
          </Grid>
          <Grid item xs={6} md={3}>
            <FormControl fullWidth>
              <InputLabel>Sort By</InputLabel>
              <Select value={sortBy} label="Sort By" onChange={(e) => setSortBy(e.target.value)}>
                <MenuItem value="rating">Top Rated</MenuItem>
                <MenuItem value="reviews">Most Popular</MenuItem>
                <MenuItem value="name">Name (A–Z)</MenuItem>
              </Select>
            </FormControl>
          </Grid>
          <Grid item xs={6} md={3}>
            <Box sx={{ px: 2 }}>
              <Typography variant="body2" sx={{ color: 'text.secondary', mb: 1 }}>
                Price Range: ₹{effectiveRange[0]} - ₹{effectiveRange[1]}
              </Typography>
              <Slider
                value={effectiveRange}
                onChange={(_, val) => setPriceRange(val as number[])}
                min={0}
                max={ceiling}
                step={500}
                sx={{ color: 'primary.main' }}
              />
            </Box>
          </Grid>
          <Grid item xs={12} md={2}>
            <Typography variant="body2" sx={{ color: 'text.secondary', textAlign: 'center' }}>
              {filteredServices.length} of {services.length} shown
            </Typography>
          </Grid>
        </Grid>
      </Card>

      <Box sx={{ display: 'flex', gap: 1, mb: 4, flexWrap: 'wrap', alignItems: 'center' }}>
        <Chip
          label="All"
          onClick={() => selectCategory(null)}
          variant={!selectedCategory ? 'filled' : 'outlined'}
          sx={{
            borderRadius: 2,
            fontWeight: 600,
            ...(!selectedCategory
              ? {
                  background: (t) =>
                    `linear-gradient(135deg, ${t.palette.primary.main}, ${t.palette.secondary.main})`,
                  color: '#fff',
                }
              : {}),
          }}
        />
        {categories.map((cat) => (
          <Chip
            key={cat}
            label={cat}
            onClick={() => selectCategory(cat)}
            variant={selectedCategory === cat ? 'filled' : 'outlined'}
            sx={{
              borderRadius: 2,
              fontWeight: 600,
              ...(selectedCategory === cat
                ? {
                    background: (t) =>
                      `linear-gradient(135deg, ${t.palette.primary.main}, ${t.palette.secondary.main})`,
                    color: '#fff',
                  }
                : {}),
            }}
          />
        ))}
        {selectedCategory && (
          <Button
            size="small"
            startIcon={<Clear />}
            onClick={() => selectCategory(null)}
            sx={{ textTransform: 'none' }}
          >
            Clear filter
          </Button>
        )}
      </Box>

      {loading && (
        <Card sx={{ borderRadius: 3, p: 6, textAlign: 'center' }}>
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            Loading services…
          </Typography>
        </Card>
      )}

      {!loading && filteredServices.length === 0 && (
        <Card sx={{ borderRadius: 3, p: 6, textAlign: 'center' }}>
          <Typography variant="h6" sx={{ fontWeight: 600 }}>
            Nothing matches those filters
          </Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', mt: 1 }}>
            Try widening the price range or clearing the category.
          </Typography>
        </Card>
      )}

      <Grid container spacing={3}>
        {filteredServices.map((service, idx) => (
          <Grid item xs={12} sm={6} md={4} lg={3} key={service.slug}>
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              // Capped so a large catalogue does not stagger the last card in by whole seconds.
              transition={{ delay: Math.min(idx, 12) * 0.03 }}
            >
              <Card
                className="card-hover"
                sx={{ borderRadius: 3, height: '100%' }}
                onClick={() => navigate(`/book/${service.slug}`)}
              >
                <CardActionArea sx={{ height: '100%', p: 3 }}>
                  {/* Only for items an admin has given artwork; everything else keeps the icon-only
                      layout the page has always had, rather than reserving an empty band. */}
                  <ServiceMedia
                    mediaUrl={service.mediaUrl}
                    mediaType={service.mediaType}
                    title={service.title}
                  />
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
                    <Avatar
                      sx={{
                        background: (t) =>
                          `linear-gradient(135deg, ${t.palette.primary.main}15, ${t.palette.secondary.main}15)`,
                        color: 'primary.main',
                        width: 48,
                        height: 48,
                      }}
                    >
                      <DynamicIcon name={service.icon} />
                    </Avatar>
                    <Box>
                      <Typography variant="h6" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
                        {service.title}
                      </Typography>
                      <Chip
                        label={service.category}
                        size="small"
                        // Stops the row click so the chip filters instead of opening a booking.
                        onClick={(event) => {
                          event.stopPropagation();
                          selectCategory(service.category);
                        }}
                        sx={{
                          bgcolor: (t) => t.palette.primary.main + '15',
                          color: 'primary.main',
                          fontWeight: 500,
                          fontSize: '0.7rem',
                        }}
                      />
                    </Box>
                  </Box>

                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                    <Rating value={service.rating} precision={0.1} size="small" readOnly />
                    <Typography variant="body2" sx={{ color: 'text.secondary', fontWeight: 600 }}>
                      {service.rating}
                    </Typography>
                    <Typography variant="body2" sx={{ color: 'text.disabled' }}>
                      ({service.reviews})
                    </Typography>
                  </Box>

                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Typography variant="h6" sx={{ color: 'primary.main', fontWeight: 700 }}>
                      {service.price}
                    </Typography>
                    <Verified sx={{ color: '#10b981', fontSize: 18 }} />
                  </Box>
                </CardActionArea>
              </Card>
            </motion.div>
          </Grid>
        ))}
      </Grid>
    </Container>
  );
};

export default ServicesPage;
