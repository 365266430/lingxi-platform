package com.lingxi.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 统一响应包装。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class Result<T> {

    public static final int SUCCESS = 0;

    private int code;
    private String message;
    private T data;
    private boolean success;
    private LocalDateTime timestamp = LocalDateTime.now();

    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS, "success", data, true, LocalDateTime.now());
    }

    public static Result<Void> ok() {
        return new Result<>(SUCCESS, "success", null, true, LocalDateTime.now());
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, false, LocalDateTime.now());
    }

    public static <T> Result<T> fail(String message) {
        return fail(500, message);
    }
}
