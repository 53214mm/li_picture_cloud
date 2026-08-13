import { spawn } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const repositoryRoot = resolve(here, '../..')
const testClasses = resolve(repositoryRoot, 'target/test-classes')
const mavenArguments = [
  '-q',
  '-DskipTests',
  '-Dspring-boot.run.profiles=test,e2e',
  '-Dspring-boot.run.useTestClasspath=true',
  `-Dspring-boot.run.additional-classpath-elements=${testClasses}`,
  'test-compile',
  'spring-boot:run'
]
const command = process.platform === 'win32' ? 'powershell.exe' : './mvnw'
const commandArguments = process.platform === 'win32'
  ? ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
      resolve(repositoryRoot, 'scripts/mvnw-java21.ps1'), ...mavenArguments]
  : mavenArguments
const child = spawn(command, commandArguments, {
  cwd: repositoryRoot,
  stdio: 'inherit',
  shell: false
})

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => child.kill(signal))
}

child.on('exit', code => process.exit(code ?? 1))
