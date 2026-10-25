# The console understands ANSI colors.
RESET = "\033[0m"
names = ["black", "red", "green", "yellow", "blue", "magenta", "cyan", "white"]

for i, name in enumerate(names):
    print(f"\033[{30 + i}m{name:8}{RESET}  \033[1;{90 + i}mbright {name}{RESET}")

print("\033[1mbold\033[0m  \033[4munderline\033[0m  \033[7minverse\033[0m")
for i in range(0, 256, 8):
    print("".join(f"\033[48;5;{c}m  " for c in range(i, i + 8)) + RESET)
