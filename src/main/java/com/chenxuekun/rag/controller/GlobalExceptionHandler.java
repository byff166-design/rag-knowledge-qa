package com.chenxuekun.rag.controller;

import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @Data
    public static class ApiError {
        private int code;
        private String message;
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError badRequest(Exception e) {
        ApiError error = new ApiError();
        error.setCode(400);
        error.setMessage(e instanceof MethodArgumentNotValidException ex
                ? ex.getBindingResult().getAllErrors().get(0).getDefaultMessage()
                : e.getMessage());
        return error;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError tooLarge(Exception e) {
        ApiError error = new ApiError();
        error.setCode(400);
        error.setMessage("文件超过大小限制(20MB)");
        return error;
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError serverError(Exception e) {
        ApiError error = new ApiError();
        error.setCode(500);
        error.setMessage(e.getMessage());
        return error;
    }
}
