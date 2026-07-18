import { Grid } from '@mui/material';
import type { PostResponse } from '../../../../api/types/post.ts';
import PostCard from '../PostCard/PostCard.tsx';

interface PostGridProps {
  posts: PostResponse[];
  onDelete?: (id: number) => void;
}

const PostGrid = ({ posts, onDelete }: PostGridProps) => {
  return (
    <Grid container spacing={2}>
      {posts.map((post) => (
        <Grid key={post.id} size={{ xs: 12, sm: 6, md: 4 }}>
          <PostCard post={post} onDelete={onDelete}/>
        </Grid>
      ))}
    </Grid>
  );
};

export default PostGrid;
