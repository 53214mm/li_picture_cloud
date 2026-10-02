import { delimiter, join } from 'node:path'

export const API_E2E_URL = 'http://127.0.0.1:18124/api'
export const API_E2E_OPT_IN = 'owned-test-e2e'
const REQUIRED_API_FILES = ['companion.api.js', 'gateway.api.js', 'protocol.api.js', 'recipe.api.js', 'story.api.js']

export function apiE2eFiles(names) {
  const files = names.filter(name => name.endsWith('.api.js')).sort()
  for (const required of REQUIRED_API_FILES) {
    if (!files.includes(required)) throw new Error(`Required API check is missing: ${required}`)
  }
  return files.map(name => `e2e/api/${name}`)
}

// Deliberately do not inherit SPRING_*, credentials, JAVA_TOOL_OPTIONS, proxy
// settings or arbitrary Maven arguments into the isolated fixture processes.
export function fixtureEnvironment(source, javaHome, mavenHome) {
  const env = {
    PATH: `${join(javaHome, 'bin')}${delimiter}${source.PATH || '/usr/bin:/bin'}`,
    JAVA_HOME: javaHome,
    MAVEN_USER_HOME: mavenHome,
    MAVEN_OPTS: `-Dmaven.user.home=${mavenHome}`,
    LANG: 'C.UTF-8',
    COMPANION_VISION_LIVE_TEST: 'false'
  }
  if (source.HOME) env.HOME = source.HOME
  if (source.TMPDIR) env.TMPDIR = source.TMPDIR
  return env
}

export function backendArguments(classpath, databaseName) {
  if (!/^lpc_api_[a-f0-9]{16}$/.test(databaseName)) throw new Error('Invalid fixture database name')
  return [
    '-cp', classpath, 'com.li.lipicturecloud.LiPictureCloudApplication',
    '--spring.profiles.active=test,e2e', '--server.address=127.0.0.1', '--server.port=18124',
    '--server.servlet.context-path=/api',
    `--spring.datasource.url=jdbc:h2:mem:${databaseName};MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER`,
    '--spring.datasource.username=sa', '--spring.datasource.password=',
    '--spring.datasource.driver-class-name=org.h2.Driver',
    '--spring.data.redis.host=127.0.0.1', '--spring.data.redis.port=6380',
    '--spring.data.redis.password=', '--spring.data.redis.database=15',
    '--app.companion.enabled=true', '--app.companion.feeding-enabled=true',
    '--app.companion.nutrition-policy=DEMO_ONLY', '--app.companion.chat-policy=DEMO_ONLY',
    '--app.creation.artwork-stub=true', '--app.model.credential.connectivity-stub=true',
    '--app.model.credential.language-stub=true', '--app.model.credential.image-stub=true',
    '--app.mcp.enabled=false', '--spring.ai.mcp.client.enabled=false',
    '--app.collaboration.store=memory'
  ]
}
