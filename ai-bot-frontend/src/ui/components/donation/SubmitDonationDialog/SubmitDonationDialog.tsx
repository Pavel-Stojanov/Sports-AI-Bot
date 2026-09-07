import {
  Button, Checkbox, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, Pagination, Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Typography
} from '@mui/material';
import { useEffect, useState } from 'react';
import type { PostResponse } from '../../../../api/types/post.ts';
import type { CreateDonationBatchRequest } from '../../../../api/types/donation.ts';
import postApi from '../../../../api/postApi.ts';
import extractErrorMessage from '../../../../api/extractErrorMessage.ts';
import useSnackbar from '../../../../hooks/useSnackbar.ts';

interface SubmitDonationDialogProps {
  open: boolean;
  onClose: () => void;
  busy: boolean;
  onCreate: (data: CreateDonationBatchRequest) => Promise<boolean>;
}

const SubmitDonationDialog = ({ open, onClose, onCreate, busy }: SubmitDonationDialogProps) => {
  const { showSnackbar } = useSnackbar();

  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [candidates, setCandidates] = useState<PostResponse[]>([]);
  const [selected, setSelected] = useState<number[]>([]);
  const [loading, setLoading] = useState<boolean>(false);

  useEffect(() => {
    if (!open) return;
    let active = true;
    const fetch = async () => {
      setLoading(true);
      try {
        const response = await postApi.findAll({ donated: false, minMacedonianConfidence: 0.6 }, page, 50);
        if (!active) return;
        setCandidates(response.data.content);
        setTotalPages(response.data.totalPages);
      } catch (err) {
        if (active) showSnackbar(extractErrorMessage(err, 'Failed to load posts.'), 'error');
      } finally {
        if (active) setLoading(false);
      }
    };
    void fetch();
    return () => { active = false; };
  }, [open, page, showSnackbar]);

  const toggle = (id: number) => {
    setSelected((current) =>
      current.includes(id) ? current.filter((s) => s !== id) : [...current, id]);
  };

  const create = async () => {
    if (busy) return;
    if (await onCreate({ postIds: selected })) onClose();
  };

  return (
    <Dialog open={open} onClose={busy ? undefined : onClose} fullWidth maxWidth='md'>
      <DialogTitle>New Donation Batch</DialogTitle>
      <DialogContent>
        {loading && <CircularProgress sx={{ display: 'block', mx: 'auto', my: 2 }}/>}
        {!loading && candidates.length === 0 && (
          <Typography color='text.secondary'>
            No unassigned posts meet the language score threshold of 0.6. Check extracted posts or run another session.
          </Typography>
        )}
        {!loading && (
          <List dense>
            {candidates.map((post) => (
              <ListItem key={post.id} disablePadding>
                <ListItemButton disabled={busy || !post.sourceUrl || !post.content || post.content.trim().length < 20} onClick={() => toggle(post.id)}>
                  <ListItemIcon>
                    <Checkbox edge='start' checked={selected.includes(post.id)} tabIndex={-1}/>
                  </ListItemIcon>
                  <ListItemText
                    primary={post.summary ?? post.content?.slice(0, 120) ?? `Post #${post.id}`}
                    secondary={`MK score: ${post.macedonianConfidence !== null ? post.macedonianConfidence.toFixed(2) : '?'} · ${post.sourceUrl ?? ''}`}
                  />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        )}
        {!loading && totalPages > 1 && (
          <Box sx={{ display: 'flex', justifyContent: 'center', my: 2 }}>
            <Pagination count={totalPages} page={page + 1} disabled={busy}
              onChange={(_, value) => setPage(value - 1)}/>
          </Box>
        )}
        <Typography variant='caption'>Selections are kept when you change pages. Donations send the article text.</Typography>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={busy}>Cancel</Button>
        <Button variant='contained' disabled={busy || loading || selected.length === 0} onClick={() => void create()}>
          Create batch ({selected.length})
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default SubmitDonationDialog;
