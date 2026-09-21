package com.spai.portal.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException e) { return ResponseEntity.status(e.getStatus()).body(ApiResponse.<Void>error(e.getCode(), e.getMessage())); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException e) { return ResponseEntity.badRequest().body(ApiResponse.<Void>error("VALIDATION_ERROR", e.getBindingResult().getAllErrors().get(0).getDefaultMessage())); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception e) { return ResponseEntity.status(500).body(ApiResponse.<Void>error("INTERNAL_ERROR", "服务内部错误")); }
}
