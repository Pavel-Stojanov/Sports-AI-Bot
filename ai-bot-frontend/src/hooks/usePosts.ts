import { useContext } from 'react';
import PostsContext from '../contexts/postsContext.ts';

const usePosts = () => {
  const context = useContext(PostsContext);
  if (!context) throw new Error('usePosts requires PostsProvider.');
  return context;
};

export default usePosts;
