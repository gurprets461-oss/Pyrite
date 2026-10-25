# Guess the number: input() works in the console.
import random

secret = random.randint(1, 100)
tries = 0
print("I'm thinking of a number between 1 and 100.")
while True:
    text = input("Your guess: ")
    if not text.strip().isdigit():
        print("Please type a whole number.")
        continue
    tries += 1
    guess = int(text)
    if guess < secret:
        print("Higher!")
    elif guess > secret:
        print("Lower!")
    else:
        print(f"Got it in {tries} tries!")
        break
