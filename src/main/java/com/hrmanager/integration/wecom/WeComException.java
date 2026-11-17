package com.hrmanager.integration.wecom;

import lombok.Getter;

@Getter
public class WeComException extends RuntimeException {

    private final int errcode;

    public WeComException(String message) {
        super(message);
        this.errcode = -1;
    }

    public WeComException(int errcode, String message) {
        super(message);
        this.errcode = errcode;
    }

    public WeComException(String message, Throwable cause) {
        super(message, cause);
        this.errcode = -1;
    }

    public boolean isTokenInvalid() {
        return errcode == 40014 || errcode == 42001 || errcode == 41001;
    }
}
