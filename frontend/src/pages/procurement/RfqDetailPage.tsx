import React, { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import {
  Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Container, MenuItem, Stack, Table, TableBody,
  TableCell, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { useDateTime } from '../../providers/UiConfigProvider';
import { apiErrorMessage } from '../../services/apiError';
import {
  RfqDetail, acceptQuotation, cancelRfq, fetchMyOrganizations, fetchRfq, formatMoney, formatQty, lineTotals,
  submitQuotation,
} from '../../services/procurementApi';
import { RFQ_STATUS_COLOR } from './ProcurementPage';

/** Every quotation side by side, line by line, cheapest first; the buyer accepts one. */
const Comparison: React.FC<{ rfq: RfqDetail }> = ({ rfq }) => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const accept = useMutation({
    mutationFn: (quotationId: number) => acceptQuotation(rfq.id, quotationId),
    onSuccess: (po) => {
      queryClient.invalidateQueries({ queryKey: ['procurement-rfq', rfq.id] });
      queryClient.invalidateQueries({ queryKey: ['procurement-orders'] });
      navigate(`/procurement/orders/${po.id}`);
    },
  });
  const quoted = new Set(rfq.quotations.map((q) => q.supplier.id));
  const waiting = rfq.invitedSuppliers.filter((s) => !quoted.has(s.id));
  const cheapest = rfq.quotations[0]?.id;

  return (
    <Card sx={{ mb: 3 }}>
      <CardContent>
        <Typography variant="h6" gutterBottom>Quotations</Typography>
        {rfq.quotations.length === 0 && (
          <Typography color="text.secondary">No quotations yet. {rfq.invitedSuppliers.length} supplier(s) invited.</Typography>
        )}
        {rfq.quotations.length > 0 && (
          <Box sx={{ overflowX: 'auto' }}>
            <Table size="small" data-testid="quotation-comparison">
              <TableHead>
                <TableRow>
                  <TableCell>Item</TableCell>
                  {rfq.quotations.map((q) => (
                    <TableCell key={q.id} align="right">
                      <Stack alignItems="flex-end">
                        <strong>{q.supplier.name}</strong>
                        {q.id === cheapest && rfq.quotations.length > 1 && <Chip size="small" color="success" label="Lowest" />}
                        {q.status !== 'SUBMITTED' && <Chip size="small" label={q.status.toLowerCase()} />}
                      </Stack>
                    </TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {rfq.lines.map((l) => (
                  <TableRow key={l.id}>
                    <TableCell>{l.description} <Typography variant="caption" color="text.secondary">× {formatQty(l.quantity)} {l.uom}</Typography></TableCell>
                    {rfq.quotations.map((q) => {
                      const ql = q.lines.find((x) => x.rfqLineId === l.id);
                      return (
                        <TableCell key={q.id} align="right">
                          {ql ? <>{formatMoney(ql.unitPrice)}/{l.uom}<Typography variant="caption" display="block" color="text.secondary">
                            {formatMoney(ql.amount)} + {ql.taxPercent}% GST</Typography></> : '—'}
                        </TableCell>
                      );
                    })}
                  </TableRow>
                ))}
                <TableRow>
                  <TableCell><strong>Total incl. tax</strong></TableCell>
                  {rfq.quotations.map((q) => (
                    <TableCell key={q.id} align="right" data-testid={`quote-total-${q.id}`}><strong>{formatMoney(q.total)}</strong></TableCell>
                  ))}
                </TableRow>
                <TableRow>
                  <TableCell>Valid until</TableCell>
                  {rfq.quotations.map((q) => <TableCell key={q.id} align="right">{q.validUntil ?? '—'}</TableCell>)}
                </TableRow>
                {rfq.status === 'OPEN' && (
                  <TableRow>
                    <TableCell />
                    {rfq.quotations.map((q) => (
                      <TableCell key={q.id} align="right">
                        <Button size="small" variant="contained" disabled={accept.isPending}
                          onClick={() => accept.mutate(q.id)} aria-label={`Accept ${q.supplier.name}`}>
                          Accept
                        </Button>
                      </TableCell>
                    ))}
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </Box>
        )}
        {rfq.quotations.some((q) => q.notes) && (
          <Stack sx={{ mt: 2 }} spacing={0.5}>
            {rfq.quotations.filter((q) => q.notes).map((q) => (
              <Typography key={q.id} variant="body2"><strong>{q.supplier.name}:</strong> {q.notes}</Typography>
            ))}
          </Stack>
        )}
        {waiting.length > 0 && rfq.status === 'OPEN' && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
            Waiting for: {waiting.map((s) => s.name).join(', ')}
          </Typography>
        )}
        {accept.isError && <Alert severity="error" sx={{ mt: 2 }}>{apiErrorMessage(accept.error, 'Could not accept.')}</Alert>}
      </CardContent>
    </Card>
  );
};

/** An invited supplier prices every line; it may revise while the RFQ is open. */
const QuoteForm: React.FC<{ rfq: RfqDetail; supplierOrgIds: number[] }> = ({ rfq, supplierOrgIds }) => {
  const queryClient = useQueryClient();
  const [supplierOrgId, setSupplierOrgId] = useState(supplierOrgIds[0]);
  const existing = rfq.quotations.find((q) => q.supplier.id === supplierOrgId);
  const [prices, setPrices] = useState<Record<number, { unitPrice: string; taxPercent: string }>>({});
  const [validUntil, setValidUntil] = useState('');
  const [notes, setNotes] = useState('');

  useEffect(() => {
    const init: Record<number, { unitPrice: string; taxPercent: string }> = {};
    rfq.lines.forEach((l) => {
      const q = existing?.lines.find((x) => x.rfqLineId === l.id);
      init[l.id] = { unitPrice: q ? String(q.unitPrice) : '', taxPercent: q ? String(q.taxPercent) : '18' };
    });
    setPrices(init);
    setValidUntil(existing?.validUntil ?? '');
    setNotes(existing?.notes ?? '');
  }, [rfq, existing]);

  const filled = rfq.lines.every((l) => prices[l.id]?.unitPrice !== '' && prices[l.id] != null);
  const totals = useMemo(() => lineTotals(rfq.lines.map((l) => ({
    quantity: l.quantity, unitPrice: Number(prices[l.id]?.unitPrice || 0), taxPercent: Number(prices[l.id]?.taxPercent || 0),
  }))), [rfq.lines, prices]);

  const submit = useMutation({
    mutationFn: () => submitQuotation(rfq.id, {
      supplierOrgId,
      validUntil: validUntil || null,
      notes: notes.trim() || undefined,
      lines: rfq.lines.map((l) => ({
        rfqLineId: l.id, unitPrice: Number(prices[l.id].unitPrice), taxPercent: Number(prices[l.id].taxPercent),
      })),
    }),
    onSuccess: (d) => queryClient.setQueryData(['procurement-rfq', rfq.id], d),
  });
  const open = rfq.status === 'OPEN';

  return (
    <Card sx={{ mb: 3 }} data-testid="quote-form">
      <CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
          <Typography variant="h6">Your quotation</Typography>
          {existing && <Chip label={existing.status.toLowerCase()} color={existing.status === 'ACCEPTED' ? 'success' : 'default'} />}
        </Stack>
        {supplierOrgIds.length > 1 && (
          <TextField select size="small" label="Quoting as" value={supplierOrgId} sx={{ mb: 2, minWidth: 240 }}
            onChange={(e) => setSupplierOrgId(Number(e.target.value))}>
            {supplierOrgIds.map((id) => (
              <MenuItem key={id} value={id}>{rfq.invitedSuppliers.find((s) => s.id === id)?.name}</MenuItem>
            ))}
          </TextField>
        )}
        <Box sx={{ overflowX: 'auto' }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Item</TableCell><TableCell align="right">Quantity</TableCell>
                <TableCell>Unit price (₹)</TableCell><TableCell>GST %</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rfq.lines.map((l) => (
                <TableRow key={l.id}>
                  <TableCell>{l.description}</TableCell>
                  <TableCell align="right">{formatQty(l.quantity)} {l.uom}</TableCell>
                  <TableCell>
                    <TextField size="small" type="number" disabled={!open} value={prices[l.id]?.unitPrice ?? ''}
                      inputProps={{ min: 0, step: '0.01', 'aria-label': `Unit price for ${l.description}` }}
                      onChange={(e) => setPrices({ ...prices, [l.id]: { ...prices[l.id], unitPrice: e.target.value } })} />
                  </TableCell>
                  <TableCell>
                    <TextField size="small" type="number" disabled={!open} value={prices[l.id]?.taxPercent ?? ''} sx={{ width: 90 }}
                      inputProps={{ min: 0, max: 100, 'aria-label': `GST for ${l.description}` }}
                      onChange={(e) => setPrices({ ...prices, [l.id]: { ...prices[l.id], taxPercent: e.target.value } })} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mt: 2 }}>
          <TextField size="small" label="Valid until" type="date" value={validUntil} disabled={!open}
            onChange={(e) => setValidUntil(e.target.value)} InputLabelProps={{ shrink: true }} />
          <TextField size="small" label="Notes (delivery, terms)" value={notes} disabled={!open} sx={{ flex: 1 }}
            onChange={(e) => setNotes(e.target.value)} inputProps={{ maxLength: 1000 }} />
        </Stack>
        <Typography sx={{ mt: 2 }} data-testid="quote-live-total">
          {formatMoney(totals.subtotal)} + {formatMoney(totals.tax)} tax = <strong>{formatMoney(totals.total)}</strong>
        </Typography>
        {submit.isError && <Alert severity="error" sx={{ mt: 2 }}>{apiErrorMessage(submit.error, 'The quotation was not sent.')}</Alert>}
        {submit.isSuccess && <Alert severity="success" sx={{ mt: 2 }}>Quotation sent.</Alert>}
        {open && (
          <Button variant="contained" sx={{ mt: 2 }} disabled={!filled || submit.isPending} onClick={() => submit.mutate()}>
            {existing ? 'Revise quotation' : 'Submit quotation'}
          </Button>
        )}
      </CardContent>
    </Card>
  );
};

const RfqDetailPage: React.FC = () => {
  const id = Number(useParams().rfqId);
  const queryClient = useQueryClient();
  const { formatDate } = useDateTime();
  const rfq = useQuery({ queryKey: ['procurement-rfq', id], queryFn: () => fetchRfq(id), retry: false });
  const orgs = useQuery({ queryKey: ['procurement-orgs'], queryFn: fetchMyOrganizations });
  const cancel = useMutation({
    mutationFn: () => cancelRfq(id),
    onSuccess: (d) => queryClient.setQueryData(['procurement-rfq', id], d),
  });

  if (rfq.isLoading) return <Container sx={{ py: 4 }}><CircularProgress /></Container>;
  if (rfq.isError || !rfq.data) {
    return <Container sx={{ py: 4 }}><Alert severity="error">{apiErrorMessage(rfq.error, 'RFQ not found.')}</Alert></Container>;
  }
  const r = rfq.data;
  const mine = new Set(orgs.data?.map((o) => o.id) ?? []);
  const supplierOrgIds = r.invitedSuppliers.map((s) => s.id).filter((sid) => mine.has(sid));

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Button component={RouterLink} to="/procurement?tab=rfqs" size="small" sx={{ mb: 1 }}>← Procurement</Button>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1, flexWrap: 'wrap', rowGap: 1 }}>
        <Typography variant="h4">{r.number}</Typography>
        <Chip color={RFQ_STATUS_COLOR[r.status]} label={r.status.toLowerCase()} data-testid="rfq-status" />
      </Stack>
      <Typography variant="h6" sx={{ mb: 1 }}>{r.title}</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        From {r.buyer.name}
        {r.deliverySite && ` · deliver to ${r.deliverySite}`}
        {r.neededBy && ` · needed by ${formatDate(r.neededBy)}`}
        {r.reference && ` · for ${r.reference}`}
      </Typography>

      {r.purchaseOrderId && (
        <Alert severity="success" sx={{ mb: 3 }} action={
          <Button component={RouterLink} to={`/procurement/orders/${r.purchaseOrderId}`} color="inherit" size="small">Open order</Button>
        }>Awarded. A purchase order has been raised.</Alert>
      )}

      {r.roles.includes('BUYER') && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>Items</Typography>
            <Table size="small">
              <TableBody>
                {r.lines.map((l) => (
                  <TableRow key={l.id}>
                    <TableCell>{l.lineNo}</TableCell><TableCell>{l.description}</TableCell>
                    <TableCell align="right">{formatQty(l.quantity)} {l.uom}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {r.roles.includes('BUYER') && <Comparison rfq={r} />}
      {r.roles.includes('SUPPLIER') && supplierOrgIds.length > 0 && <QuoteForm rfq={r} supplierOrgIds={supplierOrgIds} />}

      {r.roles.includes('BUYER') && r.status === 'OPEN' && (
        <Button color="error" disabled={cancel.isPending} onClick={() => cancel.mutate()}>Cancel RFQ</Button>
      )}
      {cancel.isError && <Alert severity="error" sx={{ mt: 2 }}>{apiErrorMessage(cancel.error, 'Could not cancel.')}</Alert>}
    </Container>
  );
};

export default RfqDetailPage;
