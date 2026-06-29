package com.bluecollar.notification.controller;

import com.bluecollar.common.dto.ApiResponse;
import com.bluecollar.notification.dto.NotificationPreferenceResponse;
import com.bluecollar.notification.dto.NotificationResponse;
import com.bluecollar.notification.dto.UpdateNotificationPreferenceRequest;
import com.bluecollar.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getNotifications(
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getMyNotifications(pageable),
                "Notifications fetched successfully"
        ));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("count", notificationService.getUnreadCount()),
                "Unread count fetched successfully"
        ));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable UUID id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Notification marked as read"));
    }

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok(ApiResponse.success(null, "All notifications marked as read"));
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> getPreferences() {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getPreferences(),
                "Preferences fetched successfully"
        ));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> updatePreferences(
            @Valid @RequestBody UpdateNotificationPreferenceRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.updatePreferences(request),
                "Preferences updated successfully"
        ));
    }
}
