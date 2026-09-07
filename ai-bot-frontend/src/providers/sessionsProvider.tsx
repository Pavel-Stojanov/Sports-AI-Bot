import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import * as React from 'react';
import sessionApi from '../api/sessionApi.ts';
import type { CreateSessionRequest, SessionResponse } from '../api/types/session.ts';
import SessionsContext from '../contexts/sessionsContext.ts';
import useSnackbar from '../hooks/useSnackbar.ts';
import extractErrorMessage from '../api/extractErrorMessage.ts';

/**
 * Fully provided as the reference example of the provider pattern used in
 * this template — mirror it when you build the posts and donations features.
 * Errors from the backend are shown as snackbars through extractErrorMessage.
 */
const SessionsProvider = ({ children }: { children: React.ReactNode }) => {
  const { showSnackbar } = useSnackbar();

  const [sessions, setSessions] = useState<SessionResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const requestNumber = useRef(0);
  const inFlight = useRef(false);
  const cancelRequests = useCallback(() => { requestNumber.current++; }, []);

  const fetch = useCallback(async (silent = false) => {
    // A slow response must not be replaced by the next poll before it arrives.
    if (silent && inFlight.current) return;
    const request = ++requestNumber.current;
    inFlight.current = true;
    if (!silent) setLoading(true);

    try {
      const response = await sessionApi.findAll();
      if (request === requestNumber.current) setSessions(response.data);
    } catch (err) {
      if (request === requestNumber.current) showSnackbar(extractErrorMessage(err, 'Failed to load sessions.'), 'error');
    } finally {
      if (request === requestNumber.current) {
        inFlight.current = false;
        setLoading(false);
      }
    }
  }, [showSnackbar]);

  const onCreate = useCallback(async (data: CreateSessionRequest) => {
    try {
      await sessionApi.add(data);
      await fetch();
      return true;
    } catch (err) {
      showSnackbar(extractErrorMessage(err, 'Failed to create session.'), 'error');
      return false;
    }
  }, [fetch, showSnackbar]);

  const onStart = useCallback(async (id: number) => {
    try {
      await sessionApi.start(id.toString());
      await fetch();
    } catch (err) {
      showSnackbar(extractErrorMessage(err, 'Failed to start session.'), 'error');
    }
  }, [fetch, showSnackbar]);

  const onStop = useCallback(async (id: number) => {
    try {
      await sessionApi.stop(id.toString());
      await fetch();
    } catch (err) {
      showSnackbar(extractErrorMessage(err, 'Failed to stop session.'), 'error');
    }
  }, [fetch, showSnackbar]);

  useEffect(() => {
    void fetch();
    return cancelRequests;
  }, [fetch, cancelRequests]);

  const hasRunningSession = sessions.some((session) => session.status === 'RUNNING');
  useEffect(() => {
    if (!hasRunningSession) return;
    const timer = setInterval(() => void fetch(true), 3000);
    return () => clearInterval(timer);
  }, [hasRunningSession, fetch]);

  const value = useMemo(
    () => ({ sessions, loading, onCreate, onStart, onStop }),
    [sessions, loading, onCreate, onStart, onStop]
  );

  return <SessionsContext value={value}>{children}</SessionsContext>;
};

export default SessionsProvider;
