import { createContext } from 'react';
import type { CreateSessionRequest, SessionResponse } from '../api/types/session.ts';

export interface SessionsContextType {
  sessions: SessionResponse[];
  loading: boolean;
  onCreate: (data: CreateSessionRequest) => Promise<boolean>;
  onStart: (id: number) => Promise<void>;
  onStop: (id: number) => Promise<void>;
}

const SessionsContext = createContext<SessionsContextType>({} as SessionsContextType);

export default SessionsContext;
