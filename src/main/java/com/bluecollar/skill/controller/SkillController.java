package com.bluecollar.skill.controller;

import com.bluecollar.common.dto.ApiResponse;
import com.bluecollar.skill.dto.CreateSkillRequest;
import com.bluecollar.skill.dto.SkillResponse;
import com.bluecollar.skill.dto.UpdateSkillRequest;
import com.bluecollar.skill.service.SkillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SkillResponse>> createSkill(@Valid @RequestBody CreateSkillRequest request) {
        SkillResponse skill = skillService.createSkill(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(skill, "Skill created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SkillResponse>>> getAllSkills(
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(skillService.getAllSkills(pageable), "Skills fetched successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SkillResponse>> getSkillById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(skillService.getSkillById(id), "Skill fetched successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SkillResponse>> updateSkill(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSkillRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(skillService.updateSkill(id, request), "Skill updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteSkill(@PathVariable UUID id) {
        skillService.deleteSkill(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Skill deleted successfully"));
    }
}
