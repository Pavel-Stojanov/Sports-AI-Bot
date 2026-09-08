import { Box, Button, FormControl, InputLabel, MenuItem, Select, Slider, TextField, Typography } from '@mui/material';
import { useState } from 'react';
import type { PostFilter } from '../../../../api/types/post.ts';

interface PostFiltersProps {
  filter: PostFilter;
  onChange: (filter: PostFilter) => void;
}

const PostFilters = ({ filter, onChange }: PostFiltersProps) => {
  const [search, setSearch] = useState<string>(filter.search ?? '');
  const [minConfidence, setMinConfidence] = useState<number>(filter.minMacedonianConfidence ?? 0);
  const [donated, setDonated] = useState<string>(
    filter.donated === undefined ? 'ALL' : filter.donated ? 'YES' : 'NO');

  const apply = () => {
    onChange({
      ...filter,
      search: search.trim() === '' ? undefined : search.trim(),
      minMacedonianConfidence: minConfidence === 0 ? undefined : minConfidence,
      donated: donated === 'ALL' ? undefined : donated === 'YES'
    });
  };

  const reset = () => {
    setSearch('');
    setMinConfidence(0);
    setDonated('ALL');
    onChange({});
  };

  return (
    <Box sx={{ mb: 2, display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}>
      <TextField
        label='Search content'
        size='small'
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && apply()}
      />
      <Box sx={{ width: 200 }}>
        <Typography variant='caption'>Min. Macedonian score: {minConfidence.toFixed(2)}</Typography>
        <Slider
          size='small'
          min={0}
          max={1}
          step={0.05}
          value={minConfidence}
          onChange={(_, value) => setMinConfidence(value as number)}
        />
      </Box>
      <FormControl size='small' sx={{ minWidth: 120 }}>
        <InputLabel>Batch assignment</InputLabel>
        <Select label='Batch assignment' value={donated} onChange={(e) => setDonated(e.target.value)}>
          <MenuItem value='ALL'>All</MenuItem>
          <MenuItem value='YES'>Assigned</MenuItem>
          <MenuItem value='NO'>Unassigned</MenuItem>
        </Select>
      </FormControl>
      <Button variant='contained' onClick={apply}>Apply</Button>
      <Button onClick={reset}>Reset</Button>
    </Box>
  );
};

export default PostFilters;
