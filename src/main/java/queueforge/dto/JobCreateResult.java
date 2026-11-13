package queueforge.dto;

public record JobCreateResult(JobResponse job, boolean replayed) { }
