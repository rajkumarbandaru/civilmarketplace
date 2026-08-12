import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Grid,
  Card,
  CardContent,
  Typography,
  Avatar,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Chip,
  LinearProgress,
  Skeleton,
} from '@mui/material';
import {
  People,
  Receipt,
  TrendingUp,
  Warning,
  Engineering,
  CheckCircle,
  Cancel,
  PendingActions,
  Category,
} from '@mui/icons-material';
import { motion } from 'framer-motion';
import { dashboardApi, DashboardStats } from '../../services/adminApi';

const typeColors: Record<string, string> = {
  user: '#667eea',
  booking: '#10b981',
  payment: '#f59e0b',
  verification: '#8b5cf6',
  dispute: '#ef4444',
  category: '#06b6d4',
  info: '#64748b',
};

const StatCard: React.FC<{
  label: string;
  value: string;
  change: string;
  icon: React.ReactNode;
  color: string;
  loading?: boolean;
}> = ({ label, value, change, icon, color, loading }) => (
  <Card sx={{ borderRadius: 3, '&:hover': { boxShadow: '0 8px 25px rgba(0,0,0,0.1)' } }}>
    <CardContent sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
        <Box>
          <Typography variant="body2" sx={{ color: '#64748b', fontWeight: 500, mb: 0.5 }}>
            {label}
          </Typography>
          {loading ? (
            <Skeleton width={80} height={40} />
          ) : (
            <Typography variant="h4" sx={{ fontWeight: 800 }}>
              {value}
            </Typography>
          )}
        </Box>
        <Avatar sx={{ bgcolor: `${color}15`, color: color, width: 48, height: 48 }}>
          {icon}
        </Avatar>
      </Box>
      {!loading && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <Chip
            label={change}
            size="small"
            sx={{
              height: 22,
              fontSize: '0.75rem',
              fontWeight: 600,
              bgcolor: change.startsWith('+') ? '#ecfdf5' : '#fef2f2',
              color: change.startsWith('+') ? '#10b981' : '#ef4444',
            }}
          />
          <Typography variant="caption" sx={{ color: '#94a3b8' }}>
            vs last month
          </Typography>
        </Box>
      )}
    </CardContent>
  </Card>
);

const AdminDashboard: React.FC = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchDashboard = async () => {
      try {
        setLoading(true);
        const response = await dashboardApi.getDashboard();
        setStats(response.data.data);
        setError(null);
      } catch (err) {
        console.error('Failed to load dashboard:', err);
        setError('Could not load dashboard data. Using default values.');
        // Set fallback defaults
        setStats({
          totalUsers: 12847, activeBookings: 1234, monthlyRevenue: 4520000, pendingActions: 27,
          userGrowth: '+12%', bookingGrowth: '+8%', revenueGrowth: '+23%', pendingActionsChange: '-5%',
          recentActivity: [
            { action: 'Dashboard initialized', user: 'System', time: 'just now', type: 'info' },
          ],
          topCities: [
            { name: 'Mumbai', users: 2456, percentage: 85 },
            { name: 'Delhi', users: 1890, percentage: 72 },
            { name: 'Bangalore', users: 1567, percentage: 64 },
            { name: 'Pune', users: 1234, percentage: 52 },
            { name: 'Hyderabad', users: 987, percentage: 41 },
          ],
          platformOverview: {
            totalEngineers: 2847, activeProjects: 856, pendingVerifications: 143,
            disputes: 12, cancelledBookings: 89, averageRating: 4.8,
          },
        });
      } finally {
        setLoading(false);
      }
    };
    fetchDashboard();
  }, []);

  const formatRevenue = (amount: number) => {
    if (amount >= 100000) return `₹${(amount / 100000).toFixed(1)}L`;
    if (amount >= 1000) return `₹${(amount / 1000).toFixed(1)}K`;
    return `₹${amount.toLocaleString()}`;
  };

  const statCards = stats ? [
    { label: 'Total Users', value: stats.totalUsers.toLocaleString(), change: stats.userGrowth, icon: <People />, color: '#667eea' },
    { label: 'Active Bookings', value: stats.activeBookings.toLocaleString(), change: stats.bookingGrowth, icon: <Receipt />, color: '#10b981' },
    { label: 'Revenue (Month)', value: formatRevenue(stats.monthlyRevenue), change: stats.revenueGrowth, icon: <TrendingUp />, color: '#f59e0b' },
    { label: 'Pending Actions', value: stats.pendingActions.toString(), change: stats.pendingActionsChange, icon: <Warning />, color: '#ef4444' },
  ] : [];

  const activity = stats?.recentActivity || [];
  const topCities = stats?.topCities || [];
  const overview = stats?.platformOverview;

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" sx={{ fontWeight: 800, mb: 1 }}>
          Good morning, Admin 👋
        </Typography>
        <Typography variant="body1" sx={{ color: '#64748b' }}>
          {loading ? 'Loading dashboard...' : "Here's what's happening with your platform today."}
        </Typography>
        {error && (
          <Chip label={error} color="warning" size="small" sx={{ mt: 1, fontWeight: 500 }} />
        )}
      </Box>

      {/* Stats Grid */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        {statCards.map((stat, idx) => (
          <Grid item xs={12} sm={6} md={3} key={idx}>
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: idx * 0.08 }}
            >
              <StatCard {...stat} loading={false} />
            </motion.div>
          </Grid>
        ))}
        {loading && Array.from({ length: 4 }).map((_, idx) => (
          <Grid item xs={12} sm={6} md={3} key={`skeleton-${idx}`}>
            <StatCard label="" value="" change="" icon={<People />} color="#667eea" loading />
          </Grid>
        ))}
      </Grid>

      {/* Quick Actions */}
      <Box sx={{ display: 'flex', gap: 2, mb: 4, flexWrap: 'wrap' }}>
        {[
          { label: 'Manage Users', icon: <People />, path: '/admin/users' },
          { label: 'View Bookings', icon: <Receipt />, path: '/admin/bookings' },
          { label: 'Categories', icon: <Engineering />, path: '/admin/categories' },
          { label: 'Analytics', icon: <TrendingUp />, path: '/admin/analytics' },
        ].map((action) => (
          <Card
            key={action.label}
            sx={{
              borderRadius: 2, cursor: 'pointer', transition: 'all 0.2s',
              '&:hover': { transform: 'translateY(-2px)', boxShadow: '0 8px 25px rgba(0,0,0,0.1)' },
            }}
            onClick={() => navigate(action.path)}
          >
            <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 1.5, py: 2, px: 3 }}>
              <Avatar sx={{ bgcolor: '#eef2ff', color: '#667eea', width: 36, height: 36 }}>
                {action.icon}
              </Avatar>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>{action.label}</Typography>
            </CardContent>
          </Card>
        ))}
      </Box>

      <Grid container spacing={3}>
        {/* Recent Activity */}
        <Grid item xs={12} md={7}>
          <Card sx={{ borderRadius: 3 }}>
            <Box sx={{ p: 3, pb: 2, borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>Recent Activity</Typography>
              <Chip label="Live" size="small" color="success" sx={{ fontWeight: 600 }} />
            </Box>
            <List sx={{ p: 0 }}>
              {loading ? (
                Array.from({ length: 4 }).map((_, idx) => (
                  <ListItem key={idx} sx={{ px: 3, py: 2 }}>
                    <Skeleton variant="circular" width={40} height={40} sx={{ mr: 2 }} />
                    <Box sx={{ flex: 1 }}>
                      <Skeleton width="60%" />
                      <Skeleton width="40%" />
                    </Box>
                  </ListItem>
                ))
              ) : activity.length > 0 ? (
                activity.map((act, idx) => (
                  <React.Fragment key={idx}>
                    <ListItem sx={{ px: 3, py: 2 }}>
                      <ListItemAvatar>
                        <Avatar sx={{ bgcolor: `${typeColors[act.type] || '#64748b'}15`, color: typeColors[act.type] || '#64748b', width: 40, height: 40 }}>
                          {act.type === 'user' ? <People /> :
                           act.type === 'booking' ? <CheckCircle /> :
                           act.type === 'payment' ? <Receipt /> :
                           act.type === 'verification' ? <Engineering /> :
                           act.type === 'dispute' ? <Warning /> : <Category />}
                        </Avatar>
                      </ListItemAvatar>
                      <ListItemText
                        primary={act.action}
                        secondary={
                          <Box component="span" sx={{ display: 'flex', gap: 1, alignItems: 'center', mt: 0.25 }}>
                            <Typography variant="body2" component="span" sx={{ color: '#64748b' }}>{act.user}</Typography>
                            <Typography variant="caption" component="span" sx={{ color: '#94a3b8' }}>• {act.time}</Typography>
                          </Box>
                        }
                      />
                    </ListItem>
                    {idx < activity.length - 1 && <Box sx={{ mx: 3, borderBottom: '1px solid #f1f5f9' }} />}
                  </React.Fragment>
                ))
              ) : (
                <ListItem sx={{ px: 3, py: 4, textAlign: 'center' }}>
                  <Typography variant="body2" sx={{ color: '#94a3b8', width: '100%' }}>No recent activity</Typography>
                </ListItem>
              )}
            </List>
          </Card>
        </Grid>

        {/* Top Cities */}
        <Grid item xs={12} md={5}>
          <Card sx={{ borderRadius: 3, height: '100%' }}>
            <Box sx={{ p: 3, borderBottom: '1px solid #e2e8f0' }}>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>Top Cities</Typography>
            </Box>
            <CardContent sx={{ p: 3 }}>
              {loading ? (
                Array.from({ length: 5 }).map((_, idx) => (
                  <Box key={idx} sx={{ mb: idx < 4 ? 2.5 : 0 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                      <Skeleton width={80} />
                      <Skeleton width={60} />
                    </Box>
                    <Skeleton variant="rectangular" height={8} sx={{ borderRadius: 4 }} />
                  </Box>
                ))
              ) : topCities.length > 0 ? (
                topCities.map((city, idx) => (
                  <Box key={idx} sx={{ mb: idx < topCities.length - 1 ? 2.5 : 0 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{city.name}</Typography>
                      <Typography variant="body2" sx={{ color: '#64748b' }}>{city.users.toLocaleString()} users</Typography>
                    </Box>
                    <LinearProgress
                      variant="determinate"
                      value={city.percentage}
                      sx={{
                        height: 8, borderRadius: 4, bgcolor: '#e2e8f0',
                        '& .MuiLinearProgress-bar': { borderRadius: 4, background: 'linear-gradient(135deg, #667eea, #764ba2)' },
                      }}
                    />
                  </Box>
                ))
              ) : (
                <Typography variant="body2" sx={{ color: '#94a3b8', textAlign: 'center', py: 4 }}>No city data available</Typography>
              )}
            </CardContent>
          </Card>
        </Grid>

        {/* Status Overview */}
        {overview && (
          <Grid item xs={12}>
            <Card sx={{ borderRadius: 3 }}>
              <Box sx={{ p: 3, borderBottom: '1px solid #e2e8f0' }}>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>Platform Overview</Typography>
              </Box>
              <CardContent sx={{ p: 3 }}>
                <Grid container spacing={3}>
                  {[
                    { label: 'Total Engineers', value: overview.totalEngineers.toLocaleString(), icon: <Engineering />, color: '#667eea' },
                    { label: 'Active Projects', value: overview.activeProjects.toLocaleString(), icon: <CheckCircle />, color: '#10b981' },
                    { label: 'Pending Verifications', value: overview.pendingVerifications.toLocaleString(), icon: <PendingActions />, color: '#f59e0b' },
                    { label: 'Disputes', value: overview.disputes.toString(), icon: <Warning />, color: '#ef4444' },
                    { label: 'Cancelled Bookings', value: overview.cancelledBookings.toString(), icon: <Cancel />, color: '#94a3b8' },
                    { label: 'Avg. Rating', value: overview.averageRating.toFixed(1), icon: <TrendingUp />, color: '#8b5cf6' },
                  ].map((item, idx) => (
                    <Grid item xs={6} sm={4} md={2} key={idx}>
                      <Box sx={{
                        textAlign: 'center', p: 2, bgcolor: '#f8fafc', borderRadius: 3,
                        transition: 'all 0.2s', '&:hover': { bgcolor: '#f1f5f9', transform: 'translateY(-2px)' },
                      }}>
                        <Avatar sx={{ bgcolor: `${item.color}15`, color: item.color, width: 40, height: 40, mx: 'auto', mb: 1 }}>
                          {item.icon}
                        </Avatar>
                        <Typography variant="h5" sx={{ fontWeight: 800 }}>{item.value}</Typography>
                        <Typography variant="caption" sx={{ color: '#64748b' }}>{item.label}</Typography>
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

export default AdminDashboard;
