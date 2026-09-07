export type DonationStatus =
  | 'DRAFT'
  | 'APPROVED'
  | 'SUBMITTED'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'FAILED';

export interface CreateDonationBatchRequest {
  postIds: number[];
}

export interface DonationBatchResponse {
  id: number;
  status: DonationStatus;
  vezilkaReference: string | null;
  submittedAt: string | null;
  createdAt: string;
  postIds: number[];
  acceptedCount: number;
  rejectedCount: number;
  pendingCount: number;
  nextRetryAt: string | null;
}
