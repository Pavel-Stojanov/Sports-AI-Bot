import { Button, Card, CardActions, CardContent, Chip, Link, Stack, Typography } from '@mui/material';
import InfoIcon from '@mui/icons-material/Info';
import DeleteIcon from '@mui/icons-material/Delete';
import { useNavigate } from 'react-router';
import type { PostResponse } from '../../../../api/types/post.ts';

interface PostCardProps {
  post: PostResponse;
  onDelete?: (id: number) => void;
}

const confidenceColor = (confidence: number | null): 'success' | 'warning' | 'default' => {
  if (confidence === null) return 'default';
  if (confidence >= 0.8) return 'success';
  if (confidence >= 0.5) return 'warning';
  return 'default';
};

const PostCard = ({ post, onDelete }: PostCardProps) => {
  const navigate = useNavigate();

  return (
    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <CardContent sx={{ flexGrow: 1 }}>
        <Typography variant='subtitle2'>{post.authorHandle ?? 'unknown author'}</Typography>
        {post.summary && (
          <Typography variant='body2' sx={{ fontStyle: 'italic', my: 1 }}>
            {post.summary}
          </Typography>
        )}
        <Typography variant='body2' color='text.secondary'>
          {post.content ? `${post.content.slice(0, 160)}${post.content.length > 160 ? '…' : ''}` : ''}
        </Typography>
        <Stack direction='row' spacing={1} sx={{ mt: 1, flexWrap: 'wrap' }}>
          <Chip
            size='small'
            color={confidenceColor(post.macedonianConfidence)}
            label={`MK ${post.macedonianConfidence !== null ? (post.macedonianConfidence * 100).toFixed(0) : '?'}%`}
          />
          {post.donationBatchId !== null && (
            <Chip size='small' color='info' label={`Donated · batch #${post.donationBatchId}`}/>
          )}
        </Stack>
        {post.sourceUrl && (
          <Link href={post.sourceUrl} target='_blank' rel='noopener' variant='caption'>
            {post.sourceUrl}
          </Link>
        )}
      </CardContent>
      <CardActions sx={{ justifyContent: 'space-between' }}>
        <Button startIcon={<InfoIcon/>} onClick={() => navigate(`/posts/${post.id}`)}>Info</Button>
        {onDelete && (
          <Button startIcon={<DeleteIcon/>} color='error' onClick={() => onDelete(post.id)}>Delete</Button>
        )}
      </CardActions>
    </Card>
  );
};

export default PostCard;
