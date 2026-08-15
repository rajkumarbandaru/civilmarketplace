import React, { useState, useEffect } from 'react';
import {
  Box, Card, CardContent, Typography, Grid, Chip, Avatar, LinearProgress, Skeleton,
} from '@mui/material';
import { TrendingUp, TrendingDown, People, Receipt, Star } from '@mui/icons-material';
import { analyticsApi, AnalyticsData, GrowthMetric, MonthlyTrend } from '../../services/adminApi';

const colorPalette = ['#667eea', '#10b981', '#f59e0b', '#8b5cf6'];

const AnalyticsPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [data, setData] = useState<AnalyticsData | null>(null);

  useEffect(() => {
    const fetchAnalytics = async () => {
      try {
        setLoading(true);
        const response = await analyticsApi.getAnalytics();
        setData(response.data.data);
      } catch (err) {
        console.error('Failed to load analytics:', err);
      } finally {
        setLoading(false);
      }
    };
    fetchAnalytics();
  }, []);

  const growthMetrics = data?.growthMetrics || [];
  const monthlyTrend = data?.monthlyTrend || [];
  const topCategories = data?.topCategories || [];
  const cityPerformance = data?.cityPerformance || [];
  const userGrowth = data?.userGrowth;

  const maxRevenue = Math.max(...monthlyTrend.map(m => m.revenue), 1);

  return (
    <Box>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h5" sx={{ fontWeight: 800, mb: 0.5 }}>Analytics Dashboard</Typography>
        <Typography variant="body2" sx={{ color: '#64748b' }}>Platform performance metrics and trends</Typography>
      </Box>

      <Grid container spacing={3} sx={{ mb: 4 }}>
        {growthMetrics.map((metric: GrowthMetric, idx: number) => (
          <Grid item xs={6} md={3} key={idx}>
            <Card sx={{ borderRadius: 3 }}>
              <CardContent sx={{ p: 3 }}>
                {loading ? (
                  <Box><Skeleton width={60} height={60} /><Skeleton width="80%" /></Box>
                ) : (
                  <>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
                      <Avatar sx={{ bgcolor: `${colorPalette[idx]}15`, color: colorPalette[idx], width: 44, height: 44 }}>
                        {idx === 0 ? <People /> : idx === 1 ? <Receipt /> : idx === 2 ? <TrendingUp /> : <Star />}
                      </Avatar>
                      <Box>
                        <Typography variant="caption" sx={{ color: '#94a3b8' }}>{metric.label}</Typography>
                        <Typography variant="h5" sx={{ fontWeight: 800 }}>{metric.value}</Typography>
                      </Box>
                    </Box>
                    <Chip icon={metric.trend === 'up' ? <TrendingUp /> : <TrendingDown />}
                      label="vs last month" size="small"
                      sx={{ bgcolor: metric.trend === 'up' ? '#ecfdf5' : '#fef2f2', color: metric.trend === 'up' ? '#10b981' : '#ef4444', fontWeight: 500 }} />
                  </>
                )}
              </CardContent>
            </Card>
          </Grid>
        ))}
        {loading && Array.from({ length: 4 }).map((_, idx) => (
          <Grid item xs={6} md={3} key={`sk-${idx}`}>
            <Card sx={{ borderRadius: 3 }}><CardContent sx={{ p: 3 }}><Skeleton height={80} /></CardContent></Card>
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Card sx={{ borderRadius: 3 }}>
            <Box sx={{ p: 3, pb: 2, borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>Revenue Trend (2024)</Typography>
              <Chip label="+31.7% YoY" color="success" size="small" sx={{ fontWeight: 600 }} />
            </Box>
            <CardContent sx={{ p: 3 }}>
              <Box sx={{ display: 'flex', alignItems: 'flex-end', gap: 1.5, height: 200 }}>
                {loading ? (
                  Array.from({ length: 12 }).map((_, idx) => (
                    <Box key={idx} sx={{ flex: 1 }}><Skeleton height={150 + Math.random() * 50} /></Box>
                  ))
                ) : (
                  monthlyTrend.map((month: MonthlyTrend) => (
                    <Box key={month.month} sx={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', height: '100%' }}>
                      <Typography variant="caption" sx={{ color: '#64748b', mb: 0.5, fontSize: '0.65rem' }}>
                        ₹{(month.revenue / 100000).toFixed(1)}L
                      </Typography>
                      <Box sx={{
                        width: '100%', maxWidth: 32,
                        height: `${Math.max((month.revenue / maxRevenue) * 100, 5)}%`,
                        background: (t) => `linear-gradient(180deg, ${t.palette.primary.main} 0%, ${t.palette.secondary.main} 100%)`,
                        borderRadius: '4px 4px 0 0', transition: 'height 0.3s ease',
                        cursor: 'pointer', '&:hover': { opacity: 0.8 },
                      }} />
                      <Typography variant="caption" sx={{ color: '#94a3b8', mt: 0.5, fontSize: '0.65rem' }}>{month.month}</Typography>
                    </Box>
                  ))
                )}
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={4}>
          <Card sx={{ borderRadius: 3, height: '100%' }}>
            <Box sx={{ p: 3, borderBottom: '1px solid #e2e8f0' }}><Typography variant="h6" sx={{ fontWeight: 700 }}>Key Metrics</Typography></Box>
            <CardContent sx={{ p: 3 }}>
              {loading ? (
                Array.from({ length: 4 }).map((_, idx) => (
                  <Box key={idx} sx={{ mb: 3 }}><Skeleton height={40} /><Skeleton height={6} sx={{ mt: 1 }} /></Box>
                ))
              ) : (
                [
                  { label: 'Conversion Rate', value: '68%', sub: '+5% vs last month', progress: 68, color: '#10b981' },
                  { label: 'Retention Rate', value: '82%', sub: '+3% vs last month', progress: 82, color: '#667eea' },
                  { label: 'Avg. Booking Value', value: '₹3,450', sub: '+12% vs last month', progress: 0, color: '#f59e0b' },
                  { label: 'Customer Satisfaction', value: '4.8/5', sub: 'Based on 2,847 reviews', progress: 96, color: '#8b5cf6' },
                ].map((metric, idx) => (
                  <Box key={idx} sx={{ mb: idx < 3 ? 3 : 0 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                      <Typography variant="body2" sx={{ color: '#64748b' }}>{metric.label}</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>{metric.value}</Typography>
                    </Box>
                    {metric.progress > 0 && (
                      <LinearProgress variant="determinate" value={metric.progress}
                        sx={{ height: 6, borderRadius: 3, bgcolor: '#e2e8f0', mb: 0.5,
                          '& .MuiLinearProgress-bar': { borderRadius: 3, bgcolor: metric.color } }} />
                    )}
                    <Typography variant="caption" sx={{ color: '#94a3b8' }}>{metric.sub}</Typography>
                  </Box>
                ))
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card sx={{ borderRadius: 3 }}>
            <Box sx={{ p: 3, borderBottom: '1px solid #e2e8f0' }}><Typography variant="h6" sx={{ fontWeight: 700 }}>Top Service Categories</Typography></Box>
            <CardContent sx={{ p: 3 }}>
              {loading ? (
                Array.from({ length: 5 }).map((_, idx) => (
                  <Box key={idx} sx={{ mb: 2 }}><Skeleton height={40} /></Box>
                ))
              ) : (
                topCategories.map((cat, idx) => (
                  <Box key={idx} sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: idx < topCategories.length - 1 ? 2.5 : 0 }}>
                    <Avatar sx={{ bgcolor: (t) => t.palette.primary.main + '15', color: 'primary.main', width: 36, height: 36, fontSize: '0.875rem' }}>{idx + 1}</Avatar>
                    <Box sx={{ flex: 1 }}>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{cat.name}</Typography>
                      <Typography variant="caption" sx={{ color: '#94a3b8' }}>{cat.bookings?.toLocaleString() || 0} bookings</Typography>
                    </Box>
                    <Chip label={cat.growth} size="small" color="success" variant="outlined" sx={{ fontWeight: 600 }} />
                  </Box>
                ))
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card sx={{ borderRadius: 3 }}>
            <Box sx={{ p: 3, borderBottom: '1px solid #e2e8f0' }}><Typography variant="h6" sx={{ fontWeight: 700 }}>User Growth</Typography></Box>
            <CardContent sx={{ p: 3 }}>
              <Box sx={{ display: 'flex', alignItems: 'flex-end', gap: 1.5, height: 150 }}>
                {loading ? (
                  Array.from({ length: 12 }).map((_, idx) => (
                    <Box key={idx} sx={{ flex: 1 }}><Skeleton height={80 + Math.random() * 40} /></Box>
                  ))
                ) : (
                  monthlyTrend.map((month: MonthlyTrend) => (
                    <Box key={month.month} sx={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                      <Box sx={{
                        width: '100%', maxWidth: 24,
                        height: `${Math.max((month.users / 3000) * 100, 3)}%`,
                        bgcolor: '#10b981', borderRadius: '3px 3px 0 0', opacity: 0.7,
                        transition: 'height 0.3s', cursor: 'pointer', '&:hover': { opacity: 1 },
                      }} />
                      <Typography variant="caption" sx={{ color: '#94a3b8', mt: 0.5, fontSize: '0.6rem' }}>{month.month}</Typography>
                    </Box>
                  ))
                )}
              </Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 2, pt: 2, borderTop: '1px solid #e2e8f0' }}>
                <Box>
                  <Typography variant="caption" sx={{ color: '#94a3b8' }}>Total Users</Typography>
                  <Typography variant="h5" sx={{ fontWeight: 800 }}>{userGrowth?.totalUsers?.toLocaleString() || '—'}</Typography>
                </Box>
                <Box sx={{ textAlign: 'right' }}>
                  <Typography variant="caption" sx={{ color: '#94a3b8' }}>Avg. Monthly Growth</Typography>
                  <Typography variant="h5" sx={{ fontWeight: 800, color: '#10b981' }}>+{userGrowth?.averageMonthlyGrowth || '—'}%</Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {cityPerformance.length > 0 && (
          <Grid item xs={12}>
            <Card sx={{ borderRadius: 3 }}>
              <Box sx={{ p: 3, borderBottom: '1px solid #e2e8f0' }}><Typography variant="h6" sx={{ fontWeight: 700 }}>City Performance</Typography></Box>
              <CardContent sx={{ p: 3 }}>
                <Grid container spacing={3}>
                  {cityPerformance.map((city, idx) => (
                    <Grid item xs={12} sm={6} md={4} lg={2.4} key={idx}>
                      <Box sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 3, textAlign: 'center' }}>
                        <Typography variant="h6" sx={{ fontWeight: 800, mb: 2 }}>{city.city}</Typography>
                        <Box sx={{ display: 'flex', justifyContent: 'center', gap: 3 }}>
                          <Box><Typography variant="caption" sx={{ color: '#94a3b8' }}>Users</Typography><Typography variant="body2" sx={{ fontWeight: 600 }}>{city.users}</Typography></Box>
                          <Box><Typography variant="caption" sx={{ color: '#94a3b8' }}>Revenue</Typography><Typography variant="body2" sx={{ fontWeight: 600 }}>{city.revenue}</Typography></Box>
                        </Box>
                        <Chip label={city.growth} size="small" color="success" sx={{ mt: 1, fontWeight: 600 }} />
                      </Box>
                    </Grid>
                  ))}
                </Grid>
              </CardContent>
            </Card>
          </Grid>
        )}
      </Grid>
    </Box>
  );
};

export default AnalyticsPage;
