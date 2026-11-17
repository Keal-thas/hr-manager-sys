import React from 'react';
import { Button, Result } from 'antd';

export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    console.error('[ui] render error', error, info);
  }

  render() {
    if (this.state.error) {
      return (
        <Result
          status="500"
          title="页面渲染失败"
          subTitle={this.state.error.message || '请刷新页面后重试'}
          extra={<Button type="primary" onClick={() => window.location.reload()}>刷新页面</Button>}
        />
      );
    }
    return this.props.children;
  }
}
