import React, { useEffect, useMemo, useState } from 'react';
import {
  Accordion, AccordionDetails, AccordionSummary, Alert, Avatar, Box, Button, Card,
  CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Divider, FormControlLabel,
  Grid, IconButton, Snackbar, Switch, Tab, Tabs, TextField, Tooltip, Typography,
} from '@mui/material';
import {
  Add, ArrowDownward, ArrowUpward, Delete, ExpandMore, Image as ImageIcon, Save, Upload,
  VisibilityOff,
} from '@mui/icons-material';
import DynamicIcon from '../../components/DynamicIcon';
import { invalidateSiteContent } from '../../hooks/useSiteContent';
import {
  ContentItem, ContentSection, ItemCommand, MediaAsset, SectionCommand, addItem, createSection,
  deleteItem, deleteMedia, deleteSection, fetchAllSections, fetchMedia, resolveMediaUrl,
  reorderItems, updateItem, updateSection, uploadMedia,
} from '../../services/siteContentApi';

/**
 * Super Admin's editor for the public site's copy: the landing page's sections, the footer's
 * columns and links, the shared logo, and the images any of them use.
 *
 * <p>One accordion per section, grouped by the page it renders on. Everything on screen maps to a
 * row the public pages read directly — there is no publish step, because there is no draft: a save
 * is live on the next page load, which is what makes this usable for fixing a typo.
 */

const errorMessage = (err: unknown, fallback: string): string => {
  const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
  return message || fallback;
};

const PAGES = [
  { key: 'HOME', label: 'Home page' },
  { key: 'FOOTER', label: 'Footer' },
  { key: 'GLOBAL', label: 'Logo & brand' },
];

/** Which text slots a section uses, so the form does not offer fields the renderer ignores. */
const SECTION_HELP: Record<string, string> = {
  'home.hero': 'Headline (wrap a word in **asterisks** to highlight it), sub-paragraph, the chip above it (Body), the search button label and where it goes. Items are the trust badges. Image becomes the hero background.',
  'home.stats': 'Items only: Title is the number, Subtitle its label, Icon a Material-UI icon name.',
  'home.how_it_works': 'Heading and sub-heading. Items are the steps: Badge is the number, Body the description. An item image replaces the numbered circle.',
  'home.services': 'Heading and sub-heading above the service grid. The grid itself comes from the service catalogue.',
  'home.cta': 'Closing banner. Items are its buttons — the first renders filled, the rest outlined. Image becomes the banner background.',
  'global.brand': 'The wordmark and logo shown in the top bar. Upload an image to replace the text.',
  'footer.brand': 'The footer’s left block: Title is the wordmark, Body the paragraph, Items the social icons.',
  'footer.legal': 'The bottom bar: Body is the copyright line ({year} is filled in), Subtitle the tagline on the right.',
};

const DEFAULT_HELP =
  'A footer link group. Title is the column heading; each item is a link — leave its URL empty to show it as plain text.';

const SiteContentManagement: React.FC = () => {
  const [sections, setSections] = useState<ContentSection[]>([]);
  const [media, setMedia] = useState<MediaAsset[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState('HOME');
  const [saving, setSaving] = useState<number | null>(null);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>(
    { open: false, message: '', severity: 'success' }
  );
  /** Which section's image is being chosen, or an item id prefixed with "item:". */
  const [pickerTarget, setPickerTarget] = useState<string | null>(null);
  const [newSectionOpen, setNewSectionOpen] = useState(false);
  const [newSection, setNewSection] = useState<SectionCommand>({ pageKey: 'FOOTER', sectionKey: '', title: '' });

  const notify = (message: string, severity: 'success' | 'error' = 'success') =>
    setSnackbar({ open: true, message, severity });

  const load = async () => {
    try {
      setLoading(true);
      const [rows, assets] = await Promise.all([fetchAllSections(), fetchMedia()]);
      setSections(rows);
      setMedia(assets);
    } catch (err) {
      notify(errorMessage(err, 'Could not load the site content'), 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  /** Replaces one section in place, so an edit does not reorder the accordions under the cursor. */
  const replace = (saved: ContentSection) =>
    setSections((prev) => prev.map((s) => (s.id === saved.id ? saved : s)));

  const patchLocal = (id: number, fields: Partial<ContentSection>) =>
    setSections((prev) => prev.map((s) => (s.id === id ? { ...s, ...fields } : s)));

  const patchItemLocal = (sectionId: number, itemId: number, fields: Partial<ContentItem>) =>
    setSections((prev) =>
      prev.map((s) =>
        s.id === sectionId
          ? { ...s, items: s.items.map((i) => (i.id === itemId ? { ...i, ...fields } : i)) }
          : s
      )
    );

  const saveSection = async (section: ContentSection) => {
    try {
      setSaving(section.id);
      const saved = await updateSection(section.id, {
        title: section.title ?? '',
        subtitle: section.subtitle ?? '',
        body: section.body ?? '',
        imageUrl: section.imageUrl ?? '',
        linkLabel: section.linkLabel ?? '',
        linkUrl: section.linkUrl ?? '',
        columnIndex: section.columnIndex,
        sortOrder: section.sortOrder,
        enabled: section.enabled,
      });
      replace(saved);
      invalidateSiteContent();
      notify('Saved — reload the public page to see it');
    } catch (err) {
      notify(errorMessage(err, 'Could not save the section'), 'error');
    } finally {
      setSaving(null);
    }
  };

  const saveItem = async (sectionId: number, item: ContentItem) => {
    try {
      const command: ItemCommand = {
        title: item.title ?? '',
        subtitle: item.subtitle ?? '',
        body: item.body ?? '',
        icon: item.icon ?? '',
        imageUrl: item.imageUrl ?? '',
        linkUrl: item.linkUrl ?? '',
        badge: item.badge ?? '',
        enabled: item.enabled,
      };
      const saved = await updateItem(item.id, command);
      patchItemLocal(sectionId, item.id, saved);
      invalidateSiteContent();
      notify('Item saved');
    } catch (err) {
      notify(errorMessage(err, 'Could not save the item'), 'error');
    }
  };

  const onAddItem = async (section: ContentSection) => {
    try {
      const created = await addItem(section.id, { title: 'New item', enabled: true });
      patchLocal(section.id, { items: [...section.items, created] });
      invalidateSiteContent();
    } catch (err) {
      notify(errorMessage(err, 'Could not add the item'), 'error');
    }
  };

  const onDeleteItem = async (section: ContentSection, item: ContentItem) => {
    try {
      await deleteItem(item.id);
      patchLocal(section.id, { items: section.items.filter((i) => i.id !== item.id) });
      invalidateSiteContent();
      notify('Item deleted');
    } catch (err) {
      notify(errorMessage(err, 'Could not delete the item'), 'error');
    }
  };

  /** Moves an item one place and persists the whole section's order in a single call. */
  const move = async (section: ContentSection, index: number, delta: number) => {
    const next = [...section.items];
    const target = index + delta;
    if (target < 0 || target >= next.length) return;
    [next[index], next[target]] = [next[target], next[index]];
    patchLocal(section.id, { items: next });
    try {
      const saved = await reorderItems(section.id, next.map((i) => i.id));
      replace(saved);
      invalidateSiteContent();
    } catch (err) {
      notify(errorMessage(err, 'Could not re-order the items'), 'error');
      load();
    }
  };

  const onDeleteSection = async (section: ContentSection) => {
    try {
      await deleteSection(section.id);
      setSections((prev) => prev.filter((s) => s.id !== section.id));
      invalidateSiteContent();
      notify('Section deleted');
    } catch (err) {
      notify(errorMessage(err, 'Could not delete the section'), 'error');
    }
  };

  const onCreateSection = async () => {
    try {
      const created = await createSection(newSection);
      setSections((prev) => [...prev, created]);
      setNewSectionOpen(false);
      setNewSection({ pageKey: 'FOOTER', sectionKey: '', title: '' });
      invalidateSiteContent();
      notify('Section added');
    } catch (err) {
      notify(errorMessage(err, 'Could not add the section'), 'error');
    }
  };

  const onUpload = async (file: File) => {
    try {
      const asset = await uploadMedia(file);
      setMedia((prev) => [asset, ...prev]);
      notify('Image uploaded');
      return asset;
    } catch (err) {
      notify(errorMessage(err, 'Could not upload the image'), 'error');
      return null;
    }
  };

  /** Applies a chosen image to whichever section or item opened the picker. */
  const applyImage = (url: string) => {
    if (!pickerTarget) return;
    if (pickerTarget.startsWith('item:')) {
      const itemId = Number(pickerTarget.slice(5));
      const owner = sections.find((s) => s.items.some((i) => i.id === itemId));
      if (owner) {
        patchItemLocal(owner.id, itemId, { imageUrl: url });
        const item = owner.items.find((i) => i.id === itemId);
        if (item) saveItem(owner.id, { ...item, imageUrl: url });
      }
    } else {
      const id = Number(pickerTarget);
      const owner = sections.find((s) => s.id === id);
      if (owner) saveSection({ ...owner, imageUrl: url });
    }
    setPickerTarget(null);
  };

  const visible = useMemo(
    () => sections.filter((s) => s.pageKey === page),
    [sections, page]
  );

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 8 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2, flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>Site Content</Typography>
          <Typography variant="body2" color="text.secondary">
            The text, links and images visitors see. Changes are live on the next page load.
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<Add />} onClick={() => setNewSectionOpen(true)}>
          Add section
        </Button>
      </Box>

      <Tabs value={page} onChange={(_, value) => setPage(value)} sx={{ mb: 2 }}>
        {PAGES.map((p) => (
          <Tab key={p.key} value={p.key} label={p.label} />
        ))}
      </Tabs>

      {visible.length === 0 && (
        <Alert severity="info">No sections on this page yet.</Alert>
      )}

      {visible.map((section) => (
        <Accordion key={section.id} sx={{ mb: 1 }}>
          <AccordionSummary expandIcon={<ExpandMore />}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, width: '100%' }}>
              <Typography sx={{ fontWeight: 600 }}>
                {section.title || section.sectionKey}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {section.sectionKey} · {section.items.length} item{section.items.length === 1 ? '' : 's'}
              </Typography>
              {!section.enabled && (
                <Tooltip title="Hidden from the public site">
                  <VisibilityOff fontSize="small" color="disabled" />
                </Tooltip>
              )}
            </Box>
          </AccordionSummary>
          <AccordionDetails>
            <Alert severity="info" sx={{ mb: 2 }}>{SECTION_HELP[section.sectionKey] ?? DEFAULT_HELP}</Alert>

            <Grid container spacing={2}>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth label="Title / heading" value={section.title ?? ''}
                  onChange={(e) => patchLocal(section.id, { title: e.target.value })}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth label="Subtitle" value={section.subtitle ?? ''}
                  onChange={(e) => patchLocal(section.id, { subtitle: e.target.value })}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  fullWidth multiline minRows={2} label="Body" value={section.body ?? ''}
                  onChange={(e) => patchLocal(section.id, { body: e.target.value })}
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  fullWidth label="Button label" value={section.linkLabel ?? ''}
                  onChange={(e) => patchLocal(section.id, { linkLabel: e.target.value })}
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  fullWidth label="Button link" placeholder="/services" value={section.linkUrl ?? ''}
                  onChange={(e) => patchLocal(section.id, { linkUrl: e.target.value })}
                />
              </Grid>
              <Grid item xs={6} md={2}>
                <TextField
                  fullWidth type="number" label="Column" value={section.columnIndex}
                  onChange={(e) => patchLocal(section.id, { columnIndex: Number(e.target.value) })}
                  helperText="Footer only"
                />
              </Grid>
              <Grid item xs={6} md={2}>
                <TextField
                  fullWidth type="number" label="Order" value={section.sortOrder}
                  onChange={(e) => patchLocal(section.id, { sortOrder: Number(e.target.value) })}
                />
              </Grid>
            </Grid>

            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 2, flexWrap: 'wrap' }}>
              {section.imageUrl && (
                <Avatar
                  variant="rounded"
                  src={resolveMediaUrl(section.imageUrl)}
                  sx={{ width: 64, height: 64 }}
                />
              )}
              <Button startIcon={<ImageIcon />} onClick={() => setPickerTarget(String(section.id))}>
                {section.imageUrl ? 'Change image' : 'Set image'}
              </Button>
              {section.imageUrl && (
                <Button color="inherit" onClick={() => saveSection({ ...section, imageUrl: '' })}>
                  Remove image
                </Button>
              )}
              <FormControlLabel
                control={
                  <Switch
                    checked={section.enabled}
                    onChange={(e) => saveSection({ ...section, enabled: e.target.checked })}
                  />
                }
                label="Visible"
              />
              <Box sx={{ flexGrow: 1 }} />
              <Button
                variant="contained" startIcon={<Save />}
                disabled={saving === section.id}
                onClick={() => saveSection(section)}
              >
                Save section
              </Button>
              {/* Built-in sections have a shipped fallback behind them, so deleting one would be a
                  confusing no-op — the console offers "Visible" instead. */}
              {!section.systemOwned && (
                <Button color="error" startIcon={<Delete />} onClick={() => onDeleteSection(section)}>
                  Delete section
                </Button>
              )}
            </Box>

            <Divider sx={{ my: 3 }} />

            <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>Items</Typography>
              <Box sx={{ flexGrow: 1 }} />
              <Button size="small" startIcon={<Add />} onClick={() => onAddItem(section)}>Add item</Button>
            </Box>

            {section.items.map((item, idx) => (
              <Card key={item.id} variant="outlined" sx={{ p: 2, mb: 1 }}>
                <Grid container spacing={2} alignItems="center">
                  <Grid item xs={12} md={3}>
                    <TextField
                      fullWidth size="small" label="Title / label" value={item.title ?? ''}
                      onChange={(e) => patchItemLocal(section.id, item.id, { title: e.target.value })}
                    />
                  </Grid>
                  <Grid item xs={12} md={3}>
                    <TextField
                      fullWidth size="small" label="Link URL" placeholder="/services or https://…"
                      value={item.linkUrl ?? ''}
                      onChange={(e) => patchItemLocal(section.id, item.id, { linkUrl: e.target.value })}
                    />
                  </Grid>
                  <Grid item xs={6} md={2}>
                    <TextField
                      fullWidth size="small" label="Subtitle" value={item.subtitle ?? ''}
                      onChange={(e) => patchItemLocal(section.id, item.id, { subtitle: e.target.value })}
                    />
                  </Grid>
                  <Grid item xs={6} md={2}>
                    <TextField
                      fullWidth size="small" label="Icon" placeholder="Star" value={item.icon ?? ''}
                      onChange={(e) => patchItemLocal(section.id, item.id, { icon: e.target.value })}
                      InputProps={{
                        endAdornment: item.icon ? <DynamicIcon name={item.icon} fontSize="small" /> : undefined,
                      }}
                    />
                  </Grid>
                  <Grid item xs={6} md={2}>
                    <TextField
                      fullWidth size="small" label="Badge" value={item.badge ?? ''}
                      onChange={(e) => patchItemLocal(section.id, item.id, { badge: e.target.value })}
                    />
                  </Grid>
                  <Grid item xs={12} md={8}>
                    <TextField
                      fullWidth size="small" label="Description" value={item.body ?? ''}
                      onChange={(e) => patchItemLocal(section.id, item.id, { body: e.target.value })}
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, justifyContent: 'flex-end' }}>
                      {item.imageUrl && (
                        <Avatar variant="rounded" src={resolveMediaUrl(item.imageUrl)} sx={{ width: 32, height: 32 }} />
                      )}
                      <Tooltip title="Image">
                        <IconButton size="small" onClick={() => setPickerTarget(`item:${item.id}`)}>
                          <ImageIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Move up">
                        <span>
                          <IconButton size="small" disabled={idx === 0} onClick={() => move(section, idx, -1)}>
                            <ArrowUpward fontSize="small" />
                          </IconButton>
                        </span>
                      </Tooltip>
                      <Tooltip title="Move down">
                        <span>
                          <IconButton
                            size="small"
                            disabled={idx === section.items.length - 1}
                            onClick={() => move(section, idx, 1)}
                          >
                            <ArrowDownward fontSize="small" />
                          </IconButton>
                        </span>
                      </Tooltip>
                      <Switch
                        size="small"
                        checked={item.enabled}
                        onChange={(e) => saveItem(section.id, { ...item, enabled: e.target.checked })}
                      />
                      <Tooltip title="Save item">
                        <IconButton size="small" color="primary" onClick={() => saveItem(section.id, item)}>
                          <Save fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Delete item">
                        <IconButton size="small" color="error" onClick={() => onDeleteItem(section, item)}>
                          <Delete fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </Box>
                  </Grid>
                </Grid>
              </Card>
            ))}
          </AccordionDetails>
        </Accordion>
      ))}

      {/* Image picker: the uploads library, shared by every section and item. */}
      <Dialog open={pickerTarget !== null} onClose={() => setPickerTarget(null)} maxWidth="md" fullWidth>
        <DialogTitle>Choose an image</DialogTitle>
        <DialogContent>
          <Button component="label" variant="outlined" startIcon={<Upload />} sx={{ mb: 2 }}>
            Upload new image
            <input
              hidden type="file" accept="image/*"
              onChange={async (e) => {
                const file = e.target.files?.[0];
                if (!file) return;
                const asset = await onUpload(file);
                if (asset) applyImage(asset.url);
              }}
            />
          </Button>
          <Grid container spacing={2}>
            {media.map((asset) => (
              <Grid item xs={6} sm={4} md={3} key={asset.id}>
                <Card variant="outlined" sx={{ p: 1, textAlign: 'center' }}>
                  <Box
                    component="img"
                    src={resolveMediaUrl(asset.url)}
                    alt={asset.filename}
                    sx={{ width: '100%', height: 90, objectFit: 'contain', cursor: 'pointer' }}
                    onClick={() => applyImage(asset.url)}
                  />
                  <Typography variant="caption" noWrap display="block">{asset.filename}</Typography>
                  <Button
                    size="small" color="error"
                    onClick={async () => {
                      await deleteMedia(asset.id);
                      setMedia((prev) => prev.filter((m) => m.id !== asset.id));
                    }}
                  >
                    Delete
                  </Button>
                </Card>
              </Grid>
            ))}
          </Grid>
          {media.length === 0 && <Alert severity="info">No images uploaded yet.</Alert>}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPickerTarget(null)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={newSectionOpen} onClose={() => setNewSectionOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Add a section</DialogTitle>
        <DialogContent>
          <Alert severity="info" sx={{ mb: 2 }}>
            A new footer section becomes another link group. Give it a key nothing else uses — it
            cannot be changed later, because the pages look sections up by it.
          </Alert>
          <TextField
            fullWidth select SelectProps={{ native: true }} label="Page" sx={{ mb: 2 }}
            value={newSection.pageKey}
            onChange={(e) => setNewSection({ ...newSection, pageKey: e.target.value })}
          >
            {PAGES.map((p) => <option key={p.key} value={p.key}>{p.label}</option>)}
          </TextField>
          <TextField
            fullWidth label="Section key" placeholder="footer.partners" sx={{ mb: 2 }}
            value={newSection.sectionKey ?? ''}
            onChange={(e) => setNewSection({ ...newSection, sectionKey: e.target.value })}
          />
          <TextField
            fullWidth label="Heading" value={newSection.title ?? ''}
            onChange={(e) => setNewSection({ ...newSection, title: e.target.value })}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setNewSectionOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={onCreateSection}>Add</Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar.open} autoHideDuration={4000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
      >
        <Alert severity={snackbar.severity} onClose={() => setSnackbar({ ...snackbar, open: false })}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default SiteContentManagement;
