import { Box, CircularProgress, Pagination, Typography } from '@mui/material';
import { useState } from 'react';
import type { PostFilter } from '../../../../api/types/post.ts';
import usePosts from '../../../../hooks/usePosts.ts';
import PostFilters from '../../../components/post/PostFilters/PostFilters.tsx';
import PostGrid from '../../../components/post/PostGrid/PostGrid.tsx';

const PostsPage = () => {
  const [filter, setFilter] = useState<PostFilter>({});
  const [page, setPage] = useState<number>(0);

  const { posts, loading, onDelete } = usePosts(filter, page, 12);

  const handleFilterChange = (next: PostFilter) => {
    setPage(0);
    setFilter(next);
  };

  return (
    <Box>
      <Typography variant='h5' sx={{ mb: 2 }}>Extracted Posts</Typography>
      <PostFilters filter={filter} onChange={handleFilterChange}/>
      {loading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
          <CircularProgress/>
        </Box>
      )}
      {!loading && (!posts || posts.content.length === 0) && (
        <Typography color='text.secondary'>
          No extracted posts yet. Run an extraction session first.
        </Typography>
      )}
      {!loading && posts && (
        <>
          <PostGrid posts={posts.content} onDelete={onDelete}/>
          {posts.totalPages > 1 && (
            <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
              <Pagination
                count={posts.totalPages}
                page={page + 1}
                onChange={(_, value) => setPage(value - 1)}
              />
            </Box>
          )}
        </>
      )}
    </Box>
  );
};

export default PostsPage;
