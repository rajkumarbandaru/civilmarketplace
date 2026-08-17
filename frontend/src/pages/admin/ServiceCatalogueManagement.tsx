import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert, Avatar, Box, Button, Card, Chip, Dialog, DialogActions, DialogContent, DialogTitle,
  FormControl, FormControlLabel, Grid, IconButton, InputAdornment, InputLabel, MenuItem, Rating,
  Select, Skeleton, Snackbar, Switch, Table, TableBody, TableCell, TableContainer, TableHead,
  TableRow, TextField, Tooltip, Typography,
} from '@mui/material';
import { Add, Delete, Edit, Search } from '@mui/icons-material';
import DynamicIcon from '../../components/DynamicIcon';
import { SortableTableCell, useTableSort } from '../../components/admin/SortableTable';
import ServiceMedia from '../../components/ServiceMedia';
import {
  AdminCategory,
  AdminServiceOffering,
  ServiceMediaType,
  ServiceOfferingRequest,
  categoryApi,
  serviceCatalogueApi,
} from '../../services/adminApi';
import { invalidateCatalogue } from '../../hooks/useCatalogue';

/**
 * The catalogue the public Services page lists — create, edit, enable/disable and delete.
 *
 * These rows used to be a hard-coded array in the frontend, so the admin console showed categories
 * that had nothing under them and the site showed items no admin could touch. Both now read the
 * same table.
 */

const EMPTY_FORM: ServiceOfferingRequest = {
  title: '',
  category: '',
  slug: '',
  icon: '',
  price: '',
  mediaUrl: '',
  mediaType: '',
  rating: 0,
  reviews: 0,
  aliases: '',
  sortOrder: 0,
  active: true,
};

const MEDIA_TYPES: { value: ServiceMediaType | ''; label: string }[] = [
  { value: '', label: 'Detect from URL' },
  { value: 'IMAGE', label: 'Photo' },
  { value: 'VIDEO', label: 'Video' },
  { value: 'ANIMATION', label: 'Animation / GIF' },
];

/** The server's own explanation ("a service with this slug already exists"), when it sent one. */
const errorMessage = (err: unknown, fallback: string): string => {
  const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
  return message || fallback;
};

const ServiceCatalogueManagement: React.FC = () => {
  const [services, setServices] = useState<AdminServiceOffering[]>([]);
  const [categories, setCategories] = useState<AdminCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [openDialog, setOpenDialog] = useState(false);
  const [editing, setEditing] = useState<AdminServiceOffering | null>(null);
  const [form, setForm] = useState<ServiceOfferingRequest>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<AdminServiceOffering | null>(null);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>(
    { open: false, message: '', severity: 'success' }
  );

  const notify = (message: string, severity: 'success' | 'error' = 'success') =>
    setSnackbar({ open: true, message, severity });

  const load = async () => {
    try {
      setLoading(true);
      // Categories come along because the form picks from them — a free-text category field is how
      // "Materials" and "materials " end up as two categories on the public site.
      const [serviceRes, categoryRes] = await Promise.all([
        serviceCatalogueApi.getServices(),
        categoryApi.getCategories(),
      ]);
      setServices(Array.isArray(serviceRes.data.data) ? serviceRes.data.data : []);
      setCategories(Array.isArray(categoryRes.data.data) ? categoryRes.data.data : []);
    } catch (err) {
      notify(errorMessage(err, 'Failed to load the service catalogue'), 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const visible = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return services.filter((service) => {
      if (categoryFilter && service.category !== categoryFilter) return false;
      if (!needle) return true;
      return [service.title, service.category, service.slug, service.aliases || '']
        .join(' ')
        .toLowerCase()
        .includes(needle);
    });
  }, [services, query, categoryFilter]);

  const { sorted, sort, onSort } = useTableSort(visible, {
    title: (s) => s.title,
    category: (s) => s.category,
    price: (s) => s.price || '',
    rating: (s) => s.rating,
    media: (s) => (s.mediaUrl ? s.mediaType || 'IMAGE' : ''),
    active: (s) => s.active,
    createdAt: (s) => (s.createdAt ? new Date(s.createdAt) : null),
    // Newest first: after adding a service the admin's next move is to price it, add media, or
    // switch it live, and alphabetical order dropped it somewhere in the middle of the list.
  }, { key: 'createdAt', direction: 'desc' });

  const handleAdd = () => {
    setEditing(null);
    setForm({ ...EMPTY_FORM, category: categoryFilter || categories[0]?.name || '' });
    setOpenDialog(true);
  };

  const handleEdit = (service: AdminServiceOffering) => {
    setEditing(service);
    setForm({
      title: service.title,
      category: service.category,
      slug: service.slug,
      icon: service.icon || '',
      price: service.price || '',
      mediaUrl: service.mediaUrl || '',
      mediaType: service.mediaType || '',
      rating: service.rating,
      reviews: service.reviews,
      aliases: service.aliases || '',
      sortOrder: service.sortOrder,
      active: service.active,
    });
    setOpenDialog(true);
  };

  const handleSave = async () => {
    if (!form.title.trim() || !form.category.trim()) {
      notify('Title and category are both required', 'error');
      return;
    }
    try {
      setSaving(true);
      if (editing) {
        await serviceCatalogueApi.updateService(editing.id, form);
        notify(`“${form.title}” updated`);
      } else {
        await serviceCatalogueApi.createService(form);
        notify(`“${form.title}” added to the catalogue`);
      }
      setOpenDialog(false);
      // The public site caches the catalogue per session; without this the change is invisible
      // there until a hard reload.
      invalidateCatalogue();
      await load();
    } catch (err) {
      notify(errorMessage(err, 'Failed to save the service'), 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleToggle = async (service: AdminServiceOffering) => {
    // Optimistic, then reconciled by the reload: the switch is the one control an admin flicks down
    // a long list, and a half-second of nothing happening reads as a dead toggle.
    setServices((rows) =>
      rows.map((row) => (row.id === service.id ? { ...row, active: !row.active } : row))
    );
    try {
      await serviceCatalogueApi.toggleServiceStatus(service.id);
      notify(`“${service.title}” ${service.active ? 'disabled' : 'enabled'}`);
      invalidateCatalogue();
      await load();
    } catch (err) {
      notify(errorMessage(err, 'Failed to change the service status'), 'error');
      await load();
    }
  };

  const handleDelete = async (service: AdminServiceOffering) => {
    try {
      await serviceCatalogueApi.deleteService(service.id);
      notify(`“${service.title}” deleted`);
      invalidateCatalogue();
      await load();
    } catch (err) {
      notify(errorMessage(err, 'Failed to delete the service'), 'error');
    } finally {
      setConfirmDelete(null);
    }
  };

  const set = <K extends keyof ServiceOfferingRequest>(key: K, value: ServiceOfferingRequest[K]) =>
    setForm((current) => ({ ...current, [key]: value }));

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3, flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800, mb: 0.5 }}>Services</Typography>
          <Typography variant="body2" sx={{ color: '#64748b' }}>
            Everything the public Services page lists — professional services, materials, equipment and vehicles
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<Add />} onClick={handleAdd} sx={{ borderRadius: 2 }}>
          Add Service
        </Button>
      </Box>

      <Card sx={{ borderRadius: 3, p: 2, mb: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              size="small"
              placeholder="Search by name, slug or search alias…"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start"><Search fontSize="small" /></InputAdornment>
                ),
              }}
            />
          </Grid>
          <Grid item xs={12} md={4}>
            <FormControl fullWidth size="small">
              <InputLabel>Category</InputLabel>
              <Select
                label="Category"
                value={categoryFilter}
                onChange={(e) => setCategoryFilter(e.target.value)}
              >
                <MenuItem value="">All categories</MenuItem>
                {categories.map((category) => (
                  <MenuItem key={category.id} value={category.name}>{category.name}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
          <Grid item xs={12} md={2}>
            <Typography variant="body2" sx={{ color: '#64748b', textAlign: 'center' }}>
              {visible.length} of {services.length}
            </Typography>
          </Grid>
        </Grid>
      </Card>

      <Card sx={{ borderRadius: 3, overflow: 'hidden' }}>
        <TableContainer sx={{ overflowX: 'auto' }}>
          <Table>
            <TableHead>
              <TableRow sx={{ bgcolor: 'action.hover' }}>
                <SortableTableCell columnKey="title" sort={sort} onSort={onSort}>Service</SortableTableCell>
                <SortableTableCell columnKey="category" sort={sort} onSort={onSort}>Category</SortableTableCell>
                <SortableTableCell columnKey="price" sort={sort} onSort={onSort}>Price</SortableTableCell>
                <SortableTableCell columnKey="rating" sort={sort} onSort={onSort}>Rating</SortableTableCell>
                <SortableTableCell columnKey="media" sort={sort} onSort={onSort}>Media</SortableTableCell>
                <SortableTableCell columnKey="active" sort={sort} onSort={onSort}>Live</SortableTableCell>
                <SortableTableCell columnKey="createdAt" sort={sort} onSort={onSort}>Added</SortableTableCell>
                <TableCell sx={{ fontWeight: 700 }} align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                Array.from({ length: 6 }).map((_, idx) => (
                  <TableRow key={idx}>
                    {Array.from({ length: 8 }).map((__, cidx) => (
                      <TableCell key={cidx}><Skeleton /></TableCell>
                    ))}
                  </TableRow>
                ))
              ) : sorted.length > 0 ? (
                sorted.map((service) => (
                  <TableRow key={service.id} hover sx={{ opacity: service.active ? 1 : 0.55 }}>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                        <Avatar sx={{ bgcolor: 'action.hover', color: 'primary.main', width: 36, height: 36 }}>
                          <DynamicIcon name={service.icon || 'Handyman'} />
                        </Avatar>
                        <Box>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>{service.title}</Typography>
                          <Typography variant="caption" sx={{ color: '#94a3b8', fontFamily: 'monospace' }}>
                            {service.slug}
                          </Typography>
                        </Box>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Chip label={service.category} size="small" variant="outlined" />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{service.price || 'Quote'}</Typography>
                    </TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                        <Rating value={service.rating} precision={0.1} size="small" readOnly />
                        <Typography variant="caption" sx={{ color: '#94a3b8' }}>({service.reviews})</Typography>
                      </Box>
                    </TableCell>
                    <TableCell>
                      {service.mediaUrl
                        ? <Chip label={(service.mediaType || 'IMAGE').toLowerCase()} size="small" color="primary" variant="outlined" />
                        : <Typography variant="caption" sx={{ color: '#94a3b8' }}>Icon only</Typography>}
                    </TableCell>
                    <TableCell>
                      <Tooltip title={service.active ? 'Shown on the site' : 'Hidden from the site'}>
                        <Switch
                          checked={service.active}
                          size="small"
                          onChange={() => handleToggle(service)}
                        />
                      </Tooltip>
                    </TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>
                      <Typography variant="caption" sx={{ color: '#94a3b8' }}>
                        {service.createdAt
                          ? new Date(service.createdAt).toLocaleDateString()
                          : '—'}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      <IconButton size="small" onClick={() => handleEdit(service)} sx={{ mr: 1 }}>
                        <Edit fontSize="small" />
                      </IconButton>
                      <IconButton size="small" color="error" onClick={() => setConfirmDelete(service)}>
                        <Delete fontSize="small" />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={8} align="center" sx={{ py: 4 }}>
                    <Typography variant="body2" sx={{ color: '#94a3b8' }}>
                      {services.length === 0 ? 'No services yet' : 'Nothing matches that search'}
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      <Dialog open={openDialog} onClose={() => setOpenDialog(false)} maxWidth="md" fullWidth PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700 }}>{editing ? 'Edit Service' : 'Add Service'}</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid item xs={12} md={6}>
              <TextField
                fullWidth size="small" label="Title" value={form.title}
                onChange={(e) => set('title', e.target.value)}
                helperText="Shown on the card, e.g. “Iron & TMT Steel Bars”"
              />
            </Grid>
            <Grid item xs={12} md={6}>
              <FormControl fullWidth size="small">
                <InputLabel>Category</InputLabel>
                <Select label="Category" value={form.category} onChange={(e) => set('category', e.target.value)}>
                  {categories.map((category) => (
                    <MenuItem key={category.id} value={category.name}>{category.name}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} md={6}>
              <TextField
                fullWidth size="small" label="Slug" value={form.slug}
                onChange={(e) => set('slug', e.target.value)}
                placeholder="derived from the title"
                // Editing it changes the booking URL, which is why it is not auto-rewritten when
                // the title changes on an existing row.
                helperText={editing ? 'Changing this changes the booking URL' : 'Leave blank to derive it from the title'}
              />
            </Grid>
            <Grid item xs={12} md={6}>
              <TextField
                fullWidth size="small" label="Price" value={form.price}
                onChange={(e) => set('price', e.target.value)}
                placeholder="₹500/hr, ₹1600/ton, Quote"
                helperText="Free text — the unit differs per trade"
              />
            </Grid>
            <Grid item xs={12} md={6}>
              <TextField
                fullWidth size="small" label="Icon name" value={form.icon}
                onChange={(e) => set('icon', e.target.value)}
                placeholder="Construction"
                helperText="Material-UI icon name; falls back to a generic tool icon"
                InputProps={{
                  endAdornment: (
                    <InputAdornment position="end">
                      <DynamicIcon name={form.icon || 'Handyman'} />
                    </InputAdornment>
                  ),
                }}
              />
            </Grid>
            <Grid item xs={6} md={3}>
              <TextField
                fullWidth size="small" type="number" label="Rating" value={form.rating ?? 0}
                onChange={(e) => set('rating', Math.min(5, Math.max(0, Number(e.target.value) || 0)))}
                inputProps={{ min: 0, max: 5, step: 0.1 }}
              />
            </Grid>
            <Grid item xs={6} md={3}>
              <TextField
                fullWidth size="small" type="number" label="Reviews" value={form.reviews ?? 0}
                onChange={(e) => set('reviews', Math.max(0, Number(e.target.value) || 0))}
                inputProps={{ min: 0 }}
              />
            </Grid>

            <Grid item xs={12} md={8}>
              <TextField
                fullWidth size="small" label="Photo / video / animation URL" value={form.mediaUrl}
                onChange={(e) => set('mediaUrl', e.target.value)}
                placeholder="https://…/excavator.jpg"
                helperText="Optional. Shown on the card in place of the icon"
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <FormControl fullWidth size="small">
                <InputLabel>Media type</InputLabel>
                <Select
                  label="Media type"
                  value={form.mediaType || ''}
                  onChange={(e) => set('mediaType', e.target.value as ServiceMediaType | '')}
                >
                  {MEDIA_TYPES.map((type) => (
                    <MenuItem key={type.value || 'auto'} value={type.value}>{type.label}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            {form.mediaUrl ? (
              <Grid item xs={12}>
                {/* Previewed at the size the card renders it, so a wrong link or a video that will
                    not play is caught here rather than on the live site. */}
                <Typography variant="caption" sx={{ color: '#64748b' }}>Preview</Typography>
                <Box sx={{ maxWidth: 320 }}>
                  <ServiceMedia
                    mediaUrl={form.mediaUrl}
                    mediaType={(form.mediaType || null) as ServiceMediaType | null}
                    title={form.title}
                  />
                </Box>
              </Grid>
            ) : null}

            <Grid item xs={12} md={8}>
              <TextField
                fullWidth size="small" label="Search aliases" value={form.aliases}
                onChange={(e) => set('aliases', e.target.value)}
                placeholder="rebar, tmt, sariya"
                helperText="Comma-separated. What people type instead of the official name"
              />
            </Grid>
            <Grid item xs={6} md={2}>
              <TextField
                fullWidth size="small" type="number" label="Sort order" value={form.sortOrder ?? 0}
                onChange={(e) => set('sortOrder', Number(e.target.value) || 0)}
              />
            </Grid>
            <Grid item xs={6} md={2}>
              <FormControlLabel
                control={<Switch checked={!!form.active} onChange={(e) => set('active', e.target.checked)} />}
                label="Live"
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ p: 3 }}>
          <Button onClick={() => setOpenDialog(false)} sx={{ color: '#64748b' }}>Cancel</Button>
          <Button variant="contained" onClick={handleSave} disabled={saving}>
            {saving ? 'Saving…' : editing ? 'Update' : 'Create'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={!!confirmDelete} onClose={() => setConfirmDelete(null)} PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700 }}>Delete “{confirmDelete?.title}”?</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            This removes it from the catalogue permanently and any link to it stops working. To take it
            off the site while keeping it, switch it off instead.
          </Typography>
        </DialogContent>
        <DialogActions sx={{ p: 3 }}>
          <Button onClick={() => setConfirmDelete(null)} sx={{ color: '#64748b' }}>Cancel</Button>
          <Button color="error" variant="contained" onClick={() => confirmDelete && handleDelete(confirmDelete)}>
            Delete
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={snackbar.open} autoHideDuration={4000} onClose={() => setSnackbar({ ...snackbar, open: false })}>
        <Alert severity={snackbar.severity} sx={{ borderRadius: 2 }}>{snackbar.message}</Alert>
      </Snackbar>
    </Box>
  );
};

export default ServiceCatalogueManagement;
