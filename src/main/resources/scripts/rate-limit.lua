-- Day 24 과제 1: Rate Limit 원자화 스크립트
--
-- KEYS[1]   : Redis 키 (예: ratelimit:dm.send:42)
-- ARGV[1]   : 윈도우 크기(초)
--
-- 동작:
--   1) INCR 로 카운터를 1 증가시킨다.
--   2) 결과가 1이면 (= 이 스크립트 실행이 이 키의 첫 호출) EXPIRE 로 TTL 을 건다.
--   3) 증가 후 값을 반환한다.
--
-- 원자성:
--   Redis 는 Lua 스크립트를 싱글 스레드로, 한 덩어리로 실행한다.
--   INCR 와 EXPIRE 사이에 다른 클라이언트의 명령이 끼어들 수 없다.
--   → "INCR 했는데 EXPIRE 못 걸고 죽음" 상황 자체가 불가능해진다.

local current = redis.call('INCR', KEYS[1])

if current == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end

return current
