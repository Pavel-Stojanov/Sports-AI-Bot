import {
  Box, Button, Dialog, DialogActions, DialogContent, DialogTitle,
  FormControl, IconButton, InputLabel, MenuItem, Select, TextField
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import { useState } from 'react';
import type { CreateTargetRequest, TargetType } from '../../../../api/types/session.ts';
import useSessions from '../../../../hooks/useSessions.ts';

interface StartSessionDialogProps {
  open: boolean;
  onClose: () => void;
}

const TARGET_TYPES: TargetType[] = ['FEED_URL', 'KEYWORD', 'HASHTAG', 'PROFILE'];

const StartSessionDialog = ({ open, onClose }: StartSessionDialogProps) => {
  const { onCreate } = useSessions();

  const [submitting, setSubmitting] = useState(false);
  const [description, setDescription] = useState<string>('');
  const [targets, setTargets] = useState<CreateTargetRequest[]>([
    { type: 'FEED_URL', value: 'https://www.gol.mk/fudbal' }
  ]);

  const updateTarget = (index: number, patch: Partial<CreateTargetRequest>) => {
    setTargets(targets.map((t, i) => (i === index ? { ...t, ...patch } : t)));
  };

  const submit = async () => {
    if (submitting) return;
    setSubmitting(true);
    const created = await onCreate({
      socialNetwork: 'SPORTS_PORTAL_GOL',
      description,
      targets: targets.filter((t) => t.value.trim() !== '')
    });
    setSubmitting(false);
    if (!created) return;
    setDescription('');
    setTargets([{ type: 'FEED_URL', value: 'https://www.gol.mk/fudbal' }]);
    onClose();
  };

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} fullWidth maxWidth='sm'>
      <DialogTitle>New Extraction Session — gol.mk</DialogTitle>
      <DialogContent>
        <TextField
          label='Description'
          fullWidth
          sx={{ my: 2 }}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        {targets.map((target, index) => (
          <Box key={index} sx={{ display: 'flex', gap: 1, mb: 1 }}>
            <FormControl size='small' sx={{ minWidth: 130 }}>
              <InputLabel>Type</InputLabel>
              <Select
                label='Type'
                value={target.type}
                onChange={(e) => updateTarget(index, { type: e.target.value as TargetType })}
              >
                {TARGET_TYPES.map((type) => (
                  <MenuItem key={type} value={type}>{type}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              size='small'
              fullWidth
              label={target.type === 'FEED_URL' ? 'gol.mk URL' : 'Value (team, keyword, section…)'}
              value={target.value}
              onChange={(e) => updateTarget(index, { value: e.target.value })}
            />
            <IconButton
              onClick={() => setTargets(targets.filter((_, i) => i !== index))}
              disabled={targets.length === 1}
            >
              <DeleteIcon/>
            </IconButton>
          </Box>
        ))}
        <Button
          startIcon={<AddIcon/>}
          onClick={() => setTargets([...targets, { type: 'KEYWORD', value: '' }])}
        >
          Add target
        </Button>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={submitting}>Cancel</Button>
        <Button
          variant='contained'
          onClick={() => void submit()}
          disabled={submitting || targets.every((t) => t.value.trim() === '')}
        >
          Create
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default StartSessionDialog;
