import { useCallback, useEffect, useState } from 'react';
import type { CreateDonationBatchRequest, DonationBatchResponse } from '../api/types/donation.ts';
import donationApi from '../api/donationApi.ts';
import useSnackbar from './useSnackbar.ts';
import extractErrorMessage from '../api/extractErrorMessage.ts';

const useDonations = () => {
  const { showSnackbar } = useSnackbar();

  const [donations, setDonations] = useState<DonationBatchResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const fetch = useCallback(async () => {
    setLoading(true);
    try {
      const response = await donationApi.findAll();
      setDonations(response.data);
    } catch (err) {
      showSnackbar(extractErrorMessage(err, 'Failed to load donations.'), 'error');
    } finally {
      setLoading(false);
    }
  }, [showSnackbar]);

  useEffect(() => {
    void fetch();
  }, [fetch]);

  const onCreate = useCallback(async (data: CreateDonationBatchRequest) => {
    try {
      await donationApi.add(data);
      await fetch();
    } catch (err) {
      showSnackbar(extractErrorMessage(err, 'Failed to create donation batch.'), 'error');
    }
  }, [fetch, showSnackbar]);

  const onApprove = useCallback(async (id: number) => {
    try {
      await donationApi.approve(id.toString());
      await fetch();
    } catch (err) {
      showSnackbar(extractErrorMessage(err, 'Failed to approve batch.'), 'error');
    }
  }, [fetch, showSnackbar]);

  const onSubmit = useCallback(async (id: number) => {
    try {
      await donationApi.submit(id.toString());
      await fetch();
    } catch (err) {
      showSnackbar(extractErrorMessage(err, 'Failed to submit batch to Vezilka.'), 'error');
    }
  }, [fetch, showSnackbar]);

  return { donations, loading, onCreate, onApprove, onSubmit };
};

export default useDonations;
