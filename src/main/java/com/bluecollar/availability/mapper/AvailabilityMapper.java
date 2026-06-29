package com.bluecollar.availability.mapper;

import com.bluecollar.availability.dto.AvailabilitySummaryResponse;
import com.bluecollar.availability.dto.ScheduleOverrideResponse;
import com.bluecollar.availability.dto.WorkingHoursEntry;
import com.bluecollar.availability.entity.WorkerScheduleOverride;
import com.bluecollar.availability.entity.WorkerWorkingHours;
import com.bluecollar.worker.entity.OnlineStatus;
import com.bluecollar.worker.entity.Worker;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AvailabilityMapper {

    public WorkingHoursEntry toWorkingHoursEntry(WorkerWorkingHours workingHours) {
        return new WorkingHoursEntry(
                workingHours.getDayOfWeek(),
                workingHours.getStartTime(),
                workingHours.getEndTime()
        );
    }

    public List<WorkingHoursEntry> toWorkingHoursEntries(List<WorkerWorkingHours> workingHours) {
        return workingHours.stream().map(this::toWorkingHoursEntry).toList();
    }

    public AvailabilitySummaryResponse toSummaryResponse(
            Worker worker,
            OnlineStatus effectiveOnlineStatus,
            List<WorkerWorkingHours> workingHours
    ) {
        return new AvailabilitySummaryResponse(
                Boolean.TRUE.equals(worker.getAvailable()),
                effectiveOnlineStatus,
                Boolean.TRUE.equals(worker.getVacationMode()),
                worker.getVacationStart(),
                worker.getVacationEnd(),
                toWorkingHoursEntries(workingHours)
        );
    }

    public ScheduleOverrideResponse toOverrideResponse(WorkerScheduleOverride override, boolean includeReason) {
        WorkingHoursEntry hours = null;
        if (override.getStartTime() != null && override.getEndTime() != null) {
            hours = new WorkingHoursEntry(
                    (short) override.getOverrideDate().getDayOfWeek().getValue(),
                    override.getStartTime(),
                    override.getEndTime()
            );
        }
        return new ScheduleOverrideResponse(
                override.getId(),
                override.getOverrideDate(),
                Boolean.TRUE.equals(override.getAvailable()),
                hours,
                includeReason ? override.getReason() : null
        );
    }
}
