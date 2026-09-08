import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { CreateDonationBatchRequest, DonationBatchResponse } from '../api/types/donation.ts';
import donationApi from '../api/donationApi.ts';
import useSnackbar from '../hooks/useSnackbar.ts';
import extractErrorMessage from '../api/extractErrorMessage.ts';
import DonationsContext from '../contexts/donationsContext.ts';
import useLatestRequest from '../hooks/useLatestRequest.ts';

const POLL_INTERVAL_MS = 5000;

const DonationsProvider = ({ children }: { children: ReactNode }) => {
  const { showSnackbar } = useSnackbar();
  const [donations, setDonations] = useState<DonationBatchResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const { run, cancel } = useLatestRequest();

  const load = useCallback(async (poll: boolean) => {
    const result = await run(() => donationApi.findAll(), poll);
    if (result.status === 'stale') return;
    if (result.status === 'error') showSnackbar(extractErrorMessage(result.error, 'Failed to load donations.'), 'error');
    else setDonations(result.value.data);
    setLoading(false);
  }, [run, showSnackbar]);
  const refresh = useCallback(() => load(false), [load]);

  useEffect(() => {
    void refresh();
    return cancel;
  }, [refresh, cancel]);

  // Only a SUBMITTED batch still has work in flight, so only then is there something to poll for.
  const hasPendingBatch = donations.some((batch) => batch.status === 'SUBMITTED');
  useEffect(() => {
    if (!hasPendingBatch) return;
    const timer = setInterval(() => void load(true), POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [hasPendingBatch, load]);

  const mutate = useCallback(async (action: () => Promise<unknown>, fallback: string) => {
    if (busy) return false;
    setBusy(true);
    try {
      await action();
      await refresh();
      return true;
    } catch (err) {
      showSnackbar(extractErrorMessage(err, fallback), 'error');
      await refresh();
      return false;
    } finally {
      setBusy(false);
    }
  }, [busy, refresh, showSnackbar]);

  const onCreate = useCallback((data: CreateDonationBatchRequest) =>
    mutate(() => donationApi.add(data), 'Failed to create donation batch.'), [mutate]);
  const onApprove = useCallback((id: number) =>
    mutate(() => donationApi.approve(id.toString()), 'Failed to approve batch.'), [mutate]);
  const onSubmit = useCallback((id: number) =>
    mutate(() => donationApi.submit(id.toString()), 'Failed to submit batch to Vezilka.'), [mutate]);

  const value = useMemo(() => ({ donations, loading, busy, onCreate, onApprove, onSubmit }),
    [donations, loading, busy, onCreate, onApprove, onSubmit]);
  return <DonationsContext value={value}>{children}</DonationsContext>;
};

export default DonationsProvider;
