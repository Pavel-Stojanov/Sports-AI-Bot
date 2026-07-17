import { Button, Card, CardActions, CardContent, Chip, Stack, Typography } from '@mui/material';
import ThumbUpIcon from '@mui/icons-material/ThumbUp';
import SendIcon from '@mui/icons-material/Send';
import type { DonationBatchResponse, DonationStatus } from '../../../../api/types/donation.ts';

interface DonationBatchCardProps {
  batch: DonationBatchResponse;
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

const DonationBatchCard = ({ batch, onApprove, onSubmit }: DonationBatchCardProps) => {
  return (
    <Card>
      <CardContent>
        <Typography variant='h6'>Batch #{batch.id}</Typography>
        <Stack direction='row' spacing={1} sx={{ my: 1 }}>
          <Chip size='small' color={STATUS_COLOR[batch.status]} label={batch.status}/>
          <Chip size='small' variant='outlined' label={`${batch.postIds.length} post(s)`}/>
        </Stack>
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
          disabled={batch.status !== 'DRAFT'}
          onClick={() => onApprove(batch.id)}
        >
          Approve
        </Button>
        <Button
          startIcon={<SendIcon/>}
          disabled={batch.status !== 'APPROVED'}
          onClick={() => onSubmit(batch.id)}
        >
          Submit to Vezilka
        </Button>
      </CardActions>
    </Card>
  );
};

export default DonationBatchCard;
