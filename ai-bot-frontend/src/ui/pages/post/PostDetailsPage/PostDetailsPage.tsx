import { Box, Chip, CircularProgress, Link, Paper, Stack, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import { useParams } from 'react-router';
import type { PostResponse } from '../../../../api/types/post.ts';
import postApi from '../../../../api/postApi.ts';
import extractErrorMessage from '../../../../api/extractErrorMessage.ts';
import useSnackbar from '../../../../hooks/useSnackbar.ts';

const PostDetailsPage = () => {
  const { id } = useParams<{ id: string }>();
  const { showSnackbar } = useSnackbar();

  const [post, setPost] = useState<PostResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    const fetch = async () => {
      try {
        const response = await postApi.findById(id!);
        setPost(response.data);
      } catch (err) {
        showSnackbar(extractErrorMessage(err, 'Failed to load post.'), 'error');
      } finally {
        setLoading(false);
      }
    };
    void fetch();
  }, [id, showSnackbar]);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
        <CircularProgress/>
      </Box>
    );
  }
  if (!post) {
    return <Typography color='text.secondary'>Post #{id} was not found.</Typography>;
  }

  return (
    <Box>
      <Typography variant='h5' gutterBottom>Post #{post.id} — {post.authorHandle}</Typography>
      <Stack direction='row' spacing={1} sx={{ mb: 2 }}>
        <Chip size='small' label={post.socialNetwork}/>
        <Chip
          size='small'
          color={post.macedonianConfidence !== null && post.macedonianConfidence >= 0.8 ? 'success' : 'default'}
          label={`MK score: ${post.macedonianConfidence !== null ? post.macedonianConfidence.toFixed(2) : '?'}`}
        />
        {post.donationBatchId !== null && (
          <Chip size='small' color='info' label={`Assigned to batch #${post.donationBatchId}`}/>
        )}
        {post.donationStatus && <Chip size='small'
          color={post.donationStatus === 'ACCEPTED' ? 'success' : 'error'} label={post.donationStatus}/>}
      </Stack>
      {post.rejectionReason && <Typography color='error' sx={{ mb: 2 }}>Vezilka rejected this post: {post.rejectionReason}</Typography>}
      {post.vezilkaId && <Typography variant='body2' sx={{ mb: 2 }}>Vezilka ID: {post.vezilkaId}</Typography>}
      {post.summary && (
        <Paper variant='outlined' sx={{ p: 2, mb: 2 }}>
          <Typography variant='subtitle2'>AI-generated summary</Typography>
          <Typography variant='body1' sx={{ fontStyle: 'italic' }}>{post.summary}</Typography>
        </Paper>
      )}
      <Typography variant='body1' sx={{ whiteSpace: 'pre-wrap', mb: 2 }}>{post.content}</Typography>
      {post.mediaItems.length > 0 && (
        <Stack direction='row' spacing={1} sx={{ mb: 2, flexWrap: 'wrap' }}>
          {post.mediaItems.map((media) => (
            <Box
              key={media.id}
              component='img'
              src={media.sourceUrl}
              alt={`Media #${media.id}`}
              sx={{ maxWidth: 320, maxHeight: 200, borderRadius: 1, objectFit: 'cover' }}
            />
          ))}
        </Stack>
      )}
      {post.sourceUrl && (
        <Link href={post.sourceUrl} target='_blank' rel='noopener'>Source: {post.sourceUrl}</Link>
      )}
      {post.postedAt && (
        <Typography variant='caption' sx={{ display: 'block', mt: 1 }}>
          Posted at: {new Date(post.postedAt).toLocaleString()}
        </Typography>
      )}
    </Box>
  );
};

export default PostDetailsPage;
