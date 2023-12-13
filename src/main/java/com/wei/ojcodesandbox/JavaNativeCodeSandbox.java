package com.wei.ojcodesandbox;

import com.wei.ojcodesandbox.model.ExecuteCodeRequest;
import com.wei.ojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.stereotype.Component;

/**
 * java原生代码沙箱实现，直接复用模版方法
 */
@Component
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate {

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }

}