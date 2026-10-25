# The first 20 Fibonacci numbers, three ways.
from functools import lru_cache


def fib_loop(n):
    a, b = 0, 1
    for _ in range(n):
        a, b = b, a + b
    return a


@lru_cache(maxsize=None)
def fib_rec(n):
    return n if n < 2 else fib_rec(n - 1) + fib_rec(n - 2)


def fib_gen():
    a, b = 0, 1
    while True:
        yield a
        a, b = b, a + b


gen = fib_gen()
for i in range(20):
    assert fib_loop(i) == fib_rec(i) == next(gen)
    print(f"fib({i:2}) = {fib_loop(i)}")
