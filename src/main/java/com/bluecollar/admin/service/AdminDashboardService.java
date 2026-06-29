package com.bluecollar.admin.service;

import com.bluecollar.admin.dto.DashboardResponse;

import java.time.LocalDate;

public interface AdminDashboardService {

    DashboardResponse getDashboard(LocalDate fromDate, LocalDate toDate);
}
