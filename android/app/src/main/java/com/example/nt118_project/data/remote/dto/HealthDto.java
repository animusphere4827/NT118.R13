package com.example.nt118_project.data.remote.dto;

/** GET /health: {"status":"ok","database":"postgresql"}. */
public class HealthDto {
    public String status;
    public String database;
}
