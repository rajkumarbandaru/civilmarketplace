import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link as RouterLink, useParams } from 'react-router-dom';
import {
  Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Container, Dialog, DialogActions, DialogContent,
  DialogTitle, Stack, Step, StepLabel, Stepper, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { useDateTime } from '../../providers/UiConfigProvider';
import { apiErrorMessage } from '../../services/apiError';
import {
  PO_STATUS_LABELS, PoDetail, PoStatus, acknowledgePurchaseOrder, approvePurchaseOrder, decideInvoice,
  fetchPurchaseOrder, formatMoney, formatQty, lineTotals, recordReceipt, rejectPurchaseOrder, submitInvoice,
} from '../../services/procurementApi';
import { PO_STATUS_COLOR } from './ProcurementPage';

const STEPS = ['Approved', 'Acknowledged', 'Received', 'Invoiced & closed'];
const stepOf = (s: PoStatus): number => ({
  PENDING_APPROVAL: 0, ISSUED: 1, ACKNOWLEDGED: 2, PARTIALLY_RECEIVED: 2, RECEIVED: 3, CLOSED: 4, CANCELLED: 0,
} as Record<PoStatus, number>)[s];

const INVOICE_COLOR = { MATCHED: 'success', EXCEPTION: 'error', APPROVED: 'success', REJECTED: 'default' } as const;

/** Record what arrived: received and, of that, rejected, per line. */
const ReceiptDialog: React.FC<{ po: PoDetail; onClose: () => void; onDone: (d: PoDetail) => void }> = ({ po, onClose, onDone }) => {
  const [qty, setQty] = useState<Record<number, { received: string; rejected: string }>>(() =>
    Object.fromEntries(po.lines.map((l) => [l.id, { received: String(Math.max(0, l.quantity - l.acceptedQty)), rejected: '0' }])));
  const [notes, setNotes] = useState('');
  const save = useMutation({
    mutationFn: () => recordReceipt(po.id, po.lines.map((l) => ({
      poLineId: l.id, receivedQty: Number(qty[l.id].received || 0), rejectedQty: Number(qty[l.id].rejected || 0),
    })), notes.trim() || undefined),
    onSuccess: onDone,
  });
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Goods receipt for {po.number}</DialogTitle>
      <DialogContent>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Item</TableCell><TableCell align="right">Ordered</TableCell><TableCell align="right">Accepted so far</TableCell>
              <TableCell>Received now</TableCell><TableCell>Rejected</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {po.lines.map((l) => (
              <TableRow key={l.id}>
                <TableCell>{l.description}</TableCell>
                <TableCell align="right">{formatQty(l.quantity)} {l.uom}</TableCell>
                <TableCell align="right">{formatQty(l.acceptedQty)}</TableCell>
                <TableCell>
                  <TextField size="small" type="number" value={qty[l.id].received} sx={{ width: 110 }}
                    inputProps={{ min: 0, 'aria-label': `Received ${l.description}` }}
                    onChange={(e) => setQty({ ...qty, [l.id]: { ...qty[l.id], received: e.target.value } })} />
                </TableCell>
                <TableCell>
                  <TextField size="small" type="number" value={qty[l.id].rejected} sx={{ width: 100 }}
                    inputProps={{ min: 0, 'aria-label': `Rejected ${l.description}` }}
                    onChange={(e) => setQty({ ...qty, [l.id]: { ...qty[l.id], rejected: e.target.value } })} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <TextField fullWidth size="small" label="Notes (vehicle, challan, damage)" value={notes} sx={{ mt: 2 }}
          onChange={(e) => setNotes(e.target.value)} inputProps={{ maxLength: 1000 }} />
        {save.isError && <Alert severity="error" sx={{ mt: 2 }}>{apiErrorMessage(save.error, 'The receipt was not recorded.')}</Alert>}
      </DialogContent>
      <DialogActions>
        <Button color="inherit" onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={save.isPending} onClick={() => save.mutate()}>Record receipt</Button>
      </DialogActions>
    </Dialog>
  );
};

/** The supplier bills what was accepted and not yet invoiced, prefilled at the ordered prices. */
const InvoiceDialog: React.FC<{ po: PoDetail; onClose: () => void; onDone: (d: PoDetail) => void }> = ({ po, onClose, onDone }) => {
  const [number, setNumber] = useState('');
  const [lines, setLines] = useState<Record<number, { quantity: string; unitPrice: string; taxPercent: string }>>(() =>
    Object.fromEntries(po.lines.map((l) => [l.id, {
      quantity: String(Math.max(0, l.acceptedQty - l.invoicedQty)), unitPrice: String(l.unitPrice), taxPercent: String(l.taxPercent),
    }])));
  const billed = po.lines.filter((l) => Number(lines[l.id].quantity) > 0);
  const totals = lineTotals(billed.map((l) => ({
    quantity: Number(lines[l.id].quantity), unitPrice: Number(lines[l.id].unitPrice), taxPercent: Number(lines[l.id].taxPercent),
  })));
  const save = useMutation({
    mutationFn: () => submitInvoice(po.id, number.trim(), billed.map((l) => ({
      poLineId: l.id, quantity: Number(lines[l.id].quantity), unitPrice: Number(lines[l.id].unitPrice),
      taxPercent: Number(lines[l.id].taxPercent),
    }))),
    onSuccess: onDone,
  });
  const set = (id: number, patch: Partial<{ quantity: string; unitPrice: string; taxPercent: string }>) =>
    setLines({ ...lines, [id]: { ...lines[id], ...patch } });
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Invoice against {po.number}</DialogTitle>
      <DialogContent>
        <TextField size="small" label="Your invoice number" value={number} onChange={(e) => setNumber(e.target.value)}
          sx={{ mt: 1, mb: 2 }} inputProps={{ maxLength: 40 }} required />
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Item</TableCell><TableCell align="right">Accepted, not invoiced</TableCell>
              <TableCell>Quantity</TableCell><TableCell>Unit price (₹)</TableCell><TableCell>GST %</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {po.lines.map((l) => (
              <TableRow key={l.id}>
                <TableCell>{l.description}</TableCell>
                <TableCell align="right">{formatQty(Math.max(0, l.acceptedQty - l.invoicedQty))} {l.uom}</TableCell>
                <TableCell><TextField size="small" type="number" value={lines[l.id].quantity} sx={{ width: 100 }}
                  inputProps={{ min: 0, 'aria-label': `Invoice quantity ${l.description}` }}
                  onChange={(e) => set(l.id, { quantity: e.target.value })} /></TableCell>
                <TableCell><TextField size="small" type="number" value={lines[l.id].unitPrice} sx={{ width: 120 }}
                  inputProps={{ min: 0, step: '0.01', 'aria-label': `Invoice price ${l.description}` }}
                  onChange={(e) => set(l.id, { unitPrice: e.target.value })} /></TableCell>
                <TableCell><TextField size="small" type="number" value={lines[l.id].taxPercent} sx={{ width: 80 }}
                  inputProps={{ min: 0, max: 100 }} onChange={(e) => set(l.id, { taxPercent: e.target.value })} /></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <Typography sx={{ mt: 2 }}>Invoice total <strong>{formatMoney(totals.total)}</strong></Typography>
        <Typography variant="caption" color="text.secondary">
          Checked against the order and the goods receipts: prices within 2% of the order, quantities no more than accepted.
        </Typography>
        {save.isError && <Alert severity="error" sx={{ mt: 2 }}>{apiErrorMessage(save.error, 'The invoice was not submitted.')}</Alert>}
      </DialogContent>
      <DialogActions>
        <Button color="inherit" onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!number.trim() || billed.length === 0 || save.isPending} onClick={() => save.mutate()}>
          Submit invoice
        </Button>
      </DialogActions>
    </Dialog>
  );
};

const PurchaseOrderPage: React.FC = () => {
  const id = Number(useParams().poId);
  const queryClient = useQueryClient();
  const { formatDateTime } = useDateTime();
  const [dialog, setDialog] = useState<'receipt' | 'invoice' | null>(null);
  const po = useQuery({ queryKey: ['procurement-po', id], queryFn: () => fetchPurchaseOrder(id), retry: false });
  const done = (d: PoDetail) => {
    setDialog(null);
    queryClient.setQueryData(['procurement-po', id], d);
    queryClient.invalidateQueries({ queryKey: ['procurement-orders'] });
  };
  const approve = useMutation({ mutationFn: () => approvePurchaseOrder(id), onSuccess: done });
  const reject = useMutation({ mutationFn: () => rejectPurchaseOrder(id), onSuccess: done });
  const acknowledge = useMutation({ mutationFn: () => acknowledgePurchaseOrder(id), onSuccess: done });
  const decide = useMutation({
    mutationFn: ({ invoiceId, ok }: { invoiceId: number; ok: boolean }) => decideInvoice(id, invoiceId, ok),
    onSuccess: done,
  });
  const actionError = approve.error || reject.error || acknowledge.error || decide.error;

  if (po.isLoading) return <Container sx={{ py: 4 }}><CircularProgress /></Container>;
  if (po.isError || !po.data) {
    return <Container sx={{ py: 4 }}><Alert severity="error">{apiErrorMessage(po.error, 'Order not found.')}</Alert></Container>;
  }
  const p = po.data;
  const buyer = p.roles.includes('BUYER');
  const supplier = p.roles.includes('SUPPLIER');

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Button component={RouterLink} to="/procurement?tab=orders" size="small" sx={{ mb: 1 }}>← Procurement</Button>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1, flexWrap: 'wrap', rowGap: 1 }}>
        <Typography variant="h4">{p.number}</Typography>
        <Chip color={PO_STATUS_COLOR[p.status]} label={PO_STATUS_LABELS[p.status]} data-testid="po-status" />
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        {p.buyer.name} buying from {p.supplier.name}
        {p.deliverySite && ` · deliver to ${p.deliverySite}`}
        {p.reference && ` · for ${p.reference}`}
        {' · '}<RouterLink to={`/procurement/rfqs/${p.rfqId}`}>RFQ</RouterLink>
      </Typography>

      {p.status !== 'CANCELLED' && (
        <Stepper activeStep={stepOf(p.status)} alternativeLabel sx={{ mb: 3 }}>
          {STEPS.map((s) => <Step key={s}><StepLabel>{s}</StepLabel></Step>)}
        </Stepper>
      )}
      {p.status === 'CANCELLED' && <Alert severity="error" sx={{ mb: 3 }}>Cancelled: {p.cancelReason}</Alert>}

      {p.status === 'PENDING_APPROVAL' && (
        <Alert severity="warning" sx={{ mb: 3 }} action={p.canApprove && (
          <Stack direction="row" spacing={1}>
            <Button color="inherit" size="small" onClick={() => reject.mutate()} disabled={reject.isPending}>Reject</Button>
            <Button variant="contained" size="small" onClick={() => approve.mutate()} disabled={approve.isPending}>Approve</Button>
          </Stack>
        )}>
          Above {p.buyer.name}'s approval limit of {formatMoney(p.approvalThreshold)}.
          {p.canApprove ? ' You can approve it.' : buyer ? ' An approver other than whoever raised it must approve it.' : ' Waiting for the buyer to approve it.'}
        </Alert>
      )}
      {p.status === 'ISSUED' && supplier && (
        <Alert severity="info" sx={{ mb: 3 }} action={
          <Button variant="contained" size="small" onClick={() => acknowledge.mutate()} disabled={acknowledge.isPending}>Acknowledge</Button>
        }>Confirm you will supply this order.</Alert>
      )}
      {actionError && <Alert severity="error" sx={{ mb: 3 }}>{apiErrorMessage(actionError, 'That did not work.')}</Alert>}

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1, flexWrap: 'wrap', rowGap: 1 }}>
            <Typography variant="h6">Lines</Typography>
            <Stack direction="row" spacing={1}>
              {buyer && ['ACKNOWLEDGED', 'PARTIALLY_RECEIVED'].includes(p.status) && (
                <Button variant="outlined" onClick={() => setDialog('receipt')}>Record goods receipt</Button>
              )}
              {supplier && ['PARTIALLY_RECEIVED', 'RECEIVED'].includes(p.status) && (
                <Button variant="outlined" onClick={() => setDialog('invoice')}>Submit invoice</Button>
              )}
            </Stack>
          </Stack>
          <Box sx={{ overflowX: 'auto' }}>
            <Table size="small" data-testid="po-lines">
              <TableHead>
                <TableRow>
                  <TableCell>Item</TableCell><TableCell align="right">Ordered</TableCell><TableCell align="right">Price</TableCell>
                  <TableCell align="right">Amount</TableCell><TableCell align="right">Accepted</TableCell>
                  <TableCell align="right">Invoiced</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {p.lines.map((l) => (
                  <TableRow key={l.id}>
                    <TableCell>{l.description}</TableCell>
                    <TableCell align="right">{formatQty(l.quantity)} {l.uom}</TableCell>
                    <TableCell align="right">{formatMoney(l.unitPrice)} + {l.taxPercent}%</TableCell>
                    <TableCell align="right">{formatMoney(l.amount)}</TableCell>
                    <TableCell align="right">
                      {formatQty(l.acceptedQty)}
                      {l.receivedQty > l.acceptedQty && (
                        <Typography variant="caption" color="error" display="block">
                          {formatQty(l.receivedQty - l.acceptedQty)} rejected
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell align="right">{formatQty(l.invoicedQty)}</TableCell>
                  </TableRow>
                ))}
                <TableRow>
                  <TableCell colSpan={3} align="right">Subtotal {formatMoney(p.subtotal)} · tax {formatMoney(p.taxTotal)}</TableCell>
                  <TableCell align="right" data-testid="po-total"><strong>{formatMoney(p.total)}</strong></TableCell>
                  <TableCell colSpan={2} />
                </TableRow>
              </TableBody>
            </Table>
          </Box>
        </CardContent>
      </Card>

      {p.receipts.length > 0 && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>Goods receipts</Typography>
            {p.receipts.map((g) => (
              <Typography key={g.id} variant="body2" sx={{ mb: 0.5 }}>
                <strong>{g.number}</strong> · {formatDateTime(g.receivedAt)} ·{' '}
                {g.lines.map((gl) => {
                  const l = p.lines.find((x) => x.id === gl.poLineId);
                  return `${l?.description}: ${formatQty(gl.receivedQty)}${gl.rejectedQty > 0 ? ` (${formatQty(gl.rejectedQty)} rejected)` : ''}`;
                }).join('; ')}
                {g.notes && ` · ${g.notes}`}
              </Typography>
            ))}
          </CardContent>
        </Card>
      )}

      {p.invoices.length > 0 && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>Invoices</Typography>
            <Stack spacing={2}>
              {p.invoices.map((inv) => (
                <Box key={inv.id} data-testid={`invoice-${inv.id}`}>
                  <Stack direction="row" spacing={1} alignItems="center" sx={{ flexWrap: 'wrap', rowGap: 1 }}>
                    <Typography sx={{ fontWeight: 600 }}>{inv.invoiceNumber}</Typography>
                    <Typography>{formatMoney(inv.total)}</Typography>
                    <Chip size="small" color={INVOICE_COLOR[inv.status]}
                      label={inv.status === 'MATCHED' ? '3-way match passed' : inv.status === 'EXCEPTION' ? 'Match failed' : inv.status.toLowerCase()} />
                    <Box sx={{ flex: 1 }} />
                    {buyer && (inv.status === 'MATCHED' || inv.status === 'EXCEPTION') && (
                      <>
                        <Button size="small" color="error" onClick={() => decide.mutate({ invoiceId: inv.id, ok: false })}>Reject</Button>
                        {inv.status === 'MATCHED' && (
                          <Button size="small" variant="contained" onClick={() => decide.mutate({ invoiceId: inv.id, ok: true })}>
                            Approve for payment
                          </Button>
                        )}
                      </>
                    )}
                  </Stack>
                  {inv.matchIssues.map((m) => <Alert key={m} severity="error" sx={{ mt: 1 }}>{m}</Alert>)}
                  {inv.decisionNote && <Typography variant="caption" color="text.secondary">{inv.decisionNote}</Typography>}
                </Box>
              ))}
            </Stack>
          </CardContent>
        </Card>
      )}

      {dialog === 'receipt' && <ReceiptDialog po={p} onClose={() => setDialog(null)} onDone={done} />}
      {dialog === 'invoice' && <InvoiceDialog po={p} onClose={() => setDialog(null)} onDone={done} />}
    </Container>
  );
};

export default PurchaseOrderPage;
