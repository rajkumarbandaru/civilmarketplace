import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Container,
  Grid,
  Card,
  CardContent,
  Typography,
  Button,
  Avatar,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  CircularProgress,
} from '@mui/material';
import {
  Dashboard as DashboardIcon,
  Assignment,
  Star,
  People,
  Add,
  Engineering,
} from '@mui/icons-material';
import { motion } from 'framer-motion';
import { useAppSelector, useAppDispatch } from '../../hooks';
import { fetchCustomerBookings } from '../../store/slices/bookingSlice';
import { useDateTime } from '../../providers/UiConfigProvider';
import { SortableTableCell, useTableSort } from '../../components/admin/SortableTable';

const statCards = [
  { label: 'Active Bookings', value: '3', icon: <Assignment />, color: '#667eea', bg: '#eef2ff' },
  { label: 'Total Bookings', value: '12', icon: <DashboardIcon />, color: '#10b981', bg: '#ecfdf5' },
  { label: 'Reviews Given', value: '8', icon: <Star />, color: '#f59e0b', bg: '#fffbeb' },
  { label: 'Saved Pros', value: '5', icon: <People />, color: '#8b5cf6', bg: '#f5f3ff' },
];

const getStatusColor = (status: string) => {
  const colors: Record<string, string> = {
    PENDING: '#f59e0b',
    CONFIRMED: '#3b82f6',
    IN_PROGRESS: '#8b5cf6',
    COMPLETED: '#10b981',
    CANCELLED: '#ef4444',
  };
  return colors[status] || '#64748b';
};

const DashboardPage: React.FC = () => {
  const { formatDate } = useDateTime();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const { bookings, loading } = useAppSelector((state) => state.booking);

  useEffect(() => {
    dispatch(fetchCustomerBookings({}));
  }, [dispatch]);

  const { sorted, sort, onSort } = useTableSort(bookings as any[], {
    bookingCode: (b: any) => b.bookingCode,
    serviceName: (b: any) => b.serviceName,
    status: (b: any) => b.status,
    totalAmount: (b: any) => Number(b.totalAmount) || 0,
    createdAt: (b: any) => (b.createdAt ? new Date(b.createdAt) : null),
  }, { key: 'createdAt', direction: 'desc' });

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      {/* Welcome Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" sx={{ fontWeight: 800, mb: 1 }}>
          Welcome back, {user?.name?.split(' ')[0] || 'User'}! 👋
        </Typography>
        <Typography variant="body1" sx={{ color: '#64748b' }}>
          Here's what's happening with your projects today.
        </Typography>
      </Box>

      {/* Stats Cards */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        {statCards.map((stat, idx) => (
          <Grid item xs={6} md={3} key={idx}>
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: idx * 0.1 }}
            >
              <Card sx={{ borderRadius: 3, p: 1 }}>
                <CardContent>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                    <Box>
                      <Typography variant="body2" sx={{ color: '#64748b', mb: 1 }}>
                        {stat.label}
                      </Typography>
                      <Typography variant="h4" sx={{ fontWeight: 800 }}>
                        {stat.value}
                      </Typography>
                    </Box>
                    <Avatar sx={{ bgcolor: stat.bg, color: stat.color, width: 48, height: 48 }}>
                      {stat.icon}
                    </Avatar>
                  </Box>
                </CardContent>
              </Card>
            </motion.div>
          </Grid>
        ))}
      </Grid>

      {/* Quick Actions */}
      <Box sx={{ display: 'flex', gap: 2, mb: 4, flexWrap: 'wrap' }}>
        <Button
          variant="contained"
          startIcon={<Add />}
          onClick={() => navigate('/services')}
          sx={{ borderRadius: 3 }}
        >
          New Booking
        </Button>
        <Button
          variant="outlined"
          startIcon={<Engineering />}
          onClick={() => navigate('/profile')}
          sx={{ borderRadius: 3, borderColor: '#e2e8f0', color: '#475569' }}
        >
          Update Profile
        </Button>
      </Box>

      {/* Recent Bookings */}
      <Card sx={{ borderRadius: 3, overflow: 'hidden' }}>
        <Box sx={{ p: 3, pb: 2, borderBottom: '1px solid #e2e8f0' }}>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>
            Recent Bookings
          </Typography>
        </Box>

        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: 8 }}>
            <CircularProgress />
          </Box>
        ) : bookings.length === 0 ? (
          <Box sx={{ textAlign: 'center', p: 8 }}>
            <Assignment sx={{ fontSize: 48, color: '#94a3b8', mb: 2 }} />
            <Typography variant="h6" sx={{ color: '#64748b', mb: 1 }}>
              No bookings yet
            </Typography>
            <Typography variant="body2" sx={{ color: '#94a3b8', mb: 3 }}>
              Book your first civil engineering service today!
            </Typography>
            <Button variant="contained" onClick={() => navigate('/services')}>
              Browse Services
            </Button>
          </Box>
        ) : (
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <SortableTableCell columnKey="bookingCode" sort={sort} onSort={onSort} sx={{ fontWeight: 600 }}>Booking Code</SortableTableCell>
                  <SortableTableCell columnKey="serviceName" sort={sort} onSort={onSort} sx={{ fontWeight: 600 }}>Service</SortableTableCell>
                  <SortableTableCell columnKey="status" sort={sort} onSort={onSort} sx={{ fontWeight: 600 }}>Status</SortableTableCell>
                  <SortableTableCell columnKey="totalAmount" sort={sort} onSort={onSort} sx={{ fontWeight: 600 }}>Amount</SortableTableCell>
                  <SortableTableCell columnKey="createdAt" sort={sort} onSort={onSort} sx={{ fontWeight: 600 }}>Date</SortableTableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {sorted.map((booking: any) => (
                  <TableRow
                    key={booking.id}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => navigate(`/book/${booking.id}`)}
                  >
                    <TableCell>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 600 }}>
                        {booking.bookingCode}
                      </Typography>
                    </TableCell>
                    <TableCell>{booking.serviceName}</TableCell>
                    <TableCell>
                      <Chip
                        label={booking.status}
                        size="small"
                        sx={{
                          bgcolor: `${getStatusColor(booking.status)}15`,
                          color: getStatusColor(booking.status),
                          fontWeight: 600,
                        }}
                      />
                    </TableCell>
                    <TableCell>₹{booking.totalAmount}</TableCell>
                    <TableCell>
                      {formatDate(booking.createdAt)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
    </Container>
  );
};

export default DashboardPage;
