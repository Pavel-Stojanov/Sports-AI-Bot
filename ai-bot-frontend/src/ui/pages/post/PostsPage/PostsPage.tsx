import { Box, CircularProgress, Pagination, Typography } from '@mui/material';
import usePosts from '../../../../hooks/usePosts.ts';
import PostFilters from '../../../components/post/PostFilters/PostFilters.tsx';
import PostGrid from '../../../components/post/PostGrid/PostGrid.tsx';

const PostsPage = () => {
  const { posts, loading, onDelete, filter, page, setPage, onFilterChange } = usePosts();

  return (
    <Box>
      <Typography variant='h5' sx={{ mb: 2 }}>Extracted Posts</Typography>
      <PostFilters filter={filter} onChange={onFilterChange}/>
      {loading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
          <CircularProgress/>
        </Box>
      )}
      {!loading && (!posts || posts.content.length === 0) && (
        <Typography color='text.secondary'>
          No posts match these filters. Clear the filters or run an extraction session.
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
