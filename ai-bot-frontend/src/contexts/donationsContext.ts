import { createContext } from 'react';
import type { CreateDonationBatchRequest, DonationBatchResponse } from '../api/types/donation.ts';

export interface DonationsContextType {
  donations: DonationBatchResponse[];
  loading: boolean;
  busy: boolean;
  onCreate: (data: CreateDonationBatchRequest) => Promise<boolean>;
  onApprove: (id: number) => Promise<boolean>;
  onSubmit: (id: number) => Promise<boolean>;
}

const DonationsContext = createContext<DonationsContextType | null>(null);
export default DonationsContext;
