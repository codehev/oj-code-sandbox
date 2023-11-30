package com.wei.ojcodesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder//构造器的方式创建对象
@NoArgsConstructor//无参构造
@AllArgsConstructor//有参构造
public class ExecuteCodeRequest {
    /**
     * 输入用例
     */
    private List<String> inputList;
    /**
     * 代码
     */
    private String  code;

    private String language;
}
