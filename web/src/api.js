import axios from 'axios';
import { message } from 'antd';

const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  timeout: 60000
});

api.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response;
    }
    const body = response.data;
    if (body && body.code === 0) {
      return body.data;
    }
    const text = body?.message || '请求失败';
    message.error(text);
    return Promise.reject(new Error(text));
  },
  (error) => {
    if (error.response?.status === 401) {
      window.dispatchEvent(new Event('app:unauthorized'));
    } else if (error.response?.status === 403) {
      message.warning(error.response.data?.message || '无权限');
    } else {
      message.error(error.response?.data?.message || '网络错误');
    }
    return Promise.reject(error);
  }
);

export default api;
