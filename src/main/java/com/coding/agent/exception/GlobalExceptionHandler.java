package com.coding.agent.exception;

import com.coding.agent.domain.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<R<Void>> handleBaseException(BaseException e) {
        return ResponseEntity
                .status(e.getStatus())
                .body(R.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleException(Exception e) {
        e.printStackTrace();
        return ResponseEntity
                .status(500)
                .body(R.error(500, "服务器内部错误"));
    }
}
