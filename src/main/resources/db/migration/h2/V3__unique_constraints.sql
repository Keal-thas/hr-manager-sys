CREATE UNIQUE INDEX uk_attendance_batch_ym ON attendance_batch(batch_year, batch_month);
CREATE UNIQUE INDEX uk_attendance_record_batch_emp ON attendance_record(batch_id, employee_no);
CREATE UNIQUE INDEX uk_attendance_penalty_batch_code ON attendance_penalty(batch_id, code);
