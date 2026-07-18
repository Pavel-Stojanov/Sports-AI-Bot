import { Button, Card, CardActions, CardContent, Chip, Stack, Typography } from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import StopIcon from '@mui/icons-material/Stop';
import InfoIcon from '@mui/icons-material/Info';
import { useNavigate } from 'react-router';
import type { SessionResponse, SessionStatus } from '../../../../api/types/session.ts';
import useSessions from '../../../../hooks/useSessions.ts';

interface SessionCardProps {
  session: SessionResponse;
}

const STATUS_COLOR: Record<SessionStatus, 'default' | 'info' | 'warning' | 'success' | 'error'> = {
  CREATED: 'default',
  RUNNING: 'info',
  PAUSED: 'warning',
  COMPLETED: 'success',
  FAILED: 'error'
};

const SessionCard = ({ session }: SessionCardProps) => {
  const navigate = useNavigate();
  const { onStart, onStop } = useSessions();

  const canStart = session.status === 'CREATED' || session.status === 'PAUSED';
  const canStop = session.status === 'RUNNING';

  return (
    <Card sx={{ maxWidth: 300, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <CardContent sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column' }}>
        <Typography variant='h5'>{session.socialNetwork}</Typography>
        <Typography variant='subtitle1'>{session.description}</Typography>
        <Stack direction='row' spacing={1} sx={{ my: 1, flexWrap: 'wrap' }}>
          {session.targets.map((target) => (
            <Chip key={target.id} size='small' variant='outlined' label={`${target.type}: ${target.value}`}/>
          ))}
        </Stack>
        <Typography variant='caption' color='text.secondary' sx={{ flexGrow: 1 }}>
          {session.startedAt ? `Started ${new Date(session.startedAt).toLocaleString()}` : 'Not started yet'}
          {session.finishedAt ? ` · finished ${new Date(session.finishedAt).toLocaleString()}` : ''}
        </Typography>
        <Chip
          label={session.status}
          color={STATUS_COLOR[session.status]}
          size='small'
          sx={{ alignSelf: 'flex-start' }}
        />
      </CardContent>
      <CardActions sx={{ justifyContent: 'space-between' }}>
        <Button startIcon={<InfoIcon/>} onClick={() => navigate(`/sessions/${session.id}`)}>Info</Button>
        <Button startIcon={<PlayArrowIcon/>} color='success' disabled={!canStart} onClick={() => onStart(session.id)}>
          Start
        </Button>
        <Button startIcon={<StopIcon/>} color='error' disabled={!canStop} onClick={() => onStop(session.id)}>
          Stop
        </Button>
      </CardActions>
    </Card>
  );
};

export default SessionCard;
