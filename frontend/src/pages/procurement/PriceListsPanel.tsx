import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, MenuItem,
  Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material';
import { apiErrorMessage } from '../../services/apiError';
import {
  Organization, PriceList, PriceListItem, PriceListStatus, decideContract, fetchDirectory, fetchPriceLists, formatMoney,
  proposeContract, saveCatalogue,
} from '../../services/procurementApi';

interface Row { description: string; uom: string; unitPrice: string; taxPercent: string }
const toRows = (items: PriceListItem[]): Row[] => items.map((i) => ({
  description: i.description, uom: i.uom, unitPrice: String(i.unitPrice), taxPercent: String(i.taxPercent),
}));
const toItems = (rows: Row[]): PriceListItem[] => rows.map((r) => ({
  description: r.description.trim(), uom: r.uom.trim(), unitPrice: Number(r.unitPrice), taxPercent: Number(r.taxPercent),
}));
const rowsOk = (rows: Row[]) => rows.every((r) => r.description.trim() && r.uom.trim() && r.unitPrice !== '' && Number(r.unitPrice) >= 0
  && r.taxPercent !== '' && Number(r.taxPercent) >= 0 && Number(r.taxPercent) <= 100);

const STATUS_COLOR: Record<PriceListStatus, 'success' | 'warning' | 'default' | 'error'> = {
  ACTIVE: 'success', PROPOSED: 'warning', DECLINED: 'default', TERMINATED: 'default',
};

/** Rows of item, unit, price and GST, editable. */
const ItemsEditor: React.FC<{ rows: Row[]; onChange: (rows: Row[]) => void; testId: string }> = ({ rows, onChange, testId }) => {
  const set = (i: number, patch: Partial<Row>) => onChange(rows.map((r, j) => (j === i ? { ...r, ...patch } : r)));
  return (
    <Box data-testid={testId}>
      {rows.map((r, i) => (
        <Stack key={i} direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mb: 1 }} alignItems={{ sm: 'center' }}>
          <TextField size="small" label="Item" value={r.description} sx={{ flex: 3 }}
            onChange={(e) => set(i, { description: e.target.value })} inputProps={{ maxLength: 300 }} />
          <TextField size="small" label="Unit" value={r.uom} sx={{ flex: 1 }} onChange={(e) => set(i, { uom: e.target.value })}
            inputProps={{ maxLength: 20 }} />
          <TextField size="small" label="Price (₹)" type="number" value={r.unitPrice} sx={{ flex: 1 }}
            onChange={(e) => set(i, { unitPrice: e.target.value })} inputProps={{ min: 0, step: '0.01' }} />
          <TextField size="small" label="GST %" type="number" value={r.taxPercent} sx={{ width: 90 }}
            onChange={(e) => set(i, { taxPercent: e.target.value })} inputProps={{ min: 0, max: 100 }} />
          <IconButton aria-label={`Remove row ${i + 1}`} onClick={() => onChange(rows.filter((_, j) => j !== i))}>
            <DeleteIcon fontSize="small" />
          </IconButton>
        </Stack>
      ))}
      <Button size="small" startIcon={<AddIcon />}
        onClick={() => onChange([...rows, { description: '', uom: '', unitPrice: '', taxPercent: '18' }])}>
        Add item
      </Button>
    </Box>
  );
};

/** A supplier's standard prices: suggested to it when it quotes anyone. */
const CatalogueCard: React.FC<{ supplier: Organization; catalogue?: PriceList }> = ({ supplier, catalogue }) => {
  const queryClient = useQueryClient();
  const [rows, setRows] = useState<Row[]>(() => toRows(catalogue?.items ?? []));
  const save = useMutation({
    mutationFn: () => saveCatalogue(supplier.id, toItems(rows)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['procurement-prices'] }),
  });
  return (
    <Card sx={{ mb: 3 }} data-testid={`catalogue-${supplier.id}`}>
      <CardContent>
        <Typography variant="h6">{supplier.name}: catalogue</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Your standard prices. They are filled in for you when you quote an RFQ for the same item and unit.
        </Typography>
        <ItemsEditor rows={rows} onChange={setRows} testId={`catalogue-items-${supplier.id}`} />
        {save.isError && <Alert severity="error" sx={{ mt: 1 }}>{apiErrorMessage(save.error, 'The catalogue was not saved.')}</Alert>}
        {save.isSuccess && <Alert severity="success" sx={{ mt: 1 }}>Catalogue saved.</Alert>}
        <Button variant="contained" sx={{ mt: 1 }} disabled={!rowsOk(rows) || save.isPending} onClick={() => save.mutate()}>
          Save catalogue
        </Button>
      </CardContent>
    </Card>
  );
};

/** The supplier offers one buyer contract rates, payment terms and a credit limit. */
const ContractDialog: React.FC<{ suppliers: Organization[]; catalogues: PriceList[]; onClose: () => void }> = ({
  suppliers, catalogues, onClose,
}) => {
  const queryClient = useQueryClient();
  const [supplierOrgId, setSupplierOrgId] = useState(suppliers[0].id);
  const [buyerOrgId, setBuyerOrgId] = useState('');
  const [name, setName] = useState('');
  const [net, setNet] = useState('30');
  const [limit, setLimit] = useState('');
  const [from, setFrom] = useState('');
  const [until, setUntil] = useState('');
  const [rows, setRows] = useState<Row[]>(() =>
    toRows(catalogues.find((c) => c.supplier.id === suppliers[0].id)?.items ?? []));
  const buyers = useQuery({
    queryKey: ['procurement-directory', 'BUYER', supplierOrgId],
    queryFn: () => fetchDirectory('BUYER', supplierOrgId),
  });
  const save = useMutation({
    mutationFn: () => proposeContract({
      supplierOrgId, buyerOrgId: Number(buyerOrgId), name: name.trim(), paymentTermsDays: Number(net),
      creditLimit: limit === '' ? null : Number(limit), validFrom: from || null, validUntil: until || null, items: toItems(rows),
    }),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['procurement-prices'] }); onClose(); },
  });
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Offer a contract</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {suppliers.length > 1 && (
            <TextField select label="Supplying as" value={supplierOrgId} onChange={(e) => setSupplierOrgId(Number(e.target.value))}>
              {suppliers.map((s) => <MenuItem key={s.id} value={s.id}>{s.name}</MenuItem>)}
            </TextField>
          )}
          <TextField select label="Buyer" value={buyerOrgId} onChange={(e) => setBuyerOrgId(e.target.value)}>
            {buyers.data?.map((b) => <MenuItem key={b.id} value={String(b.id)}>{b.name}</MenuItem>)}
          </TextField>
          <TextField label="Contract name" value={name} onChange={(e) => setName(e.target.value)}
            placeholder="Cement supply 2026-27" inputProps={{ maxLength: 120 }} />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField label="Payment terms (days)" type="number" value={net} onChange={(e) => setNet(e.target.value)}
              helperText="Net N: invoices due N days after approval" inputProps={{ min: 0, max: 180 }} />
            <TextField label="Credit limit (₹)" type="number" value={limit} onChange={(e) => setLimit(e.target.value)}
              helperText="Most the buyer may have open at once; empty for none" inputProps={{ min: 0 }} />
          </Stack>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField label="Valid from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} InputLabelProps={{ shrink: true }} />
            <TextField label="Valid until" type="date" value={until} onChange={(e) => setUntil(e.target.value)} InputLabelProps={{ shrink: true }} />
          </Stack>
          <Typography variant="subtitle1">Contract rates</Typography>
          <Typography variant="caption" color="text.secondary">The most you may quote this buyer for each item.</Typography>
          <ItemsEditor rows={rows} onChange={setRows} testId="contract-items" />
          {save.isError && <Alert severity="error">{apiErrorMessage(save.error, 'The contract was not offered.')}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button color="inherit" onClick={onClose}>Cancel</Button>
        <Button variant="contained"
          disabled={!buyerOrgId || !name.trim() || rows.length === 0 || !rowsOk(rows) || Number(net) < 0 || Number(net) > 180 || save.isPending}
          onClick={() => save.mutate()}>
          Offer contract
        </Button>
      </DialogActions>
    </Dialog>
  );
};

/** Catalogues this person manages, and every contract their organizations are party to. */
const PriceListsPanel: React.FC<{ organizations: Organization[] }> = ({ organizations }) => {
  const queryClient = useQueryClient();
  const [offering, setOffering] = useState(false);
  const lists = useQuery({ queryKey: ['procurement-prices'], queryFn: fetchPriceLists });
  const decide = useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'accept' | 'decline' | 'terminate' }) => decideContract(id, action),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['procurement-prices'] });
      queryClient.invalidateQueries({ queryKey: ['procurement-directory'] });
    },
  });
  const managed = organizations.filter((o) => o.myRole !== 'MEMBER');
  const suppliers = managed.filter((o) => o.capabilities.includes('SUPPLIER'));
  const managedIds = new Set(managed.map((o) => o.id));
  const catalogues = lists.data?.filter((p) => !p.contract) ?? [];
  const contracts = lists.data?.filter((p) => p.contract) ?? [];

  return (
    <Box>
      {!lists.isLoading && suppliers.map((s) => (
        <CatalogueCard key={s.id} supplier={s} catalogue={catalogues.find((c) => c.supplier.id === s.id)} />
      ))}

      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
        <Typography variant="h6">Contracts</Typography>
        {suppliers.length > 0 && <Button variant="outlined" onClick={() => setOffering(true)}>Offer a contract</Button>}
      </Stack>
      {contracts.length === 0 && (
        <Typography color="text.secondary">
          No contracts. A supplier can offer a buyer fixed rates, payment terms and a credit limit here.
        </Typography>
      )}
      <Stack spacing={2}>
        {contracts.map((c) => {
          const asBuyer = c.buyer != null && managedIds.has(c.buyer.id);
          const asSupplier = managedIds.has(c.supplier.id);
          return (
            <Card key={c.id} data-testid={`contract-${c.id}`}>
              <CardContent>
                <Stack direction="row" spacing={1} alignItems="center" sx={{ flexWrap: 'wrap', rowGap: 1, mb: 1 }}>
                  <Typography sx={{ fontWeight: 600 }}>{c.name}</Typography>
                  <Chip size="small" color={STATUS_COLOR[c.status]} label={c.status.toLowerCase()} />
                  <Typography variant="body2" color="text.secondary" sx={{ flex: 1 }}>
                    {c.supplier.name} → {c.buyer?.name} · Net {c.paymentTermsDays}
                    {c.creditLimit != null && ` · credit limit ${formatMoney(c.creditLimit)}`}
                    {c.exposure != null && ` · ${formatMoney(c.exposure)} open`}
                    {(c.validFrom || c.validUntil) && ` · ${c.validFrom ?? '…'} to ${c.validUntil ?? '…'}`}
                  </Typography>
                  {c.status === 'PROPOSED' && asBuyer && (
                    <>
                      <Button size="small" color="inherit" onClick={() => decide.mutate({ id: c.id, action: 'decline' })}>Decline</Button>
                      <Button size="small" variant="contained" onClick={() => decide.mutate({ id: c.id, action: 'accept' })}>Accept</Button>
                    </>
                  )}
                  {(c.status === 'ACTIVE' || (c.status === 'PROPOSED' && asSupplier)) && (asBuyer || asSupplier) && (
                    <Button size="small" color="error" onClick={() => decide.mutate({ id: c.id, action: 'terminate' })}>
                      {c.status === 'PROPOSED' ? 'Withdraw' : 'End contract'}
                    </Button>
                  )}
                </Stack>
                <Table size="small">
                  <TableHead>
                    <TableRow><TableCell>Item</TableCell><TableCell>Unit</TableCell><TableCell align="right">Rate</TableCell>
                      <TableCell align="right">GST</TableCell></TableRow>
                  </TableHead>
                  <TableBody>
                    {c.items.map((i) => (
                      <TableRow key={i.id}>
                        <TableCell>{i.description}</TableCell><TableCell>{i.uom}</TableCell>
                        <TableCell align="right">{formatMoney(i.unitPrice)}</TableCell>
                        <TableCell align="right">{i.taxPercent}%</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          );
        })}
      </Stack>
      {decide.isError && <Alert severity="error" sx={{ mt: 2 }}>{apiErrorMessage(decide.error, 'That did not work.')}</Alert>}
      {offering && <ContractDialog suppliers={suppliers} catalogues={catalogues} onClose={() => setOffering(false)} />}
    </Box>
  );
};

export default PriceListsPanel;
