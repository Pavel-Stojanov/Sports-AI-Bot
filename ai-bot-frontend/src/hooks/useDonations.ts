import { useContext } from 'react';
import DonationsContext from '../contexts/donationsContext.ts';

const useDonations = () => {
  const context = useContext(DonationsContext);
  if (!context) throw new Error('useDonations requires DonationsProvider.');
  return context;
};

export default useDonations;
