import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { PageResponse, PostFilter, PostResponse } from '../api/types/post.ts';
import postApi from '../api/postApi.ts';
import useSnackbar from '../hooks/useSnackbar.ts';
import extractErrorMessage from '../api/extractErrorMessage.ts';

import type { ReactNode } from 'react';
import PostsContext from '../contexts/postsContext.ts';

const PostsProvider = ({ children }: { children: ReactNode }) => {
  const [filter, setFilter] = useState<PostFilter>({});
  const [page, setPage] = useState(0);
  const requestNumber = useRef(0);
  const cancelRequests = useCallback(() => { requestNumber.current++; }, []);
  const onFilterChange = useCallback((next: PostFilter) => {
    setFilter(next);
    setPage(0);
  }, []);
  const { showSnackbar } = useSnackbar();

  const [posts, setPosts] = useState<PageResponse<PostResponse> | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  const fetch = useCallback(async () => {
    const request = ++requestNumber.current;
    setLoading(true);
    try {
      const response = await postApi.findAll(filter, page, 12);
      if (request !== requestNumber.current) return;
      setPosts(response.data);
      if (page > 0 && page >= response.data.totalPages) setPage(Math.max(0, response.data.totalPages - 1));
    } catch (err) {
      if (request === requestNumber.current) showSnackbar(extractErrorMessage(err, 'Failed to load posts.'), 'error');
    } finally {
      if (request === requestNumber.current) setLoading(false);
    }
  }, [filter, page, showSnackbar]);

  useEffect(() => {
    void fetch();
    return cancelRequests;
  }, [fetch, cancelRequests]);

  const onDelete = useCallback(async (id: number) => {
    try {
      await postApi.delete(id.toString());
      await fetch();
    } catch (err) {
      showSnackbar(extractErrorMessage(err, 'Failed to delete post.'), 'error');
    }
  }, [fetch, showSnackbar]);

  const value = useMemo(() => ({ posts, loading, filter, page, setPage, onFilterChange, onDelete }),
    [posts, loading, filter, page, onFilterChange, onDelete]);
  return <PostsContext value={value}>{children}</PostsContext>;
};

export default PostsProvider;
