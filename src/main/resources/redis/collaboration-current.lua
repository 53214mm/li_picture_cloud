local stateKey = KEYS[1]
local ttlMillis = tonumber(ARGV[1])
local pictureId = ARGV[2]

if redis.call('EXISTS', stateKey) == 0 then
    redis.call('HSET', stateKey, 'rotation', '0', 'scale', '1.0', 'version', '0')
end
redis.call('PEXPIRE', stateKey, ttlMillis)

return {pictureId, redis.call('HGET', stateKey, 'rotation'),
        redis.call('HGET', stateKey, 'scale'), redis.call('HGET', stateKey, 'version')}
