"""离线补充验证：借用已有 Maven 报告的依赖清单，重新编译当前源码并在 Java 8 上执行 JUnit。

不会运行历史 target 中的项目类，不启动服务、不安装依赖；正式交付仍需 Maven reactor 构建。
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RUNNER = '''
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;
public class OfflineUnitRunner {
    public static void main(String[] args) {
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        org.junit.platform.launcher.Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(selectPackage("com.spai.portal")).build());
        listener.getSummary().printTo(new java.io.PrintWriter(System.out, true));
        listener.getSummary().printFailuresTo(new java.io.PrintWriter(System.out, true));
        if (listener.getSummary().getTestsFoundCount() == 0 || listener.getSummary().getTotalFailureCount() > 0) System.exit(1);
    }
}
'''

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--javac', type=Path, required=True)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--classpath-report', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    for path in (args.javac, args.java, args.classpath_report):
        if not path.is_file(): raise ValueError('缺少本地文件：' + str(path))
    # The historical report supplies library coordinates only; never use target classes or project jars.
    cp_value = ET.parse(args.classpath_report).find(".//property[@name='java.class.path']").get('value')
    jars = []
    cache = (ROOT / '.m2-cache').resolve()
    for entry in cp_value.replace('\\', '/').split(';'):
        if '/.m2-cache/' not in entry: continue
        path = (cache / entry.split('/.m2-cache/', 1)[1]).resolve()
        if not path.is_relative_to(cache) or not path.is_file(): raise ValueError('缺少缓存依赖：' + str(path))
        if '/com/spai/' in path.as_posix(): continue
        jars.append(path)
    launcher = cache / 'org/junit/platform/junit-platform-launcher/1.3.1/junit-platform-launcher-1.3.1.jar'
    if not launcher.is_file(): raise ValueError('缺少已缓存的 JUnit launcher')
    jars.append(launcher)
    args.output.mkdir(parents=True, exist_ok=False)
    output = args.output.resolve(); classes = output / 'classes'; classes.mkdir()
    runner = output / 'OfflineUnitRunner.java'; runner.write_text(RUNNER, encoding='utf-8')
    sources = sorted((ROOT / 'backend').glob('*/src/main/java/**/*.java')) + sorted((ROOT / 'backend').glob('*/src/test/java/**/*.java'))
    classpath = os.pathsep.join(str(path) for path in dict.fromkeys(jars))
    quoted = lambda text: '"' + str(text).replace('\\', '/') + '"'
    compiler_args = ['--release', '8', '-Xlint:-options', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(classes)] + [str(path) for path in sources] + [str(runner)]
    argfile = output / 'javac.args'; argfile.write_text('\n'.join(quoted(value) for value in compiler_args), encoding='utf-8')
    subprocess.run([str(args.javac), '-J-Dfile.encoding=UTF-8', '@' + str(argfile)], cwd=ROOT, check=True)
    for compiled in classes.rglob('*.class'):
        if int.from_bytes(compiled.read_bytes()[6:8], 'big') != 52: raise ValueError('发现非 Java 8 字节码')
    result = subprocess.run([str(args.java), '-Dfile.encoding=UTF-8', '-cp', str(classes) + os.pathsep + classpath, 'OfflineUnitRunner'], cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    (output / 'junit-result.txt').write_bytes(result.stdout)
    report_text = result.stdout.decode('utf-8', errors='replace')
    print(report_text[report_text.find('Test run finished'):])
    evidence = {'method': 'current source compiled with --release 8; Java 8 JUnit; no historical project binaries', 'sourceCount': len(sources), 'exitCode': result.returncode,
        'sources': {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}}
    (output / 'source-evidence.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding='utf-8')
    raise SystemExit(result.returncode)

if __name__ == '__main__': main()
