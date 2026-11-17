ALTER TABLE sys_user ADD COLUMN wecom_user_id VARCHAR(64) NULL AFTER email;

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
    created_at DATETIME,
    KEY idx_wecom_log_user (user_id),
    KEY idx_wecom_log_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
