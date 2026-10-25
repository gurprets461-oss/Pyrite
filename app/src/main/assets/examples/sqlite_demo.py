# sqlite3 is in the standard library.
import sqlite3

db = sqlite3.connect(":memory:")
db.execute("create table fruit (name text, price real)")
db.executemany("insert into fruit values (?, ?)", [("apple", 0.5), ("banana", 0.25), ("cherry", 3.0)])
for name, price in db.execute("select name, price from fruit order by price desc"):
    print(f"{name:8} ${price:.2f}")
print("Average price:", db.execute("select round(avg(price), 2) from fruit").fetchone()[0])
