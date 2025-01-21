package com.wei.ojcodesandbox.model;

import lombok.Data;

/**
 * 进程执行信息
 * @author wei
 */
@Data
public class ExecuteMessage {
    /**
     * 不要用int，默认值为0
     */
    private Integer exitValue;
    /**
     * 包含代码执行成功的控制台输出结果或执行失败的结果
     */
    private String message;
    private String errorMessage;
    private Long time;
    private Long memory;
}
