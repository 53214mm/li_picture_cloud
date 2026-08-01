local stateKey = KEYS[1]
local commandKey = KEYS[2]
local ttlMillis = tonumber(ARGV[1])
local pictureId = ARGV[2]
local operation = ARGV[3]
local baseVersion = tonumber(ARGV[4])

local duplicate = redis.call('GET', commandKey)
if duplicate then
    local saved = cjson.decode(duplicate)
    redis.call('PEXPIRE', stateKey, ttlMillis)
    redis.call('PEXPIRE', commandKey, ttlMillis)
    return {'DUPLICATE', pictureId, tostring(saved.rotation), tostring(saved.scale), tostring(saved.version)}
end

if redis.call('EXISTS', stateKey) == 0 then
    redis.call('HSET', stateKey, 'rotation', '0')
    redis.call('HSET', stateKey, 'scale', '1.0')
    redis.call('HSET', stateKey, 'version', '0')
end

local rotation = tonumber(redis.call('HGET', stateKey, 'rotation'))
local scale = tonumber(redis.call('HGET', stateKey, 'scale'))
local version = tonumber(redis.call('HGET', stateKey, 'version'))

if baseVersion ~= version then
    return {'CONFLICT', pictureId, tostring(rotation), tostring(scale), tostring(version)}
end

if operation == 'ROTATE_LEFT' then
    rotation = (rotation - 90) % 360
elseif operation == 'ROTATE_RIGHT' then
    rotation = (rotation + 90) % 360
elseif operation == 'ZOOM_IN' then
    scale = math.min(4.0, math.floor((scale + 0.1) * 100 + 0.5) / 100)
elseif operation == 'ZOOM_OUT' then
    scale = math.max(0.25, math.floor((scale - 0.1) * 100 + 0.5) / 100)
else
    return redis.error_reply('unknown collaboration operation')
end

version = version + 1
redis.call('HSET', stateKey, 'rotation', tostring(rotation))
redis.call('HSET', stateKey, 'scale', tostring(scale))
redis.call('HSET', stateKey, 'version', tostring(version))
redis.call('PEXPIRE', stateKey, ttlMillis)
local result = cjson.encode({rotation = rotation, scale = scale, version = version})
redis.call('SET', commandKey, result, 'PX', ttlMillis)

return {'APPLIED', pictureId, tostring(rotation), tostring(scale), tostring(version)}
