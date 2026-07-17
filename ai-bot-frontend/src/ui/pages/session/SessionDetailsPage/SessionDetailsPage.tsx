import { Box, Chip, CircularProgress, Stack, Typography } from '@mui/material';
import { useParams } from 'react-router';
import useSessionDetails from '../../../../hooks/useSessionDetails.ts';
import SessionLogViewer from '../../../components/session/SessionLogViewer/SessionLogViewer.tsx';

const SessionDetailsPage = () => {
  const { id } = useParams<{ id: string }>();
  const { session, logs, loading } = useSessionDetails(id!);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
        <CircularProgress/>
      </Box>
    );
  }
  if (!session) {
    return <Typography color='text.secondary'>Session #{id} was not found.</Typography>;
  }

  return (
    <Box>
      <Typography variant='h5' gutterBottom>
        Session #{session.id} — {session.socialNetwork}
      </Typography>
      <Stack direction='row' spacing={1} sx={{ mb: 1, flexWrap: 'wrap' }}>
        <Chip size='small' color={session.status === 'RUNNING' ? 'info' : 'default'} label={session.status}/>
        {session.targets.map((target) => (
          <Chip key={target.id} size='small' variant='outlined' label={`${target.type}: ${target.value}`}/>
        ))}
      </Stack>
      <Typography variant='caption' color='text.secondary' sx={{ display: 'block', mb: 2 }}>
        {session.startedAt ? `Started ${new Date(session.startedAt).toLocaleString()}` : 'Not started yet'}
        {session.finishedAt ? ` · finished ${new Date(session.finishedAt).toLocaleString()}` : ''}
      </Typography>
      <Typography variant='h6' gutterBottom>Bot trace {session.status === 'RUNNING' ? '(live)' : ''}</Typography>
      <SessionLogViewer logs={logs}/>
    </Box>
  );
};

export default SessionDetailsPage;
