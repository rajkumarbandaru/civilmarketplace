import React, { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Stack,
  Switch,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import {
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
} from '@mui/material';
import { ArrowDownward, ArrowUpward, ArrowBack, Add } from '@mui/icons-material';
import ThemeEditor from '../../components/admin/ThemeEditor';
import { apiErrorMessage } from '../../services/apiError';
import DynamicIcon from '../../components/DynamicIcon';
import { useUiConfig } from '../../providers/UiConfigProvider';
import {
  createWorkspace,
  fetchWorkspaceMenu,
  fetchWorkspaces,
  fetchEffectiveWorkspaceTheme,
  fetchWorkspaceTheme,
  MenuUpdateCommand,
  resetWorkspaceMenu,
  resetWorkspaceTheme,
  ThemeUpdateCommand,
  updateWorkspaceMenu,
  updateWorkspaceTheme,
  WorkspaceCreateCommand,
  WorkspaceMenuRow,
  WorkspaceSummary,
} from '../../services/uiConfigApi';
import { SortableTableCell, useTableSort } from '../../components/admin/SortableTable';

/**
 * Super Admin's view over every workspace — one role is one workspace. Picking a workspace opens
 * its side menu and its theme override.
 */

/** "Site engineer" -> SITE_ENGINEER, mirroring what the backend will store, so the dialog can
 *  show the identifier the rest of the platform will actually use before it is created. */
const previewRoleName = (name: string) =>
  name.trim().toUpperCase().replace(/[\s-]+/g, '_').replace(/[^A-Z0-9_]/g, '');

const NewWorkspaceDialog: React.FC<{
  open: boolean;
  onClose: () => void;
  onCreated: (workspace: WorkspaceSummary) => void;
}> = ({ open, onClose, onCreated }) => {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');

  const create = useMutation({
    mutationFn: (command: WorkspaceCreateCommand) => createWorkspace(command),
    onSuccess: (workspace) => {
      setName('');
      setDescription('');
      onCreated(workspace);
    },
  });

  const roleName = previewRoleName(name);
  const valid = /^[A-Z][A-Z0-9_]*$/.test(roleName) && roleName.length <= 50;

  const close = () => {
    if (create.isPending) return;
    create.reset();
    onClose();
  };

  return (
    <Dialog open={open} onClose={close} fullWidth maxWidth="sm">
      <DialogTitle>New workspace</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          A workspace is a role. Creating one adds that role to the platform, and its side menu and
          theme start on the platform defaults — configure them here afterwards.
        </DialogContentText>

        {create.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {apiErrorMessage(create.error, 'The workspace could not be created.')}
          </Alert>
        )}

        <TextField
          autoFocus
          fullWidth
          label="Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          sx={{ mb: 2 }}
          helperText={
            name.trim() === ''
              ? 'For example "Site Engineer".'
              : valid
              ? `Stored as ${roleName}`
              : 'Use letters, digits, spaces and underscores, starting with a letter.'
          }
          error={name.trim() !== '' && !valid}
        />
        <TextField
          fullWidth
          label="Description (optional)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </DialogContent>
      <DialogActions>
        <Button color="inherit" onClick={close} disabled={create.isPending}>Cancel</Button>
        <Button
          variant="contained"
          disabled={!valid || create.isPending}
          onClick={() => create.mutate({ name: name.trim(), description: description.trim() })}
        >
          {create.isPending ? 'Creating…' : 'Create workspace'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

const WorkspaceList: React.FC<{ onOpen: (role: string) => void }> = ({ onOpen }) => {
  const { data, isLoading, isError } = useQuery<WorkspaceSummary[]>({
    queryKey: ['ui-config', 'workspaces'],
    queryFn: fetchWorkspaces,
  });

  const { sorted, sort, onSort } = useTableSort(data || [], {
    label: (w) => w.label,
    userCount: (w) => w.userCount,
    visibleMenuCount: (w) => w.visibleMenuCount,
    customised: (w) => Number(w.menuCustomised) + Number(w.themeCustomised),
  }, { key: 'label' });

  if (isLoading) {
    return <Box sx={{ display: 'flex', justifyContent: 'center', p: 6 }}><CircularProgress /></Box>;
  }
  if (isError) return <Alert severity="error">Could not load the workspace list.</Alert>;

  return (
    <Card>
      <Table>
        <TableHead>
          <TableRow>
            <SortableTableCell columnKey="label" sort={sort} onSort={onSort}>Workspace</SortableTableCell>
            <SortableTableCell columnKey="userCount" sort={sort} onSort={onSort} align="right">Members</SortableTableCell>
            <SortableTableCell columnKey="visibleMenuCount" sort={sort} onSort={onSort} align="right">Menu items</SortableTableCell>
            <SortableTableCell columnKey="customised" sort={sort} onSort={onSort}>Customised</SortableTableCell>
            <TableCell />
          </TableRow>
        </TableHead>
        <TableBody>
          {sorted.map((workspace) => (
            <TableRow key={workspace.role} hover>
              <TableCell>
                <Typography variant="body2" fontWeight={600}>{workspace.label}</Typography>
                <Typography variant="caption" color="text.secondary">{workspace.role}</Typography>
              </TableCell>
              <TableCell align="right">{workspace.userCount}</TableCell>
              <TableCell align="right">{workspace.visibleMenuCount}</TableCell>
              <TableCell>
                <Stack direction="row" spacing={0.5}>
                  {workspace.menuCustomised && <Chip size="small" label="Menu" />}
                  {workspace.themeCustomised && <Chip size="small" label="Theme" color="primary" />}
                  {!workspace.menuCustomised && !workspace.themeCustomised && (
                    <Typography variant="caption" color="text.secondary">Defaults</Typography>
                  )}
                </Stack>
              </TableCell>
              <TableCell align="right">
                <Button size="small" onClick={() => onOpen(workspace.role)}>Configure</Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Card>
  );
};

const MenuEditor: React.FC<{ role: string }> = ({ role }) => {
  const queryClient = useQueryClient();
  const { refresh } = useUiConfig();
  const queryKey = ['ui-config', 'workspace-menu', role];

  const { data, isLoading, isError } = useQuery<WorkspaceMenuRow[]>({
    queryKey,
    queryFn: () => fetchWorkspaceMenu(role),
  });

  // Edited locally and saved as one batch: re-ordering is several moves, and a request per move
  // would leave a half-ordered menu behind if one failed.
  const [rows, setRows] = useState<WorkspaceMenuRow[]>([]);
  useEffect(() => setRows(data || []), [data]);

  const onSaved = (saved: WorkspaceMenuRow[]) => {
    queryClient.setQueryData(queryKey, saved);
    queryClient.invalidateQueries({ queryKey: ['ui-config', 'workspaces'] });
    refresh();
  };

  const save = useMutation({
    mutationFn: (commands: MenuUpdateCommand[]) => updateWorkspaceMenu(role, commands),
    onSuccess: onSaved,
  });

  const reset = useMutation({
    mutationFn: () => resetWorkspaceMenu(role),
    onSuccess: onSaved,
  });

  if (isLoading) {
    return <Box sx={{ display: 'flex', justifyContent: 'center', p: 6 }}><CircularProgress /></Box>;
  }
  if (isError) return <Alert severity="error">Could not load this workspace's menu.</Alert>;

  const move = (index: number, delta: number) => {
    const target = index + delta;
    if (target < 0 || target >= rows.length) return;
    const next = [...rows];
    [next[index], next[target]] = [next[target], next[index]];
    // Sort order is rewritten from the new positions rather than swapped, so the values stay
    // evenly spaced and a later catalogue insert has room to land between them.
    setRows(next.map((row, i) => ({ ...row, sortOrder: (i + 1) * 10 })));
  };

  const setRow = (itemKey: string, patch: Partial<WorkspaceMenuRow>) =>
    setRows((prev) => prev.map((row) => (row.itemKey === itemKey ? { ...row, ...patch } : row)));

  const commands: MenuUpdateCommand[] = rows.map((row) => ({
    itemKey: row.itemKey,
    visible: row.visible,
    sortOrder: row.sortOrder,
    labelOverride: row.label === row.defaultLabel ? null : row.label,
  }));

  return (
    <Card>
      <CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
          <Box>
            <Typography variant="h6">Side menu</Typography>
            <Typography variant="body2" color="text.secondary">
              Hide, rename or re-order what this workspace sees. Entries come from the catalogue —
              a new screen has to be added there before it can appear here.
            </Typography>
          </Box>
          <Button color="inherit" disabled={reset.isPending} onClick={() => reset.mutate()}>
            Reset to defaults
          </Button>
        </Stack>

        {save.isSuccess && <Alert severity="success" sx={{ mb: 2 }}>Menu saved.</Alert>}
        {save.isError && <Alert severity="error" sx={{ mb: 2 }}>The menu could not be saved.</Alert>}

        <List dense>
          {rows.map((row, index) => (
            <ListItem
              key={row.itemKey}
              divider
              secondaryAction={
                <Stack direction="row" alignItems="center">
                  <IconButton size="small" disabled={index === 0} onClick={() => move(index, -1)}>
                    <ArrowUpward fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    disabled={index === rows.length - 1}
                    onClick={() => move(index, 1)}
                  >
                    <ArrowDownward fontSize="small" />
                  </IconButton>
                  <Switch
                    checked={row.visible}
                    onChange={(e) => setRow(row.itemKey, { visible: e.target.checked })}
                  />
                </Stack>
              }
            >
              <Box sx={{ mr: 2, color: 'text.secondary', display: 'flex' }}>
                <DynamicIcon name={row.icon} />
              </Box>
              <ListItemText
                sx={{ pr: 18 }}
                primary={
                  <TextField
                    variant="standard"
                    size="small"
                    value={row.label}
                    onChange={(e) => setRow(row.itemKey, { label: e.target.value })}
                    sx={{ maxWidth: 260 }}
                  />
                }
                secondary={
                  <>
                    {row.section} · {row.path}
                    {row.label !== row.defaultLabel && ` · renamed from "${row.defaultLabel}"`}
                    {!row.defaultVisible && ' · hidden by default for this role'}
                  </>
                }
              />
            </ListItem>
          ))}
        </List>

        <Button
          variant="contained"
          sx={{ mt: 2 }}
          disabled={save.isPending}
          onClick={() => save.mutate(commands)}
        >
          {save.isPending ? 'Saving…' : 'Save menu'}
        </Button>
      </CardContent>
    </Card>
  );
};

/** LABOUR_CONTRACTOR -> "Labour Contractor", matching how the API labels workspaces. */
const humaniseRole = (role: string) =>
  role.toLowerCase().split('_').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');

const WorkspaceThemeEditor: React.FC<{ role: string }> = ({ role }) => {
  const queryClient = useQueryClient();
  const { refresh } = useUiConfig();
  const queryKey = ['ui-config', 'workspace-theme', role];

  const { data, isLoading, isError } = useQuery({
    queryKey,
    queryFn: () => fetchWorkspaceTheme(role),
  });

  // The merged result, so the editor can show what each blank field will actually inherit from
  // the platform theme — and preview it — instead of falling back to the shipped defaults.
  const { data: effective } = useQuery({
    queryKey: ['ui-config', 'workspace-theme-effective', role],
    queryFn: () => fetchEffectiveWorkspaceTheme(role),
  });

  const onSaved = () => {
    queryClient.invalidateQueries({ queryKey });
    queryClient.invalidateQueries({ queryKey: ['ui-config', 'workspace-theme-effective', role] });
    queryClient.invalidateQueries({ queryKey: ['ui-config', 'workspaces'] });
    refresh();
  };

  const save = useMutation({
    mutationFn: (command: ThemeUpdateCommand) => updateWorkspaceTheme(role, command),
    onSuccess: onSaved,
  });

  const reset = useMutation({
    mutationFn: () => resetWorkspaceTheme(role),
    onSuccess: onSaved,
  });

  if (isLoading) {
    return <Box sx={{ display: 'flex', justifyContent: 'center', p: 6 }}><CircularProgress /></Box>;
  }
  if (isError) return <Alert severity="error">Could not load this workspace's theme.</Alert>;

  return (
    <>
      {save.isSuccess && <Alert severity="success" sx={{ mb: 2 }}>Workspace theme saved.</Alert>}
      {save.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {apiErrorMessage(save.error, 'The theme could not be saved.')}
        </Alert>
      )}
      <ThemeEditor
        value={data}
        effective={effective}
        scopeLabel={`The ${humaniseRole(role)} workspace`}
        saving={save.isPending || reset.isPending}
        onSave={(command) => save.mutate(command)}
        onReset={() => reset.mutate()}
        resetLabel="Inherit the platform theme"
      />
    </>
  );
};

const WorkspaceManagement: React.FC = () => {
  const [role, setRole] = useState<string | null>(null);
  const [tab, setTab] = useState(0);
  const [creating, setCreating] = useState(false);
  const queryClient = useQueryClient();

  if (!role) {
    return (
      <Box>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start" sx={{ mb: 3 }}>
          <Box>
            <Typography variant="h4" gutterBottom>Workspaces</Typography>
            <Typography variant="body2" color="text.secondary">
              One role is one workspace. Each has its own side menu and can override the platform
              theme.
            </Typography>
          </Box>
          <Button variant="contained" startIcon={<Add />} onClick={() => setCreating(true)}>
            New workspace
          </Button>
        </Stack>

        <WorkspaceList onOpen={(r) => { setRole(r); setTab(0); }} />

        <NewWorkspaceDialog
          open={creating}
          onClose={() => setCreating(false)}
          onCreated={(workspace) => {
            setCreating(false);
            // The list is refetched rather than patched: the row the API returns is the same
            // shape, but the member count and menu totals of a brand-new workspace are the
            // server's answer to give, not this component's to assume.
            queryClient.invalidateQueries({ queryKey: ['ui-config', 'workspaces'] });
            setRole(workspace.role);
            setTab(0);
          }}
        />
      </Box>
    );
  }

  return (
    <Box>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
        <IconButton size="small" onClick={() => setRole(null)}><ArrowBack /></IconButton>
        <Typography variant="h4">{role}</Typography>
      </Stack>

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
        <Tab label="Side menu" />
        <Tab label="Theme" />
      </Tabs>
      <Divider sx={{ mb: 3 }} />

      {tab === 0 ? <MenuEditor role={role} /> : <WorkspaceThemeEditor role={role} />}
    </Box>
  );
};

export default WorkspaceManagement;
