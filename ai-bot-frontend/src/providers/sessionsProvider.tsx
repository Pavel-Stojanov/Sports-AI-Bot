import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import sessionApi from '../api/sessionApi.ts';
import type { CreateSessionRequest, SessionResponse } from '../api/types/session.ts';
import useSnackbar from '../hooks/useSnackbar.ts';
import extractErrorMessage from '../api/extractErrorMessage.ts';
import SessionsContext from '../contexts/sessionsContext.ts';
import useLatestRequest from '../hooks/useLatestRequest.ts';

const POLL_INTERVAL_MS = 3000;

const SessionsProvider = ({ children }: { children: ReactNode }) => {
  const { showSnackbar } = useSnackbar();

  // Null until the first answer arrives; that is the only time the page shows a spinner.
  const [sessions, setSessions] = useState<SessionResponse[] | null>(null);
  const { run, cancel } = useLatestRequest();

  const fetch = useCallback((silent = false) =>
    run(() => sessionApi.findAll(), silent).then((result) => {
      if (result.status === 'stale') return;
      if (result.status === 'error') {
        showSnackbar(extractErrorMessage(result.error, 'Failed to load sessions.'), 'error');
        setSessions((current) => current ?? []);
      } else {
        setSessions(result.value.data);
      }
    }), [run, showSnackbar]);

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
    return cancel;
  }, [fetch, cancel]);

  const hasRunningSession = sessions?.some((session) => session.status === 'RUNNING') ?? false;
  useEffect(() => {
    if (!hasRunningSession) return;
    const timer = setInterval(() => void fetch(true), POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [hasRunningSession, fetch]);

  const value = useMemo(
    () => ({ sessions: sessions ?? [], loading: sessions === null, onCreate, onStart, onStop }),
    [sessions, onCreate, onStart, onStop]
  );

  return <SessionsContext value={value}>{children}</SessionsContext>;
};

export default SessionsProvider;
