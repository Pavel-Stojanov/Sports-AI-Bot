import { Box, Button, CircularProgress, Grid, Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useState } from 'react';
import useSessions from '../../../../hooks/useSessions.ts';
import SessionCard from '../../../components/session/SessionCard/SessionCard.tsx';
import StartSessionDialog from '../../../components/session/StartSessionDialog/StartSessionDialog.tsx';

/**
 * The bot control panel: StartSessionDialog creates gol.mk extraction
 * sessions, each SessionCard drives its lifecycle (start/stop) and links to
 * the live trace on the details page.
 */
const SessionsPage = () => {
  const { sessions, loading } = useSessions();

  const [newSessionDialogOpen, setNewSessionDialogOpen] = useState<boolean>(false);

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
           <Typography variant='h5'>Extraction Sessions</Typography>
           <Button variant='contained' startIcon={<AddIcon/>} onClick={() => setNewSessionDialogOpen(true)}>
             New Session
           </Button>
         </Box>
         {sessions.length === 0 && (
           <Typography color='text.secondary'>
             No extraction sessions yet. Create one to set your bot in motion.
           </Typography>
         )}
         <Grid container spacing={2}>
           {sessions.map((session) => (
             <Grid key={session.id} size={{ xs: 12, sm: 6, md: 4 }}>
               <SessionCard session={session}/>
             </Grid>
           ))}
         </Grid>
         <StartSessionDialog
           open={newSessionDialogOpen}
           onClose={() => setNewSessionDialogOpen(false)}
         />
       </>}
    </Box>
  );
};

export default SessionsPage;
