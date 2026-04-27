package com.knowledge.assistant.common.dto;

import lombok.Data;

@Data
public class Result<T> {
    private int code;
    private boolean success;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setSuccess(true);
        r.setData(data);
        return r;
    }

    public static <T> Result<T> fail(String message) {
        Result<T> r = new Result<>();
        r.setCode(500);
        r.setSuccess(false);
        r.setMessage(message);
        return r;
    }
}
