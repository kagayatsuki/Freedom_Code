package com.coding.agent.exception;

import lombok.Data;

@Data
public class BaseException extends RuntimeException {
    private final int code;
    public BaseException(String message) {
        super(message);
        this.code = 0;
    }
    public BaseException(int code, String message) {

        super(message);
        this.code=code;
    }
    public BaseException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code=code;
    }
    public BaseException(Throwable cause, String message) {
        super(message, cause);
        this.code = 0;
    }
    public int getStatus(){
        return 500;
    }
}
