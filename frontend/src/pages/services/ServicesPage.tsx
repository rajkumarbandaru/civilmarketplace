import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Container,
  Typography,
  Grid,
  Card,
  CardContent,
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
import {
  Search,
  Engineering,
  Home,
  Architecture,
  Map,
  DesignServices,
  Construction,
  ElectricalServices,
  WaterDrop,
  LocationOn,
  Verified,
  Inventory,
  Agriculture,
  LocalShipping,
  HandymanOutlined,
  Groups,
  AssignmentTurnedIn,
  SupervisorAccount,
  School,
  Work,
} from '@mui/icons-material';
import { motion } from 'framer-motion';

const allServices = [
  { icon: <Home />, title: 'House Planning', category: 'Architecture', price: '₹500/hr', rating: 4.8, reviews: 234 },
  { icon: <Architecture />, title: 'Architecture Design', category: 'Architecture', price: '₹800/hr', rating: 4.9, reviews: 189 },
  { icon: <Engineering />, title: 'Structural Engineering', category: 'Engineering', price: '₹1000/hr', rating: 4.7, reviews: 156 },
  { icon: <Map />, title: 'Land Survey', category: 'Survey', price: '₹3000/visit', rating: 4.6, reviews: 98 },
  { icon: <DesignServices />, title: 'Interior Design', category: 'Design', price: '₹600/hr', rating: 4.8, reviews: 312 },
  { icon: <Construction />, title: 'Building Construction', category: 'Construction', price: 'Quote', rating: 4.5, reviews: 67 },
  { icon: <ElectricalServices />, title: 'Electrical Work', category: 'Services', price: '₹400/hr', rating: 4.4, reviews: 423 },
  { icon: <WaterDrop />, title: 'Plumbing Services', category: 'Services', price: '₹350/hr', rating: 4.3, reviews: 567 },
  { icon: <Home />, title: 'Villa Planning', category: 'Architecture', price: '₹700/hr', rating: 4.9, reviews: 145 },
  { icon: <Engineering />, title: 'Earthquake Design', category: 'Engineering', price: '₹1200/hr', rating: 4.8, reviews: 89 },
  { icon: <Map />, title: 'Drone Survey', category: 'Survey', price: '₹5000/visit', rating: 4.7, reviews: 234 },
  { icon: <DesignServices />, title: '3D Modeling', category: 'Design', price: '₹900/hr', rating: 4.6, reviews: 178 },
  { icon: <Architecture />, title: 'Elevation Design', category: 'Architecture', price: '₹600/hr', rating: 4.5, reviews: 256 },
  { icon: <Construction />, title: 'Renovation', category: 'Construction', price: '₹500/hr', rating: 4.4, reviews: 345 },
  { icon: <Engineering />, title: 'BIM Modeling', category: 'Engineering', price: '₹1500/hr', rating: 4.9, reviews: 112 },
  { icon: <Map />, title: 'GIS Mapping', category: 'Survey', price: '₹4000/visit', rating: 4.6, reviews: 76 },
  { icon: <Inventory />, title: 'Material Supply', category: 'Materials', price: 'Quote', rating: 4.5, reviews: 289 },
  { icon: <Agriculture />, title: 'Equipment Rental', category: 'Equipment', price: '₹4500/day', rating: 4.4, reviews: 163 },
  { icon: <LocalShipping />, title: 'Transport & Logistics', category: 'Logistics', price: '₹2500/trip', rating: 4.3, reviews: 201 },
  { icon: <HandymanOutlined />, title: 'Skilled Labour', category: 'Labour', price: '₹900/day', rating: 4.6, reviews: 612 },
  { icon: <Groups />, title: 'Daily Wage Labour', category: 'Labour', price: '₹650/day', rating: 4.2, reviews: 738 },
  { icon: <AssignmentTurnedIn />, title: 'Contractor Services', category: 'Construction', price: 'Quote', rating: 4.5, reviews: 154 },
  { icon: <SupervisorAccount />, title: 'Site Supervision', category: 'Engineering', price: '₹1800/day', rating: 4.7, reviews: 121 },
  { icon: <Work />, title: 'Project Management', category: 'Management', price: 'Quote', rating: 4.8, reviews: 94 },
  { icon: <School />, title: 'Skill & Safety Training', category: 'Training', price: '₹1200/course', rating: 4.7, reviews: 208 },
];

const categories = [
  'All',
  'Architecture',
  'Engineering',
  'Survey',
  'Design',
  'Construction',
  'Services',
  'Materials',
  'Equipment',
  'Logistics',
  'Labour',
  'Management',
  'Training',
];

const ServicesPage: React.FC = () => {
  const navigate = useNavigate();
  const [selectedCategory, setSelectedCategory] = useState('All');
  const [searchQuery, setSearchQuery] = useState('');
  const [priceRange, setPriceRange] = useState<number[]>([0, 5000]);
  const [sortBy, setSortBy] = useState('rating');

  const filteredServices = allServices
    .filter(s => (selectedCategory === 'All' || s.category === selectedCategory))
    .filter(s => s.title.toLowerCase().includes(searchQuery.toLowerCase()))
    .filter(s => {
      const price = parseInt(s.price.replace(/[₹,hr/visit]/g, '')) || 0;
      // Simple price filter - if it says Quote, include it
      if (s.price === 'Quote') return true;
      return price >= priceRange[0] && price <= priceRange[1];
    })
    .sort((a, b) => sortBy === 'rating' ? b.rating - a.rating : b.reviews - a.reviews);

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h3" sx={{ fontWeight: 800, mb: 1 }}>
          Find Your Service
        </Typography>
        <Typography variant="body1" sx={{ color: '#64748b' }}>
          Browse through our comprehensive range of civil engineering services
        </Typography>
      </Box>

      {/* Filters */}
      <Card sx={{ borderRadius: 3, p: 3, mb: 4 }}>
        <Grid container spacing={3} alignItems="center">
          <Grid item xs={12} md={4}>
            <TextField
              fullWidth
              placeholder="Search services..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              InputProps={{
                startAdornment: <InputAdornment position="start"><Search sx={{ color: 'primary.main' }} /></InputAdornment>,
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
              </Select>
            </FormControl>
          </Grid>
          <Grid item xs={6} md={3}>
            <Box sx={{ px: 2 }}>
              <Typography variant="body2" sx={{ color: '#64748b', mb: 1 }}>
                Price Range: ₹{priceRange[0]} - ₹{priceRange[1]}
              </Typography>
              <Slider
                value={priceRange}
                onChange={(_, val) => setPriceRange(val as number[])}
                min={0}
                max={5000}
                step={100}
                sx={{ color: 'primary.main' }}
              />
            </Box>
          </Grid>
          <Grid item xs={12} md={2}>
            <Typography variant="body2" sx={{ color: '#64748b', textAlign: 'center' }}>
              {filteredServices.length} services found
            </Typography>
          </Grid>
        </Grid>
      </Card>

      {/* Category Chips */}
      <Box sx={{ display: 'flex', gap: 1, mb: 4, flexWrap: 'wrap' }}>
        {categories.map((cat) => (
          <Chip
            key={cat}
            label={cat}
            onClick={() => setSelectedCategory(cat)}
            variant={selectedCategory === cat ? 'filled' : 'outlined'}
            sx={{
              borderRadius: 2,
              fontWeight: 600,
              ...(selectedCategory === cat
                ? { background: (t) => `linear-gradient(135deg, ${t.palette.primary.main}, ${t.palette.secondary.main})`, color: '#fff' }
                : { borderColor: '#e2e8f0', color: '#64748b' }),
            }}
          />
        ))}
      </Box>

      {/* Services Grid */}
      <Grid container spacing={3}>
        {filteredServices.map((service, idx) => (
          <Grid item xs={12} sm={6} md={4} lg={3} key={idx}>
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: idx * 0.03 }}
            >
              <Card
                className="card-hover"
                sx={{ borderRadius: 3, height: '100%' }}
                onClick={() => navigate('/book/1')}
              >
                <CardActionArea sx={{ height: '100%', p: 3 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
                    <Avatar
                      sx={{
                        background: (t) => `linear-gradient(135deg, ${t.palette.primary.main}15, ${t.palette.secondary.main}15)`,
                        color: 'primary.main',
                        width: 48,
                        height: 48,
                      }}
                    >
                      {service.icon}
                    </Avatar>
                    <Box>
                      <Typography variant="h6" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
                        {service.title}
                      </Typography>
                      <Chip
                        label={service.category}
                        size="small"
                        sx={{ bgcolor: (t) => t.palette.primary.main + '15', color: 'primary.main', fontWeight: 500, fontSize: '0.7rem' }}
                      />
                    </Box>
                  </Box>

                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                    <Rating value={service.rating} precision={0.1} size="small" readOnly />
                    <Typography variant="body2" sx={{ color: '#64748b', fontWeight: 600 }}>
                      {service.rating}
                    </Typography>
                    <Typography variant="body2" sx={{ color: '#94a3b8' }}>
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
