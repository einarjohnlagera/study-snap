## Overview

When a program gives a wrong result, adding print statements and rerunning is one way to find out why. A **debugger** is a tool that lets you stop a running program, look inside it and run it one step at a time, so you can *watch* the mistake happen instead of guessing where it is. This note introduces the core ideas of debugging with a debugger for C++17 programs on the command line: building a program with debugging information, setting breakpoints, stepping, inspecting variables and the call stack, and working through a real bug. The ideas are the same in graphical environments, which provide the same actions as buttons.

## What a debugger lets you do

| Action | What it gives you |
|---|---|
| **Breakpoint** | Stop the program at a chosen line, before that line runs |
| **Step** | Run one line at a time and watch the effect |
| **Inspect (print/watch)** | See the current value of any variable |
| **Call stack (backtrace)** | See which functions are currently active and how the program reached this point |
| **Continue** | Run on until the next breakpoint or the end |

The aim is the same as in all debugging: form a hypothesis about what is wrong, then use the tool to test it.

## Building for debugging

A debugger works with the compiled program, so the program must carry information that links machine code back to your source lines and variable names. Request it with the **`-g`** flag, and avoid optimization while debugging, because optimized code is rearranged and variables may disappear:

```
g++ -std=c++17 -Wall -Wextra -g -O0 -o program program.cpp
```

- `-g` includes the debugging information.
- `-O0` turns optimization off, so the program runs in the order you wrote it.

The most common debuggers on the command line are **`gdb`** (common on Linux) and **`lldb`** (the default on macOS). They are started on the built program, for example `gdb ./program` or `lldb ./program`. IDEs provide the same functions through a graphical interface.

## The core commands

The two debuggers use slightly different names for the same actions:

| Action | `gdb` | `lldb` |
|---|---|---|
| Set a breakpoint at a function | `break main` | `breakpoint set --name main` (or `b main`) |
| Set a breakpoint at a line | `break file.cpp:12` | `breakpoint set --file file.cpp --line 12` |
| Start the program | `run` | `run` |
| Run the next line (do not enter functions) | `next` | `next` |
| Run the next line (enter a function call) | `step` | `step` |
| Run until the current function returns | `finish` | `finish` |
| Show a variable | `print total` | `print total` (or `p total`) |
| Show the call stack | `backtrace` | `bt` |
| Continue to the next breakpoint | `continue` | `continue` |
| Quit | `quit` | `quit` |

**Step over versus step into:** `next` treats a function call as one line and moves past it; `step` goes *into* the called function. Use `next` for code you trust and `step` when you suspect the function you are about to call.

## A worked example

A program should add the numbers 1 to 5, which is 15, but it prints 10.

```cpp
#include <iostream>

int sumTo(int n) {
    int total = 0;
    for (int i = 1; i < n; i = i + 1) {      // line 5
        total = total + i;
    }
    return total;
}

int main() {
    int result = sumTo(5);
    std::cout << "Sum: " << result << '\n';  // prints 10, expected 15
    return 0;
}
```

### Step 1: reproduce and state the symptom

Build with `-g -O0`, run it, and note the facts: the output is 10, the expected value is 15.

### Step 2: form a hypothesis

The difference is 5, the last number. Perhaps the loop stops one step early. That is a hypothesis we can test.

### Step 3: stop where the suspicion is

The transcripts below are **illustrative**: the exact wording of the prompts and messages differs between `gdb`, `lldb` and IDEs, but the actions and the values observed are what matter.

```
(gdb) break sumTo
(gdb) run
Breakpoint 1, sumTo (n=5) at program.cpp:4
(gdb) next
(gdb) next
```

The program stops at the start of `sumTo`, and the debugger shows that `n` is 5, so the argument is correct. The first `next` runs `total = 0`; the second enters the loop.

### Step 4: watch the loop

Repeating `next` and printing the variables shows how they change:

```
(gdb) print i
$1 = 1
(gdb) print total
$2 = 0
```

After the loop body runs, `total` becomes 1, then 3, then 6, then 10. On the next check the loop ends.

```
(gdb) print i
$3 = 5
(gdb) print total
$4 = 10
```

When `i` reaches 5, the condition `i < n` is false, so `total` never includes 5. That confirms the hypothesis: the loop stops one value too early.

### Step 5: fix and verify

Change `i < n` to `i <= n`, rebuild, run again, and check that the output is 15. Rebuilding is essential: the debugger and the program use the *compiled* file, so editing the source does nothing until you recompile.

## The call stack

When a program crashes, the most useful single command is `backtrace` (`bt` in `lldb`). It lists the active functions, from the innermost to `main`, with the line each is on, so you can see how the program arrived at the failure:

```
(gdb) backtrace
#0  average (values=..., count=0) at stats.cpp:9
#1  0x... in report () at main.cpp:21
#2  0x... in main () at main.cpp:34
```

Here the crash is in `average` called from `report` called from `main`. You can often move to a frame and print its variables to see what was passed in; the arguments (for example `count=0`) frequently reveal the cause.

## Debugger versus print statements

| | Print statements | Debugger |
|---|---|---|
| Setup | None | Build with `-g`, learn commands |
| Change code to inspect | Yes, and rebuild each time | No |
| See any variable at any moment | Only the ones you printed | Any variable in scope |
| Step through the logic | No | Yes |
| Best for | Quick checks and logging | Understanding *why* a specific run misbehaves |

Both are useful; the debugger removes the cycle of adding output, rebuilding and rerunning.

## Habits for effective debugging

1. **Reproduce the problem reliably first.** Know the exact input and the expected result.
2. **Form a hypothesis before stepping**, so each step tests something.
3. **Bisect.** Put a breakpoint midway; decide whether the values are already wrong there, and narrow the range.
4. **Change one thing at a time**, then rebuild and retest.
5. **Always rebuild after an edit**, and confirm you are running the new executable.
6. **Compile with `-g -O0` while debugging**, and return to normal builds afterwards.

## Common misconceptions

- **"The debugger finds the bug."** It shows you the program's state; you still have to reason about what is wrong.
- **"I can debug an optimized build the same way."** Optimization reorders and removes code, so lines and variables may not match your source.
- **"Editing the source changes the running program."** Only a rebuild does.
- **"`step` and `next` are the same."** `next` goes over a call; `step` goes into it.
- **"A crash line is the mistake."** The failing line shows where the problem *appeared*; use the call stack and the variables to find where it *began*.

## Key points to remember

- A debugger stops a running program, shows its variables and call stack, and runs it line by line.
- Build with `-g` (and `-O0`) so the compiled program can be mapped back to your source.
- The essential actions are breakpoint, run, next, step, print, backtrace and continue, with equivalent commands in `gdb` and `lldb`.
- Debug by forming a hypothesis, testing it with a breakpoint and inspection, fixing one thing, rebuilding and rechecking.
- The call stack at a crash shows how the program reached the failure and which values it was given.
- Print statements are quick; a debugger lets you inspect any variable without changing and rebuilding the code.
