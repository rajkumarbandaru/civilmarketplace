import React, { useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Avatar,
  Box,
  Chip,
  ClickAwayListener,
  InputBase,
  ListItemButton,
  Paper,
  Popper,
  Typography,
} from '@mui/material';
import { Search as SearchIcon } from '@mui/icons-material';
import { styled, alpha } from '@mui/material/styles';
import DynamicIcon from './DynamicIcon';
import { searchServices, slugify } from '../constants/serviceCatalogue';
import { useCatalogue } from '../hooks/useCatalogue';

/**
 * The field is tinted from `text.primary` rather than from white. Both app bars that host this
 * component paint themselves `background.paper`, so a white-on-white fill left the box invisible
 * in light mode — you had to already know the search was there to find it.
 */
const SearchRoot = styled('div')(({ theme }) => ({
  position: 'relative',
  borderRadius: 12,
  border: `1px solid ${theme.palette.divider}`,
  backgroundColor: alpha(theme.palette.text.primary, 0.04),
  '&:hover': { backgroundColor: alpha(theme.palette.text.primary, 0.08) },
  marginRight: theme.spacing(2),
  marginLeft: 0,
  width: '100%',
  [theme.breakpoints.up('sm')]: { marginLeft: theme.spacing(3), width: 'auto' },
}));

const SearchIconWrapper = styled('div')(({ theme }) => ({
  padding: theme.spacing(0, 2),
  height: '100%',
  position: 'absolute',
  pointerEvents: 'none',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
}));

const StyledInputBase = styled(InputBase)(({ theme }) => ({
  color: 'inherit',
  '& .MuiInputBase-input': {
    padding: theme.spacing(1, 1, 1, 0),
    paddingLeft: `calc(1em + ${theme.spacing(4)})`,
    transition: theme.transitions.create('width'),
    width: '100%',
    [theme.breakpoints.up('md')]: { width: '24ch' },
    '&:focus': { [theme.breakpoints.up('md')]: { width: '32ch' } },
  },
}));

/** Enough to be useful in a dropdown without turning it into the results page. */
const MAX_SUGGESTIONS = 8;

/**
 * The header search, across the whole catalogue — services, professions, materials and machinery.
 *
 * It previously wrote every keystroke into `ui.searchQuery` in Redux and stopped there: nothing
 * read that value, so the box looked functional and did nothing at all. It now matches on
 * substrings via `searchServices` (so "steel", "tmt" or "jcb" all find something), shows what it
 * found, and Enter opens the full result set.
 */
const GlobalSearch: React.FC = () => {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const anchorRef = useRef<HTMLDivElement | null>(null);

  const { services } = useCatalogue();
  const results = useMemo(
    () => searchServices(query, services, MAX_SUGGESTIONS),
    [query, services]
  );
  // The dropdown reports "nothing found" too — silently showing an empty box reads as the search
  // being broken again, which is the exact impression this component exists to undo.
  const showDropdown = open && query.trim().length > 0;

  const goToResults = () => {
    if (!query.trim()) return;
    setOpen(false);
    navigate(`/services?q=${encodeURIComponent(query.trim())}`);
  };

  return (
    <ClickAwayListener onClickAway={() => setOpen(false)}>
      <Box sx={{ display: { xs: 'none', sm: 'block' } }}>
        <SearchRoot ref={anchorRef}>
          <SearchIconWrapper>
            <SearchIcon sx={{ color: 'text.secondary' }} />
          </SearchIconWrapper>
          <StyledInputBase
            placeholder="Search services, materials, equipment…"
            inputProps={{ 'aria-label': 'search services, materials and equipment' }}
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setOpen(true);
            }}
            onFocus={() => setOpen(true)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') goToResults();
              if (event.key === 'Escape') setOpen(false);
            }}
            sx={{ color: 'text.primary' }}
          />
        </SearchRoot>

        <Popper
          open={showDropdown}
          anchorEl={anchorRef.current}
          placement="bottom-start"
          // Above the app bar's own stacking context, or the panel renders behind the header.
          sx={{ zIndex: 1300 }}
        >
          <Paper
            elevation={8}
            sx={{ mt: 1, width: 360, maxHeight: 420, overflowY: 'auto', borderRadius: 2 }}
          >
            {results.length === 0 ? (
              <Box sx={{ p: 2 }}>
                <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                  Nothing matches “{query}”.
                </Typography>
              </Box>
            ) : (
              <>
                {results.map((service) => (
                  <ListItemButton
                    key={service.slug}
                    onClick={() => {
                      setOpen(false);
                      navigate(`/book/${service.slug}`);
                    }}
                    sx={{ gap: 1.5, py: 1 }}
                  >
                    <Avatar
                      sx={{
                        width: 32,
                        height: 32,
                        bgcolor: (t) => alpha(t.palette.primary.main, 0.12),
                        color: 'primary.main',
                      }}
                    >
                      <DynamicIcon name={service.icon} fontSize="small" />
                    </Avatar>
                    <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                      <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>
                        {service.title}
                      </Typography>
                      <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                        {service.price}
                      </Typography>
                    </Box>
                    <Chip
                      size="small"
                      label={service.category}
                      onClick={(event) => {
                        event.stopPropagation();
                        setOpen(false);
                        navigate(`/services/${slugify(service.category)}`);
                      }}
                      sx={{ fontSize: '0.65rem' }}
                    />
                  </ListItemButton>
                ))}
                <ListItemButton onClick={goToResults} sx={{ justifyContent: 'center', py: 1 }}>
                  <Typography variant="caption" sx={{ color: 'primary.main', fontWeight: 600 }}>
                    See all results for “{query}”
                  </Typography>
                </ListItemButton>
              </>
            )}
          </Paper>
        </Popper>
      </Box>
    </ClickAwayListener>
  );
};

export default GlobalSearch;
