import React, { useState, useEffect, useCallback } from 'react';
import {
  Box, Card, Typography, TextField, InputAdornment, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, TablePagination, Chip, Avatar, IconButton,
  Button, Menu, MenuItem, Dialog, DialogTitle, DialogContent, DialogActions,
  FormControl, InputLabel, Select, Grid, Skeleton, Snackbar, Alert,
} from '@mui/material';
import {
  Search, MoreVert, Edit, Delete, Block, CheckCircle, FilterList, PersonAdd,
} from '@mui/icons-material';
import { userApi, AdminUser } from '../../services/adminApi';

const roleColors: Record<string, string> = {
  CUSTOMER: '#667eea', CIVIL_ENGINEER: '#10b981', ARCHITECT: '#f59e0b',
  SURVEYOR: '#8b5cf6', WORKER: '#06b6d4', CONTRACTOR: '#ef4444', ADMIN: '#1e293b',
  SUPER_ADMIN: '#dc2626', SUB_ADMIN: '#0891b2',
};

const statusColors: Record<string, string> = {
  ACTIVE: '#10b981', PENDING: '#f59e0b', SUSPENDED: '#ef4444',
  BANNED: '#dc2626', INACTIVE: '#94a3b8', PENDING_VERIFICATION: '#f59e0b',
};

const UserManagement: React.FC = () => {
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [searchQuery, setSearchQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('ALL');
  const [totalElements, setTotalElements] = useState(0);
  const [openDialog, setOpenDialog] = useState(false);
  const [selectedUser, setSelectedUser] = useState<any>(null);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>({ open: false, message: '', severity: 'success' });

  const fetchUsers = useCallback(async () => {
    try {
      setLoading(true);
      const response = await userApi.getUsers({
        page, size: rowsPerPage,
        search: searchQuery || undefined,
        role: roleFilter !== 'ALL' ? roleFilter : undefined,
      });
      const data = response.data;
      if (Array.isArray(data.data)) {
        setUsers(data.data);
        setTotalElements(data.totalElements || data.data.length);
      } else {
        setUsers([]);
        setTotalElements(0);
      }
    } catch (err) {
      console.error('Failed to fetch users:', err);
      setSnackbar({ open: true, message: 'Failed to load users from server', severity: 'error' });
    } finally {
      setLoading(false);
    }
  }, [page, rowsPerPage, searchQuery, roleFilter]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const handleUpdateUser = async () => {
    if (!selectedUser) return;
    try {
      await userApi.updateUser(selectedUser.id, {
        name: selectedUser.name,
        email: selectedUser.email,
        role: selectedUser.role,
        status: selectedUser.status,
      });
      setSnackbar({ open: true, message: 'User updated successfully', severity: 'success' });
      setOpenDialog(false);
      fetchUsers();
    } catch (err) {
      setSnackbar({ open: true, message: 'Failed to update user', severity: 'error' });
    }
  };

  const handleStatusAction = async (action: string) => {
    if (!selectedUser) return;
    try {
      const statusMap: Record<string, string> = {
        'Verify': 'ACTIVE',
        'Suspend': 'SUSPENDED',
        'Activate': 'ACTIVE',
        'Ban': 'BANNED',
      };
      await userApi.updateUserStatus(selectedUser.id, {
        status: statusMap[action] || 'ACTIVE',
        reason: `${action} by admin`,
      });
      setSnackbar({ open: true, message: `User ${action.toLowerCase()}d successfully`, severity: 'success' });
      setAnchorEl(null);
      fetchUsers();
    } catch (err) {
      setSnackbar({ open: true, message: `Failed to ${action.toLowerCase()} user`, severity: 'error' });
    }
  };

  const handleDeleteUser = async () => {
    if (!selectedUser) return;
    try {
      await userApi.deleteUser(selectedUser.id);
      setSnackbar({ open: true, message: 'User deleted successfully', severity: 'success' });
      setAnchorEl(null);
      fetchUsers();
    } catch (err) {
      setSnackbar({ open: true, message: 'Failed to delete user', severity: 'error' });
    }
  };

  const filteredUsers = users;

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800, mb: 0.5 }}>User Management</Typography>
          <Typography variant="body2" sx={{ color: '#64748b' }}>{totalElements} users total</Typography>
        </Box>
        <Button variant="contained" startIcon={<PersonAdd />} sx={{ borderRadius: 2 }}>Add User</Button>
      </Box>

      <Card sx={{ borderRadius: 3, p: 2, mb: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} md={5}>
            <TextField
              fullWidth size="small" placeholder="Search by name or email..."
              value={searchQuery}
              onChange={(e) => { setSearchQuery(e.target.value); setPage(0); }}
              InputProps={{ startAdornment: <InputAdornment position="start"><Search sx={{ color: '#94a3b8' }} /></InputAdornment> }}
              sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }}
            />
          </Grid>
          <Grid item xs={6} md={3}>
            <FormControl fullWidth size="small">
              <InputLabel>Role</InputLabel>
              <Select value={roleFilter} label="Role" onChange={(e) => { setRoleFilter(e.target.value); setPage(0); }}>
                <MenuItem value="ALL">All Roles</MenuItem>
                <MenuItem value="CUSTOMER">Customer</MenuItem>
                <MenuItem value="CIVIL_ENGINEER">Civil Engineer</MenuItem>
                <MenuItem value="ARCHITECT">Architect</MenuItem>
                <MenuItem value="SURVEYOR">Surveyor</MenuItem>
                <MenuItem value="WORKER">Worker</MenuItem>
                <MenuItem value="CONTRACTOR">Contractor</MenuItem>
                <MenuItem value="ADMIN">Admin</MenuItem>
              </Select>
            </FormControl>
          </Grid>
          <Grid item xs={6} md={2}>
            <Button startIcon={<FilterList />} fullWidth sx={{ borderRadius: 2, color: '#64748b', borderColor: '#e2e8f0' }} variant="outlined">
              More Filters
            </Button>
          </Grid>
          <Grid item xs={12} md={2}>
            <Chip label={`${users.filter(u => u.status === 'PENDING').length} pending`} color="warning" sx={{ fontWeight: 600, width: '100%' }} />
          </Grid>
        </Grid>
      </Card>

      <Card sx={{ borderRadius: 3, overflow: 'hidden' }}>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow sx={{ bgcolor: 'action.hover' }}>
                <TableCell sx={{ fontWeight: 700 }}>User</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Role</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>City</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Bookings</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Rating</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Joined</TableCell>
                <TableCell sx={{ fontWeight: 700 }} align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                Array.from({ length: 5 }).map((_, idx) => (
                  <TableRow key={idx}>
                    {Array.from({ length: 8 }).map((_, cidx) => (
                      <TableCell key={cidx}><Skeleton /></TableCell>
                    ))}
                  </TableRow>
                ))
              ) : filteredUsers.length > 0 ? (
                filteredUsers.map((user) => (
                  <TableRow key={user.id} hover sx={{ cursor: 'pointer' }}>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                        <Avatar sx={{ width: 36, height: 36, bgcolor: `${roleColors[user.role] || '#667eea'}20`, color: roleColors[user.role] || '#667eea', fontSize: '0.875rem' }}>
                          {user.name?.charAt(0) || '?'}
                        </Avatar>
                        <Box>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>{user.name}</Typography>
                          <Typography variant="caption" sx={{ color: '#94a3b8' }}>{user.email}</Typography>
                        </Box>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Chip label={user.role?.replace(/_/g, ' ') || 'N/A'} size="small"
                        sx={{ bgcolor: `${roleColors[user.role] || '#667eea'}15`, color: roleColors[user.role] || '#667eea', fontWeight: 600, fontSize: '0.75rem' }} />
                    </TableCell>
                    <TableCell>
                      <Chip label={user.status} size="small"
                        sx={{ bgcolor: `${statusColors[user.status] || '#94a3b8'}15`, color: statusColors[user.status] || '#94a3b8', fontWeight: 600, fontSize: '0.75rem' }} />
                    </TableCell>
                    <TableCell><Typography variant="body2">{user.city || 'N/A'}</Typography></TableCell>
                    <TableCell><Typography variant="body2" sx={{ fontWeight: 600 }}>{user.bookings || 0}</Typography></TableCell>
                    <TableCell><Typography variant="body2" sx={{ fontWeight: 600 }}>⭐ {user.rating || 'N/A'}</Typography></TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ color: '#64748b' }}>
                        {user.joinedAt ? new Date(user.joinedAt).toLocaleDateString() : 'N/A'}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      <IconButton size="small" onClick={(e) => { setSelectedUser(user); setAnchorEl(e.currentTarget); }}>
                        <MoreVert />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={8} align="center" sx={{ py: 4 }}>
                    <Typography variant="body2" sx={{ color: '#94a3b8' }}>No users found</Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={totalElements}
          page={page}
          onPageChange={(_, p) => setPage(p)}
          rowsPerPage={rowsPerPage}
          onRowsPerPageChange={(e) => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
        />
      </Card>

      <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => setAnchorEl(null)} PaperProps={{ sx: { borderRadius: 2, minWidth: 160 } }}>
        <MenuItem onClick={() => { setOpenDialog(true); setAnchorEl(null); }}><Edit fontSize="small" sx={{ mr: 1.5 }} /> Edit User</MenuItem>
        <MenuItem onClick={() => handleStatusAction('Verify')}><CheckCircle fontSize="small" sx={{ mr: 1.5 }} /> Verify</MenuItem>
        <MenuItem onClick={() => handleStatusAction('Suspend')} sx={{ color: '#f59e0b' }}><Block fontSize="small" sx={{ mr: 1.5 }} /> Suspend</MenuItem>
        <MenuItem onClick={handleDeleteUser} sx={{ color: '#ef4444' }}><Delete fontSize="small" sx={{ mr: 1.5 }} /> Delete</MenuItem>
      </Menu>

      <Dialog open={openDialog} onClose={() => setOpenDialog(false)} maxWidth="sm" fullWidth PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700 }}>Edit User</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={6}>
              <TextField fullWidth label="Name" value={selectedUser?.name || ''} onChange={(e) => setSelectedUser({ ...selectedUser, name: e.target.value })} size="small" />
            </Grid>
            <Grid item xs={6}>
              <TextField fullWidth label="Email" value={selectedUser?.email || ''} onChange={(e) => setSelectedUser({ ...selectedUser, email: e.target.value })} size="small" />
            </Grid>
            <Grid item xs={6}>
              <FormControl fullWidth size="small">
                <InputLabel>Role</InputLabel>
                <Select value={selectedUser?.role || 'CUSTOMER'} label="Role" onChange={(e) => setSelectedUser({ ...selectedUser, role: e.target.value })}>
                  <MenuItem value="CUSTOMER">Customer</MenuItem>
                  <MenuItem value="CIVIL_ENGINEER">Civil Engineer</MenuItem>
                  <MenuItem value="ARCHITECT">Architect</MenuItem>
                  <MenuItem value="ADMIN">Admin</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={6}>
              <FormControl fullWidth size="small">
                <InputLabel>Status</InputLabel>
                <Select value={selectedUser?.status || 'ACTIVE'} label="Status" onChange={(e) => setSelectedUser({ ...selectedUser, status: e.target.value })}>
                  <MenuItem value="ACTIVE">Active</MenuItem>
                  <MenuItem value="PENDING">Pending</MenuItem>
                  <MenuItem value="SUSPENDED">Suspended</MenuItem>
                </Select>
              </FormControl>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ p: 3 }}>
          <Button onClick={() => setOpenDialog(false)} sx={{ color: '#64748b' }}>Cancel</Button>
          <Button variant="contained" onClick={handleUpdateUser}>Save Changes</Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={snackbar.open} autoHideDuration={4000} onClose={() => setSnackbar({ ...snackbar, open: false })}>
        <Alert severity={snackbar.severity} sx={{ borderRadius: 2 }}>{snackbar.message}</Alert>
      </Snackbar>
    </Box>
  );
};

export default UserManagement;
