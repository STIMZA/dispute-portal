package com.example.disputeportal.dto;

import java.util.List;

/** Error body returned by every API failure. It never contains stack traces or internal details. */
public record ApiError(String title, int status, List<ApiProblem> errors) {
}
