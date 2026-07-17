import { Box, Button, CircularProgress, Grid, Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useState } from 'react';
import useDonations from '../../../../hooks/useDonations.ts';
import DonationBatchCard from '../../../components/donation/DonationBatchCard/DonationBatchCard.tsx';
import SubmitDonationDialog from '../../../components/donation/SubmitDonationDialog/SubmitDonationDialog.tsx';

/**
 * The donation dashboard towards doniraj.vezilka.ai: batches are created from
 * undonated posts (SubmitDonationDialog), then approved and submitted from
 * their DonationBatchCard via useDonations.
 */
const DonationsPage = () => {
  const { donations, loading, onCreate, onApprove, onSubmit } = useDonations();

  const [newBatchDialogOpen, setNewBatchDialogOpen] = useState<boolean>(false);

  return (
    <Box>
      {loading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
          <CircularProgress/>
        </Box>
      )}
      {!loading &&
       <>
         <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
           <Typography variant='h5'>Donations</Typography>
           <Button variant='contained' startIcon={<AddIcon/>} onClick={() => setNewBatchDialogOpen(true)}>
             New Batch
           </Button>
         </Box>
         {donations.length === 0 && (
           <Typography color='text.secondary'>
             No donation batches yet. Group extracted posts into a batch and
             donate them to doniraj.vezilka.ai.
           </Typography>
         )}
         <Grid container spacing={2}>
           {donations.map((batch) => (
             <Grid key={batch.id} size={{ xs: 12, sm: 6, md: 4 }}>
               <DonationBatchCard batch={batch} onApprove={onApprove} onSubmit={onSubmit}/>
             </Grid>
           ))}
         </Grid>
         <SubmitDonationDialog
           open={newBatchDialogOpen}
           onClose={() => setNewBatchDialogOpen(false)}
           onCreate={onCreate}
         />
       </>}
    </Box>
  );
};

export default DonationsPage;
