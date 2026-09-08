import { useCallback, useRef } from 'react';

export type RequestResult<T> =
  | { status: 'ok'; value: T }
  | { status: 'error'; error: unknown }
  | { status: 'stale' };

/**
 * Keeps only the newest response of one request family. A poll is skipped while a
 * request is in flight, and a response that arrives after a newer request started is
 * reported as stale so it cannot overwrite the newer state.
 */
const useLatestRequest = () => {
  const requestNumber = useRef(0);
  const inFlight = useRef(false);

  const cancel = useCallback(() => {
    requestNumber.current++;
    inFlight.current = false;
  }, []);

  const run = useCallback(async <T>(load: () => Promise<T>, poll = false): Promise<RequestResult<T>> => {
    if (poll && inFlight.current) return { status: 'stale' };
    const request = ++requestNumber.current;
    inFlight.current = true;
    try {
      const value = await load();
      return request === requestNumber.current ? { status: 'ok', value } : { status: 'stale' };
    } catch (error) {
      return request === requestNumber.current ? { status: 'error', error } : { status: 'stale' };
    } finally {
      if (request === requestNumber.current) inFlight.current = false;
    }
  }, []);

  return { run, cancel };
};

export default useLatestRequest;
