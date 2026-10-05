## Overview

An **algorithm** is a finite, precise sequence of steps that solves a problem or accomplishes a task. Algorithms are the central idea of computing: before any program is written, someone must be able to say exactly how a problem will be solved. An algorithm is independent of any particular programming language, machine or notation. The same algorithm can be written in everyday language, in pseudocode, as a diagram, or as a program in any language.

This note defines what an algorithm is, the properties that separate an algorithm from a vague set of instructions, the basic building blocks every algorithm is made from, and the difference between an algorithm and a program.

## A first example

**Problem:** given a list of numbers, find the largest one.

A procedure written in plain steps:

1. Treat the first number as the largest seen so far.
2. Look at each remaining number in turn.
3. If the number is larger than the largest seen so far, make it the new largest.
4. When no numbers remain, the largest seen so far is the answer.

The same algorithm in pseudocode:

```
ALGORITHM FindLargest(list)
    largest ← first item of list
    FOR EACH item IN the rest of list
        IF item > largest THEN
            largest ← item
        END IF
    END FOR
    RETURN largest
END ALGORITHM
```

Tracing it on the list 4, 9, 2, 7:

| Item examined | largest after the step |
|---|---|
| (start) | 4 |
| 9 | 9 |
| 2 | 9 |
| 7 | 9 |

The result is 9. Notice that the algorithm says *what to do at each step* without mentioning any programming language.

## Properties of an algorithm

A set of instructions is an algorithm only if it has these properties.

1. **Input.** It accepts zero or more well-defined inputs (here, a list of numbers).
2. **Output.** It produces at least one result related to the inputs (here, the largest number).
3. **Definiteness.** Every step is stated precisely and unambiguously, so two people following it get the same behaviour. "Pick a good number" is not definite; "pick the first number" is.
4. **Finiteness.** It stops after a finite number of steps for every valid input. A procedure that can run forever is not an algorithm for that problem.
5. **Effectiveness.** Each step is basic enough to be carried out exactly, in principle by a person with pencil and paper. "Compute the exact decimal expansion of π" is not an effective step.

A further requirement is **correctness**: for every valid input, the output must actually solve the problem. The first five properties describe what makes something an algorithm; correctness describes whether it is a *good* algorithm for a given problem.

### Examples and non-examples

| Instructions | Algorithm? | Why |
|---|---|---|
| "Add the numbers from 1 to n." | Yes | Precise, finite for any whole number n. |
| "Make the tastiest soup." | No | Not definite: "tastiest" is undefined. |
| "Keep adding 1 to the total and report when you are done." | No | Never finishes, so not finite, and "done" is never defined. |
| "Divide by the number of items." (if the list may be empty) | Incomplete | Fails on an input the problem allows. |

## The building blocks of algorithms

Any algorithm can be built from three kinds of step.

- **Sequence**: steps carried out one after another, in order.
- **Selection** (decision): choose between alternatives depending on a condition (IF ... THEN ... ELSE).
- **Repetition** (iteration): repeat steps while or until a condition holds (WHILE, FOR EACH).

A second example uses all three. **Euclid's algorithm** finds the greatest common divisor of two positive whole numbers a and b:

```
ALGORITHM GCD(a, b)
    WHILE b ≠ 0
        remainder ← a mod b
        a ← b
        b ← remainder
    END WHILE
    RETURN a
END ALGORITHM
```

For a = 48 and b = 18: (48, 18) → (18, 12) → (12, 6) → (6, 0), so the result is 6. The repetition ends because the remainder strictly decreases and cannot go below zero, which is why it is finite.

## Ways to express an algorithm

| Representation | Strength | Limitation |
|---|---|---|
| Natural language | Easy to read at first | Prone to ambiguity |
| Pseudocode | Precise and structured, not tied to a language | No formal standard; must be written carefully |
| Flowchart | Shows the flow of decisions and repetition visually | Becomes unwieldy for large algorithms |
| Program | Can be executed by a machine | Includes language details that hide the idea |

Choosing a representation is a communication decision. The algorithm itself stays the same.

## Algorithm versus program

A **program** is an implementation of an algorithm in a particular programming language, for a particular machine or system. The algorithm is the idea; the program is one concrete expression of it. The same algorithm can be implemented as many different programs, and a program can contain details (memory handling, input and output formats, error messages) that the algorithm deliberately leaves out.

This distinction matters in practice: you can reason about correctness and efficiency at the level of the algorithm, then translate it into code as a separate step.

## Correctness and efficiency

Several different algorithms can solve the same problem. To compare them we ask two questions.

- **Is it correct?** Does it give the right answer for every valid input, including edge cases such as an empty list, a single item, or repeated values?
- **Is it efficient?** How does the number of steps, or the amount of memory, grow as the input gets larger?

For example, to find a value in a list that is already sorted, one algorithm checks items one by one from the start. A different algorithm repeatedly looks at the middle item and discards half the remaining list. Both are correct, but for large lists the second needs far fewer steps. Faster hardware helps, but it does not replace a better algorithm: the difference in growth quickly outweighs a constant speed-up.

## Checking an algorithm by tracing

A reliable way to understand or test an algorithm is to **trace** it by hand: choose an input, execute the steps exactly, and record how the values change. Good test inputs include:

- a typical case;
- the smallest valid case (such as an empty or one-item list);
- cases at the boundary of a condition;
- cases with repeated or equal values.

Tracing finds missing cases before any code exists.

## Common misconceptions

- **"An algorithm is code."** Code is one way to express it; the algorithm exists independently of any language.
- **"If it runs, it is correct."** A program can run and still give wrong answers on inputs nobody tried.
- **"There is one algorithm per problem."** Many problems have several algorithms with different trade-offs.
- **"A faster computer fixes a slow algorithm."** Growth in the work required can outpace any fixed speed-up.
- **"Vague steps are fine if a smart reader will understand."** Definiteness means the steps are unambiguous to anyone, and to a machine.

## Key points to remember

- An algorithm is a finite, precise, effective sequence of steps that turns inputs into outputs.
- Its defining properties are input, output, definiteness, finiteness and effectiveness; correctness measures whether it solves the problem for all valid inputs.
- Every algorithm is built from sequence, selection and repetition.
- Algorithms can be written as plain language, pseudocode or flowcharts, and are independent of programming languages.
- A program implements an algorithm; several algorithms can solve the same problem with different efficiency.
- Tracing with well-chosen inputs, including edge cases, is the basic way to check an algorithm.
