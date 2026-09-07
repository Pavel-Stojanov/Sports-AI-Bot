import { Chip, Paper, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import type { BotActionLogResponse } from '../../../../api/types/session.ts';

interface SessionLogViewerProps {
  logs: BotActionLogResponse[];
}

const SessionLogViewer = ({ logs }: SessionLogViewerProps) => {
  if (logs.length === 0) {
    return <Typography color='text.secondary'>No bot actions logged yet.</Typography>;
  }

  return (
    <Paper variant='outlined'>
      <Table size='small'>
        <TableHead>
          <TableRow>
            <TableCell>#</TableCell>
            <TableCell>Action</TableCell>
            <TableCell>Details</TableCell>
            <TableCell>OK</TableCell>
            <TableCell>Time</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {logs.map((log, index) => (
            <TableRow key={log.id}>
              <TableCell>{index + 1}</TableCell>
              <TableCell><Chip size='small' label={log.actionType}/></TableCell>
              <TableCell sx={{ maxWidth: 420, overflowWrap: 'anywhere', whiteSpace: 'pre-wrap' }}>
                {log.details}
              </TableCell>
              <TableCell>
                {log.successful
                  ? <CheckCircleIcon color='success' fontSize='small'/>
                  : <ErrorIcon color='error' fontSize='small'/>}
              </TableCell>
              <TableCell>{new Date(log.occurredAt).toLocaleTimeString()}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Paper>
  );
};

export default SessionLogViewer;
