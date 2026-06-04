package com.emp_management.feature.attendance.service;

import com.emp_management.feature.attendance.dto.*;
import com.emp_management.feature.attendance.entity.AttendanceSummary;
import com.emp_management.feature.attendance.repository.AttendanceSummaryRepository;
import com.emp_management.feature.employee.entity.Employee;
import com.emp_management.feature.employee.repository.EmployeeRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;

@Service
public class AttendanceService {

    private final AttendanceSummaryRepository repo;
    private final EmployeeRepository employeeRepository;

    public AttendanceService(AttendanceSummaryRepository repo, EmployeeRepository employeeRepository) {
        this.repo = repo;
        this.employeeRepository = employeeRepository;
    }

    // 🔹 Employee Monthly Calendar
    public List<AttendanceCalendarDTO> getEmployeeMonthly(String empId, int year, int month) {

        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());

        return repo
                .findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(empId, from, to)
                .stream()
                .map(this::mapToCalendar)
                .toList();
    }

    public List<AttendanceCalendarDTO> getRecords(String empId, String fromDate, String toDate) {
        LocalDate start = LocalDate.parse(fromDate);
        LocalDate end = LocalDate.parse(toDate);
        return repo.findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(empId, start, end)
                .stream()
                .map(this::mapToCalendar)
                .toList();
    }

    // 🔹 Daily View
    public List<AttendanceDetailDTO> getDailyAttendance(LocalDate date) {

        return repo
                .findByAttendanceDateOrderByEmployeeNameAsc(date)
                .stream()
                .map(this::mapToDetail)
                .toList();
    }

    // 🔹 All Employees (Pagination + Filter)
    public Page<AttendanceDetailDTO> getAllEmployeesAttendance(
            LocalDate from,
            LocalDate to,
            String status,
            int page,
            int size) {

        Pageable pageable = PageRequest.of(page, size);

        return repo
                .findFilteredAttendance(status, from, to, pageable)
                .map(this::mapToDetail);
    }

    // 🔹 Punch Records (single day)
    public AttendanceCalendarDTO getPunchRecords(String empId, LocalDate date) {
        return repo.findByEmployeeIdAndAttendanceDate(empId, date)
                .map(this::mapToCalendar)
                .orElse(null);
    }
//
//    // 🔹 CORE FIX: Calculate working hours properly
//    private LocalTime calculateWorkingHours(LocalTime checkIn, LocalTime checkOut) {
//
//        if (checkIn == null || checkOut == null) {
//            return null;
//        }
//
//        Duration duration = Duration.between(checkIn, checkOut);
//
//        long hours = duration.toHours();
//        long minutes = duration.toMinutes() % 60;
//
//        return LocalTime.of((int) hours, (int) minutes);
//    }

    // 🔹 Mappers
    private AttendanceCalendarDTO mapToCalendar(AttendanceSummary att) {

        AttendanceCalendarDTO dto = new AttendanceCalendarDTO();

        dto.setDate(att.getAttendanceDate());
        dto.setStatus(att.getAttendanceStatus());
        dto.setCheckIn(att.getCheckIn());
        dto.setCheckOut(att.getCheckOut());
        dto.setWorkingHours(att.getWorkingHours());

        // ✅ Always compute instead of trusting DB blindly
//        dto.setWorkingHours(
//                calculateWorkingHours(att.getCheckIn(), att.getCheckOut())
//        );

        dto.setPunchRecords(att.getPunchRecords());

        return dto;
    }

    public Page<AttendanceDetailDTO> getTeamAttendance(
            String managerId,
            LocalDate from,
            LocalDate to,
            String status,
            int page,
            int size) {

        // 1. Get all subordinates
        List<String> reporteeIds = employeeRepository.findByReportingId(managerId)
                .stream()
                .map(Employee::getEmpId)
                .toList();
        if (reporteeIds.isEmpty()) {
            return Page.empty();
        }

        // 2. Fetch their attendance records
        Pageable pageable = PageRequest.of(page, size, Sort.by("attendanceDate").descending());

        return repo.findByEmployeeIdIn(reporteeIds, status, from, to, pageable)
                .map(this::mapToDetail);
    }

    public Page<AttendanceCalendarDTO> getEmployeeAttendanceByRange(
            String empId,
            LocalDate from,
            LocalDate to,
            int page,
            int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("attendanceDate").descending());

        return repo.findByEmployeeIdAndDateRange(empId, from, to, pageable)
                .map(this::mapToCalendar);
    }

    public ByteArrayInputStream exportAttendanceToExcel(List<AttendanceCalendarDTO> records, String employeeName, String employeeId) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Attendance Report");

            // 1. Style for Header
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            CellStyle headerCellStyle = workbook.createCellStyle();
            headerCellStyle.setFont(headerFont);
            headerCellStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            headerCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerCellStyle.setBorderBottom(BorderStyle.THIN);

            // 2. Updated Header Row with Employee Info
            String[] columns = {"Emp ID", "Emp Name", "Date", "Status", "Check In", "Check Out", "Working Hours", "Punches"};
            Row headerRow = sheet.createRow(0);

            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerCellStyle);
            }

            // 3. Fill Data Rows
            int rowIdx = 1;
            for (AttendanceCalendarDTO record : records) {
                Row row = sheet.createRow(rowIdx++);

                // New Columns: ID and Name (Passed into the method)
                row.createCell(0).setCellValue(employeeId);
                row.createCell(1).setCellValue(employeeName);

                // Existing Columns (Shifted index by 2)
                row.createCell(2).setCellValue(record.getDate() != null ? record.getDate().toString() : "N/A");
                row.createCell(3).setCellValue(record.getStatus() != null ? record.getStatus() : "UNCATEGORIZED");
                row.createCell(4).setCellValue(record.getCheckIn() != null ? record.getCheckIn().toString() : "--:--");
                row.createCell(5).setCellValue(record.getCheckOut() != null ? record.getCheckOut().toString() : "--:--");
                row.createCell(6).setCellValue(record.getWorkingHours() != null ? record.getWorkingHours().toString() : "00:00");
                row.createCell(7).setCellValue(record.getPunchRecords() != null ? record.getPunchRecords() : "");
            }

            // 4. Auto-size all columns (0 to 7)
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }

    public ByteArrayInputStream exportTeamAttendanceToExcel(List<AttendanceDetailDTO> records) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Team Attendance Report");

            // 1. Create Style for Header
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            CellStyle headerCellStyle = workbook.createCellStyle();
            headerCellStyle.setFont(headerFont);
            headerCellStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            headerCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerCellStyle.setBorderBottom(BorderStyle.THIN);

            // 2. Create Header Row
            String[] columns = {"Emp ID", "Name", "Date", "Status", "Check In", "Check Out", "Working Hours", "Punches"};
            Row headerRow = sheet.createRow(0);

            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerCellStyle);
            }

            // 3. Fill Data Rows
            int rowIdx = 1;
            for (AttendanceDetailDTO record : records) {
                Row row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue(record.getEmployeeId() != null ? record.getEmployeeId() : "");
                row.createCell(1).setCellValue(record.getEmployeeName() != null ? record.getEmployeeName() : "");
                row.createCell(2).setCellValue(record.getDate() != null ? record.getDate().toString() : "N/A");
                row.createCell(3).setCellValue(record.getStatus() != null ? record.getStatus() : "N/A");
                row.createCell(4).setCellValue(record.getCheckIn() != null ? record.getCheckIn().toString() : "--:--");
                row.createCell(5).setCellValue(record.getCheckOut() != null ? record.getCheckOut().toString() : "--:--");
                row.createCell(6).setCellValue(record.getWorkingHours() != null ? record.getWorkingHours().toString() : "00:00");
                row.createCell(7).setCellValue(record.getPunchRecords() != null ? record.getPunchRecords() : "");
            }

            // 4. Auto-size all columns for better readability
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }


    private AttendanceDetailDTO mapToDetail(AttendanceSummary att) {

        AttendanceDetailDTO dto = new AttendanceDetailDTO();

        dto.setEmployeeId(att.getEmployeeId());
        dto.setEmployeeName(att.getEmployeeName());
        dto.setDate(att.getAttendanceDate());
        dto.setStatus(att.getAttendanceStatus() != null
                ? att.getAttendanceStatus().trim()
                : null);
        dto.setCheckIn(att.getCheckIn());
        dto.setCheckOut(att.getCheckOut());
        dto.setWorkingHours(att.getWorkingHours());


        // ✅ Same fix here
//        dto.setWorkingHours(
//                calculateWorkingHours(att.getCheckIn(), att.getCheckOut())
//        );

        dto.setPunchRecords(att.getPunchRecords());
        dto.setLopTriggered(att.isLopTriggered());

        return dto;
    }

    // In AttendanceService.java

    public List<AttendanceDetailDTO> getTeamAttendanceExportData(List<String> empIds, LocalDate from, LocalDate to) {
        // Uses a repository method that returns List<AttendanceSummary> instead of Page
        return repo.findByEmployeeIdInAndAttendanceDateBetweenOrderByAttendanceDateAsc(empIds, from, to)
                .stream()
                .map(this::mapToDetail)
                .toList();
    }

    public List<AttendanceDetailDTO> getAllAttendanceExportData(LocalDate from, LocalDate to, String status) {
        return repo.findFilteredAttendanceList(status, from, to) // Need a non-paginated version in Repo
                .stream()
                .map(this::mapToDetail)
                .toList();
    }

    // ─── Monthly Summary: All Employees (Admin/CFO) ────────────────
    public List<AttendanceMonthlySummaryDTO> getMonthlySummaryAll(LocalDate from, LocalDate to) {
        List<AttendanceSummary> records = repo.findByAttendanceDateBetweenOrderByAttendanceDateAsc(from, to);
        return buildSummary(records);
    }

    // ─── Monthly Summary: Team only (Manager) ──────────────────────
    public List<AttendanceMonthlySummaryDTO> getMonthlySummaryForTeam(List<String> empIds, LocalDate from, LocalDate to) {
        List<AttendanceSummary> records = repo.findByEmployeeIdInAndAttendanceDateBetweenOrderByAttendanceDateAsc(empIds, from, to);
        return buildSummary(records);
    }

    // ─── Core builder: group by employee → compute stats ───────────
    private List<AttendanceMonthlySummaryDTO> buildSummary(List<AttendanceSummary> records) {
        Map<String, List<AttendanceSummary>> grouped = records.stream()
                .collect(java.util.stream.Collectors.groupingBy(AttendanceSummary::getEmployeeId));

        List<AttendanceMonthlySummaryDTO> result = new java.util.ArrayList<>();

        for (Map.Entry<String, List<AttendanceSummary>> entry : grouped.entrySet()) {
            List<AttendanceSummary> empRecords = entry.getValue();
            AttendanceMonthlySummaryDTO dto = new AttendanceMonthlySummaryDTO();

            dto.setEmployeeId(entry.getKey());
            dto.setEmployeeName(empRecords.get(0).getEmployeeName());

            int present = 0, absent = 0, halfDay = 0, wfh = 0, lop = 0, leave = 0, weekend = 0, holiday = 0;
            long totalMinutes = 0;
            int workMinuteCount = 0;
            LocalTime earliest = null;
            LocalTime latest = null;

            for (AttendanceSummary r : empRecords) {
                String status = r.getAttendanceStatus() != null ? r.getAttendanceStatus().trim().toUpperCase() : "";

                switch (status) {
                    case "PRESENT", "ACTIVE" -> present++;
                    case "ABSENT"            -> absent++;
                    case "HALF_DAY", "FIRST_HALF", "SECOND_HALF" -> halfDay++;
                    case "WFH"               -> wfh++;
                    case "LOP"               -> lop++;
                    case "LEAVE"             -> leave++;
                    case "WEEKEND"           -> weekend++;
                    case "HOLIDAY"           -> holiday++;
                    default                  -> {}
                }

                if (r.isLopTriggered()) lop++;

                if (r.getWorkingHours() != null) {
                    long mins = r.getWorkingHours().getHour() * 60L + r.getWorkingHours().getMinute();
                    if (mins > 0) {
                        totalMinutes += mins;
                        workMinuteCount++;
                    }
                }

                if (r.getCheckIn() != null) {
                    if (earliest == null || r.getCheckIn().isBefore(earliest)) earliest = r.getCheckIn();
                }
                if (r.getCheckOut() != null) {
                    if (latest == null || r.getCheckOut().isAfter(latest)) latest = r.getCheckOut();
                }
            }

            dto.setTotalWorkingDays(empRecords.size() - weekend - holiday);
            dto.setPresentDays(present);
            dto.setAbsentDays(absent);
            dto.setHalfDays(halfDay);
            dto.setWfhDays(wfh);
            dto.setLopDays(lop);
            dto.setLeaveDays(leave);
            dto.setWeekendCount(weekend);
            dto.setHolidayCount(holiday);

            long totalH = totalMinutes / 60, totalM = totalMinutes % 60;
            dto.setTotalWorkingHours(String.format("%d:%02d", totalH, totalM));
            dto.setAvgWorkingHours(workMinuteCount > 0
                    ? String.format("%02d:%02d", (totalMinutes / workMinuteCount) / 60, (totalMinutes / workMinuteCount) % 60)
                    : "00:00");
            dto.setEarliestCheckIn(earliest != null ? earliest.toString().substring(0, 5) : "--:--");
            dto.setLatestCheckOut(latest != null ? latest.toString().substring(0, 5) : "--:--");

            result.add(dto);
        }

        result.sort(java.util.Comparator.comparing(AttendanceMonthlySummaryDTO::getEmployeeName,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return result;
    }

    // ─── Excel Export for Monthly Summary ──────────────────────────
    public ByteArrayInputStream exportMonthlySummaryToExcel(
            List<AttendanceMonthlySummaryDTO> summaries, LocalDate from, LocalDate to) {

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Monthly Summary");

            // Title style
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 13);
            titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            titleStyle.setFont(titleFont);

            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Monthly Attendance Summary — " + from + " to " + to);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 14));

            // Header style
            Font hFont = workbook.createFont();
            hFont.setBold(true);
            hFont.setColor(IndexedColors.WHITE.getIndex());
            CellStyle hStyle = workbook.createCellStyle();
            hStyle.setFont(hFont);
            hStyle.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            hStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            hStyle.setBorderBottom(BorderStyle.THIN);
            hStyle.setAlignment(HorizontalAlignment.CENTER);

            String[] cols = {
                    "Emp ID", "Employee Name",
                    "Working Days", "Present", "Absent", "Half Day",
                    "WFH", "Leave", "LOP", "Weekend", "Holiday",
                    "Total Hours", "Avg Hrs/Day", "Earliest In", "Latest Out"
            };

            Row hRow = sheet.createRow(1);
            for (int i = 0; i < cols.length; i++) {
                Cell c = hRow.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(hStyle);
            }

            // Even row style
            CellStyle evenStyle = workbook.createCellStyle();
            evenStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)235, (byte)245, (byte)251}, null));
            evenStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            int rowIdx = 2;
            for (AttendanceMonthlySummaryDTO s : summaries) {
                Row row = sheet.createRow(rowIdx);
                CellStyle rowStyle = (rowIdx % 2 == 0) ? evenStyle : workbook.createCellStyle();
                String[] values = {
                        s.getEmployeeId(), s.getEmployeeName(),
                        String.valueOf(s.getTotalWorkingDays()),
                        String.valueOf(s.getPresentDays()),
                        String.valueOf(s.getAbsentDays()),
                        String.valueOf(s.getHalfDays()),
                        String.valueOf(s.getWfhDays()),
                        String.valueOf(s.getLeaveDays()),
                        String.valueOf(s.getLopDays()),
                        String.valueOf(s.getWeekendCount()),
                        String.valueOf(s.getHolidayCount()),
                        s.getTotalWorkingHours(),
                        s.getAvgWorkingHours(),
                        s.getEarliestCheckIn(),
                        s.getLatestCheckOut()
                };
                for (int i = 0; i < values.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(values[i]);
                    cell.setCellStyle(rowStyle);
                }
                rowIdx++;
            }

            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate summary Excel", e);
        }
    }
}