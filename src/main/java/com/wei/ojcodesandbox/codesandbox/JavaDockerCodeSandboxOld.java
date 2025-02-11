package com.wei.ojcodesandbox.codesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.wei.ojcodesandbox.model.ExecuteCodeRequest;
import com.wei.ojcodesandbox.model.ExecuteCodeResponse;
import com.wei.ojcodesandbox.model.ExecuteMessage;
import com.wei.ojcodesandbox.model.JudgeInfo;
import com.wei.ojcodesandbox.utils.ProcessUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 此实现类，是在main函数的参数String[] args接收参数，而非代码中键盘输入参数
 */
@Deprecated
public class JavaDockerCodeSandboxOld implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tempCode";

    /**
     * java文件名为Main.java
     * <p>
     * java公共（public）类名要与文件名同名，而又运行class文件需要指定文件名（java -cp ./ Main）
     * 所以用户输入代码的类名限制为 Main（参考 Poj）,可以减少编译时类名不一致的风险,而且不用从用户代码中提取类名，更方便。
     */
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    /**
     * 设置最大运行时间，超过则结束进程
     */
    private static final long TIME_OUT = 5000L;


    /**
     * SecurityManager类的.class文件路径
     */
    private static final String SECURITY_MANAGER_PATH = "C:\\code\\oj-code-sandbox\\src\\main\\resources\\security";

    /**
     * SecurityManager类的.class文件名字
     */
    private static final String SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";

    private static final Boolean FIRST_INIT = false;

    @Value("${docker.host}")
    private  String DOCKER_HOST;

    @Value("${docker.api-version}")
    private  String DOCKER_API_VERSION;


    public static void main(String[] args) {
        JavaDockerCodeSandboxOld javaNativeCodeSandbox = new JavaDockerCodeSandboxOld();

        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
//        String code = ResourceUtil.readStr("testCode/simpleCompute/Main.java", StandardCharsets.uTF_8);
//        String code = ResourceUtil.readStr("testCode/unsafeCode/ReadFileError.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");

        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
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
//        4.在容器中执行代码，得到输出结果
//        5.收集整理输出结果
//        6.文件清理，释放空间
//        7.错误处理，提升程序健壮性

        /**
         * 3.把编译好的文件上传到容器环境内
         */
//        String DOCKER_HOST = "tcp://192.168.200.139:2375";
//        String DOCKER_API_VERSION = "1.43";
//        DockerClient dockerClient = DockerClientUtils.connect(DOCKER_HOST, DOCKER_API_VERSION);

        // 获取默认的 Docker Client
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        /**
         * 拉取镜像
         */
        String image = "openjdk:8-alpine";
        if (FIRST_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            //定义回调函数。镜像拉取完后干什么事情
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                /**
                 * 镜像每下载一部分（镜像的layer）会调用该方法
                 * @param item
                 */
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载状态：" + item.getStatus());
                    super.onNext(item);
                }
            };

            try {
                //异步的方式执行,awaitCompletion,阻塞，直到下载完成（没下载完，会一直卡在那），不然不会等下载完成就往下执行
                pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
            } catch (Exception e) {
                System.out.println("拉取镜像异常");
                return getErrorResponse(e);
            }
            System.out.println("下载完成");
        }


        /**
         * 创建容器
         */
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);

        HostConfig hostConfig = new HostConfig();
        //挂载数据卷
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        //限制内存100m
        hostConfig.withMemory(100 * 1024 * 1024L);
        //内存交换空间（写入数据时，如果没有足够的内存，会将内存中的数据写入到交换空间中，交换空间的大小与内存大小相同，当内存满时，会将交换空间中的数据写入到硬盘中）
        hostConfig.withMemorySwap(0L);
        //cpu核心数
        hostConfig.withCpuCount(1L);
        //Linux seccomp安全管理配置
        //hostConfig.withSecurityOpts(Arrays.asList("seccomp=安全管理配置字符串"));

        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                // 是否禁用网络，默认为false，如果设置为true，则不会发送网络请求，只使用缓存数据。
                .withNetworkDisabled(true)
                //容器的根目录是只读的,即限制用户不能向根目录写文件
                .withReadonlyRootfs(true)
                //容器的标准输入、输出、错误输出都连接到当前进程的标准输入、输出、错误输出
                //这样可以在宿主机上直接查看容器中的终端输出，并且可以向容器中输入文本。如果设置为false，则无法进行终端交互。
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                //容器的终端是否为tty终端
                //在启用终端模拟时，容器中的进程将认为它在一个真正的终端上运行，并且可以进行按键输入和输出等操作。这对于需要与终端进行交互的应用程序非常重要。
                .withTty(true)
                .exec();
        System.out.println("createContainerResponse：" + createContainerResponse);
        String containerId = createContainerResponse.getId();


        /**
         * 启动容器
         * 异步执行
         */
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        dockerClient.startContainerCmd(containerId).exec();
        for (String inputArgs : inputList) {
            /**
             * 创建执行命令
             */
            //注意，要把命令按照空格拆分，作为一个数组传递，否则可能会被识别为一个字符串，而不是多个参数。
            //{"java", "-cp", "/tmp/code", "Main", "1", "3"}
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
            //docker exec keen_blackwell java -cp /app Main 1 3
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();
            System.out.println("创建执行命令：" + execCreateCmdResponse);


            final long[] maxMemory = {0L};
            //监控内存信息
            /**
             * 监控内存信息
             */
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(
                    new ResultCallback<Statistics>() {
                        @Override
                        public void onStart(Closeable closeable) {

                        }

                        @Override
                        public void onNext(Statistics statistics) {
                            maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
                            System.out.println("内存占用：" + statistics.getMemoryStats().getUsage());
                        }

                        @Override
                        public void onError(Throwable throwable) {

                        }

                        @Override
                        public void onComplete() {

                        }

                        @Override
                        public void close() throws IOException {

                        }
                    });
            //启动监控
            statsCmd.exec(statisticsResultCallback);

            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            long time = 0L;
            //是否超时
            final boolean[] timout = {true};

            //用来计算运行时间，sping的工具类
            StopWatch stopWatch = new StopWatch();

            try {
                //开始计时
                stopWatch.start();
                /**
                 * 异步执行
                 */
                dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(new ExecStartResultCallback() {
                    @Override
                    public void onNext(Frame item) {
                        StreamType streamType = item.getStreamType();
                        if (streamType.equals(StreamType.STDERR)) {
                            errorMessage[0] = new String(item.getPayload());
                            System.out.println("标准错误：" + errorMessage[0]);
                        } else {
                            message[0] = new String(item.getPayload());
                            System.out.println("标准输出：" + message[0]);
                        }
                        super.onNext(item);
                    }
                    //执行完成的回调
                    @Override
                    public void onComplete() {
                        //如果执行完成，则没有超时
                        timout[0] = true;
                        super.onComplete();
                    }
                }).awaitCompletion(TIME_OUT, TimeUnit.MICROSECONDS);
                //设置超时时间，单位毫秒，这种方式无论超时与否，都会往下执行，无法判断是否超时。
                //停止计时
                stopWatch.stop();
                time = stopWatch.getTotalTimeMillis();
                //关闭监控
                statsCmd.close();

            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }

            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessageList.add(executeMessage);
        }


        /**
         * 4.收集整理输出结果
         */
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        //取用时最大值，便与判断是否超时
        long maxTime = 0;
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
        //judgeInfo.setMemory();
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