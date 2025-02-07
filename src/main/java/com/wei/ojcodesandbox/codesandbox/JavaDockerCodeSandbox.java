package com.wei.ojcodesandbox.codesandbox;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.wei.ojcodesandbox.model.ExecuteCodeRequest;
import com.wei.ojcodesandbox.model.ExecuteCodeResponse;
import com.wei.ojcodesandbox.model.ExecuteMessage;
import com.wei.ojcodesandbox.utils.DockerClientUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * java docker代码沙箱实现
 */
@Component
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {

    @Value("${docker.host:tcp://101.126.44.74:2375}")
    private String DOCKER_HOST;

    @Value("${docker.api-version:1.47}")
    private String DOCKER_API_VERSION;

    /**
     * 设置最大运行时间毫秒，超过则结束进程
     */
    private static final long TIME_OUT = 5000L;

    private static final Boolean FIRST_INIT = false;


    public static void main(String[] args) {
        JavaDockerCodeSandbox javaDockerCodeSandbox = new JavaDockerCodeSandbox();

        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
//        String code = ResourceUtil.readStr("testCode/simpleCompute/Main.java", StandardCharsets.uTF_8);
//        String code = ResourceUtil.readStr("testCode/unsafeCode/ReadFileError.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");

        ExecuteCodeResponse executeCodeResponse = javaDockerCodeSandbox.executeCode(executeCodeRequest);
        System.out.println("executeCodeResponse=" + executeCodeResponse);
    }


    /**
     * 3.运行代码
     */
    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

        //获取默认的 Docker client，如果你没有显式设置 dockerHttpClient，docker-java 会回退到 Jersey 客户端，并发出警告。
        //DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String DOCKER_HOST = "tcp://192.168.117.131:2375";
        String DOCKER_API_VERSION = "1.47";

        DockerClient dockerClient = DockerClientUtils.connect(DOCKER_HOST, DOCKER_API_VERSION);

        /**
         * 拉取镜像
         * alpine轻量版本
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
//                System.out.println("拉取镜像异常");
                throw new RuntimeException("拉取镜像异常", e);
//                return getErrorResponse(e);
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
        //限制内存512m
        hostConfig.withMemory(512 * 1024 * 1024L);
        //内存交换空间（写入数据时，如果没有足够的内存，会将内存中的数据写入到交换空间中，交换空间的大小与内存大小相同，当内存满时，会将交换空间中的数据写入到硬盘中）
        hostConfig.withMemorySwap(0L);
        //cpu核心数
        hostConfig.withCpuCount(2L);
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
//        System.out.println("createContainerResponse：" + createContainerResponse);
        String containerId = createContainerResponse.getId();


        /**
         * 启动容器
         * 异步执行
         * 只需用docker执行代码，其他步骤（编译等）都在容器外服务器操作
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

            //缩小e6倍为1mb
            final long[] maxMemory = {0L};
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
//            statsCmd.exec(statisticsResultCallback);

            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            long time = 0L;
            //是否超时
            final boolean[] timout = {true};

            //用来计算运行时间，spring的工具类
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

                    //TIME_OUT时间内执行完成的回调
                    @Override
                    public void onComplete() {
                        //如果执行完成，则没有超时
                        timout[0] = false;
                        super.onComplete();
                    }
                }).awaitCompletion(TIME_OUT, TimeUnit.MICROSECONDS);
                //设置超时时间，单位毫秒，这种方式无论超时与否，都会往下执行，无法判断是否超时。
                //停止计时
                stopWatch.stop();
                //获取所有任务的总时间（以毫秒为单位）
                time = stopWatch.getTotalTimeMillis();
                //关闭监控
                statsCmd.close();

            } catch (InterruptedException e) {
                throw new RuntimeException("程序执行异常", e);
            }

            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);

            executeMessageList.add(executeMessage);
        }
        /**
         * 关闭容器
         */
        dockerClient.stopContainerCmd(containerId).exec();
        /**
         * 移除容器
         */
        dockerClient.removeContainerCmd(containerId).exec();

        return executeMessageList;
    }
}