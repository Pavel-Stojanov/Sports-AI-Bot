import { useState } from 'react';
import type { RegisterRequest } from '../api/types/user.ts';
import userApi from '../api/userApi.ts';
import { useNavigate } from 'react-router';
import useSnackbar from './useSnackbar.ts';
import extractErrorMessage from '../api/extractErrorMessage.ts';

const useRegister = () => {
  const navigate = useNavigate();
  const { showSnackbar } = useSnackbar();
  const [loading, setLoading] = useState<boolean>(false);

  const register = async (data: RegisterRequest) => {
    setLoading(true);

    try {
      await userApi.register(data);
      navigate('/login');
    } catch (err) {
      showSnackbar(extractErrorMessage(err, 'Registration failed. Please try again!'), 'error');
    } finally {
      setLoading(false);
    }
  };

  return { loading, register };
};

export default useRegister;
