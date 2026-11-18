package com.hrmanager.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hrmanager.attendance.dto.AttendanceRow;
import com.hrmanager.attendance.dto.PenaltyRow;
import com.hrmanager.attendance.entity.AttendanceBatch;
import com.hrmanager.attendance.entity.AttendancePenalty;
import com.hrmanager.attendance.entity.AttendanceRecord;
import com.hrmanager.attendance.mapper.AttendanceBatchMapper;
import com.hrmanager.attendance.mapper.AttendancePenaltyMapper;
import com.hrmanager.attendance.mapper.AttendanceRecordMapper;
import com.hrmanager.common.BusinessException;
import com.hrmanager.common.PageResult;
import com.hrmanager.dept.service.DepartmentService;
import com.hrmanager.notice.service.MessageService;
import com.hrmanager.user.entity.SysUser;
import com.hrmanager.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceBatchMapper batchMapper;
    private final AttendanceRecordMapper recordMapper;
    private final AttendancePenaltyMapper penaltyMapper;
    private final AttendanceExcelService excelService;
    private final UserService userService;
    private final DepartmentService departmentService;
    private final MessageService messageService;

    public PageResult<Map<String, Object>> pageBatches(int page, int size, Integer year, Integer month) {
        LambdaQueryWrapper<AttendanceBatch> wrapper = Wrappers.<AttendanceBatch>lambdaQuery()
                .orderByDesc(AttendanceBatch::getBatchYear)
                .orderByDesc(AttendanceBatch::getBatchMonth);
        if (year != null) {
            wrapper.eq(AttendanceBatch::getBatchYear, year);
        }
        if (month != null) {
            wrapper.eq(AttendanceBatch::getBatchMonth, month);
        }
        Page<AttendanceBatch> result = batchMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> records = result.getRecords().stream()
                .map(this::batchView)
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public Map<String, Object> batchView(AttendanceBatch batch) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", batch.getId());
        item.put("batchYear", batch.getBatchYear());
        item.put("batchMonth", batch.getBatchMonth());
        item.put("status", batch.getStatus());
        item.put("uploadMode", batch.getUploadMode());
        item.put("latest", batch.getLatest());
        item.put("createdAt", batch.getCreatedAt());
        item.put("publishedAt", batch.getPublishedAt());
        item.put("recordCount", recordMapper.selectCount(Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getBatchId, batch.getId())));
        item.put("confirmedCount", recordMapper.selectCount(Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getBatchId, batch.getId())
                .eq(AttendanceRecord::getStatus, "CONFIRMED")));
        item.put("readCount", recordMapper.selectCount(Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getBatchId, batch.getId())
                .eq(AttendanceRecord::getStatus, "READ")));
        item.put("unreadCount", recordMapper.selectCount(Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getBatchId, batch.getId())
                .eq(AttendanceRecord::getStatus, "UNREAD")));
        return item;
    }

    public AttendanceBatch getBatch(Long id) {
        AttendanceBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BusinessException("考勤记录不存在");
        }
        return batch;
    }

    public AttendanceBatch latestBatch() {
        AttendanceBatch batch = batchMapper.selectOne(Wrappers.<AttendanceBatch>lambdaQuery()
                .eq(AttendanceBatch::getLatest, true)
                .orderByDesc(AttendanceBatch::getBatchYear)
                .orderByDesc(AttendanceBatch::getBatchMonth)
                .last("limit 1"));
        if (batch == null) {
            batch = batchMapper.selectOne(Wrappers.<AttendanceBatch>lambdaQuery()
                    .orderByDesc(AttendanceBatch::getBatchYear)
                    .orderByDesc(AttendanceBatch::getBatchMonth)
                    .last("limit 1"));
        }
        return batch;
    }

    private void refreshLatest() {
        AttendanceBatch latest = batchMapper.selectOne(Wrappers.<AttendanceBatch>lambdaQuery()
                .orderByDesc(AttendanceBatch::getBatchYear)
                .orderByDesc(AttendanceBatch::getBatchMonth)
                .last("limit 1"));
        batchMapper.update(null, Wrappers.<AttendanceBatch>lambdaUpdate()
                .set(AttendanceBatch::getLatest, false));
        if (latest != null) {
            batchMapper.update(null, Wrappers.<AttendanceBatch>lambdaUpdate()
                    .set(AttendanceBatch::getLatest, true)
                    .eq(AttendanceBatch::getId, latest.getId()));
        }
    }

    private boolean isLatestYearMonth(int year, int month) {
        AttendanceBatch latest = batchMapper.selectOne(Wrappers.<AttendanceBatch>lambdaQuery()
                .orderByDesc(AttendanceBatch::getBatchYear)
                .orderByDesc(AttendanceBatch::getBatchMonth)
                .last("limit 1"));
        if (latest == null) {
            return true;
        }
        return year * 100 + month >= latest.getBatchYear() * 100 + latest.getBatchMonth();
    }

    public AttendanceBatch findByYearMonth(int year, int month) {
        return batchMapper.selectOne(Wrappers.<AttendanceBatch>lambdaQuery()
                .eq(AttendanceBatch::getBatchYear, year)
                .eq(AttendanceBatch::getBatchMonth, month)
                .orderByDesc(AttendanceBatch::getCreatedAt)
                .last("limit 1"));
    }

    @Transactional
    public Map<String, Object> upload(int year, int month, String mode, MultipartFile file, Long userId) {
        if (year < 2000 || year > 2100) {
            throw new BusinessException("考勤年份不合法");
        }
        if (month < 1 || month > 12) {
            throw new BusinessException("考勤月份不合法");
        }
        if (YearMonth.of(year, month).isAfter(YearMonth.now())) {
            throw new BusinessException("新增考勤不能晚于当前时间，请修改");
        }
        String uploadMode = mode == null ? "NEW" : mode.toUpperCase();
        if (!Arrays.asList("NEW", "OVERWRITE", "INCREMENTAL").contains(uploadMode)) {
            throw new BusinessException("上传方式不合法");
        }
        List<AttendanceRow> rows = excelService.parseAttendance(file);
        Set<String> employeeNos = new HashSet<>();
        Map<String, SysUser> users = new LinkedHashMap<>();
        for (AttendanceRow row : rows) {
            if (!employeeNos.add(row.getEmployeeNo())) {
                throw new BusinessException("清单中存在重复人员，请修改后重新上传");
            }
            SysUser user = userService.findByEmployeeNo(row.getEmployeeNo());
            if (user == null) {
                throw new BusinessException("工号" + row.getEmployeeNo() + "不存在，请修改后重新上传");
            }
            if (!row.getName().equals(user.getName())) {
                throw new BusinessException("工号" + row.getEmployeeNo() + "姓名不匹配，请修改后重新上传");
            }
            users.put(row.getEmployeeNo(), user);
        }

        boolean latestBefore = isLatestYearMonth(year, month);
        AttendanceBatch existing = findByYearMonth(year, month);
        AttendanceBatch batch;
        if ("NEW".equals(uploadMode)) {
            if (existing != null) {
                throw new BusinessException("该月份考勤已存在，请使用覆盖上传或增量上传");
            }
            batch = new AttendanceBatch();
            batch.setBatchYear(year);
            batch.setBatchMonth(month);
            batch.setStatus("PUBLISHED");
            batch.setUploadMode("NEW");
            batch.setCreatedBy(userId);
            batch.setCreatedAt(LocalDateTime.now());
            batch.setPublishedAt(LocalDateTime.now());
            batchMapper.insert(batch);
        } else {
            if (existing == null) {
                throw new BusinessException("该月份考勤不存在，请先新增考勤");
            }
            if ("OVERWRITE".equals(uploadMode) && !latestBefore) {
                throw new BusinessException("仅最新批次支持覆盖上传");
            }
            batch = existing;
            if ("OVERWRITE".equals(uploadMode)) {
                recordMapper.delete(Wrappers.<AttendanceRecord>lambdaQuery()
                        .eq(AttendanceRecord::getBatchId, batch.getId()));
                penaltyMapper.delete(Wrappers.<AttendancePenalty>lambdaQuery()
                        .eq(AttendancePenalty::getBatchId, batch.getId()));
            }
            batch.setUploadMode(uploadMode);
            batch.setStatus("PUBLISHED");
            batch.setPublishedAt(LocalDateTime.now());
            batchMapper.updateById(batch);
        }
        refreshLatest();

        Map<Long, String> deptNames = departmentService.nameMap();
        for (AttendanceRow row : rows) {
            SysUser user = users.get(row.getEmployeeNo());
            AttendanceRecord record = null;
            if ("INCREMENTAL".equals(uploadMode)) {
                record = recordMapper.selectOne(Wrappers.<AttendanceRecord>lambdaQuery()
                        .eq(AttendanceRecord::getBatchId, batch.getId())
                        .eq(AttendanceRecord::getEmployeeNo, row.getEmployeeNo())
                        .last("limit 1"));
            }
            boolean isNew = record == null;
            if (isNew) {
                record = new AttendanceRecord();
                record.setBatchId(batch.getId());
                record.setEmployeeNo(row.getEmployeeNo());
                record.setCreatedAt(LocalDateTime.now());
                record.setStatus("UNREAD");
                record.setAgree(false);
            }
            record.setUserId(user.getId());
            record.setName(user.getName());
            record.setDepartmentName(deptNames.get(user.getDepartmentId()));
            record.setDataJson(excelService.toJson(row.getData()));
            record.setUpdatedAt(LocalDateTime.now());
            if (!"CONFIRMED".equals(record.getStatus())) {
                record.setStatus("UNREAD");
                record.setAgree(false);
            }
            if (isNew) {
                recordMapper.insert(record);
            } else {
                recordMapper.updateById(record);
            }
            boolean sendMessage = !"INCREMENTAL".equals(uploadMode) || latestBefore;
            if (sendMessage) {
                String suffix;
                if ("OVERWRITE".equals(uploadMode)) {
                    suffix = "已调整";
                } else if ("INCREMENTAL".equals(uploadMode)) {
                    suffix = isNew ? "已生成" : "已调整";
                } else {
                    suffix = "已生成";
                }
                messageService.send(user.getId(), "ATTENDANCE", "考勤通知",
                        user.getName() + "您好，您" + year + "年" + month + "月的考勤" + suffix
                                + "，请尽快前往员工自助-我的考勤中签收，若有疑问请联系人事部门。",
                        batch.getId());
            }
        }
        return batchView(batch);
    }

    public PageResult<Map<String, Object>> pageRecords(Long batchId, int page, int size, String keyword, String status) {
        LambdaQueryWrapper<AttendanceRecord> wrapper = Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getBatchId, batchId)
                .orderByAsc(AttendanceRecord::getEmployeeNo);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(q -> q.like(AttendanceRecord::getEmployeeNo, keyword)
                    .or().like(AttendanceRecord::getName, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(AttendanceRecord::getStatus, status);
        }
        Page<AttendanceRecord> result = recordMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> records = result.getRecords().stream()
                .map(this::recordListView)
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    private Map<String, Object> recordListView(AttendanceRecord record) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", record.getId());
        item.put("batchId", record.getBatchId());
        item.put("employeeNo", record.getEmployeeNo());
        item.put("name", record.getName());
        item.put("departmentName", record.getDepartmentName());
        item.put("status", record.getStatus());
        item.put("signatureTime", record.getSignatureTime());
        item.put("agree", record.getAgree());
        return item;
    }

    public Map<String, Object> recordDetail(Long id) {
        AttendanceRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("考勤明细不存在");
        }
        AttendanceBatch batch = getBatch(record.getBatchId());
        return recordView(record, batch, true);
    }

    @Transactional
    public Map<String, Object> offlineSignature(Long recordId, MultipartFile file) {
        AttendanceRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException("考勤明细不存在");
        }
        if ("CONFIRMED".equals(record.getStatus())) {
            throw new BusinessException("考勤已确认，无需重复上传");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择签字文件");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException("签字文件不能超过10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null
                || (!contentType.startsWith("image/") && !"application/pdf".equals(contentType))) {
            throw new BusinessException("仅支持图片或PDF签字文件");
        }
        AttendanceBatch batch = getBatch(record.getBatchId());
        if (Boolean.TRUE.equals(batch.getLatest())) {
            throw new BusinessException("最新批次请使用在线签字");
        }
        try {
            String signature = "data:" + contentType + ";base64,"
                    + Base64.getEncoder().encodeToString(file.getBytes());
            record.setSignatureImage(signature);
            record.setSignatureTime(LocalDateTime.now());
            record.setAgree(true);
            record.setStatus("CONFIRMED");
            record.setUpdatedAt(LocalDateTime.now());
            recordMapper.updateById(record);
            return recordView(record, batch, true);
        } catch (IOException ex) {
            throw new BusinessException("签字文件读取失败");
        }
    }

    @Transactional
    public int remind(Long batchId, String type) {
        AttendanceBatch batch = getBatch(batchId);
        AttendanceBatch latest = latestBatch();
        if (latest == null || !latest.getId().equals(batchId)) {
            throw new BusinessException("仅最新批次支持提醒");
        }
        String remindType = type == null ? "ATTENDANCE" : type.toUpperCase();
        List<AttendanceRecord> records;
        if ("ABNORMAL".equals(remindType)) {
            Set<String> employees = penaltyMapper.selectList(Wrappers.<AttendancePenalty>lambdaQuery()
                            .eq(AttendancePenalty::getBatchId, batchId))
                    .stream().map(AttendancePenalty::getEmployeeNo).filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
            if (employees.isEmpty()) {
                return 0;
            }
            records = recordMapper.selectList(Wrappers.<AttendanceRecord>lambdaQuery()
                    .eq(AttendanceRecord::getBatchId, batchId)
                    .eq(AttendanceRecord::getStatus, "UNREAD")
                    .in(AttendanceRecord::getEmployeeNo, employees));
        } else {
            records = recordMapper.selectList(Wrappers.<AttendanceRecord>lambdaQuery()
                    .eq(AttendanceRecord::getBatchId, batchId)
                    .ne(AttendanceRecord::getStatus, "CONFIRMED"));
        }
        for (AttendanceRecord record : records) {
            String content = "ABNORMAL".equals(remindType)
                    ? record.getName() + "您好，您" + batch.getBatchYear() + "年" + batch.getBatchMonth()
                    + "月的考勤存在异常，请及时处理，若有疑问请联系人事部门。"
                    : record.getName() + "您好，请尽快确认您" + batch.getBatchYear() + "年" + batch.getBatchMonth()
                    + "月的考勤，若有疑问请联系人事部门。";
            messageService.send(record.getUserId(), "ABNORMAL".equals(remindType) ? "ATTENDANCE_ABNORMAL" : "ATTENDANCE_REMIND",
                    "ABNORMAL".equals(remindType) ? "考勤异常提醒" : "考勤提醒", content, batchId);
        }
        return records.size();
    }

    @Transactional
    public int uploadPenalties(Long batchId, MultipartFile file) {
        AttendanceBatch batch = getBatch(batchId);
        List<PenaltyRow> rows = excelService.parsePenalty(file);
        Map<Long, String> deptNames = departmentService.nameMap();
        Map<String, Integer> seqs = new HashMap<>();
        for (PenaltyRow row : rows) {
            SysUser user = userService.findByEmployeeNo(row.getEmployeeNo());
            if (user == null) {
                throw new BusinessException("工号" + row.getEmployeeNo() + "不存在");
            }
            if (!row.getName().equals(user.getName())) {
                throw new BusinessException("工号" + row.getEmployeeNo() + "姓名不匹配");
            }
            long attendanceCount = recordMapper.selectCount(Wrappers.<AttendanceRecord>lambdaQuery()
                    .eq(AttendanceRecord::getBatchId, batchId)
                    .eq(AttendanceRecord::getEmployeeNo, row.getEmployeeNo()));
            if (attendanceCount == 0) {
                throw new BusinessException("工号" + row.getEmployeeNo() + "不在该批次考勤中");
            }
            AttendancePenalty penalty;
            if ("MODIFY".equals(row.getImportType())) {
                penalty = penaltyMapper.selectOne(Wrappers.<AttendancePenalty>lambdaQuery()
                        .eq(AttendancePenalty::getBatchId, batchId)
                        .eq(AttendancePenalty::getCode, row.getCode())
                        .last("limit 1"));
                if (penalty == null) {
                    throw new BusinessException("编号" + row.getCode() + "未匹配到对应的处罚明细，请调整后重新导入");
                }
                if (!row.getEmployeeNo().equals(penalty.getEmployeeNo())) {
                    throw new BusinessException("编号" + row.getCode() + "与工号不匹配");
                }
            } else {
                long existing = penaltyMapper.selectCount(Wrappers.<AttendancePenalty>lambdaQuery()
                        .eq(AttendancePenalty::getBatchId, batchId)
                        .eq(AttendancePenalty::getEmployeeNo, row.getEmployeeNo()));
                int seq = seqs.merge(row.getEmployeeNo(), 1, Integer::sum);
                int total = (int) existing + seq;
                String code = row.getEmployeeNo() + String.format("%02d", batch.getBatchMonth())
                        + String.format("%03d", total);
                penalty = new AttendancePenalty();
                penalty.setBatchId(batchId);
                penalty.setImportType("NEW");
                penalty.setCode(code);
                penalty.setEmployeeNo(row.getEmployeeNo());
                penalty.setCreatedAt(LocalDateTime.now());
            }
            penalty.setName(user.getName());
            penalty.setDepartmentName(deptNames.get(user.getDepartmentId()));
            penalty.setDataJson(excelService.toJson(row.getData()));
            if (penalty.getId() == null) {
                penaltyMapper.insert(penalty);
            } else {
                penaltyMapper.updateById(penalty);
            }
        }
        return rows.size();
    }

    public PageResult<Map<String, Object>> pagePenalties(Long batchId, int page, int size, String keyword) {
        LambdaQueryWrapper<AttendancePenalty> wrapper = Wrappers.<AttendancePenalty>lambdaQuery()
                .eq(AttendancePenalty::getBatchId, batchId)
                .orderByAsc(AttendancePenalty::getCode);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(q -> q.like(AttendancePenalty::getEmployeeNo, keyword)
                    .or().like(AttendancePenalty::getName, keyword)
                    .or().like(AttendancePenalty::getCode, keyword));
        }
        Page<AttendancePenalty> result = penaltyMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> records = result.getRecords().stream()
                .map(this::penaltyView)
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    private Map<String, Object> penaltyView(AttendancePenalty penalty) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", penalty.getId());
        item.put("batchId", penalty.getBatchId());
        item.put("importType", penalty.getImportType());
        item.put("code", penalty.getCode());
        item.put("employeeNo", penalty.getEmployeeNo());
        item.put("name", penalty.getName());
        item.put("departmentName", penalty.getDepartmentName());
        item.put("createdAt", penalty.getCreatedAt());
        item.put("data", excelService.fromJson(penalty.getDataJson()));
        return item;
    }

    private List<Map<String, Object>> penaltyViews(Long batchId, String employeeNo) {
        List<AttendancePenalty> penalties = penaltyMapper.selectList(Wrappers.<AttendancePenalty>lambdaQuery()
                .eq(AttendancePenalty::getBatchId, batchId)
                .eq(AttendancePenalty::getEmployeeNo, employeeNo)
                .orderByAsc(AttendancePenalty::getCode));
        List<Map<String, Object>> result = new ArrayList<>();
        for (AttendancePenalty penalty : penalties) {
            result.add(penaltyView(penalty));
        }
        return result;
    }

    public byte[] export(Long batchId) {
        getBatch(batchId);
        List<AttendanceRecord> records = recordMapper.selectList(Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getBatchId, batchId)
                .orderByAsc(AttendanceRecord::getEmployeeNo));
        return excelService.export(records);
    }

    public byte[] template() {
        return excelService.template();
    }

    public byte[] penaltyTemplate() {
        return excelService.penaltyTemplate();
    }

    public Map<String, Object> myCurrent(Long userId, Integer year, Integer month) {
        AttendanceBatch batch;
        if (year != null && month != null) {
            batch = findByYearMonth(year, month);
        } else {
            batch = latestBatch();
        }
        if (batch == null) {
            return null;
        }
        AttendanceRecord record = recordMapper.selectOne(Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getBatchId, batch.getId())
                .eq(AttendanceRecord::getUserId, userId)
                .last("limit 1"));
        if (record == null) {
            return null;
        }
        return recordView(record, batch, false);
    }

    @Transactional
    public Map<String, Object> myDetail(Long batchId, Long userId) {
        AttendanceBatch batch = getBatch(batchId);
        AttendanceRecord record = recordMapper.selectOne(Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getBatchId, batchId)
                .eq(AttendanceRecord::getUserId, userId)
                .last("limit 1"));
        if (record == null) {
            throw new BusinessException("考勤明细不存在");
        }
        if ("UNREAD".equals(record.getStatus())) {
            record.setStatus("READ");
            record.setUpdatedAt(LocalDateTime.now());
            recordMapper.updateById(record);
        }
        return recordView(record, batch, true);
    }

    public PageResult<Map<String, Object>> myHistory(Long userId, int page, int size) {
        Page<AttendanceRecord> result = recordMapper.selectPage(new Page<>(page, size),
                Wrappers.<AttendanceRecord>lambdaQuery()
                        .eq(AttendanceRecord::getUserId, userId)
                        .orderByDesc(AttendanceRecord::getCreatedAt));
        List<Map<String, Object>> records = new ArrayList<>();
        for (AttendanceRecord record : result.getRecords()) {
            AttendanceBatch batch = batchMapper.selectById(record.getBatchId());
            if (batch != null) {
                records.add(recordView(record, batch, false));
            }
        }
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional
    public Map<String, Object> confirm(Long batchId, Long userId, String signatureImage, boolean agree) {
        if (!agree) {
            throw new BusinessException("请阅读并同意考勤内容");
        }
        if (!StringUtils.hasText(signatureImage)) {
            throw new BusinessException("请完成签名");
        }
        AttendanceBatch batch = getBatch(batchId);
        AttendanceRecord record = recordMapper.selectOne(Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getBatchId, batchId)
                .eq(AttendanceRecord::getUserId, userId)
                .last("limit 1"));
        if (record == null) {
            throw new BusinessException("考勤明细不存在");
        }
        if (!"CONFIRMED".equals(record.getStatus())) {
            record.setStatus("CONFIRMED");
            record.setSignatureImage(signatureImage);
            record.setSignatureTime(LocalDateTime.now());
            record.setAgree(true);
            record.setUpdatedAt(LocalDateTime.now());
            recordMapper.updateById(record);
        }
        return recordView(record, batch, true);
    }

    private Map<String, Object> recordView(AttendanceRecord record, AttendanceBatch batch, boolean includeSignature) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", record.getId());
        item.put("batchId", record.getBatchId());
        item.put("batchYear", batch.getBatchYear());
        item.put("batchMonth", batch.getBatchMonth());
        item.put("employeeNo", record.getEmployeeNo());
        item.put("name", record.getName());
        item.put("departmentName", record.getDepartmentName());
        item.put("status", record.getStatus());
        item.put("signatureTime", record.getSignatureTime());
        item.put("agree", record.getAgree());
        item.put("data", excelService.fromJson(record.getDataJson()));
        item.put("penalties", penaltyViews(record.getBatchId(), record.getEmployeeNo()));
        if (includeSignature) {
            item.put("signatureImage", record.getSignatureImage());
        }
        return item;
    }
}
