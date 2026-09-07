import { useCallback, useEffect, useState } from 'react';
import type { PageResponse, PostFilter, PostResponse } from '../api/types/post.ts';
import postApi from '../api/postApi.ts';
import useSnackbar from './useSnackbar.ts';
import extractErrorMessage from '../api/extractErrorMessage.ts';

const usePosts = (filter: PostFilter, page: number, size: number) => {
  const { showSnackbar } = useSnackbar();

  const [posts, setPosts] = useState<PageResponse<PostResponse> | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  const fetch = useCallback(async () => {
    setLoading(true);
    try {
      const response = await postApi.findAll(filter, page, size);
      setPosts(response.data);
    } catch (err) {
      showSnackbar(extractErrorMessage(err, 'Failed to load posts.'), 'error');
    } finally {
      setLoading(false);
    }
  }, [filter, page, size, showSnackbar]);

  useEffect(() => {
    void fetch();
  }, [fetch]);

  const onDelete = useCallback(async (id: number) => {
    try {
      await postApi.delete(id.toString());
      await fetch();
    } catch (err) {
      showSnackbar(extractErrorMessage(err, 'Failed to delete post.'), 'error');
    }
  }, [fetch, showSnackbar]);

  return { posts, loading, onDelete };
};

export default usePosts;
