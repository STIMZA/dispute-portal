package com.example.disputeportal.exception;

import com.example.disputeportal.dto.ApiProblem;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * A batch API request (loading transactions or creating customers) was rejected as a whole; nothing was saved.
 */
public class BatchRejectedException extends RuntimeException {

    /**
     * HTTP status to answer with: 422 for invalid items, 409 for conflicts.
     */
    private final HttpStatus status;
    /**
     * Every problem found, each pointing at the offending item and field.
     */
    private final transient List<ApiProblem> problems;

    /**
     * @param status   HTTP status to answer with
     * @param message  short, fixed summary (never request data)
     * @param problems every problem found
     */
    public BatchRejectedException(HttpStatus status, String message, List<ApiProblem> problems) {
        super(message);
        this.status = status;
        this.problems = List.copyOf(problems);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public List<ApiProblem> getProblems() {
        return problems;
    }
}
