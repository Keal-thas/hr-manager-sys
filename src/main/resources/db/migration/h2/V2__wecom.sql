ALTER TABLE sys_user ADD COLUMN wecom_user_id VARCHAR(64);

CREATE TABLE IF NOT EXISTS wecom_message_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    employee_no VARCHAR(32),
    wecom_user_id VARCHAR(64),
    scene VARCHAR(32),
    title VARCHAR(200),
    content TEXT,
    status VARCHAR(20) NOT NULL,
    errcode INT,
    errmsg VARCHAR(500),
    created_at TIMESTAMP
);

CREATE INDEX idx_wecom_log_user ON wecom_message_log (user_id);
CREATE INDEX idx_wecom_log_created ON wecom_message_log (created_at);
