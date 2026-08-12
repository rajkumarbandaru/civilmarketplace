import React, { useState, useEffect, useCallback } from 'react';
import {
  Box, Card, Typography, TextField, InputAdornment, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, TablePagination, Chip, Avatar, IconButton,
  Button, Menu, MenuItem, Dialog, DialogTitle, DialogContent, DialogActions,
  FormControl, InputLabel, Select, Grid, Skeleton, Snackbar, Alert,
} from '@mui/material';
import {
  Search, MoreVert, Visibility, CheckCircle, Cancel, FilterList, Receipt,
} from '@mui/icons-material';
import { bookingApi, AdminBooking } from '../../services/adminApi';

const statusColors: Record<string, string> = {
  PENDING: '#f59e0b', QUOTATION_PENDING: '#f59e0b', QUOTATION_SENT: '#3b82f6',
  QUOTATION_ACCEPTED: '#10b981', QUOTATION_REJECTED: '#ef4444', AWAITING_PAYMENT: '#f97316',
  CONFIRMED: '#3b82f6', ASSIGNED: '#8b5cf6', IN_PROGRESS: '#8b5cf6',
  COMPLETED: '#10b981', CANCELLED: '#ef4444', REFUNDED: '#64748b', DISPUTED: '#f97316',
};

const paymentColors: Record<string, string> = {
  PAID: '#10b981', PENDING: '#f59e0b', REFUNDED: '#64748b', FAILED: '#ef4444',
};

const BookingManagement: React.FC = () => {
  const [bookings, setBookings] = useState<AdminBooking[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [totalElements, setTotalElements] = useState(0);
  const [openDialog, setOpenDialog] = useState(false);
  const [selectedBooking, setSelectedBooking] = useState<AdminBooking | null>(null);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [bookingStats, setBookingStats] = useState<any>({});
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>({ open: false, message: '', severity: 'success' });

  const fetchBookings = useCallback(async () => {
    try {
      setLoading(true);
      const response = await bookingApi.getBookings({
        page, size: rowsPerPage,
        search: searchQuery || undefined,
        status: statusFilter !== 'ALL' ? statusFilter : undefined,
      });
      const data = response.data;
      if (Array.isArray(data.data)) {
        setBookings(data.data);
        setTotalElements(data.totalElements || data.data.length);
      }
    } catch (err) {
      console.error('Failed to fetch bookings:', err);
      setSnackbar({ open: true, message: 'Failed to load bookings', severity: 'error' });
    } finally {
      setLoading(false);
    }
  }, [page, rowsPerPage, searchQuery, statusFilter]);

  useEffect(() => {
    fetchBookings();
    fetchBookingStats();
  }, [fetchBookings]);

  const fetchBookingStats = async () => {
    try {
      const response = await bookingApi.getBookingStats();
      setBookingStats(response.data.data || {});
    } catch (err) {
      console.error('Failed to fetch booking stats:', err);
    }
  };

  const handleStatusChange = async (status: string) => {
    if (!selectedBooking) return;
    try {
      await bookingApi.updateBookingStatus(selectedBooking.id, {
        status,
        reason: status === 'CANCELLED' ? 'Cancelled by admin' : undefined,
      });
      setSnackbar({ open: true, message: `Booking ${status.toLowerCase()} successfully`, severity: 'success' });
      setAnchorEl(null);
      setOpenDialog(false);
      fetchBookings();
      fetchBookingStats();
    } catch (err) {
      setSnackbar({ open: true, message: 'Failed to update booking', severity: 'error' });
    }
  };

  const formatCurrency = (amount: number) => {
    return `₹${(amount || 0).toLocaleString()}`;
  };

  const statCards = [
    { label: 'Active', value: bookingStats.activeBookings || 0, color: '#8b5cf6' },
    { label: 'Completed', value: bookingStats.completedCount || 0, color: '#10b981' },
    { label: 'Pending', value: bookingStats.pendingCount || 0, color: '#f59e0b' },
    { label: 'Disputed', value: bookingStats.disputedCount || 0, color: '#f97316' },
  ];

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800, mb: 0.5 }}>Booking Management</Typography>
          <Typography variant="body2" sx={{ color: '#64748b' }}>{totalElements} bookings found</Typography>
        </Box>
      </Box>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {statCards.map((stat) => (
          <Grid item xs={6} sm={3} key={stat.label}>
            <Card sx={{ p: 2, borderRadius: 2, textAlign: 'center', borderLeft: `4px solid ${stat.color}` }}>
              {loading ? <Skeleton width={40} height={40} sx={{ mx: 'auto' }} /> : (
                <Typography variant="h4" sx={{ fontWeight: 800, color: stat.color }}>{stat.value}</Typography>
              )}
              <Typography variant="body2" sx={{ color: '#64748b' }}>{stat.label}</Typography>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Card sx={{ borderRadius: 3, p: 2, mb: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} md={4}>
            <TextField fullWidth size="small" placeholder="Search by code or customer..."
              value={searchQuery} onChange={(e) => { setSearchQuery(e.target.value); setPage(0); }}
              InputProps={{ startAdornment: <InputAdornment position="start"><Search sx={{ color: '#94a3b8' }} /></InputAdornment> }}
              sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }} />
          </Grid>
          <Grid item xs={6} md={3}>
            <FormControl fullWidth size="small">
              <InputLabel>Status</InputLabel>
              <Select value={statusFilter} label="Status" onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}>
                <MenuItem value="ALL">All Status</MenuItem>
                <MenuItem value="PENDING">Pending</MenuItem>
                <MenuItem value="CONFIRMED">Confirmed</MenuItem>
                <MenuItem value="IN_PROGRESS">In Progress</MenuItem>
                <MenuItem value="COMPLETED">Completed</MenuItem>
                <MenuItem value="CANCELLED">Cancelled</MenuItem>
                <MenuItem value="DISPUTED">Disputed</MenuItem>
              </Select>
            </FormControl>
          </Grid>
          <Grid item xs={6} md={3}>
            <FormControl fullWidth size="small">
              <InputLabel>Payment</InputLabel>
              <Select label="Payment">
                <MenuItem value="ALL">All</MenuItem>
                <MenuItem value="PAID">Paid</MenuItem>
                <MenuItem value="PENDING">Pending</MenuItem>
                <MenuItem value="REFUNDED">Refunded</MenuItem>
              </Select>
            </FormControl>
          </Grid>
          <Grid item xs={12} md={2}>
            <Button startIcon={<FilterList />} fullWidth variant="outlined" sx={{ borderRadius: 2, color: '#64748b', borderColor: '#e2e8f0' }}>Filters</Button>
          </Grid>
        </Grid>
      </Card>

      <Card sx={{ borderRadius: 3, overflow: 'hidden' }}>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow sx={{ bgcolor: '#f8fafc' }}>
                <TableCell sx={{ fontWeight: 700 }}>Booking Code</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Customer</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Worker</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Service</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Amount</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Payment</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Date</TableCell>
                <TableCell sx={{ fontWeight: 700 }} align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                Array.from({ length: 5 }).map((_, idx) => (
                  <TableRow key={idx}>
                    {Array.from({ length: 9 }).map((_, cidx) => (
                      <TableCell key={cidx}><Skeleton /></TableCell>
                    ))}
                  </TableRow>
                ))
              ) : bookings.length > 0 ? (
                bookings.map((booking) => (
                  <TableRow key={booking.id} hover>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 600, color: '#667eea' }}>
                        {booking.bookingCode}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Avatar sx={{ width: 28, height: 28, fontSize: '0.75rem', bgcolor: '#eef2ff', color: '#667eea' }}>
                          {booking.customerName?.charAt(0) || '?'}
                        </Avatar>
                        <Typography variant="body2">{booking.customerName}</Typography>
                      </Box>
                    </TableCell>
                    <TableCell><Typography variant="body2" sx={{ color: '#64748b' }}>{booking.workerName || 'Unassigned'}</Typography></TableCell>
                    <TableCell><Typography variant="body2" sx={{ fontWeight: 500 }}>{booking.serviceName}</Typography></TableCell>
                    <TableCell>
                      <Chip label={booking.status?.replace(/_/g, ' ') || 'N/A'} size="small"
                        sx={{ bgcolor: `${statusColors[booking.status] || '#94a3b8'}15`, color: statusColors[booking.status] || '#94a3b8', fontWeight: 600, fontSize: '0.7rem' }} />
                    </TableCell>
                    <TableCell><Typography variant="body2" sx={{ fontWeight: 600 }}>{formatCurrency(booking.amount)}</Typography></TableCell>
                    <TableCell>
                      <Chip label={booking.paymentStatus || 'N/A'} size="small"
                        sx={{ bgcolor: `${paymentColors[booking.paymentStatus] || '#94a3b8'}15`, color: paymentColors[booking.paymentStatus] || '#94a3b8', fontWeight: 600, fontSize: '0.7rem' }} />
                    </TableCell>
                    <TableCell><Typography variant="body2" sx={{ color: '#64748b' }}>
                      {booking.createdAt ? new Date(booking.createdAt).toLocaleDateString() : 'N/A'}
                    </Typography></TableCell>
                    <TableCell align="right">
                      <IconButton size="small" onClick={(e) => { setSelectedBooking(booking); setAnchorEl(e.currentTarget); }}>
                        <MoreVert fontSize="small" />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={9} align="center" sx={{ py: 4 }}>
                    <Typography variant="body2" sx={{ color: '#94a3b8' }}>No bookings found</Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div" count={totalElements} page={page}
          onPageChange={(_, p) => setPage(p)} rowsPerPage={rowsPerPage}
          onRowsPerPageChange={(e) => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
        />
      </Card>

      <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => { setAnchorEl(null); setSelectedBooking(null); }} PaperProps={{ sx: { borderRadius: 2, minWidth: 160 } }}>
        <MenuItem onClick={() => { setOpenDialog(true); setAnchorEl(null); }}><Visibility fontSize="small" sx={{ mr: 1.5 }} /> View Details</MenuItem>
        <MenuItem onClick={() => handleStatusChange('COMPLETED')}><CheckCircle fontSize="small" sx={{ mr: 1.5 }} /> Mark Complete</MenuItem>
        <MenuItem onClick={() => handleStatusChange('CANCELLED')} sx={{ color: '#ef4444' }}><Cancel fontSize="small" sx={{ mr: 1.5 }} /> Cancel</MenuItem>
      </Menu>

      <Dialog open={openDialog} onClose={() => setOpenDialog(false)} maxWidth="md" fullWidth PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700 }}>Booking Details</DialogTitle>
        <DialogContent>
          {selectedBooking && (
            <Grid container spacing={2}>
              <Grid item xs={6}>
                <Typography variant="caption" sx={{ color: '#94a3b8' }}>Booking Code</Typography>
                <Typography variant="body2" sx={{ fontWeight: 600, fontFamily: 'monospace' }}>{selectedBooking.bookingCode}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="caption" sx={{ color: '#94a3b8' }}>Status</Typography>
                <Chip label={selectedBooking.status} size="small"
                  sx={{ ml: 1, bgcolor: `${statusColors[selectedBooking.status]}15`, color: statusColors[selectedBooking.status], fontWeight: 600 }} />
              </Grid>
              <Grid item xs={6}>
                <Typography variant="caption" sx={{ color: '#94a3b8' }}>Customer</Typography>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>{selectedBooking.customerName}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="caption" sx={{ color: '#94a3b8' }}>Worker</Typography>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>{selectedBooking.workerName || 'Unassigned'}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="caption" sx={{ color: '#94a3b8' }}>Amount</Typography>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>{formatCurrency(selectedBooking.amount)}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="caption" sx={{ color: '#94a3b8' }}>Date</Typography>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {selectedBooking.createdAt ? new Date(selectedBooking.createdAt).toLocaleDateString() : 'N/A'}
                </Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="caption" sx={{ color: '#94a3b8' }}>Service</Typography>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>{selectedBooking.serviceName}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="caption" sx={{ color: '#94a3b8' }}>City</Typography>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>{selectedBooking.city}</Typography>
              </Grid>
              {selectedBooking.description && (
                <Grid item xs={12}>
                  <Typography variant="caption" sx={{ color: '#94a3b8' }}>Description</Typography>
                  <Typography variant="body2">{selectedBooking.description}</Typography>
                </Grid>
              )}
            </Grid>
          )}
        </DialogContent>
        <DialogActions sx={{ p: 3 }}>
          <Button onClick={() => setOpenDialog(false)} sx={{ color: '#64748b' }}>Close</Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={snackbar.open} autoHideDuration={4000} onClose={() => setSnackbar({ ...snackbar, open: false })}>
        <Alert severity={snackbar.severity} sx={{ borderRadius: 2 }}>{snackbar.message}</Alert>
      </Snackbar>
    </Box>
  );
};

export default BookingManagement;
