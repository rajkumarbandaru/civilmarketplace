import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Container,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Grid,
  IconButton,
  InputAdornment,
  MenuItem,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import {
  Add as AddIcon,
  Delete as DeleteIcon,
  Edit as EditIcon,
} from '@mui/icons-material';
import { useAppSelector } from '../../hooks';
import { apiErrorMessage } from '../../services/apiError';
import {
  MaterialItem,
  MaterialPriceInput,
  SupplierMaterialPrice,
  UNIT_LABEL,
  createMaterialPrice,
  deleteMaterialPrice,
  fetchMaterialCatalogue,
  fetchMyMaterialPrices,
  updateMaterialPrice,
} from '../../services/materialApi';
import { SortableTableCell, useTableSort } from '../../components/admin/SortableTable';

/** Roles the service accepts a published rate from; anything else gets a read-only explanation. */
const SUPPLIER_ROLES = ['MATERIAL_SUPPLIER', 'EQUIPMENT_RENTAL', 'ADMIN', 'SUPER_ADMIN'];

interface FormState {
  id: number | null;
  materialItemId: number | '';
  price: string;
  city: string;
  brand: string;
  minOrderQuantity: string;
  deliveryIncluded: boolean;
  notes: string;
  isActive: boolean;
}

const EMPTY_FORM: FormState = {
  id: null,
  materialItemId: '',
  price: '',
  city: '',
  brand: '',
  minOrderQuantity: '',
  deliveryIncluded: false,
  notes: '',
  isActive: true,
};

/**
 * A supplier's own material price list.
 *
 * <p>These rates are not decoration: the Civil AI Assistant quotes the low and the high of every
 * material back to customers with the publishing supplier's user ID attached, so this page is
 * saying "quote me at this" rather than filling in a profile. The copy says so, because a rate
 * left stale here is a rate someone else is budgeting against.
 */
const MaterialPricesPage: React.FC = () => {
  const { user } = useAppSelector((state) => state.auth);
  const canPublish = SUPPLIER_ROLES.includes((user?.role ?? '').toUpperCase());

  const [catalogue, setCatalogue] = useState<MaterialItem[]>([]);
  const [prices, setPrices] = useState<SupplierMaterialPrice[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [items, mine] = await Promise.all([
        fetchMaterialCatalogue(),
        fetchMyMaterialPrices(),
      ]);
      setCatalogue(items);
      setPrices(mine);
      setError(null);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not load your price list.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const { sorted, sort, onSort } = useTableSort(prices, {
    material: (p) => p.materialItem.name,
    unit: (p) => UNIT_LABEL[p.materialItem.unit],
    price: (p) => Number(p.price) || 0,
    city: (p) => p.city,
    brand: (p) => p.brand,
    minOrderQuantity: (p) => p.minOrderQuantity ?? null,
    deliveryIncluded: (p) => p.deliveryIncluded,
    isActive: (p) => p.isActive,
    updatedAt: (p) => (p.updatedAt ? new Date(p.updatedAt) : null),
    // Most recently changed first. A rate list is read to check what is current, and a rate that
    // has not been touched in months is the one worth noticing — sorting by material name buried
    // that under the alphabet.
  }, { key: 'updatedAt', direction: 'desc' });

  const selectedMaterial = useMemo(
    () => catalogue.find((item) => item.id === form.materialItemId),
    [catalogue, form.materialItemId],
  );

  const openCreate = () => {
    setForm(EMPTY_FORM);
    setDialogOpen(true);
  };

  const openEdit = (price: SupplierMaterialPrice) => {
    setForm({
      id: price.id,
      materialItemId: price.materialItem.id,
      price: String(price.price),
      city: price.city,
      brand: price.brand ?? '',
      minOrderQuantity: price.minOrderQuantity == null ? '' : String(price.minOrderQuantity),
      deliveryIncluded: price.deliveryIncluded,
      notes: price.notes ?? '',
      isActive: price.isActive,
    });
    setDialogOpen(true);
  };

  const save = async () => {
    if (form.materialItemId === '' || !form.price.trim() || !form.city.trim()) return;

    const payload: MaterialPriceInput = {
      materialItem: { id: Number(form.materialItemId) },
      price: Number(form.price),
      city: form.city.trim(),
      brand: form.brand.trim() || undefined,
      minOrderQuantity: form.minOrderQuantity.trim() ? Number(form.minOrderQuantity) : null,
      deliveryIncluded: form.deliveryIncluded,
      notes: form.notes.trim() || undefined,
      isActive: form.isActive,
    };

    setSaving(true);
    try {
      if (form.id == null) {
        await createMaterialPrice(payload);
        setNotice('Rate published. It will appear in estimates within a few minutes.');
      } else {
        // The material and city form the row's identity on the service, so an edit changes only
        // the commercial terms; moving a rate to another city means publishing a new one.
        const { materialItem, city, ...editable } = payload;
        await updateMaterialPrice(form.id, editable);
        setNotice('Rate updated.');
      }
      setDialogOpen(false);
      await load();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save that rate.'));
    } finally {
      setSaving(false);
    }
  };

  const remove = async (price: SupplierMaterialPrice) => {
    setError(null);
    try {
      await deleteMaterialPrice(price.id);
      setNotice(`Removed your rate for ${price.materialItem.name}.`);
      await load();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not remove that rate.'));
    }
  };

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 1 }}
             alignItems={{ sm: 'center' }} justifyContent="space-between">
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>Material price list</Typography>
          <Typography variant="body2" color="text.secondary">
            Rates you publish here are quoted in customer estimates, with your supplier ID shown
            against them.
          </Typography>
        </Box>
        {canPublish && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>
            Publish a rate
          </Button>
        )}
      </Stack>

      {!canPublish && (
        <Alert severity="info" sx={{ my: 2 }}>
          Only registered material suppliers can publish rates. Your account is signed in as
          {` ${user?.role ?? 'a customer'}`}.
        </Alert>
      )}

      {error && <Alert severity="error" sx={{ my: 2 }} onClose={() => setError(null)}>{error}</Alert>}
      {notice && <Alert severity="success" sx={{ my: 2 }} onClose={() => setNotice(null)}>{notice}</Alert>}

      <Card sx={{ mt: 2 }}>
        <CardContent>
          {loading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
              <CircularProgress />
            </Box>
          ) : prices.length === 0 ? (
            <Box sx={{ textAlign: 'center', py: 6 }}>
              <Typography variant="body2" color="text.secondary">
                You have not published any material rates yet. Estimates will quote market
                assumptions for these materials until you do.
              </Typography>
            </Box>
          ) : (
            <TableContainer sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <SortableTableCell columnKey="material" sort={sort} onSort={onSort}>Material</SortableTableCell>
                    <SortableTableCell columnKey="unit" sort={sort} onSort={onSort}>Unit</SortableTableCell>
                    <SortableTableCell columnKey="price" sort={sort} onSort={onSort} align="right">Rate</SortableTableCell>
                    <SortableTableCell columnKey="city" sort={sort} onSort={onSort}>City</SortableTableCell>
                    <SortableTableCell columnKey="brand" sort={sort} onSort={onSort}>Brand</SortableTableCell>
                    <SortableTableCell columnKey="minOrderQuantity" sort={sort} onSort={onSort} align="right">Min order</SortableTableCell>
                    <SortableTableCell columnKey="deliveryIncluded" sort={sort} onSort={onSort}>Delivery</SortableTableCell>
                    <SortableTableCell columnKey="isActive" sort={sort} onSort={onSort}>Status</SortableTableCell>
                    <SortableTableCell columnKey="updatedAt" sort={sort} onSort={onSort}>Updated</SortableTableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {sorted.map((price) => (
                    <TableRow key={price.id} hover>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                          {price.materialItem.name}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {price.materialItem.specification}
                        </Typography>
                      </TableCell>
                      <TableCell>{UNIT_LABEL[price.materialItem.unit]}</TableCell>
                      <TableCell align="right">
                        {price.currency} {Number(price.price).toLocaleString('en-IN')}
                      </TableCell>
                      <TableCell>{price.city}</TableCell>
                      <TableCell>{price.brand || '—'}</TableCell>
                      <TableCell align="right">{price.minOrderQuantity ?? '—'}</TableCell>
                      <TableCell>{price.deliveryIncluded ? 'Included' : 'Extra'}</TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          label={price.isActive ? 'Live' : 'Paused'}
                          color={price.isActive ? 'success' : 'default'}
                          variant={price.isActive ? 'filled' : 'outlined'}
                        />
                      </TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>
                        {price.updatedAt
                          ? new Date(price.updatedAt).toLocaleDateString()
                          : '—'}
                      </TableCell>
                      <TableCell align="right">
                        <Tooltip title="Edit rate">
                          <IconButton size="small" onClick={() => openEdit(price)}>
                            <EditIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Remove rate">
                          <IconButton size="small" onClick={() => remove(price)}>
                            <DeleteIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{form.id == null ? 'Publish a rate' : 'Edit rate'}</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0 }}>
            <Grid item xs={12}>
              <TextField
                select
                fullWidth
                label="Material"
                value={form.materialItemId}
                // The material and city are the row's key on the service, so changing them on an
                // existing rate would silently create a different one.
                disabled={form.id != null}
                onChange={(event) =>
                  setForm({ ...form, materialItemId: Number(event.target.value) })}
                helperText={selectedMaterial
                  ? `Quoted per ${UNIT_LABEL[selectedMaterial.unit]}`
                  : 'Pick the catalogue entry customers will see'}
              >
                {catalogue.map((item) => (
                  <MenuItem key={item.id} value={item.id}>
                    {item.category ? `${item.category} — ${item.name}` : item.name}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Rate"
                type="number"
                value={form.price}
                onChange={(event) => setForm({ ...form, price: event.target.value })}
                InputProps={{
                  startAdornment: <InputAdornment position="start">₹</InputAdornment>,
                  endAdornment: selectedMaterial ? (
                    <InputAdornment position="end">
                      per {UNIT_LABEL[selectedMaterial.unit]}
                    </InputAdornment>
                  ) : undefined,
                }}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="City"
                value={form.city}
                disabled={form.id != null}
                onChange={(event) => setForm({ ...form, city: event.target.value })}
                helperText="Where this rate applies"
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Brand or make"
                value={form.brand}
                onChange={(event) => setForm({ ...form, brand: event.target.value })}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Minimum order quantity"
                type="number"
                value={form.minOrderQuantity}
                onChange={(event) => setForm({ ...form, minOrderQuantity: event.target.value })}
                helperText="Leave blank if there is no minimum"
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Notes"
                value={form.notes}
                multiline
                minRows={2}
                onChange={(event) => setForm({ ...form, notes: event.target.value })}
                helperText="Anything a customer should know before budgeting against this rate"
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <FormControlLabel
                control={
                  <Switch
                    checked={form.deliveryIncluded}
                    onChange={(event) =>
                      setForm({ ...form, deliveryIncluded: event.target.checked })}
                  />
                }
                label="Delivery included"
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <FormControlLabel
                control={
                  <Switch
                    checked={form.isActive}
                    onChange={(event) => setForm({ ...form, isActive: event.target.checked })}
                  />
                }
                label="Quote this rate in estimates"
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={save}
            disabled={saving || form.materialItemId === '' || !form.price.trim() || !form.city.trim()}
          >
            {saving ? 'Saving…' : 'Save rate'}
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
};

export default MaterialPricesPage;
