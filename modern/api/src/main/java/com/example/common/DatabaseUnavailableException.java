package com.example.common;

/**
 * 뒤편 DB 조회가 실패했음을 알리는 예외. {@link GlobalExceptionHandler} 가 502 로 바꾼다.
 *
 * <p>레거시 성적 집계는 DB 오류를 502 로 돌려준다(legacy/grade-mssql GradeController.java:57-58, :108-113).
 * 상태 코드는 그대로 따르고, DB 원문 메시지는 응답에 싣지 않는다(원인은 로그에만 남긴다).
 */
public class DatabaseUnavailableException extends RuntimeException {

    public DatabaseUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
