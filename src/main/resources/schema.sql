CREATE TABLE IF NOT EXISTS sys_department (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT,
    level INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort INT DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    permissions TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_no VARCHAR(32) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    initial_password VARCHAR(100),
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(32),
    email VARCHAR(128),
    department_id BIGINT,
    role_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    must_change_password BOOLEAN DEFAULT FALSE,
    failed_attempts INT DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS attendance_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_year INT NOT NULL,
    batch_month INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    upload_mode VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_by BIGINT,
    created_at TIMESTAMP,
    published_at TIMESTAMP,
    latest BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS attendance_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    employee_no VARCHAR(32) NOT NULL,
    user_id BIGINT,
    name VARCHAR(100),
    department_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    signature_image TEXT,
    signature_time TIMESTAMP,
    agree BOOLEAN DEFAULT FALSE,
    data_json TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS attendance_penalty (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    import_type VARCHAR(20),
    code VARCHAR(50),
    employee_no VARCHAR(32),
    name VARCHAR(100),
    department_name VARCHAR(100),
    data_json TEXT,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS message_notice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    target_type VARCHAR(20) NOT NULL,
    target_ids TEXT,
    publisher_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notice_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(200),
    content TEXT,
    biz_id BIGINT,
    read_flag BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_dept ON sys_user(department_id);
CREATE INDEX IF NOT EXISTS idx_record_batch ON attendance_record(batch_id);
CREATE INDEX IF NOT EXISTS idx_record_user ON attendance_record(user_id);
CREATE INDEX IF NOT EXISTS idx_message_user ON sys_message(user_id, read_flag);
CREATE INDEX IF NOT EXISTS idx_penalty_batch ON attendance_penalty(batch_id);
