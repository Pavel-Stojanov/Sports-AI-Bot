import { Button, Card, CardActions, CardContent, Chip, Stack, Typography } from '@mui/material';
import ThumbUpIcon from '@mui/icons-material/ThumbUp';
import SendIcon from '@mui/icons-material/Send';
import type { DonationBatchResponse, DonationStatus } from '../../../../api/types/donation.ts';
import useNow from '../../../../hooks/useNow.ts';

interface DonationBatchCardProps {
  batch: DonationBatchResponse;
  busy: boolean;
  onApprove: (id: number) => void;
  onSubmit: (id: number) => void;
}

const STATUS_COLOR: Record<DonationStatus, 'default' | 'info' | 'warning' | 'success' | 'error'> = {
  DRAFT: 'default',
  APPROVED: 'info',
  SUBMITTED: 'warning',
  ACCEPTED: 'success',
  REJECTED: 'error',
  FAILED: 'error'
};

const DonationBatchCard = ({ batch, onApprove, onSubmit, busy }: DonationBatchCardProps) => {
  const now = useNow();
  const retryTime = batch.nextRetryAt ? new Date(batch.nextRetryAt) : null;
  // The backend refuses a submission before the retry time, so the button follows the same rule.
  const waitingForRetry = retryTime !== null && retryTime.getTime() > now;
  const submittable = batch.status === 'APPROVED' || batch.status === 'SUBMITTED' || batch.status === 'FAILED';
  return (
    <Card>
      <CardContent>
        <Typography variant='h6'>Batch #{batch.id}</Typography>
        <Stack direction='row' spacing={1} sx={{ my: 1 }}>
          <Chip size='small' color={STATUS_COLOR[batch.status]} label={batch.status}/>
          <Chip size='small' variant='outlined' label={`${batch.postIds.length} post(s)`}/>
        </Stack>
        <Typography variant='body2'>
          {batch.acceptedCount} accepted, {batch.rejectedCount} rejected, {batch.pendingCount} pending
        </Typography>
        {retryTime && (
          <Typography variant='body2'>Retry after {retryTime.toLocaleString()}</Typography>
        )}
        {batch.status === 'SUBMITTED' && (
          <Typography variant='body2'>Unsent posts will retry automatically after the delay.</Typography>
        )}
        {batch.status === 'FAILED' && (
          <Typography variant='body2'>Automatic retries stopped after {batch.attemptCount} attempt(s). Retry by hand once the cause is fixed.</Typography>
        )}
        {batch.lastError && (
          <Typography variant='body2' color='error'>Last error: {batch.lastError}</Typography>
        )}
        {batch.vezilkaReference && (
          <Typography variant='body2'>Vezilka reference: {batch.vezilkaReference}</Typography>
        )}
        <Typography variant='caption' color='text.secondary'>
          Created {new Date(batch.createdAt).toLocaleString()}
          {batch.submittedAt ? ` · submitted ${new Date(batch.submittedAt).toLocaleString()}` : ''}
        </Typography>
      </CardContent>
      <CardActions>
        <Button
          startIcon={<ThumbUpIcon/>}
          disabled={busy || batch.status !== 'DRAFT'}
          onClick={() => onApprove(batch.id)}
        >
          Approve
        </Button>
        <Button
          startIcon={<SendIcon/>}
          disabled={busy || !submittable || waitingForRetry}
          onClick={() => onSubmit(batch.id)}
        >
          {batch.status === 'APPROVED' ? 'Submit to Vezilka' : 'Retry pending posts'}
        </Button>
      </CardActions>
    </Card>
  );
};

export default DonationBatchCard;
