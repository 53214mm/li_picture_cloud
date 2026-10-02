import assert from 'node:assert/strict'
import test from 'node:test'
import { API_E2E_OPT_IN, API_E2E_URL, backendArguments, fixtureEnvironment } from '../scripts/lib/api-e2e-policy.mjs'

test('API fixture environment excludes inherited credentials and runtime overrides', () => {
  const source = {
    PATH: '/usr/bin:/bin', HOME: '/test-home', TMPDIR: '/tmp',
    SPRING_DATASOURCE_URL: 'jdbc:mysql://remote/production',
    SPRING_APPLICATION_JSON: '{"server":{"port":8124}}',
    MYSQL_PASSWORD: 'should-not-inherit', REDIS_PASSWORD: 'should-not-inherit',
    JAVA_TOOL_OPTIONS: '-Dspring.profiles.active=prod', JDK_JAVA_OPTIONS: '-Xmx99g',
    MAVEN_OPTS: 'unsafe-override', MAVEN_ARGS: 'deploy', HTTP_PROXY: 'proxy-with-secret',
    DASHSCOPE_API_KEY: 'should-not-inherit', COMPANION_VISION_LIVE_TEST: 'true'
  }
  const env = fixtureEnvironment(source, '/tools/jdk', '/tools/maven')
  assert.deepEqual(env, {
    PATH: '/tools/jdk/bin:/usr/bin:/bin', HOME: '/test-home', TMPDIR: '/tmp',
    JAVA_HOME: '/tools/jdk', MAVEN_USER_HOME: '/tools/maven',
    MAVEN_OPTS: '-Dmaven.user.home=/tools/maven', LANG: 'C.UTF-8', COMPANION_VISION_LIVE_TEST: 'false'
  })
})

test('API fixture command pins profiles, local stores, demo behavior and every external stub', () => {
  const args = backendArguments('/test/classpath', 'lpc_api_0123456789abcdef')
  assert.deepEqual(args.slice(0, 3), ['-cp', '/test/classpath', 'com.li.lipicturecloud.LiPictureCloudApplication'])
  for (const option of [
    '--spring.profiles.active=test,e2e', '--server.address=127.0.0.1', '--server.port=18124',
    '--spring.datasource.url=jdbc:h2:mem:lpc_api_0123456789abcdef;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER',
    '--spring.datasource.username=sa', '--spring.datasource.password=', '--spring.datasource.driver-class-name=org.h2.Driver',
    '--spring.data.redis.host=127.0.0.1', '--spring.data.redis.port=6380', '--spring.data.redis.password=', '--spring.data.redis.database=15',
    '--app.companion.nutrition-policy=DEMO_ONLY', '--app.companion.chat-policy=DEMO_ONLY',
    '--app.creation.artwork-stub=true', '--app.model.credential.connectivity-stub=true',
    '--app.model.credential.language-stub=true', '--app.model.credential.image-stub=true',
    '--spring.ai.mcp.client.enabled=false', '--app.mcp.enabled=false', '--app.collaboration.store=memory'
  ]) assert.ok(args.includes(option), option)
  assert.equal(API_E2E_URL, 'http://127.0.0.1:18124/api')
  assert.equal(API_E2E_OPT_IN, 'owned-test-e2e')
})

test('API fixture rejects database names that can inject H2 connection options', () => {
  for (const name of ['production', 'lpc_api_0123456789abcdef;INIT=RUNSCRIPT', '', null]) {
    assert.throws(() => backendArguments('cp', name), /Invalid fixture database name/)
  }
})
