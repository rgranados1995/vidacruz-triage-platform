-- Reads the projection of the emergency room in one round trip.
-- KEYS[1-5] set of active patients of each level, KEYS[6] time of the latest event applied
-- Returns the size of each set, then that time in epoch microseconds ('' before any event).
local result = {}
for index = 1, 5 do
    result[index] = redis.call('SCARD', KEYS[index])
end
result[6] = redis.call('GET', KEYS[6]) or ''
return result
