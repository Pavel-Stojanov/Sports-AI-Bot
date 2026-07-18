import {
  Button, Checkbox, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Typography
} from '@mui/material';
import { useEffect, useState } from 'react';
import type { PostResponse } from '../../../../api/types/post.ts';
import type { CreateDonationBatchRequest } from '../../../../api/types/donation.ts';
import postApi from '../../../../api/postApi.ts';
import useSnackbar from '../../../../hooks/useSnackbar.ts';

interface SubmitDonationDialogProps {
  open: boolean;
  onClose: () => void;
  onCreate: (data: CreateDonationBatchRequest) => Promise<void>;
}

const SubmitDonationDialog = ({ open, onClose, onCreate }: SubmitDonationDialogProps) => {
  const { showSnackbar } = useSnackbar();

  const [candidates, setCandidates] = useState<PostResponse[]>([]);
  const [selected, setSelected] = useState<number[]>([]);
  const [loading, setLoading] = useState<boolean>(false);

  useEffect(() => {
    if (!open) return;
    const fetch = async () => {
      setLoading(true);
      try {
        const response = await postApi.findAll({ donated: false }, 0, 50);
        setCandidates(response.data.content);
        setSelected([]);
      } catch (err) {
        showSnackbar(err instanceof Error ? err.message : 'Failed to load posts.', 'error');
      } finally {
        setLoading(false);
      }
    };
    void fetch();
  }, [open, showSnackbar]);

  const toggle = (id: number) => {
    setSelected((current) =>
      current.includes(id) ? current.filter((s) => s !== id) : [...current, id]);
  };

  const create = async () => {
    await onCreate({ postIds: selected });
    onClose();
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth='md'>
      <DialogTitle>New Donation Batch</DialogTitle>
      <DialogContent>
        {loading && <CircularProgress sx={{ display: 'block', mx: 'auto', my: 2 }}/>}
        {!loading && candidates.length === 0 && (
          <Typography color='text.secondary'>
            No undonated posts available. Run an extraction session first.
          </Typography>
        )}
        {!loading && (
          <List dense>
            {candidates.map((post) => (
              <ListItem key={post.id} disablePadding>
                <ListItemButton onClick={() => toggle(post.id)}>
                  <ListItemIcon>
                    <Checkbox edge='start' checked={selected.includes(post.id)} tabIndex={-1}/>
                  </ListItemIcon>
                  <ListItemText
                    primary={post.summary ?? post.content?.slice(0, 120) ?? `Post #${post.id}`}
                    secondary={`MK ${post.macedonianConfidence !== null ? (post.macedonianConfidence * 100).toFixed(0) : '?'}% · ${post.sourceUrl ?? ''}`}
                  />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant='contained' disabled={selected.length === 0} onClick={() => void create()}>
          Create batch ({selected.length})
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default SubmitDonationDialog;
