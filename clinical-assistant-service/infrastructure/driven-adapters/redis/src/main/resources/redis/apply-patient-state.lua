-- Applies the latest state of a patient to the projection of the emergency room, atomically.
-- KEYS[1]   hash of the patient (level, status, occurredAt)
-- KEYS[2]   time of the latest event applied
-- KEYS[3-7] set of active patients of each level
-- KEYS[8]   set of active patients of the level of this event
-- ARGV[1] patient id, ARGV[2] level, ARGV[3] status, ARGV[4] occurredAt (epoch microseconds),
-- ARGV[5] '1' if the patient is active, ARGV[6] seconds a discharged patient is remembered
-- Returns 1 when applied, 0 when an event newer than this one was already applied (it changes nothing).
local stored = redis.call('HGET', KEYS[1], 'occurredAt')
if stored and tonumber(stored) > tonumber(ARGV[4]) then
    return 0
end
for index = 3, 7 do
    redis.call('SREM', KEYS[index], ARGV[1])
end
redis.call('HSET', KEYS[1], 'level', ARGV[2], 'status', ARGV[3], 'occurredAt', ARGV[4])
if ARGV[5] == '1' then
    redis.call('SADD', KEYS[8], ARGV[1])
    redis.call('PERSIST', KEYS[1])
else
    -- Remembered for a while so that a late, older event cannot bring the patient back
    redis.call('EXPIRE', KEYS[1], ARGV[6])
end
local updated = redis.call('GET', KEYS[2])
if (not updated) or tonumber(updated) < tonumber(ARGV[4]) then
    redis.call('SET', KEYS[2], ARGV[4])
end
return 1
