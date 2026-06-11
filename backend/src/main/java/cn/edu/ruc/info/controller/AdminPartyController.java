package cn.edu.ruc.info.controller;

import cn.edu.ruc.info.common.Result;
import cn.edu.ruc.info.service.AdminPartyService;
import cn.edu.ruc.info.util.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/party")
public class AdminPartyController {

    private final AdminPartyService adminPartyService;

    public AdminPartyController(AdminPartyService adminPartyService) {
        this.adminPartyService = adminPartyService;
    }

    @GetMapping("/stages")
    public Result<List<AdminPartyService.PartyStageVO>> listStages() {
        try {
            requireAdminRole();
            return Result.success(adminPartyService.listStages());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/students")
    public Result<List<AdminPartyService.StudentPartyProgressSummaryVO>> listStudents(
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) String keyword) {
        try {
            requireAdminRole();
            AdminPartyService.StudentProgressQuery query = new AdminPartyService.StudentProgressQuery();
            query.setGrade(grade);
            query.setMajor(major);
            query.setKeyword(keyword);
            return Result.success(adminPartyService.listStudentProgress(query));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/students/{userId}")
    public Result<AdminPartyService.StudentPartyProgressDetailVO> detail(@PathVariable Long userId) {
        try {
            requireAdminRole();
            return Result.success(adminPartyService.getStudentProgressDetail(userId));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/students/{userId}")
    public Result<AdminPartyService.StudentPartyProgressDetailVO> save(@PathVariable Long userId,
            @RequestBody AdminPartyService.SaveStudentProgressRequest request) {
        try {
            requireAdminRole();
            return Result.success(adminPartyService.saveStudentProgress(userId, request, UserContext.getUserId()));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping(value = "/import", consumes = "application/json")
    public Result<AdminPartyService.ImportPartyProgressResult> importPartyProgress(
            @RequestBody List<AdminPartyService.ImportPartyProgressRow> rows,
            @RequestParam(defaultValue = "party_progress.xlsx") String fileName) {
        try {
            requireAdminRole();
            AdminPartyService.ImportPartyProgressResult result = adminPartyService.importPartyProgress(
                    fileName,
                    rows,
                    UserContext.getUserId());
            Result<AdminPartyService.ImportPartyProgressResult> response = Result.success(result);
            response.setMessage(result.getMessage());
            return response;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    private void requireAdminRole() {
        Integer role = UserContext.getRoleId();
        if (role == null || (role != 1 && role != 2)) {
            throw new RuntimeException("无权限访问");
        }
    }
}
