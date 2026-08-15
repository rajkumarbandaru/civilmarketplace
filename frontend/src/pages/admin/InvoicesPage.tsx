import React, { useCallback, useEffect, useState } from 'react';
import {
  Box, Card, CardContent, Typography, Grid, Chip, Avatar, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, TablePagination, TextField, InputAdornment,
  ToggleButton, ToggleButtonGroup, Skeleton, IconButton, Dialog, DialogTitle, DialogContent,
  DialogActions, Button, Divider, Stack, Snackbar, Alert,
} from '@mui/material';
import {
  Receipt, Search, Visibility, Download, AccountBalanceWallet, HourglassEmpty, Undo, Add,
} from '@mui/icons-material';
import {
  invoiceApi, AdminInvoice, InvoiceSummary, InvoiceStatus,
} from '../../services/adminApi';
import { apiErrorMessage } from '../../services/apiError';

/** Kept as strings so a half-typed number is not coerced to 0 while the admin is still typing. */
const EMPTY_RAISE_FORM = { bookingId: '', customerId: '', amount: '', description: '' };

const formatCurrency = (amount: number) => `₹${amount.toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;

const formatDate = (value: string | null) =>
  value ? new Date(value).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }) : '—';

/** The badge colours are per status, not per theme, because they carry meaning of their own. */
const STATUS_STYLE: Record<InvoiceStatus, { bg: string; color: string }> = {
  PAID: { bg: '#ecfdf5', color: '#10b981' },
  PENDING: { bg: '#fffbeb', color: '#f59e0b' },
  REFUNDED: { bg: '#eff6ff', color: '#3b82f6' },
  CANCELLED: { bg: '#fef2f2', color: '#ef4444' },
};

const STATUS_FILTERS: Array<{ value: string; label: string }> = [
  { value: '', label: 'All' },
  { value: 'PAID', label: 'Paid' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'REFUNDED', label: 'Refunded' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

/**
 * Invoices billed to customers.
 *
 * An invoice here is a payment presented as a document — payment-service derives it, so the
 * amounts on this screen cannot drift from the money that actually moved.
 */
const InvoicesPage: React.FC = () => {
  const [invoices, setInvoices] = useState<AdminInvoice[]>([]);
  const [summary, setSummary] = useState<InvoiceSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [totalElements, setTotalElements] = useState(0);
  const [status, setStatus] = useState('');
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [selected, setSelected] = useState<AdminInvoice | null>(null);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [raiseOpen, setRaiseOpen] = useState(false);
  const [raiseForm, setRaiseForm] = useState(EMPTY_RAISE_FORM);
  const [raising, setRaising] = useState(false);
  const [raiseError, setRaiseError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  // Typing a customer name should not fire a request per keystroke — the list query walks every
  // payment when a filter is set.
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search);
      setPage(0);
    }, 400);
    return () => clearTimeout(timer);
  }, [search]);

  const loadInvoices = useCallback(async () => {
    try {
      setLoading(true);
      const response = await invoiceApi.getInvoices({
        page,
        size: rowsPerPage,
        status: status || undefined,
        search: debouncedSearch || undefined,
      });
      setInvoices(Array.isArray(response.data.data) ? response.data.data : []);
      setTotalElements(response.data.totalElements ?? 0);
      if (response.data.success === false && response.data.message) {
        setError(response.data.message);
      }
    } catch (err) {
      setInvoices([]);
      setTotalElements(0);
      setError(apiErrorMessage(err, 'Invoices could not be loaded'));
    } finally {
      setLoading(false);
    }
  }, [page, rowsPerPage, status, debouncedSearch]);

  useEffect(() => {
    loadInvoices();
  }, [loadInvoices]);

  // Hoisted out of the effect so raising an invoice can refresh the header totals with the same
  // call the page loads them with.
  const loadSummary = useCallback(async () => {
    try {
      const response = await invoiceApi.getInvoiceSummary();
      setSummary(response.data.data);
    } catch (err) {
      console.error('Failed to load invoice totals:', err);
    }
  }, []);

  useEffect(() => {
    loadSummary();
  }, [loadSummary]);

  const openInvoice = async (invoice: AdminInvoice) => {
    // The row already holds everything but the billing lines, so it is shown immediately and the
    // detail fills in — the dialog never opens empty.
    setSelected(invoice);
    try {
      setLoadingDetail(true);
      const response = await invoiceApi.getInvoice(invoice.invoiceNumber);
      if (response.data.data) setSelected(response.data.data);
    } catch (err) {
      setError(apiErrorMessage(err, 'The invoice detail could not be loaded'));
    } finally {
      setLoadingDetail(false);
    }
  };

  /**
   * Raises the invoice, then reloads the list and the header totals so the new row and the new
   * outstanding balance appear together — a refreshed list beside stale totals reads as a bug.
   */
  const submitRaise = async () => {
    setRaising(true);
    setRaiseError(null);
    try {
      await invoiceApi.raiseInvoice({
        bookingId: Number(raiseForm.bookingId),
        customerId: Number(raiseForm.customerId),
        amount: Number(raiseForm.amount),
        description: raiseForm.description.trim() || undefined,
      });
      setRaiseOpen(false);
      setToast('Invoice raised');
      await Promise.all([loadInvoices(), loadSummary()]);
    } catch (err) {
      setRaiseError(apiErrorMessage(err, 'The invoice could not be raised'));
    } finally {
      setRaising(false);
    }
  };

  // Every field is required except the description, and the amount carries the same ₹1 floor the
  // server enforces — so the button is disabled rather than the request refused.
  const raiseValid =
    Number(raiseForm.bookingId) > 0 &&
    Number(raiseForm.customerId) > 0 &&
    Number(raiseForm.amount) >= 1;

  /**
   * The open invoice as a CSV of its lines. Built in the browser because it is a rendering of what
   * is already on screen, not a second read of the data.
   */
  const downloadInvoice = (invoice: AdminInvoice) => {
    const rows: string[][] = [
      ['Invoice', invoice.invoiceNumber],
      ['Booking', invoice.bookingCode],
      ['Customer', invoice.customerName ?? ''],
      ['Email', invoice.customerEmail ?? ''],
      ['Issued', formatDate(invoice.issuedAt)],
      ['Status', invoice.status],
      [],
      ['Line', 'Amount'],
      ...(invoice.lines ?? []).map((line) => [line.label, line.amount.toFixed(2)]),
      ['Total', invoice.total.toFixed(2)],
    ];
    const csv = rows
      .map((row) => row.map((cell) => (/[",\n]/.test(cell) ? `"${cell.replace(/"/g, '""')}"` : cell)).join(','))
      .join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `${invoice.invoiceNumber}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  };

  const stats = summary ? [
    { label: 'Billed', value: formatCurrency(summary.totalBilled), caption: `${summary.invoiceCount} invoices`, icon: <Receipt />, color: '#6366f1' },
    { label: 'Collected', value: formatCurrency(summary.totalCollected), caption: `${summary.paidCount} paid`, icon: <AccountBalanceWallet />, color: '#10b981' },
    { label: 'Outstanding', value: formatCurrency(summary.totalOutstanding), caption: `${summary.pendingCount} pending`, icon: <HourglassEmpty />, color: '#f59e0b' },
    { label: 'Refunded', value: formatCurrency(summary.totalRefunded), caption: `${summary.refundedCount} refunds`, icon: <Undo />, color: '#3b82f6' },
  ] : [];

  return (
    <Box>
      <Box sx={{ mb: 3, display: 'flex', flexWrap: 'wrap', gap: 2, justifyContent: 'space-between', alignItems: 'center' }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800, mb: 0.5 }}>Invoices</Typography>
          <Typography variant="body2" color="text.secondary">
            Every booking billed to a customer, and whether it has been paid
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<Add />}
          onClick={() => { setRaiseForm(EMPTY_RAISE_FORM); setRaiseError(null); setRaiseOpen(true); }}
          sx={{ borderRadius: 2 }}
        >
          Raise Invoice
        </Button>
      </Box>

      <Grid container spacing={3} sx={{ mb: 3 }}>
        {(summary ? stats : Array.from({ length: 4 })).map((stat: any, idx: number) => (
          <Grid item xs={12} sm={6} md={3} key={idx}>
            <Card sx={{ borderRadius: 3 }}>
              <CardContent sx={{ p: 3 }}>
                {!summary ? (
                  <Skeleton height={80} />
                ) : (
                  <>
                    <Avatar sx={{ bgcolor: `${stat.color}15`, color: stat.color, width: 44, height: 44, mb: 2 }}>
                      {stat.icon}
                    </Avatar>
                    <Typography variant="h5" sx={{ fontWeight: 800, mb: 0.5 }}>{stat.value}</Typography>
                    <Typography variant="body2" color="text.secondary">{stat.label}</Typography>
                    <Typography variant="caption" color="text.disabled">{stat.caption}</Typography>
                  </>
                )}
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Card sx={{ borderRadius: 3 }}>
        <Box sx={{ p: 2.5, display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'center', justifyContent: 'space-between', borderBottom: 1, borderColor: 'divider' }}>
          <TextField
            size="small"
            placeholder="Search invoice, booking or customer"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            sx={{ minWidth: 300 }}
            InputProps={{
              startAdornment: <InputAdornment position="start"><Search fontSize="small" /></InputAdornment>,
            }}
          />
          <ToggleButtonGroup
            size="small"
            exclusive
            value={status}
            onChange={(_, value) => { setStatus(value ?? ''); setPage(0); }}
          >
            {STATUS_FILTERS.map((filter) => (
              <ToggleButton key={filter.value || 'all'} value={filter.value} sx={{ px: 2, textTransform: 'none' }}>
                {filter.label}
              </ToggleButton>
            ))}
          </ToggleButtonGroup>
        </Box>

        <TableContainer>
          <Table>
            <TableHead>
              <TableRow sx={{ bgcolor: 'action.hover' }}>
                <TableCell sx={{ fontWeight: 700 }}>Invoice</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Booking</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Customer</TableCell>
                <TableCell sx={{ fontWeight: 700 }} align="right">Total</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Issued</TableCell>
                <TableCell sx={{ fontWeight: 700 }} align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                Array.from({ length: 6 }).map((_, idx) => (
                  <TableRow key={idx}>
                    {Array.from({ length: 7 }).map((__, cidx) => (
                      <TableCell key={cidx}><Skeleton /></TableCell>
                    ))}
                  </TableRow>
                ))
              ) : invoices.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} align="center" sx={{ py: 5 }}>
                    <Typography variant="body2" color="text.secondary">No invoices found</Typography>
                  </TableCell>
                </TableRow>
              ) : (
                invoices.map((invoice) => (
                  <TableRow key={invoice.invoiceNumber} hover>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 600 }}>
                        {invoice.invoiceNumber}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', color: 'primary.main' }}>
                        {invoice.bookingCode}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{invoice.customerName || 'Unknown'}</Typography>
                      <Typography variant="caption" color="text.secondary">{invoice.customerEmail}</Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{formatCurrency(invoice.total)}</Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={invoice.status}
                        sx={{
                          fontWeight: 600,
                          bgcolor: STATUS_STYLE[invoice.status]?.bg,
                          color: STATUS_STYLE[invoice.status]?.color,
                        }}
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary">{formatDate(invoice.issuedAt)}</Typography>
                    </TableCell>
                    <TableCell align="right">
                      <IconButton size="small" onClick={() => openInvoice(invoice)} aria-label="View invoice">
                        <Visibility fontSize="small" />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>

        <TablePagination
          component="div"
          count={totalElements}
          page={page}
          onPageChange={(_, next) => setPage(next)}
          rowsPerPage={rowsPerPage}
          onRowsPerPageChange={(e) => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
          rowsPerPageOptions={[10, 20, 50, 100]}
        />
      </Card>

      <Dialog open={selected !== null} onClose={() => setSelected(null)} maxWidth="sm" fullWidth
        PaperProps={{ sx: { borderRadius: 3 } }}>
        {selected && (
          <>
            <DialogTitle sx={{ fontWeight: 700 }}>
              {selected.invoiceNumber}
              <Typography variant="body2" color="text.secondary">
                {selected.bookingCode} · {formatDate(selected.issuedAt)}
              </Typography>
            </DialogTitle>
            <DialogContent dividers>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="caption" color="text.secondary">Billed to</Typography>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>{selected.customerName || 'Unknown'}</Typography>
                  <Typography variant="body2" color="text.secondary">{selected.customerEmail}</Typography>
                </Box>

                <Divider />

                {loadingDetail && !selected.lines ? (
                  Array.from({ length: 3 }).map((_, idx) => <Skeleton key={idx} height={28} />)
                ) : (
                  (selected.lines ?? []).map((line, idx) => (
                    <Box key={idx} sx={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Typography variant="body2">{line.label}</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 500 }}>{formatCurrency(line.amount)}</Typography>
                    </Box>
                  ))
                )}

                <Divider />

                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body1" sx={{ fontWeight: 700 }}>Total</Typography>
                  <Typography variant="body1" sx={{ fontWeight: 700 }}>{formatCurrency(selected.total)}</Typography>
                </Box>

                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip size="small" label={selected.status}
                    sx={{ fontWeight: 600, bgcolor: STATUS_STYLE[selected.status]?.bg, color: STATUS_STYLE[selected.status]?.color }} />
                  {selected.paymentMethod && <Chip size="small" variant="outlined" label={selected.paymentMethod} />}
                  {selected.paidAt && <Chip size="small" variant="outlined" label={`Paid ${formatDate(selected.paidAt)}`} />}
                  {selected.refundedAt && <Chip size="small" variant="outlined" label={`Refunded ${formatDate(selected.refundedAt)}`} />}
                </Stack>

                {selected.refundReason && (
                  <Alert severity="info">Refund reason: {selected.refundReason}</Alert>
                )}
                {selected.failureReason && (
                  <Alert severity="error">Failure: {selected.failureReason}</Alert>
                )}
              </Stack>
            </DialogContent>
            <DialogActions sx={{ p: 2 }}>
              <Button startIcon={<Download />} onClick={() => downloadInvoice(selected)}>Download</Button>
              <Button variant="contained" onClick={() => setSelected(null)} sx={{ borderRadius: 2 }}>Close</Button>
            </DialogActions>
          </>
        )}
      </Dialog>

      <Dialog open={raiseOpen} onClose={() => !raising && setRaiseOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle sx={{ fontWeight: 700 }}>Raise an invoice</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2.5} sx={{ pt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Bills a booking to its customer. The invoice is raised unpaid — the customer settles
              it through Checkout, and platform fee and GST are added on top of the amount below.
            </Typography>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                label="Booking ID"
                type="number"
                fullWidth
                required
                value={raiseForm.bookingId}
                onChange={(e) => setRaiseForm((f) => ({ ...f, bookingId: e.target.value }))}
              />
              <TextField
                label="Customer ID"
                type="number"
                fullWidth
                required
                value={raiseForm.customerId}
                onChange={(e) => setRaiseForm((f) => ({ ...f, customerId: e.target.value }))}
              />
            </Stack>
            <TextField
              label="Amount"
              type="number"
              fullWidth
              required
              value={raiseForm.amount}
              onChange={(e) => setRaiseForm((f) => ({ ...f, amount: e.target.value }))}
              helperText="Subtotal in rupees, before platform fee and GST. Minimum ₹1."
              InputProps={{ startAdornment: <InputAdornment position="start">₹</InputAdornment> }}
            />
            <TextField
              label="Description"
              fullWidth
              multiline
              minRows={2}
              value={raiseForm.description}
              onChange={(e) => setRaiseForm((f) => ({ ...f, description: e.target.value }))}
              helperText="Optional — what this invoice is for"
            />
            {raiseError && <Alert severity="error">{raiseError}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button onClick={() => setRaiseOpen(false)} disabled={raising}>Cancel</Button>
          <Button
            variant="contained"
            onClick={submitRaise}
            disabled={!raiseValid || raising}
            sx={{ borderRadius: 2 }}
          >
            {raising ? 'Raising…' : 'Raise Invoice'}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={error !== null}
        autoHideDuration={5000}
        onClose={() => setError(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert severity="error" onClose={() => setError(null)} variant="filled">{error}</Alert>
      </Snackbar>

      <Snackbar
        open={toast !== null}
        autoHideDuration={4000}
        onClose={() => setToast(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert severity="success" onClose={() => setToast(null)} variant="filled">{toast}</Alert>
      </Snackbar>
    </Box>
  );
};

export default InvoicesPage;
