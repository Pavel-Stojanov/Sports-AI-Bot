import { useSyncExternalStore } from 'react';

const TICK_MS = 1000;

const subscribe = (onChange: () => void) => {
  const timer = setInterval(onChange, TICK_MS);
  return () => clearInterval(timer);
};
const getSnapshot = () => Math.floor(Date.now() / TICK_MS) * TICK_MS;

/** The current time in milliseconds, rounded to the second, re-rendering once a second. */
const useNow = () => useSyncExternalStore(subscribe, getSnapshot, getSnapshot);

export default useNow;
