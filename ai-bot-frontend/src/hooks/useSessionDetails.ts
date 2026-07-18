import { useCallback, useEffect, useState } from 'react';
import type { BotActionLogResponse, SessionResponse } from '../api/types/session.ts';
import sessionApi from '../api/sessionApi.ts';
import useSnackbar from './useSnackbar.ts';

const POLL_INTERVAL_MS = 3000;

const useSessionDetails = (id: string) => {
  const { showSnackbar } = useSnackbar();

  const [session, setSession] = useState<SessionResponse | null>(null);
  const [logs, setLogs] = useState<BotActionLogResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const fetch = useCallback(async (silent: boolean = false) => {
    if (!silent) setLoading(true);
    try {
      const [sessionResponse, logsResponse] = await Promise.all([
        sessionApi.findById(id),
        sessionApi.findLogs(id)
      ]);
      setSession(sessionResponse.data);
      setLogs(logsResponse.data);
    } catch (err) {
      if (!silent) {
        showSnackbar(err instanceof Error ? err.message : 'Failed to load session.', 'error');
      }
    } finally {
      if (!silent) setLoading(false);
    }
  }, [id, showSnackbar]);

  useEffect(() => {
    void fetch();
  }, [fetch]);

  useEffect(() => {
    if (session?.status !== 'RUNNING') return;
    const timer = setInterval(() => void fetch(true), POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [session?.status, fetch]);

  return { session, logs, loading };
};

export default useSessionDetails;
