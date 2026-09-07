import { useState } from 'react';
import type { LoginRequest } from '../api/types/user.ts';
import userApi from '../api/userApi.ts';
import { useNavigate } from 'react-router';
import useAuth from './useAuth.ts';
import useSnackbar from './useSnackbar.ts';
import extractErrorMessage from '../api/extractErrorMessage.ts';

const useLogin = () => {
  const navigate = useNavigate();
  const { login: authLogin } = useAuth();
  const { showSnackbar } = useSnackbar();
  const [loading, setLoading] = useState<boolean>(false);

  const login = async (data: LoginRequest) => {
    setLoading(true);

    try {
      const response = await userApi.login(data);
      authLogin(response.data.token);
      navigate('/');
    } catch (err) {
      showSnackbar(extractErrorMessage(err, 'Login failed. Please try again!'), 'error');
    } finally {
      setLoading(false);
    }
  };

  return { loading, login };
};

export default useLogin;
