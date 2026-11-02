package com.coding.agent.exception;

import lombok.Getter;

@Getter
public class BadRequestException extends BaseException{

  public BadRequestException(String message) {
    super(400, message);
  }

  public BadRequestException(int code, String message) {
    super(code, message);
  }

  public BadRequestException(int code, String message, Throwable cause) {
    super(code, message, cause);
  }

  @Override
  public int getStatus() {
    return getCode();
  }
}
