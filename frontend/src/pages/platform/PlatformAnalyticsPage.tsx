import React from 'react';
import { Box, Typography } from '@mui/material';
import WarehouseKpis from '../../components/WarehouseKpis';

/** Cross-tenant figures for RK staff: every tenant's totals and how change-data capture is keeping up. */
const PlatformAnalyticsPage: React.FC = () => (
  <Box data-testid="platform-analytics">
    <Typography variant="h5" sx={{ fontWeight: 800 }}>Platform analytics</Typography>
    <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
      Every tenant's activity, from the analytics warehouse.
    </Typography>
    <WarehouseKpis />
  </Box>
);

export default PlatformAnalyticsPage;
