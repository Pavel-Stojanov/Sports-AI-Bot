import './App.css';
import { BrowserRouter, Outlet, Route, Routes } from 'react-router';
import Layout from './ui/components/layout/Layout/Layout.tsx';
import HomePage from './ui/pages/home/HomePage/HomePage.tsx';
import RegisterPage from './ui/pages/auth/RegisterPage/RegisterPage.tsx';
import LoginPage from './ui/pages/auth/LoginPage/LoginPage.tsx';
import ProtectedRoute from './ui/components/routing/ProtectedRoute/ProtectedRoute.tsx';
import PostsProvider from './providers/postsProvider.tsx';
import DonationsProvider from './providers/donationsProvider.tsx';
import SessionsProvider from './providers/sessionsProvider.tsx';
import SessionsPage from './ui/pages/session/SessionsPage/SessionsPage.tsx';
import SessionDetailsPage from './ui/pages/session/SessionDetailsPage/SessionDetailsPage.tsx';
import PostsPage from './ui/pages/post/PostsPage/PostsPage.tsx';
import PostDetailsPage from './ui/pages/post/PostDetailsPage/PostDetailsPage.tsx';
import DonationsPage from './ui/pages/donation/DonationsPage/DonationsPage.tsx';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path='/register' element={<RegisterPage/>}/>
        <Route path='/login' element={<LoginPage/>}/>
        <Route path='/' element={<Layout/>}>
          <Route index element={<HomePage/>}/>
          <Route element={<ProtectedRoute/>}>
            <Route element={<SessionsProvider><Outlet/></SessionsProvider>}>
              <Route path='sessions' element={<SessionsPage/>}/>
              <Route path='sessions/:id' element={<SessionDetailsPage/>}/>
            </Route>
            <Route path='posts' element={<PostsProvider><PostsPage/></PostsProvider>}/>
            <Route path='posts/:id' element={<PostDetailsPage/>}/>
            <Route path='donations' element={<DonationsProvider><DonationsPage/></DonationsProvider>}/>
          </Route>
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
