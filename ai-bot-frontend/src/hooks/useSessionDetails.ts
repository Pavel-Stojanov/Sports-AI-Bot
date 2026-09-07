import { useCallback, useEffect, useRef, useState } from 'react';
import type { BotActionLogResponse, SessionResponse } from '../api/types/session.ts';
import sessionApi from '../api/sessionApi.ts';
import useSnackbar from './useSnackbar.ts';
import extractErrorMessage from '../api/extractErrorMessage.ts';

const POLL_INTERVAL_MS = 3000;

const useSessionDetails = (id: string) => {
  const { showSnackbar } = useSnackbar();

  const [session, setSession] = useState<SessionResponse | null>(null);
  const [logs, setLogs] = useState<BotActionLogResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const requestNumber = useRef(0);
  const inFlight = useRef(false);
  const cancelRequests = useCallback(() => { requestNumber.current++; }, []);

  const fetch = useCallback(async (silent: boolean = false) => {
    // A slow response must not be replaced by the next poll before it arrives,
    // and an older response must not overwrite the status read after Start or Stop.
    if (silent && inFlight.current) return;
    const request = ++requestNumber.current;
    inFlight.current = true;
    if (!silent) setLoading(true);
    try {
      const [sessionResponse, logsResponse] = await Promise.all([
        sessionApi.findById(id),
        sessionApi.findLogs(id)
      ]);
      if (request !== requestNumber.current) return;
      setSession(sessionResponse.data);
      setLogs(logsResponse.data);
    } catch (err) {
      if (!silent && request === requestNumber.current) {
        showSnackbar(extractErrorMessage(err, 'Failed to load session.'), 'error');
      }
    } finally {
      if (request === requestNumber.current) {
        inFlight.current = false;
        if (!silent) setLoading(false);
      }
    }
  }, [id, showSnackbar]);

  useEffect(() => {
    void fetch();
    return cancelRequests;
  }, [fetch, cancelRequests]);

  useEffect(() => {
    if (session?.status !== 'RUNNING') return;
    const timer = setInterval(() => void fetch(true), POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [session?.status, fetch]);

  return { session, logs, loading, refresh: fetch };
};

export default useSessionDetails;
