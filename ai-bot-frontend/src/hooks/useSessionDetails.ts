import { useCallback, useEffect, useState } from 'react';
import type { BotActionLogResponse, SessionResponse } from '../api/types/session.ts';
import sessionApi from '../api/sessionApi.ts';
import useSnackbar from './useSnackbar.ts';
import extractErrorMessage from '../api/extractErrorMessage.ts';
import useLatestRequest from './useLatestRequest.ts';

const POLL_INTERVAL_MS = 3000;

const useSessionDetails = (id: string) => {
  const { showSnackbar } = useSnackbar();

  const [session, setSession] = useState<SessionResponse | null>(null);
  const [logs, setLogs] = useState<BotActionLogResponse[]>([]);
  // The id that last answered. The spinner shows only until the current id has answered once.
  const [answeredId, setAnsweredId] = useState<string | null>(null);
  const { run, cancel } = useLatestRequest();

  /** A silent refresh never shows the error snackbar; polls and button clicks use it. */
  const fetch = useCallback((silent: boolean = false) =>
    run(() => Promise.all([sessionApi.findById(id), sessionApi.findLogs(id)]), silent).then((result) => {
      if (result.status === 'stale') return;
      if (result.status === 'error') {
        if (!silent) showSnackbar(extractErrorMessage(result.error, 'Failed to load session.'), 'error');
      } else {
        const [sessionResponse, logsResponse] = result.value;
        setSession(sessionResponse.data);
        setLogs(logsResponse.data);
      }
      setAnsweredId(id);
    }), [id, run, showSnackbar]);

  useEffect(() => {
    void fetch();
    return cancel;
  }, [fetch, cancel]);

  useEffect(() => {
    if (session?.status !== 'RUNNING') return;
    const timer = setInterval(() => void fetch(true), POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [session?.status, fetch]);

  return { session, logs, loading: answeredId !== id, refresh: fetch };
};

export default useSessionDetails;
