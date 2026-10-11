#!/usr/bin/env python3
"""Terminal-only bounded synthetic chat probe. No secrets are accepted as arguments/files."""
import getpass
import os
from pathlib import Path
import resource
import signal
import subprocess
import sys
import warnings

ROOT = Path(__file__).resolve().parents[1]
MAIN = 'com.li.lipicturecloud.application.companion.LingyeLiveChatProbe'
JAVA = Path('/usr/bin/java')


def fail(code):
    print('LINGYE_LAUNCHER_BLOCKED=' + code, file=sys.stderr)
    raise SystemExit(2)


def command():
    cp_file = ROOT / 'target/lingye-live-classpath.txt'
    main_file = ROOT / ('target/test-classes/' + MAIN.replace('.', '/') + '.class')
    if not JAVA.is_file() or not cp_file.is_file() or not main_file.is_file():
        fail('PREPARE_COMPILED_TEST_RUNTIME_FIRST')
    dependencies = cp_file.read_text(encoding='utf-8').strip().split(os.pathsep)
    if not dependencies or any(not Path(p).is_file() for p in dependencies):
        fail('MISSING_TEST_DEPENDENCY')
    agents = [p for p in dependencies if Path(p).name == 'mockito-core-5.17.0.jar']
    if len(agents) != 1:
        fail('EXPECTED_MOCKITO_STARTUP_AGENT_MISSING')
    classpath = os.pathsep.join([str(ROOT / 'target/test-classes'), str(ROOT / 'target/classes'), *dependencies])
    return [str(JAVA), '-XX:-HeapDumpOnOutOfMemoryError', '-XX:ErrorFile=/dev/null',
            '-Djdk.httpclient.disableRetryConnect=true', '-Djdk.httpclient.enableAllMethodRetry=false',
            '-javaagent:' + agents[0], '-cp', classpath, MAIN]


class SignalAbort(BaseException):
    def __init__(self, signum):
        self.signum = signum


def abort_on_signal(signum, _frame):
    raise SignalAbort(signum)


def run(cmd, mode, key=None):
    # No provider keys, JVM debug flags, proxies or user runtime overrides are inherited.
    # Only this exact child JVM receives the anonymous pipe; argv/env contain no key.
    env = {'PATH': '/usr/bin:/bin', 'LANG': 'C.UTF-8', 'LC_ALL': 'C.UTF-8'}
    child = None
    try:
        child = subprocess.Popen(cmd + [mode], cwd=ROOT, env=env, text=True,
                                 stdin=subprocess.PIPE if key is not None else subprocess.DEVNULL)
        child.communicate(input=None if key is None else key + '\n', timeout=420)
        return child.returncode
    finally:
        if child is not None and child.poll() is None:
            # Always stop the owned child before leaving on timeout, Ctrl-C, HUP or TERM.
            # This does not undo an already-dispatched billable provider request.
            for sig in (signal.SIGTERM, signal.SIGHUP, signal.SIGINT):
                signal.signal(sig, signal.SIG_IGN)
            child.terminate()
            try:
                child.wait(timeout=5)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait()
            for sig in (signal.SIGTERM, signal.SIGHUP):
                signal.signal(sig, abort_on_signal)
            signal.signal(signal.SIGINT, signal.default_int_handler)


def main():
    args = sys.argv[1:]
    if args not in ([], ['--dry-run'], ['--live'], ['--preflight-network']):
        fail('USE_ONLY_DRY_RUN_LIVE_OR_PREFLIGHT_NETWORK')
    # Prevent OS core files before any key is entered. This is process-local.
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    for sig in (signal.SIGTERM, signal.SIGHUP):
        signal.signal(sig, abort_on_signal)
    cmd = command()
    mode = args[0] if args else '--dry-run'
    if mode != '--live':
        return run(cmd, mode)
    if not sys.stdin.isatty() or not sys.stderr.isatty():
        fail('LIVE_REQUIRES_USER_CONTROLLED_TERMINAL')
    if run(cmd, '--dry-run') != 0:
        fail('DRY_RUN_FAILED_NO_KEY_REQUESTED')
    if run(cmd, '--preflight-network') != 0:
        fail('NETWORK_PREFLIGHT_FAILED_NO_KEY_REQUESTED')
    print('\n准备完成。真实模型尚未调用。')
    print('此终端将向 DeepSeek 官方 API 发送最多 4 次合成验收对话。')
    print('模型 deepseek-flash；关闭思考；每次最多 256 生成 tokens；无重试、并发或平台回退。')
    print('会消耗你的 DeepSeek 余额；这是服务层测试，不是完整网页验收。')
    print('不读取你的图库、聊天日志或已有模型凭据；临时 key 只传入本次子进程。')
    print('费用/取消限制：已经发出的请求可能仍收费，本地中止不能撤回请求。')
    answer = input('由你亲自确认：输入 RUN 继续，其他输入取消：')
    if answer != 'RUN':
        print('LINGYE_LAUNCHER_CANCELLED=NO_MODEL_REQUESTS')
        return 0
    warnings.simplefilter('error', getpass.GetPassWarning)
    key = getpass.getpass('现在亲自输入临时 DeepSeek key（输入不可见；回车后开始测试）：')
    if not key or len(key) > 512 or any(ch.isspace() or ord(ch) < 32 for ch in key):
        fail('INVALID_KEY_SHAPE_VALUE_NOT_RECORDED')
    print('LINGYE_KEY_CONFIGURED=true（不显示值）', flush=True)
    try:
        return run(cmd, '--live', key)
    finally:
        key = None
        print('测试进程已结束。请撤销临时 key；没有保存到仓库或凭据库。')


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except SignalAbort as interrupted:
        print('\nLINGYE_LAUNCHER_CANCELLED=SIGNAL_INTERRUPTED', file=sys.stderr)
        raise SystemExit(128 + interrupted.signum)
    except (KeyboardInterrupt, EOFError):
        print('\nLINGYE_LAUNCHER_CANCELLED=USER_INTERRUPTED', file=sys.stderr)
        raise SystemExit(130)
    except getpass.GetPassWarning:
        fail('HIDDEN_INPUT_UNAVAILABLE')
    except subprocess.TimeoutExpired:
        fail('TEST_PROCESS_TIMED_OUT_NO_RETRY')
    except Exception:
        # Never print exception details, environment, command dumps or tracebacks.
        fail('UNEXPECTED_LAUNCHER_ERROR')
