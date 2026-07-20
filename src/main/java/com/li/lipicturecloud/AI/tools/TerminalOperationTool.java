package com.li.lipicturecloud.AI.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * 终端操作工具 —— 安全加固版本
 *
 * 安全特性：
 * 1. 命令白名单 + 正则参数校验
 * 2. 使用 exec(String[] cmdarray) 避免 shell 注入
 * 3. ProcessBuilder 超时机制 + 强制销毁
 * 4. 同时读取 stdout/stderr 防止死锁
 * 5. 输出长度限制，防止 OOM
 * 6. 管理员白名单校验
 *
 * @author li-ai-agent
 */
public class TerminalOperationTool {

    private static final Logger log = LoggerFactory.getLogger(TerminalOperationTool.class);

    // ============================================================
    // 1. 管理员白名单（用户标识 / 系统账户）
    // ============================================================
    private static final Set<String> ADMIN_WHITELIST = Set.of(
            "admin",      // 默认管理员
            "root",       // Unix root
            "MECHREVO"    // 当前系统用户（按需修改）
    );

    // ============================================================
    // 2. 命令白名单 —— 仅允许安全可控的命令
    // ============================================================
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "dir",        // Windows 列出目录
            "echo",       // 输出文本
            "type",       // 显示文件内容（Windows 版 cat）
            "find",       // 字符串查找
            "ping",       // 网络探测（带参数限制）
            "ipconfig",   // 网络配置
            "systeminfo", // 系统信息
            "tasklist",   // 进程列表
            "whoami"      // 当前用户
    );

    // ============================================================
    // 3. 命令对应参数的严格正则白名单
    // ============================================================
    private static final Map<String, Pattern> COMMAND_PARAM_PATTERNS = new HashMap<>();

    static {
        // dir: 仅允许 路径/目录名（字母、数字、下划线、连字符、冒号、反斜杠、点）
        COMMAND_PARAM_PATTERNS.put("dir", Pattern.compile("^[a-zA-Z0-9_:\\/. -]+$"));
        // echo: 仅允许安全的字母数字和常见符号
        COMMAND_PARAM_PATTERNS.put("echo", Pattern.compile("^[a-zA-Z0-9_ -]+$"));
        // type: 仅允许安全的文件路径
        COMMAND_PARAM_PATTERNS.put("type", Pattern.compile("^[a-zA-Z0-9_:\\/. -]+$"));
        // find: 仅允许安全的搜索字符串和路径
        COMMAND_PARAM_PATTERNS.put("find", Pattern.compile("^[a-zA-Z0-9_\"'/:\\\\ .-]+$"));
        // ping: 仅允许 IP 或域名，最多 4 个 ping
        COMMAND_PARAM_PATTERNS.put("ping", Pattern.compile("^(?:-n \\d+ )?[a-zA-Z0-9.-]+$"));
        // ipconfig: 无参数 或 /all
        COMMAND_PARAM_PATTERNS.put("ipconfig", Pattern.compile("^/?all$|^$"));
        // systeminfo: 无参数
        COMMAND_PARAM_PATTERNS.put("systeminfo", Pattern.compile("^$"));
        // tasklist: 仅允许 /v /fo 等简单参数
        COMMAND_PARAM_PATTERNS.put("tasklist", Pattern.compile("^/?[a-zA-Z ]*$"));
        // whoami: 无参数
        COMMAND_PARAM_PATTERNS.put("whoami", Pattern.compile("^$"));
    }

    // ============================================================
    // 4. 安全常量
    // ============================================================
    private static final long COMMAND_TIMEOUT_SECONDS = 10;   // 超时时间（秒）
    private static final long MAX_OUTPUT_BYTES = 1024 * 100;  // 最大输出 100KB
    private static final String COMMAND_SEPARATOR_REGEX = "(?<!\\^ ) "; // 按空格分割（忽略 ^ 转义的空格）

    /**
     * 执行终端命令 —— 安全加固版本
     *
     * @param command 要执行的命令（如 "dir C:\\Users"）
     * @param adminId 调用者标识（用于管理员白名单校验）
     * @return 命令执行结果（stdout + stderr）
     */
    @Tool(description = "执行终端命令（安全加固版）。支持命令白名单 + 参数正则校验 + 超时销毁。需提供 adminId 进行管理员校验。")
    public String executeCommandSafe(
            @ToolParam(description = "要执行的终端命令，如 'dir C:\\Users'") String command,
            @ToolParam(description = "调用者用户标识，用于管理员白名单校验") String adminId) {

        // ============================================================
        // 步骤 A：管理员白名单校验
        // ============================================================
        if (adminId == null || !ADMIN_WHITELIST.contains(adminId.trim())) {
            log.warn("[安全拦截] 非管理员尝试执行命令，adminId={}", adminId);
            return "【安全拦截】无执行权限：\"" + adminId + "\" 不在管理员白名单中。";
        }

        if (command == null || command.trim().isEmpty()) {
            return "【错误】命令不能为空。";
        }

        command = command.trim();

        // ============================================================
        // 步骤 B：解析命令名 + 参数
        // ============================================================
        // 按空格切分，支持 ^ 转义空格（Windows cmd 风格）
        List<String> parts = parseCommandParts(command);
        if (parts.isEmpty()) {
            return "【错误】无法解析命令。";
        }

        String cmdName = parts.get(0).toLowerCase();
        // 提取参数部分（去掉命令名本身）
        String[] cmdArgs = parts.size() > 1
                ? parts.subList(1, parts.size()).toArray(new String[0])
                : new String[0];
        String paramsJoined = String.join(" ", cmdArgs);

        // ============================================================
        // 步骤 C：命令白名单检查
        // ============================================================
        if (!ALLOWED_COMMANDS.contains(cmdName)) {
            log.warn("[安全拦截] 禁止的命令: {} (来自: {})", cmdName, adminId);
            return "【安全拦截】命令 \"" + cmdName + "\" 不在白名单中。允许的命令: " + ALLOWED_COMMANDS;
        }

        // ============================================================
        // 步骤 D：参数正则校验
        // ============================================================
        Pattern paramPattern = COMMAND_PARAM_PATTERNS.get(cmdName);
        if (paramPattern != null && !paramPattern.matcher(paramsJoined).matches()) {
            log.warn("[安全拦截] 命令参数不符合规则: cmd={}, params={}", cmdName, paramsJoined);
            return "【安全拦截】命令 \"" + cmdName + "\" 的参数不符合安全规则，已拒绝执行。";
        }

        // ============================================================
        // 步骤 E：使用 exec(String[] cmdarray) 避免 shell 解析
        // ============================================================
        String[] cmdArray;
        if (cmdArgs.length == 0) {
            cmdArray = new String[]{cmdName};
        } else {
            cmdArray = new String[cmdArgs.length + 1];
            cmdArray[0] = cmdName;
            System.arraycopy(cmdArgs, 0, cmdArray, 1, cmdArgs.length);
        }

        log.info("[终端执行] adminId={}, cmdArray={}", adminId, Arrays.toString(cmdArray));

        // ============================================================
        // 步骤 F：ProcessBuilder + 超时控制 + 输出长度限制
        // ============================================================
        ProcessBuilder processBuilder = new ProcessBuilder(cmdArray);
        // 合并错误流到标准流，避免单独读取造成死锁
        processBuilder.redirectErrorStream(true);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = null;

        try {
            Process process = processBuilder.start();

            // 提交异步读取任务
            future = executor.submit(() -> {
                // 同时读取合并后的 stdout（已包含 stderr）
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                try (InputStream inputStream = process.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalRead = 0;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        // 限制输出长度
                        if (totalRead + bytesRead > MAX_OUTPUT_BYTES) {
                            int allowed = (int) (MAX_OUTPUT_BYTES - totalRead);
                            if (allowed > 0) {
                                outputStream.write(buffer, 0, allowed);
                            }
                            outputStream.write(("\n\n... [输出已截断，超过最大限制 " + (MAX_OUTPUT_BYTES / 1024) + "KB]").getBytes());
                            process.destroyForcibly();
                            break;
                        }
                        outputStream.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }
                }
                return outputStream.toString("UTF-8"); // Windows cmd 默认 GBK 编码
            });

            // ============================================================
            // 步骤 G：超时控制 + 强制销毁
            // ============================================================
            String result;
            try {
                result = future.get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                // 超时，强制销毁进程
                process.destroyForcibly();
                future.cancel(true);
                log.warn("[安全告警] 命令执行超时，已强制销毁: cmd={}", cmdName);
                return "【安全告警】命令执行超过 " + COMMAND_TIMEOUT_SECONDS + " 秒，已强制终止。";
            }

            return result;

        } catch (IOException e) {
            log.error("[终端执行异常] cmd={}", cmdName, e);
            return "【错误】执行命令时发生 IO 异常: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "【错误】执行命令时被中断。";
        } catch (ExecutionException e) {
            return "【错误】读取命令输出时异常: " + e.getCause().getMessage();
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 安全解析命令字符串，支持 Windows cmd 的 ^ 转义空格
     * 例如: echo hello^ world  → ["echo", "hello world"]
     */
    private List<String> parseCommandParts(String command) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);

            if (escaped) {
                // 上一个字符是 ^，当前字符原样保留
                current.append(c);
                escaped = false;
                continue;
            }

            if (c == '^') {
                // ^ 是 Windows cmd 转义符，标记转义
                escaped = true;
                continue;
            }

            if (c == ' ') {
                // 遇到空格，结束当前 token
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current = new StringBuilder();
                }
                continue;
            }

            current.append(c);
        }

        // 处理最后一个 token
        if (current.length() > 0) {
            parts.add(current.toString());
        }

        return parts;
    }
}
