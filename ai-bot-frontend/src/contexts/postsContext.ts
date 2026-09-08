import { createContext } from 'react';
import type { PageResponse, PostFilter, PostResponse } from '../api/types/post.ts';

export interface PostsContextType {
  posts: PageResponse<PostResponse> | null;
  loading: boolean;
  filter: PostFilter;
  page: number;
  setPage: (page: number) => void;
  onFilterChange: (filter: PostFilter) => void;
  onDelete: (id: number) => Promise<void>;
}

const PostsContext = createContext<PostsContextType | null>(null);
export default PostsContext;
