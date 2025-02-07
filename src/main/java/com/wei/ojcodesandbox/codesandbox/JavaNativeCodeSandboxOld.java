package com.wei.ojcodesandbox.codesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.WordTree;
import com.wei.ojcodesandbox.model.ExecuteCodeRequest;
import com.wei.ojcodesandbox.model.ExecuteCodeResponse;
import com.wei.ojcodesandbox.model.ExecuteMessage;
import com.wei.ojcodesandbox.model.JudgeInfo;
import com.wei.ojcodesandbox.utils.ProcessUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 此实现类，是在main函数的参数String[] args接收参数，而非代码中键盘输入参数
 */
public class JavaNativeCodeSandboxOld implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tempCode";

    /**
     * java文件名为Main.java
     * <p>
     * java公共（public）类名要与文件名同名，而又运行class文件需要指定文件名（java -cp ./ Main）
     * 所以用户输入代码的类名限制为 Main（参考 Poj）,可以减少编译时类名不一致的风险,而且不用从用户代码中提取类名，更方便。
     */
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    //设置最大运行时间，超过则结束进程
    private static final long TIME_OUT = 5000L;
    //定义代码黑名单，定义不可用的类，方法，通过字符串匹配去查找代码是否包含
    private static final List<String> blackList = Arrays.asList("Files", "exec");

    private static final WordTree WORD_TREE;

    static {
        // 初始化字典树，hutool工具类，可以用更少的空间存储更多的敏感词汇，实现更高效的敏感词查找
        WORD_TREE = new WordTree();
        WORD_TREE.addWords(blackList);
    }

    //SecurityManager类的.class文件路径
    private static final String SECURITY_MANAGER_PATH = "C:\\code\\oj-code-sandbox\\src\\main\\resources\\security";
    //SecurityManager类的.class文件名字
    private static final String SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";

    public static void main(String[] args) {
        JavaNativeCodeSandboxOld javaNativeCodeSandboxOld = new JavaNativeCodeSandboxOld();

        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
//        String code = ResourceUtil.readStr("testCode/simpleCompute/Main.java", StandardCharsets.uTF_8);
//        String code = ResourceUtil.readStr("testCode/unsafeCode/ReadFileError.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");

        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandboxOld.executeCode(executeCodeRequest);
        System.out.println("executeCodeResponse=" + executeCodeResponse);
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        //第五期视频，3：37：00
        // 使用用自定义的安全管理器，定义代码黑名单可能会漏，而且不同编程语言不一样，人工成本高
        //不应该从这开始进行安全管理，会限制之后的所有代码，应该在运行用户代码时开始
        //所以java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=MySecurityManager Main
//        System.setSecurityManager(new MySecurityManager());

        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        /**
         * 0.校验代码
         * todo 待优化
         */
        //去查找代码是否包含blackList中的关键词
/*        FoundWord foundWord = WORD_TREE.matchWord(code);
        if (foundWord != null) {
            //找到了，终止程序，代码都不用保存本地，再编译运行
            System.out.println("包含禁止词=" + foundWord.getFoundWord());
            return null;
        }*/

/*        WordTree wordTree = new WordTree();
        wordTree.addWords(blackList);
        //去查找代码是否包含blackList中的关键词
        FoundWord foundWord = wordTree.matchWord(code);
        if (foundWord != null) {
            //找到了，终止程序，代码都不用保存本地，再编译运行
            System.out.println("foundWord=" + foundWord.getFoundWord());
            return null;
        }*/

        /**
         * 1.把用户的代码保存为文件
         */
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        //把用户代码隔离存放
        //存放代码目录路径
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        //代码路径
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        //代码文件
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        /**
         * 2.编译代码，得到 class 文件
         *
         */

        //控制台编码与java文件编码可能不一致，指定编译编码-encoding utf-8，解决编译乱码（输出结果中文乱码）
        //%s类似于C语言的printf格式化输出
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsoluteFile());
        try {
            //执行命令行命令
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            System.out.println(executeMessage + "\n");
        } catch (Exception e) {
            //throw new RuntimeException(e);
            return getErrorResponse(e);
        }
        /**
         * 3.执行代码，得到输出结果
         *
         * 注意：有多个输入用例，会运行多次
         */
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            //Xmx512m（最大堆空间大小,不能太小，可能运行程序会报错）-Xms（初始堆空间大小），防止运行的代码无限占用空间
            //注意！-Xmx参数。JVM的堆内存限制，不等同于系统实际占用的最大资源，可能会超出。
            //-Dfile.encoding=UTF-8解决运行乱码（输出结果中文乱码）
            //第一个%s目录路径，第二个%s输入参数列表（java代码 main函数的参数String[] args）
            //;%s -Djava.security.manager=%s 第一个%s安全管理器的编译好的.class文件路径，第二个%s.class文件名
            String runCmd = String.format("java -Xmx512m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
//            String runCmd = String.format("java -Xmx512m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s Main %s", userCodeParentPath, SECURITY_MANAGER_PATH, SECURITY_MANAGER_CLASS_NAME, inputArgs);
            try {
                //执行命令行命令
                Process runProcess = Runtime.getRuntime().exec(runCmd);

                // 超时控制，在创建一个守护线程，超过限制时间，销毁runProcess
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        System.out.println("超时了，中断");
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();

                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                //ExecuteMessage executeMessage = ProcessUtils.runInteractProcessAndGetMessage(runProcess, inputArgs);
                executeMessageList.add(executeMessage);
                System.out.println(executeMessage + "\n");
            } catch (Exception e) {
                //throw new RuntimeException(e);
                return getErrorResponse(e);
            }
        }
        /**
         * 4.收集整理输出结果
         */
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        //取用时最大值，便与判断是否超时
        long maxTime = 0;
        long maxMemory = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            //错误信息非空
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                //执行过程中存在错误，3代表失败
                executeCodeResponse.setStatus(3);
                break;
            }
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
            Long memory = executeMessage.getMemory();
            if (memory != null) {
                maxMemory = Math.max(maxMemory, memory);
            }
            //执行过程中不存在错误
            outputList.add(executeMessage.getMessage());
        }
        if (outputList.size() == executeMessageList.size()) {
            // 2代表成功
            executeCodeResponse.setStatus(2);
        }
        executeCodeResponse.setOutputList(outputList);

        JudgeInfo judgeInfo = new JudgeInfo();
        //judgeInfo.setMessage();//非程序执行结果输出的信息,在判题服务例设置，此处不设置
        judgeInfo.setMemory(maxMemory);//比较难实现获取内存
        judgeInfo.setTime(maxTime);

        executeCodeResponse.setJudgeInfo(judgeInfo);

        /**
         * 5.文件清理，释放空间
         */
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }

        return executeCodeResponse;
    }

    /**
     * 6.错误处理，提升程序健壮性,其实就是封装一个错误处理方法
     * <p>
     * 获取错误响应
     *
     * @param e
     * @return
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        //表示代码沙箱错误
        executeCodeResponse.setStatus(3);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}