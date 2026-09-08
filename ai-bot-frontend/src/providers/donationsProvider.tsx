import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import type { CreateDonationBatchRequest, DonationBatchResponse } from '../api/types/donation.ts';
import donationApi from '../api/donationApi.ts';
import useSnackbar from '../hooks/useSnackbar.ts';
import extractErrorMessage from '../api/extractErrorMessage.ts';
import DonationsContext from '../contexts/donationsContext.ts';

const DonationsProvider = ({ children }: { children: ReactNode }) => {
  const { showSnackbar } = useSnackbar();
  const [donations, setDonations] = useState<DonationBatchResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const requestNumber = useRef(0);
  const inFlight = useRef(false);
  const cancelRequests = useCallback(() => { requestNumber.current++; }, []);

  const load = useCallback(async (poll: boolean) => {
    // A slow response must not be replaced by the next poll before it arrives.
    if (poll && inFlight.current) return;
    const request = ++requestNumber.current;
    inFlight.current = true;
    try {
      const response = await donationApi.findAll();
      if (request === requestNumber.current) setDonations(response.data);
    } catch (err) {
      if (request === requestNumber.current) showSnackbar(extractErrorMessage(err, 'Failed to load donations.'), 'error');
    } finally {
      if (request === requestNumber.current) {
        inFlight.current = false;
        setLoading(false);
      }
    }
  }, [showSnackbar]);
  const refresh = useCallback(() => load(false), [load]);

  useEffect(() => {
    void refresh();
    const timer = setInterval(() => void load(true), 5000);
    return () => { clearInterval(timer); cancelRequests(); };
  }, [refresh, load, cancelRequests]);

  const mutate = useCallback(async (action: () => Promise<unknown>, fallback: string) => {
    if (busyRef.current) return false;
    busyRef.current = true;
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
      busyRef.current = false;
      setBusy(false);
    }
  }, [refresh, showSnackbar]);

  const onCreate = useCallback((data: CreateDonationBatchRequest) =>
    mutate(() => donationApi.add(data), 'Failed to create donation batch.'), [mutate]);
  const onApprove = useCallback((id: number) =>
    mutate(() => donationApi.approve(id.toString()), 'Failed to approve batch.'), [mutate]);
  const onSubmit = useCallback((id: number) =>
    mutate(() => donationApi.submit(id.toString()), 'Failed to submit batch to Vezilka.'), [mutate]);

  const value = useMemo(() => ({ donations, loading, busy, onCreate, onApprove, onSubmit, refresh }),
    [donations, loading, busy, onCreate, onApprove, onSubmit, refresh]);
  return <DonationsContext value={value}>{children}</DonationsContext>;
};

export default DonationsProvider;
