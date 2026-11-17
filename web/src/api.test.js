import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('antd', () => ({
  message: {
    error: vi.fn(),
    warning: vi.fn()
  }
}));

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => ({
      interceptors: {
        response: {
          use: vi.fn()
        }
      }
    }))
  }
}));

import { message } from 'antd';
import { handleError, handleResponse } from './api';

describe('api response handling', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('returns the data payload when code is zero', () => {
    const result = handleResponse({ config: {}, data: { code: 0, data: { id: 1 } } });
    expect(result).toEqual({ id: 1 });
  });

  it('returns blob responses unchanged', () => {
    const response = { config: { responseType: 'blob' }, data: 'binary' };
    expect(handleResponse(response)).toBe(response);
  });

  it('rejects business errors and shows the message', async () => {
    await expect(handleResponse({ config: {}, data: { code: 1, message: '保存失败' } }))
      .rejects.toThrow('保存失败');
    expect(message.error).toHaveBeenCalledWith('保存失败');
  });

  it('dispatches the unauthorized event on 401', async () => {
    const dispatchEvent = vi.fn();
    vi.stubGlobal('window', { dispatchEvent });
    const error = { response: { status: 401 } };

    await expect(handleError(error)).rejects.toBe(error);
    expect(dispatchEvent).toHaveBeenCalledTimes(1);
  });

  it('warns on 403 using the server message', async () => {
    const error = { response: { status: 403, data: { message: '无权限操作' } } };

    await expect(handleError(error)).rejects.toBe(error);
    expect(message.warning).toHaveBeenCalledWith('无权限操作');
  });

  it('shows the network message on other failures', async () => {
    const error = { response: { status: 500, data: { message: '服务器内部错误' } } };

    await expect(handleError(error)).rejects.toBe(error);
    expect(message.error).toHaveBeenCalledWith('服务器内部错误');
  });
});
