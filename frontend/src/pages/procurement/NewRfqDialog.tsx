import React, { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Alert, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, IconButton,
  MenuItem, Stack, TextField, Typography,
} from '@mui/material';
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material';
import { apiErrorMessage } from '../../services/apiError';
import { Organization, RfqDetail, createRfq, fetchDirectory } from '../../services/procurementApi';

interface Line { description: string; quantity: string; uom: string }
const EMPTY_LINE: Line = { description: '', quantity: '', uom: '' };

/** Raise an RFQ: what, how much, where and when — and which suppliers to ask. */
const NewRfqDialog: React.FC<{
  buyers: Organization[];
  onClose: () => void;
  onCreated: (rfq: RfqDetail) => void;
}> = ({ buyers, onClose, onCreated }) => {
  const [buyerOrgId, setBuyerOrgId] = useState<number>(buyers[0]?.id);
  const [title, setTitle] = useState('');
  const [deliverySite, setDeliverySite] = useState('');
  const [neededBy, setNeededBy] = useState('');
  const [reference, setReference] = useState('');
  const [lines, setLines] = useState<Line[]>([{ ...EMPTY_LINE }]);
  const [suppliers, setSuppliers] = useState<number[]>([]);

  const directory = useQuery({
    queryKey: ['procurement-directory', 'SUPPLIER', buyerOrgId],
    queryFn: () => fetchDirectory('SUPPLIER', buyerOrgId),
    enabled: buyerOrgId != null,
  });
  const create = useMutation({
    mutationFn: () => createRfq({
      buyerOrgId,
      title: title.trim(),
      deliverySite: deliverySite.trim() || undefined,
      neededBy: neededBy || null,
      reference: reference.trim() || undefined,
      lines: lines.map((l) => ({ description: l.description.trim(), quantity: Number(l.quantity), uom: l.uom.trim() })),
      supplierOrgIds: suppliers,
    }),
    onSuccess: onCreated,
  });

  const setLine = (i: number, patch: Partial<Line>) => setLines(lines.map((l, j) => (j === i ? { ...l, ...patch } : l)));
  const linesOk = lines.every((l) => l.description.trim() && Number(l.quantity) > 0 && l.uom.trim());

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>New request for quotation</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {buyers.length > 1 && (
            <TextField select label="Buying as" value={buyerOrgId}
              onChange={(e) => { setBuyerOrgId(Number(e.target.value)); setSuppliers([]); }}>
              {buyers.map((b) => <MenuItem key={b.id} value={b.id}>{b.name}</MenuItem>)}
            </TextField>
          )}
          <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required
            placeholder="Cement and steel for the Sharma extension" inputProps={{ maxLength: 200 }} />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField label="Deliver to" value={deliverySite} onChange={(e) => setDeliverySite(e.target.value)}
              sx={{ flex: 2 }} inputProps={{ maxLength: 300 }} />
            <TextField label="Needed by" type="date" value={neededBy} onChange={(e) => setNeededBy(e.target.value)}
              InputLabelProps={{ shrink: true }} sx={{ flex: 1 }} />
          </Stack>
          <TextField label="Reference" value={reference} onChange={(e) => setReference(e.target.value)}
            helperText="What it is for, e.g. the customer booking you are fulfilling" inputProps={{ maxLength: 120 }} />

          <Typography variant="subtitle1">Items</Typography>
          {lines.map((l, i) => (
            <Stack key={i} direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ sm: 'center' }}
              data-testid={`rfq-line-${i}`}>
              <TextField size="small" label="Item" value={l.description} sx={{ flex: 3 }}
                onChange={(e) => setLine(i, { description: e.target.value })} />
              <TextField size="small" label="Quantity" type="number" value={l.quantity} sx={{ flex: 1 }}
                onChange={(e) => setLine(i, { quantity: e.target.value })} inputProps={{ min: 0 }} />
              <TextField size="small" label="Unit" value={l.uom} sx={{ flex: 1 }} placeholder="bag"
                onChange={(e) => setLine(i, { uom: e.target.value })} />
              <IconButton aria-label={`Remove item ${i + 1}`} disabled={lines.length === 1}
                onClick={() => setLines(lines.filter((_, j) => j !== i))}>
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Stack>
          ))}
          <Button startIcon={<AddIcon />} onClick={() => setLines([...lines, { ...EMPTY_LINE }])} sx={{ alignSelf: 'flex-start' }}>
            Add item
          </Button>

          <Typography variant="subtitle1">Ask these suppliers</Typography>
          {directory.data?.length === 0 && (
            <Alert severity="info">No suppliers are registered in this workspace yet.</Alert>
          )}
          <Stack>
            {directory.data?.map((s) => (
              <FormControlLabel key={s.id} control={
                <Checkbox checked={suppliers.includes(s.id)}
                  onChange={(e) => setSuppliers(e.target.checked ? [...suppliers, s.id] : suppliers.filter((x) => x !== s.id))} />
              } label={
                <Stack direction="row" spacing={1} alignItems="center">
                  <span>{s.name}</span>
                  {s.preferred && <Chip size="small" color="success" label="Preferred" />}
                </Stack>
              } />
            ))}
          </Stack>
          {create.isError && <Alert severity="error">{apiErrorMessage(create.error, 'The RFQ could not be sent.')}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button color="inherit" onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!title.trim() || !linesOk || suppliers.length === 0 || create.isPending}
          onClick={() => create.mutate()}>
          {create.isPending ? 'Sending…' : `Send to ${suppliers.length || ''} supplier${suppliers.length === 1 ? '' : 's'}`}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default NewRfqDialog;
