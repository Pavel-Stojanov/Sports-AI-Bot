import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { PageResponse, PostFilter, PostResponse } from '../api/types/post.ts';
import postApi from '../api/postApi.ts';
import useSnackbar from '../hooks/useSnackbar.ts';
import extractErrorMessage from '../api/extractErrorMessage.ts';
import PostsContext from '../contexts/postsContext.ts';
import useLatestRequest from '../hooks/useLatestRequest.ts';

const PostsProvider = ({ children }: { children: ReactNode }) => {
  const [filter, setFilter] = useState<PostFilter>({});
  const [page, setPage] = useState(0);
  const { run, cancel } = useLatestRequest();
  const onFilterChange = useCallback((next: PostFilter) => {
    setFilter(next);
    setPage(0);
  }, []);
  const { showSnackbar } = useSnackbar();

  const [posts, setPosts] = useState<PageResponse<PostResponse> | null>(null);
  // The request key that last answered. Loading is derived, so no state changes before the request starts.
  const [answeredKey, setAnsweredKey] = useState<string | null>(null);
  const requestKey = JSON.stringify([filter, page]);

  const fetch = useCallback(() =>
    run(() => postApi.findAll(filter, page, 12)).then((result) => {
      if (result.status === 'stale') return;
      if (result.status === 'error') {
        showSnackbar(extractErrorMessage(result.error, 'Failed to load posts.'), 'error');
      } else {
        setPosts(result.value.data);
        if (page > 0 && page >= result.value.data.totalPages) setPage(Math.max(0, result.value.data.totalPages - 1));
      }
      setAnsweredKey(JSON.stringify([filter, page]));
    }), [filter, page, run, showSnackbar]);

  useEffect(() => {
    void fetch();
    return cancel;
  }, [fetch, cancel]);

  const onDelete = useCallback(async (id: number) => {
    try {
      await postApi.delete(id.toString());
      await fetch();
    } catch (err) {
      showSnackbar(extractErrorMessage(err, 'Failed to delete post.'), 'error');
    }
  }, [fetch, showSnackbar]);

  const loading = answeredKey !== requestKey;
  const value = useMemo(() => ({ posts, loading, filter, page, setPage, onFilterChange, onDelete }),
    [posts, loading, filter, page, onFilterChange, onDelete]);
  return <PostsContext value={value}>{children}</PostsContext>;
};

export default PostsProvider;
