package com.knowitai.dto;

/** The one error format every failing endpoint returns. No stack traces, ever. */
public record ApiError(String code, String message) { }
