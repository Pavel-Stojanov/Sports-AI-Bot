import { Box, Card, CardContent, Container, Grid, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import postApi from '../../../../api/postApi.ts';
import sessionApi from '../../../../api/sessionApi.ts';
import useAuth from '../../../../hooks/useAuth.ts';

interface DashboardStats {
  totalPosts: number;
  highConfidencePosts: number;
  donatedPosts: number;
  latestSessionStatus: string | null;
}

const HomePage = () => {
  const { user } = useAuth();
  const [stats, setStats] = useState<DashboardStats | null>(null);

  useEffect(() => {
    if (!user) {
      setStats(null);
      return;
    }
    const fetch = async () => {
      try {
        const [allPosts, mkPosts, donatedPosts, sessions] = await Promise.all([
          postApi.findAll({}, 0, 1),
          postApi.findAll({ minMacedonianConfidence: 0.8 }, 0, 1),
          postApi.findAll({ donated: true }, 0, 1),
          sessionApi.findAll()
        ]);
        const latest = sessions.data.length > 0 ? sessions.data[sessions.data.length - 1] : null;
        setStats({
          totalPosts: allPosts.data.totalElements,
          highConfidencePosts: mkPosts.data.totalElements,
          donatedPosts: donatedPosts.data.totalElements,
          latestSessionStatus: latest ? `#${latest.id} — ${latest.status}` : null
        });
      } catch {
        setStats(null);
      }
    };
    void fetch();
  }, [user]);

  return (
    <Box sx={{ m: 0, p: 0 }}>
      <Container maxWidth='xl' sx={{ mt: 3, py: 3 }}>
        <Typography variant='h4' gutterBottom>
          Sports AI Bot for doniraj.vezilka.ai 🤖⚽
        </Typography>
        <Typography variant='body1' sx={{ mb: 4 }}>
          This bot navigates gol.mk, extracts Macedonian sports results and match
          reports, summarizes them, and donates the summaries to the Vezilka
          language-preservation platform. Use the Sessions page to run the bot,
          the Posts page to browse what it collected, and the Donations page to
          review and submit batches.
        </Typography>
        {stats && (
          <Grid container spacing={2}>
            {[
              { label: 'Extracted posts', value: stats.totalPosts },
              { label: 'Macedonian (≥80%)', value: stats.highConfidencePosts },
              { label: 'Donated posts', value: stats.donatedPosts },
              { label: 'Latest session', value: stats.latestSessionStatus ?? '—' }
            ].map((item) => (
              <Grid key={item.label} size={{ xs: 12, sm: 6, md: 3 }}>
                <Card>
                  <CardContent>
                    <Typography variant='h4'>{item.value}</Typography>
                    <Typography variant='body2' color='text.secondary'>{item.label}</Typography>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
        )}
      </Container>
    </Box>
  );
};

export default HomePage;
