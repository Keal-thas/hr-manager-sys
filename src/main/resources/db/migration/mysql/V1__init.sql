CREATE TABLE IF NOT EXISTS sys_department (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT,
    level INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort INT DEFAULT 0,
    created_at DATETIME,
    updated_at DATETIME,
    KEY idx_department_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    permissions TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    locked_until DATETIME,
    created_at DATETIME,
    updated_at DATETIME,
    KEY idx_user_dept (department_id),
    KEY idx_user_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS attendance_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_year INT NOT NULL,
    batch_month INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    upload_mode VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_by BIGINT,
    created_at DATETIME,
    published_at DATETIME,
    latest BOOLEAN DEFAULT FALSE,
    KEY idx_batch_ym (batch_year, batch_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS attendance_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    employee_no VARCHAR(32) NOT NULL,
    user_id BIGINT,
    name VARCHAR(100),
    department_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    signature_image LONGTEXT,
    signature_time DATETIME,
    agree BOOLEAN DEFAULT FALSE,
    data_json LONGTEXT,
    created_at DATETIME,
    updated_at DATETIME,
    KEY idx_record_batch (batch_id),
    KEY idx_record_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS attendance_penalty (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    import_type VARCHAR(20),
    code VARCHAR(50),
    employee_no VARCHAR(32),
    name VARCHAR(100),
    department_name VARCHAR(100),
    data_json LONGTEXT,
    created_at DATETIME,
    KEY idx_penalty_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS message_notice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    target_type VARCHAR(20) NOT NULL,
    target_ids TEXT,
    publisher_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notice_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(200),
    content TEXT,
    biz_id BIGINT,
    read_flag BOOLEAN DEFAULT FALSE,
    created_at DATETIME,
    KEY idx_message_user (user_id, read_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
