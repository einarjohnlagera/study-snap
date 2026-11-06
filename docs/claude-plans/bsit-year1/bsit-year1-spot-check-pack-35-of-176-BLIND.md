Status: BLIND spot-check pack for an independent reviewer, 2026-11-05. READ-ONLY EXTRACT of production state. Contains NO prior opinions and NO dispositions. NOT implementation-ready.

# BSIT Year 1: spot-check pack for the metadata-ready Notes (35 of 176)

**Purpose.** 176 Notes in the proposed BSIT Year 1 journey are "metadata-ready": they already carry Information Technology, an explicit Domain Context and `COLLEGE` depth, but nobody has re-read their bodies for BSIT. This pack is a **risk-weighted sample of 35**, chosen to find the problems the full set is most likely to hide. Apply the same rubric used for the Discrete Mathematics Notes (A canonical equivalence, B prerequisites and depth, C assessment treatment, D disposition, E record), and record a **recommended** disposition per Note in `bsit-year1-spot-check-manifest-35.tsv`.

The question for every Note: could this exact Note, with its existing Study Pack, **unchanged**, serve a first-year BS Information Technology learner?

## How the 35 were chosen (so you can judge the coverage)
The 176 are: Introduction to Computing 38, Computer Programming 1 42, Computer Programming 2 31, Mathematics in the Modern World 21, Purposive Communication 12, The Contemporary World 9, Understanding the Self 8, Readings in Philippine History 8, Art Appreciation 7. A Note can be in more than one category.
- **A. Asymptotic notation (12 Notes).** An earlier review found a Discrete Mathematics Note whose quiz used Theta notation that its body never defines. A text search across all 176 bodies found 12 that use Theta, Omega, Big-O or O(...) notation. All 12 are in the sample. (An earlier draft of this header said 13; the thirteenth was a false positive from a case-insensitive search.)
- **B. C++-specific Notes (6 of 42).** Reuse of these depends on the approved C++17 reference language. Sampled across Computer Programming 1 and 2 and across topics (toolchain, pointers, exceptions, dynamic memory, testing, file I/O).
- **C. Computing-flavoured Mathematics in the Modern World Notes (6).** Codes, cryptography, graphs, modular arithmetic and linear programming were authored with a computing audience in mind.
- **D. Introduction to Computing Notes written for a computing audience (7).** Professional societies, organizational solutions, Turing and von Neumann, AI, Philippine computing laws, computing in other fields, the computing industry.
- **E. General Education Notes touching technology or workplace writing (4).**
- Plan coverage: Computer Programming 2 has 14, Introduction to Computing 7, Mathematics in the Modern World 6, Computer Programming 1 4, Purposive Communication 2, The Contemporary World 1, Understanding the Self 1. **Readings in Philippine History and Art Appreciation are not sampled**: they are history and art Notes with no computing framing and were judged the lowest risk. Say so if you want them added.

## Automated screen over ALL 176 bodies (FACT, a text search, not a review)
- Mentions of "computer science student/major/program/curriculum", "BSCS" or "CS student": **0**.
- Mentions of teacher, pedagogy, classroom or lesson plan: **0**.
- C++ appearing anywhere in the body of a Note whose title does not name C++ (the BSCS convention D-35 acceptance check): **0** among the 31 language-neutral programming Notes.
- Other languages named (Python, Java, JavaScript): 6 Notes (Documentation, Comments and Coding Style; Syntax, Runtime and Logic Errors; Nested Loops; HTML Document Structure and Elements; The World Wide Web: Browsers, Servers and Web Pages; Programming Languages and Language Levels). I read the matching sentences: in every case the language is an illustrative example ("in languages such as C, Java, and Python"; "a runtime error ... in Python"; "executes JavaScript"), not a required language. Only one of the 6 (Nested Loops) is in the sample; the rest are not, so treat this as a text-search observation, not a review.
- The text search proves absence of those phrases only. It does not prove the Notes are suitable.

## Instructions
- Source: production `notes` and `study_packs`, read 2026-11-05, exactly as stored. Each Note has one Study Pack with 5 quiz items.
- Nothing was modified. Your result is a **recommendation**; the owner is the curator and approves.
- Do not reopen: the Computing Domain Context, the C++17 reference language, the BSCS decisions. A Note that uses C++ is fine if its title names C++ (the S convention).
- Label claims FACT (from the pack), INFERENCE or PROPOSAL. If something is undecidable from the text, say HOLD and say what evidence would settle it.
- Beyond the rubric, flag any **quiz item that tests something the body does not teach**, any answer or explanation you believe is wrong, and any Note whose depth is beyond a first-year IT course.

---

## 1. Nested Loops

| Field | Value |
|---|---|
| Note id | `d8d3ea8d-db2c-4ff4-9b40-28866eb58b31` |
| Plan / Section | ⌨️ Computer Programming 1 / Iteration |
| Why sampled | A: uses asymptotic notation (same class as the Theta issue in DM Note 22) |
| Subject / Domain Context / Authored Depth | Programming Fundamentals / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Nested Loops

📘 Overview
Nested loops place one loop inside another to repeat work across multiple levels of iteration. Their total work depends on how often the inner loop runs during each outer-loop iteration.

🧠 Key Idea
Count inner-loop executions across all outer-loop iterations. Multiply iteration counts for fixed, independent bounds; add the changing counts when the inner bound depends on the outer index.

⚔️ Core Details:
- In normal execution, the inner loop completes all its iterations before the outer loop advances.
- For fixed bounds, the innermost body executes $mn$ times, where $m$ is the outer iteration count and $n$ is the inner iteration count.
- An inner loop running $i$ times on outer iteration $i$ executes its body $\sum_{i=1}^{n} i = \frac{n(n+1)}{2}$ times across $n$ outer iterations.
- Two nested loops that each run $n$ times have time complexity $\Theta(n^2)$ when the innermost body takes constant time.
- A break statement exits only the nearest enclosing loop in languages such as C, Java, and Python.
- A continue statement skips the remaining statements in the current iteration of the nearest enclosing loop.

🎯 Why It Matters:
- Nested loops express traversal of grids, tables, and multidimensional arrays.
- Accurate iteration counts reveal performance costs hidden by short source code.
- Dependent bounds appear in pairwise comparisons and triangular traversals, where multiplying identical bounds overcounts the work.
- Separating loop indices and checking boundaries helps prevent incorrect traversal and nontermination.

🧠 Quick Recall:
- Nested loop — a loop contained within the body of another loop.
- Outer loop — controls how many times execution reaches the inner loop.
- Rectangular traversal — $mn$ body executions for $m$ outer iterations and $n$ inner iterations per outer iteration.
- Triangular traversal — $\frac{n(n+1)}{2}$ body executions when inner counts increase from one through $n$.
- Auxiliary space — $\Theta(1)$ for a fixed number of loop counters with no additional growing storage; nesting alone does not imply quadratic space.
```

### Study Pack

**Summary:** Nested loops place one loop inside another. In normal execution, the inner loop completes before the outer loop advances. To determine total work, count inner-loop body executions across all outer iterations.

| Traversal | Inner-loop behavior | Total body executions |
|---|---|---|
| Rectangular | Fixed $n$ iterations for each of $m$ outer iterations | $mn$ |
| Triangular | $i$ iterations on outer iteration $i$, from $1$ through $n$ | $\frac{n(n+1)}{2}$ |

Multiply iteration counts when bounds are fixed and independent. When inner bounds depend on the outer index, add the changing counts: $\sum_{i=1}^{n} i = \frac{n(n+1)}{2}$.

Two nested loops that each run $n$ times take $\Theta(n^2)$ time if the innermost body takes constant time. A fixed number of loop counters requires $\Theta(1)$ auxiliary space when no additional growing storage is used.

In C, Java, and Python, break exits only the nearest enclosing loop. A continue statement skips the remaining statements in the current iteration of the nearest enclosing loop. Nested loops support grids, tables, multidimensional arrays, and triangular traversals. Separate loop indices and careful boundary checks help prevent traversal errors and nontermination.

**Common Misconceptions**: Nesting alone does not imply quadratic space; dependent bounds cannot always be counted by multiplying identical bounds; an inner-loop break does not automatically exit the outer loop.

**Key concepts:** Nested loops; Outer loop; Rectangular traversal; Triangular traversal; Dependent bounds; Time complexity; Auxiliary space; Break statement; Continue statement

**Q1** (MCQ, COMPUTATIONAL): An outer loop runs $3$ times, and its inner loop runs $4$ times per outer iteration. With no early exits, how many times does the innermost body execute?

- A. $7$
- B. $12$ ✅
- C. $16$
- D. $24$

_Explanation:_ For fixed, independent bounds, multiply the outer iteration count by the inner iteration count: $3 \times 4 = 12$. The innermost body executes $12$ times.

**Q2** (MCQ, COMPUTATIONAL): An outer loop uses index $i$ from $1$ through $4$. Its inner loop executes $i$ times per outer iteration. How many total inner-loop body executions occur?

- A. $4$
- B. $8$
- C. $16$
- D. $10$ ✅

_Explanation:_ Add the changing inner-loop counts: $1 + 2 + 3 + 4 = 10$. Equivalently, $\frac{4(4+1)}{2} = 10$. The total is $10$.

**Q3** (MCQ, CONCEPTUAL): Which best describes two nested loops that each execute $n$ times, have a constant-time innermost body, and use only a fixed number of counters with no growing storage?

- A. Time $\Theta(n^2)$; auxiliary space $\Theta(1)$ ✅
- B. Time $\Theta(n)$; auxiliary space $\Theta(1)$
- C. Time $\Theta(n^2)$; auxiliary space $\Theta(n^2)$
- D. Time $\Theta(n)$; auxiliary space $\Theta(n)$

_Explanation:_ Multiplying the two iteration counts gives quadratic growth in body executions. The number of stored counters remains fixed as the input grows, so nesting does not itself increase auxiliary storage.

**Q4** (MCQ, CONCEPTUAL): In C, Java, or Python, which claim about a break executed inside an inner loop is NOT correct?

- A. It exits the nearest enclosing loop.
- B. It can reduce the total number of inner-loop body executions.
- C. It automatically terminates both the inner and outer loops. ✅
- D. The outer loop can continue after the inner loop exits.

_Explanation:_ A break affects only its nearest enclosing loop. An enclosing outer loop remains active unless separate control flow terminates it.

**Q5** (MCQ, CONCEPTUAL): Statement 1: During normal nested-loop execution, the inner loop completes its iterations before the outer loop advances. Statement 2: A continue inside the inner loop skips the remaining statements of the current outer-loop iteration. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Normal execution finishes the inner loop before advancing the outer loop. A continue applies to the nearest enclosing loop, so an inner-loop continue affects the current inner iteration rather than the outer iteration.

---

## 2. Algorithms and Their Properties

| Field | Value |
|---|---|
| Note id | `583c82dc-fc5b-4088-ae21-dd474a787be3` |
| Plan / Section | ⌨️ Computer Programming 1 / Problem Solving and Algorithms |
| Why sampled | A: uses asymptotic notation (same class as the Theta issue in DM Note 22) |
| Subject / Domain Context / Authored Depth | Programming Fundamentals / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Algorithms and Their Properties

📘 Overview
An algorithm is a finite, precisely specified sequence of effective steps for solving a computational problem. Its specification defines valid inputs and required outputs, while correctness and efficiency determine whether it solves the problem reliably and with acceptable resource use.

🧠 Key Idea
An algorithm must terminate and produce the required result for every valid input. Correctness concerns the result; efficiency concerns the time and memory needed to obtain it.

⚔️ Core Details:
- Input-output contract: An algorithm specifies zero or more inputs and at least one result satisfying the problem's requirements.
- Definiteness: Every operation and decision has a precise meaning.
- Finiteness: Execution terminates after a finite number of steps for every valid input.
- Effectiveness: Each basic operation is executable in finite time using the computational model's available operations.
- Correctness: The algorithm satisfies its output specification for every input meeting its preconditions.
- Efficiency: Time complexity and space complexity describe how computational work and memory requirements scale with input size.

🎯 Why It Matters:
- Explicit input and output contracts expose assumptions that could otherwise cause failures on empty, boundary, or malformed inputs.
- Separating correctness from efficiency prevents a fast but incorrect implementation from being accepted as a valid solution.
- Resource-growth analysis supports algorithm selection when input sizes become too large for small test cases to represent performance.
- Precise algorithm descriptions support implementation in different programming languages without changing the intended computation.

🧠 Quick Recall:
- Algorithm — A finite, precisely specified procedure composed of executable steps for solving a computational problem.
- Precondition and postcondition — A required condition before execution and a guaranteed condition after successful completion.
- Partial correctness — The required result is produced if execution terminates.
- Total correctness — Partial correctness together with guaranteed termination for every valid input.
- Big-O notation — An asymptotic upper bound on resource growth; $O(n)$ denotes a linear upper bound in input size $n$.
```

### Study Pack

**Summary:** An algorithm is a finite, precisely specified sequence of executable steps for solving a computational problem. Its input-output contract specifies valid inputs, preconditions, and required results. It may accept zero or more inputs but must produce at least one result.

Definiteness requires unambiguous operations and decisions. Effectiveness requires each basic operation to be executable in finite time using the computational model's available operations. Finiteness requires the entire procedure to terminate for every valid input.

| Concept | What it establishes |
|---|---|
| Partial correctness | The required result is produced if execution terminates. |
| Total correctness | Partial correctness plus guaranteed termination for every valid input. |
| Efficiency | How time and memory requirements scale with input size. |

Time complexity describes computational work; space complexity describes memory requirements. Big-O notation gives an asymptotic upper bound on resource growth; $O(n)$ denotes a linear upper bound in input size $n$. Resource-growth analysis supports algorithm selection beyond small test cases, while precise specifications support consistent implementation across programming languages.

**Common Misconceptions**: Fast execution does not establish correctness. Partial correctness does not guarantee termination. Big-O describes an upper bound, not an exact execution time.

**Key concepts:** Input-output contract; Definiteness; Finiteness; Effectiveness; Preconditions and postconditions; Partial correctness; Total correctness; Time and space complexity; Big-O notation

**Q1** (MCQ, CONCEPTUAL): What should an algorithm's input-output contract establish?

- A. The programming language and compiler used
- B. The valid inputs and required results ✅
- C. The exact running time on every computer
- D. The results observed in selected test cases

_Explanation:_ A contract makes the problem's assumptions and success criteria explicit, allowing implementations to be checked against the specification.

**Q2** (MULTI_SELECT, CONCEPTUAL): Select the two requirements that together establish definiteness and effectiveness.

- A. Every operation and decision has a precise meaning. ✅
- B. Every basic operation is executable in finite time within the computational model. ✅
- C. Every implementation uses the smallest possible amount of memory.
- D. Every algorithm is written in a particular programming language.

_Explanation:_ An algorithm needs unambiguous instructions that its computational model can actually carry out. These properties concern the meaning and executability of individual steps.

**Q3** (MCQ, CONCEPTUAL): Statement 1: Partial correctness guarantees the required result if execution terminates. Statement 2: Total correctness additionally requires termination for every valid input. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ A conditional guarantee about the result leaves termination unresolved. Establishing total correctness also requires showing that execution finishes throughout the valid input domain.

**Q4** (MCQ, CONCEPTUAL): Which best describes the role of algorithm efficiency analysis?

- A. Proving that every output satisfies the postcondition
- B. Specifying which inputs meet the preconditions
- C. Removing ambiguity from individual decisions
- D. Assessing how computational work and memory scale with input size ✅

_Explanation:_ Resource-growth analysis helps predict whether an approach remains practical as problems become larger, even when small test cases appear fast.

**Q5** (MCQ, CONCEPTUAL): Which claim about Big-O notation is NOT correct?

- A. It expresses an asymptotic upper bound.
- B. It describes resource growth relative to input size.
- C. It specifies the exact execution time for every input. ✅
- D. It can describe the growth of memory requirements.

_Explanation:_ Asymptotic bounds characterize how resource requirements scale rather than giving precise timing measurements for individual executions.

---

## 3. Building and Running a C++ Program

| Field | Value |
|---|---|
| Note id | `bb34133d-7427-46ca-a8de-b0364cc2236a` |
| Plan / Section | ⌨️ Computer Programming 1 / Problem Solving and Algorithms |
| Why sampled | B: C++-specific Note (reuse depends on Q1) |
| Subject / Domain Context / Authored Depth | Programming Fundamentals / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Building and Running a C++ Program

📘 Overview
A C++ program becomes runnable through preprocessing, compilation, assembly, and linking. Separating these build stages helps distinguish source-code errors, unresolved dependencies, and failures that occur during execution.

🧠 Key Idea
Building translates source files into object files and links them with required libraries to produce an executable. Running executes that artifact; a successful build does not guarantee correct behavior.

⚔️ Core Details:
- Preprocessing handles directives such as #include, #define, and conditional compilation before translation into machine code.
- Compilation translates each preprocessed translation unit into assembly; assembly converts that output into an object file.
- Linking combines object files and required libraries, resolves external symbol references, and produces an executable.
- With GCC, g++ main.cpp -o app builds an executable; g++ -c main.cpp produces an object file without linking.
- On a typical Unix-like system, ./app launches the executable from the current directory; hosted C++ execution enters main after runtime initialization.
- A debug build such as g++ -g -O0 main.cpp -o app includes debugging information and disables optimization for easier source-level inspection.

🎯 Why It Matters:
- Identifying the failing build stage narrows troubleshooting: syntax and type errors differ from linker errors such as undefined references.
- Separate compilation allows unchanged source files to reuse existing object files, reducing rebuild time in larger projects.
- Testing and debugging remain necessary because logical errors and undefined behavior can exist in successfully built programs.
- Executables target a particular platform and architecture; distributing source code does not eliminate compiler or library compatibility requirements.

🧠 Quick Recall:
- Translation unit — A source file after preprocessing, including the content introduced through its headers.
- Object file — Machine code and metadata awaiting linking; commonly stored with a .o or .obj extension.
- Undefined reference — A linker error indicating that a referenced symbol lacks a required definition in the linked inputs.
- Breakpoint — A debugger stop point for inspecting program state and stepping through execution.
- Exit status — A value reported to the execution environment; returning zero from main indicates successful termination.
```

### Study Pack

**Summary:** A C++ program becomes runnable through preprocessing, compilation, assembly, and linking. Preprocessing handles #include, #define, and conditional compilation. A translation unit is a source file after preprocessing, including content introduced through headers.

| Stage | Main task | Output |
|---|---|---|
| Compilation | Translates a preprocessed translation unit | Assembly code |
| Assembly | Converts assembly into machine code and metadata | Object file |
| Linking | Combines object files and libraries and resolves external symbols | Executable |

Object files commonly use .o or .obj extensions. Separate compilation allows existing object files to be reused when their source files have not changed, reducing rebuild time.

With GCC, g++ main.cpp -o app builds an executable, while g++ -c main.cpp produces an object file without linking. On a typical Unix-like system, ./app launches the executable from the current directory. Hosted C++ execution enters main after runtime initialization. Returning zero from main reports successful termination to the execution environment through an exit status.

Syntax and type errors arise during compilation; an undefined reference is a linker error indicating that a referenced symbol lacks a required definition in the linked inputs. The command g++ -g -O0 main.cpp -o app includes debugging information and disables optimization for easier source-level inspection. Breakpoints pause execution so program state can be inspected.

Executables target particular platforms and architectures. Distributing source still requires compatible compilers and libraries.

**Common Misconceptions**: A successful build does not guarantee correct behavior; logical errors and undefined behavior may remain. An object file is not the final linked executable.

**Key concepts:** Preprocessing; Translation units; Compilation; Assembly; Object files; Symbol resolution; Separate compilation; Breakpoints; Exit status

**Q1** (MCQ, CONCEPTUAL): Which build stage processes #include, #define, and conditional compilation directives?

- A. Preprocessing ✅
- B. Compilation
- C. Assembly
- D. Linking

_Explanation:_ These directives must be handled before the resulting translation unit is translated into assembly code.

**Q2** (MCQ, CONCEPTUAL): Which statement about linking is NOT correct?

- A. It combines object files with required libraries.
- B. It resolves external symbol references.
- C. It reports undefined references when required definitions are missing.
- D. It translates preprocessed source directly into assembly code. ✅

_Explanation:_ Source-to-assembly translation belongs to compilation. Linking occurs later, when separately produced machine-code inputs are combined.

**Q3** (MCQ, CONCEPTUAL): Which best describes the result of running g++ -c main.cpp successfully?

- A. An executable ready to launch with ./app
- B. An object file that has not undergone linking ✅
- C. A running program paused at a breakpoint
- D. A source file containing only expanded headers

_Explanation:_ The -c flag stops the build before external symbols are resolved and the final executable is produced. The generated machine code can be reused in a later link step.

**Q4** (MCQ, CONCEPTUAL): Statement 1: A successful build guarantees that a program has no logical errors or undefined behavior. Statement 2: Returning zero from main indicates successful termination to the execution environment. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct ✅
- D. Neither statement is correct

_Explanation:_ Building verifies that the build stages can complete, not that runtime behavior is correct. The value returned from main communicates termination status, with zero conventionally reporting success.

**Q5** (MCQ, CONCEPTUAL): Why use g++ -g -O0 main.cpp -o app when preparing for source-level debugging?

- A. To skip linking and retain only an object file
- B. To guarantee that runtime errors cannot occur
- C. To make the executable independent of its target architecture
- D. To include debugging information and disable optimization ✅

_Explanation:_ The -g flag supplies information used by the debugger, while -O0 keeps optimization from complicating source-level inspection. Breakpoints can then help examine program state during execution.

---

## 4. Recursion: Base Case and Recursive Case

| Field | Value |
|---|---|
| Note id | `20cc025e-0626-489a-b970-4d6c089a08f7` |
| Plan / Section | ⌨️ Computer Programming 1 / Recursion |
| Why sampled | A: uses asymptotic notation (same class as the Theta issue in DM Note 22) |
| Subject / Domain Context / Authored Depth | Programming Fundamentals / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Recursion: Base Case and Recursive Case

📘 Overview
A recursive function solves a problem by calling itself on a smaller or simpler instance. Correct recursion requires a base case that stops further calls and a recursive case that makes progress toward it.

🧠 Key Idea
Every recursive path must eventually reach a base case. The recursive case reduces the problem and uses the result of the smaller call to produce the current result.

⚔️ Core Details:
- A base case returns a result without making another recursive call.
- A recursive case calls the same function with an input that moves toward a base case.
- For factorial on nonnegative integers, the base case is $0! = 1$.
- The recursive factorial rule is $n! = n \times (n-1)!$ for integers $n > 0$.
- Each active call has a stack frame containing its execution state, such as parameters, local variables, and a return location.
- A missing or unreachable base case can cause unbounded recursion and eventual stack overflow in stack-based implementations.

🎯 Why It Matters:
- Recursive definitions translate naturally into implementations for tree traversal, nested data processing, and divide-and-conquer algorithms.
- Checking both the stopping condition and progress toward it exposes termination errors that a base-case check alone misses.
- Tracking pending calls explains return-value propagation and the memory cost of deep recursion.

🧠 Quick Recall:
- Base case — A directly solvable input handled without another recursive call.
- Recursive case — A rule that reduces the current problem through one or more calls to the same function.
- Termination measure — A nonnegative integer quantity that strictly decreases along each recursive path until a base case is reached.
- Stack unwinding — The completion of suspended calls as deeper calls return their results.
- Factorial complexity — A direct recursive implementation takes $O(n)$ time and $O(n)$ auxiliary stack space without call-stack optimization.
```

### Study Pack

**Summary:** A recursive function solves a problem by calling itself on a smaller or simpler instance. Correct recursion requires both a stopping condition and progress toward that condition along every recursive path.

| Component | Role |
|---|---|
| Base case | Returns a directly solvable result without another recursive call. |
| Recursive case | Calls the same function on a reduced problem and uses its result. |

For factorial on nonnegative integers, the base case is $0! = 1$, and the recursive rule is $n! = n \times (n-1)!$ for integers $n > 0$. A termination measure is a nonnegative integer quantity that strictly decreases along each recursive path until a base case is reached.

Each active call has a stack frame holding execution state, including parameters, local variables, and a return location. Stack unwinding occurs as deeper calls return and suspended calls complete. Direct recursive factorial takes $O(n)$ time and $O(n)$ auxiliary stack space without call-stack optimization.

Recursion naturally supports tree traversal, nested data processing, and divide-and-conquer algorithms. Missing or unreachable base cases can cause unbounded recursion and eventual stack overflow in stack-based implementations.

**Common Misconceptions**: Merely including a base case does not guarantee termination; every recursive path must reach it. Suspended calls still occupy stack space while waiting for deeper calls to return.

**Key concepts:** Base case; Recursive case; Termination measure; Stack frame; Stack unwinding; Return propagation; Stack overflow; Factorial complexity

**Q1** (MCQ, CONCEPTUAL): What distinguishes a base case from a recursive case?

- A. It solves the current input without another recursive call. ✅
- B. It calls the same function on a simpler input.
- C. It preserves the current input for another recursive call.
- D. It waits for a deeper call before computing its result.

_Explanation:_ A recursive process needs a directly solvable stopping point. Handling that input immediately prevents the call chain from continuing indefinitely.

**Q2** (MCQ, COMPUTATIONAL): Using $0! = 1$ and $n! = n \times (n-1)!$, what value does a recursive factorial call return for input $3$?

- A. $3$
- B. $9$
- C. $6$ ✅
- D. $1$

_Explanation:_ Expand the recursive calls: $3! = 3 \times 2 \times 1 \times 0!$. Substitute $0! = 1$, then multiply to obtain $6$.

**Q3** (MCQ, CONCEPTUAL): Which claim about recursive termination is NOT correct?

- A. Every recursive path must eventually reach a base case.
- B. Including a base case guarantees termination even when calls move away from it. ✅
- C. A termination measure strictly decreases along each recursive path.
- D. An unreachable base case can lead to stack overflow.

_Explanation:_ Termination depends on both a stopping condition and progress toward it. If successive calls never reach the stopping input, recursion can continue until stack resources are exhausted.

**Q4** (MCQ, CONCEPTUAL): Which best describes stack unwinding during recursive execution?

- A. Creating execution-state records for newly invoked calls.
- B. Reducing an input before making the next recursive call.
- C. Checking whether every input is already a base case.
- D. Completing suspended calls as deeper calls return results. ✅

_Explanation:_ Earlier invocations remain pending while deeper invocations execute. When a deeper invocation finishes, its caller resumes and uses the returned value to continue its own computation.

**Q5** (MCQ, CONCEPTUAL): Consider direct recursive factorial without call-stack optimization. Statement 1: Its time complexity is $O(n)$. Statement 2: Its auxiliary stack space is $O(n)$. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ The input decreases by one per call, so the number of calls grows linearly with the input. Each pending invocation also retains a stack frame, making peak stack usage grow linearly.

---

## 5. Global Media, Technology, and Information Society

| Field | Value |
|---|---|
| Note id | `8ca37142-0ce8-431a-8886-86837ab9fa24` |
| Plan / Section | 🌏 The Contemporary World / Technology and Society |
| Why sampled | E: General Education Note touching technology or workplace writing |
| Subject / Domain Context / Authored Depth | The Contemporary World / GENERAL_EDUCATION / COLLEGE |
| Current Applicable Programs | Computer Science, Early Childhood Education, Elementary Education, Information Systems, Information Technology, Physical Education, Secondary Education, Special Needs Education, Technical-Vocational Teacher Education |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Global Media, Technology, and the Information Society

📘 Overview
Global media networks organize the cross-border circulation of information, culture, and public debate. In an information society, producing, processing, and distributing information become central to economic activity and social life. These developments expand connectivity without eliminating inequalities in access, influence, or representation.

🧠 Key Idea
Global connectivity does not produce equal participation: media ownership, platform rules, cultural power, and digital inequalities shape whose information circulates and whose voices gain influence.

⚔️ Core Details:
- Digital convergence brings previously separate media forms, such as television, newspapers, and radio, onto shared digital platforms.
- Platform gatekeeping uses recommendation algorithms and moderation policies to shape the visibility of information.
- Cultural globalization includes both the spread of dominant cultural products and hybrid forms combining global influences with local traditions.
- Technological innovations such as smartphones and artificial intelligence accelerate information production and circulation.
- The digital divide includes inequalities in connectivity, affordability, digital skills, accessibility, and benefits gained from technology.
- Concentrated ownership of media and digital platforms gives a limited number of organizations substantial power over global information flows.

🎯 Why It Matters:
- Networked communication enables transnational activism, diaspora connections, and rapid coordination around shared concerns.
- Unequal digital participation can reinforce existing disadvantages in education, employment, and access to public services.
- Commercial incentives favoring attention can amplify sensational or misleading content at the expense of informed public debate.
- Digital governance raises questions about privacy, freedom of expression, corporate accountability, and state surveillance.

🧠 Quick Recall:
- Information society — A society in which information production, processing, and distribution are central to economic and social organization.
- Cultural homogenization — Increasing similarity among cultures through the widespread adoption of dominant cultural forms.
- Cultural hybridization — The creation of new cultural forms through interactions between local and global influences.
- Digital divide — Unequal access to digital technologies, abilities to use them, and opportunities to benefit from them.
- Media and information literacy — The ability to access, evaluate, create, and share information critically and responsibly.
```

### Study Pack

**Summary:** Global media networks circulate information, culture, and public debate across borders. In an information society, producing, processing, and distributing information are central to economic activity and social organization. Smartphones and artificial intelligence accelerate these processes, while digital convergence brings formerly separate media onto shared platforms.

Connectivity does not guarantee equal participation. Concentrated ownership gives a small number of organizations substantial influence over information flows. Platform gatekeeping uses recommendation algorithms and moderation policies to shape visibility. Commercial incentives that reward attention can amplify sensational or misleading material.

Cultural globalization can produce different outcomes:

| Process | Cultural outcome |
|---|---|
| Homogenization | Greater similarity through adoption of dominant cultural forms |
| Hybridization | New forms combining local traditions and global influences |

The digital divide includes inequalities in connectivity, affordability, digital skills, accessibility, and benefits gained from technology. These inequalities can reinforce disadvantages in education, employment, and public services.

Networked communication supports transnational activism, diaspora connections, and rapid coordination. However, digital governance raises concerns about privacy, freedom of expression, corporate accountability, and state surveillance. Media and information literacy supports critical and responsible access, evaluation, creation, and sharing of information.

**Common Misconceptions**: Global connectivity does not ensure equal influence; the digital divide extends beyond internet access; cultural globalization does not produce only cultural uniformity.

**Key concepts:** Information society; Digital convergence; Platform gatekeeping; Cultural homogenization; Cultural hybridization; Digital divide; Media ownership concentration; Digital governance; Media information literacy

**Q1** (MCQ, CONCEPTUAL): Which best describes an information society?

- A. A society in which digital connectivity ensures equal public influence
- B. A society organized around the production, processing, and distribution of information ✅
- C. A society in which local traditions disappear as media networks expand
- D. A society in which media ownership no longer affects public debate

_Explanation:_ The defining feature is the central role of information-related activities in both economic activity and social organization.

**Q2** (MCQ, CONCEPTUAL): A platform changes its recommendation algorithm and moderation policies, affecting which posts users encounter. What process does this illustrate?

- A. Digital convergence
- B. Cultural hybridization
- C. Transnational activism
- D. Platform gatekeeping ✅

_Explanation:_ Decisions about ranking and content rules influence what becomes visible and whose voices receive attention.

**Q3** (MCQ, CONCEPTUAL): Which claim about the digital divide is NOT supported by the notes?

- A. Providing connectivity alone eliminates inequalities in digital participation. ✅
- B. Affordability can affect people's opportunities to participate digitally.
- C. Digital skills influence people's ability to use technology effectively.
- D. People can differ in the benefits they gain from technology.

_Explanation:_ Meaningful participation also depends on costs, abilities, accessibility, and opportunities to benefit. A network connection addresses only one dimension of inequality.

**Q4** (MCQ, CONCEPTUAL): Statement 1: Cultural homogenization involves increasing similarity through the spread of dominant cultural forms. Statement 2: Cultural hybridization creates new forms through interaction between local and global influences. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Cultural globalization can encourage uniformity while also generating creative combinations. These are distinct processes rather than a single inevitable outcome.

**Q5** (MCQ, CONCEPTUAL): Which practice demonstrates media and information literacy?

- A. Assuming highly recommended content is reliable
- B. Treating widespread circulation as proof of accuracy
- C. Critically evaluating information before sharing it responsibly ✅
- D. Prioritizing attention over the quality of public discussion

_Explanation:_ Responsible participation requires judgment about information quality and the consequences of circulating it, rather than relying on popularity or platform visibility.

---

## 6. Technical Writing and Documentation

| Field | Value |
|---|---|
| Note id | `1c6d9d70-7338-498e-953b-b5c9625476ce` |
| Plan / Section | 💬 Purposive Communication / Presentation and Workplace Writing |
| Why sampled | E: General Education Note touching technology or workplace writing |
| Subject / Domain Context / Authored Depth | Purposive Communication / GENERAL_EDUCATION / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Technical Writing and Documentation

📘 Overview
Technical writing communicates specialized information so a defined audience can understand a subject, complete a task, or make an informed decision. Documentation organizes that information into accurate, accessible, and maintainable records.

🧠 Key Idea
Effective technical documentation translates specialized knowledge into information suited to the reader’s purpose, prior knowledge, and situation. Its quality depends on accuracy, clarity, usability, and ethical representation.

⚔️ Core Details:
- Audience analysis identifies readers’ prior knowledge, information needs, intended tasks, and conditions of use.
- Document purpose determines the genre: instructions guide actions, reports present findings, specifications define requirements, and reference documents support targeted lookup.
- Clear technical prose uses precise terms, consistent labels, concise sentences, and explicit actions.
- Information design uses descriptive headings, logical sequencing, numbered procedures, and labeled visuals to make content easy to locate and interpret.
- Documentation development proceeds through planning, information gathering, drafting, review, usability testing, and revision.
- Document control uses version identifiers, revision histories, and assigned ownership to distinguish current information from obsolete content.

🎯 Why It Matters:
- Task-centered documentation reduces misunderstandings that can cause errors, delays, or unsafe actions.
- Accessible organization and language help readers with different backgrounds obtain essential information without unnecessary barriers.
- Accurate attribution and transparent reporting protect credibility by distinguishing evidence from assumptions and acknowledging limitations.
- Maintained documentation preserves institutional knowledge when responsibilities, personnel, or procedures change.

🧠 Quick Recall:
- Technical writing — Audience-centered communication of specialized information for understanding, action, or decision-making.
- Documentation — An organized body of recorded information that explains a subject, procedure, system, or decision.
- Usability testing — Evaluation in which representative readers attempt realistic tasks using a document.
- Instruction versus specification — An instruction explains how to perform an action; a specification states required characteristics or performance.
- Revision versus editing — Revision improves content and organization; editing improves sentence-level clarity, correctness, and consistency.
```

### Study Pack

**Summary:** Technical writing communicates specialized information so a defined audience can understand a subject, complete a task, or make an informed decision. Documentation organizes this information into accurate, accessible, and maintainable records.

Audience analysis considers readers’ prior knowledge, information needs, intended tasks, and conditions of use. Effective prose uses precise terminology, consistent labels, concise sentences, and explicit actions. Descriptive headings, logical sequencing, numbered procedures, and labeled visuals help readers locate and interpret information.

Document purpose determines the appropriate genre:

| Genre | Primary purpose |
|---|---|
| Instructions | Guide readers through actions |
| Reports | Present findings |
| Specifications | Define requirements |
| Reference documents | Support targeted lookup |

Development proceeds through planning, information gathering, drafting, review, usability testing, and revision. Usability testing involves representative readers attempting realistic tasks with the document. Version identifiers, revision histories, and assigned ownership help distinguish current information from obsolete content.

Ethical documentation attributes sources accurately, distinguishes evidence from assumptions, and acknowledges limitations. Accessible and maintained documentation reduces misunderstandings and preserves institutional knowledge.

**Common Misconceptions**: Instructions explain how to act; specifications state what is required. Revision improves content and organization, while editing addresses sentence-level clarity, correctness, and consistency. Usability testing evaluates readers’ task performance, not merely grammatical correctness.

**Key concepts:** Audience analysis; Document purpose; Technical prose; Information design; Documentation development; Usability testing; Document control; Ethical representation; Revision and editing

**Q1** (MCQ, CONCEPTUAL): Which best describes the role of audience analysis in technical documentation?

- A. Selecting a document genre solely from the writer’s preferences
- B. Identifying readers’ knowledge, needs, tasks, and conditions of use ✅
- C. Checking sentence correctness after the document is completed
- D. Recording changes made across successive document versions

_Explanation:_ Effective documentation must fit the people who will use it. Understanding their background and situation helps the writer select suitable detail, language, and organization.

**Q2** (MCQ, CONCEPTUAL): A document must state the required characteristics and performance of a system. Which genre fits this purpose?

- A. Instructions
- B. Report
- C. Specification ✅
- D. Reference document

_Explanation:_ The document’s purpose is to establish requirements rather than guide a sequence of actions, communicate findings, or support isolated information searches.

**Q3** (MCQ, CONCEPTUAL): Which practice does NOT support effective document control?

- A. Assigning ownership for maintaining a document
- B. Recording changes in a revision history
- C. Using version identifiers to distinguish document versions
- D. Leaving obsolete copies indistinguishable from current copies ✅

_Explanation:_ Readers need to recognize which information remains valid. When outdated material cannot be identified, they may rely on superseded guidance and make avoidable errors.

**Q4** (MCQ, CONCEPTUAL): How should a writer conduct usability testing of a procedural document?

- A. Ask representative readers to attempt realistic tasks using it ✅
- B. Check only whether its sentences follow grammatical conventions
- C. Confirm only that its sources receive accurate attribution
- D. Review only whether its versions have distinct identifiers

_Explanation:_ Observing intended users work with the material reveals whether they can find, understand, and apply the information. This provides evidence of practical effectiveness beyond proofreading.

**Q5** (MCQ, CONCEPTUAL): Statement 1: Reorganizing a document to improve its content flow is revision. Statement 2: Correcting sentence-level inconsistency is also revision rather than editing. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Revision addresses larger concerns such as content and organization. Editing focuses on wording, correctness, and consistency within sentences, so the second statement confuses these distinct activities.

---

## 7. Workplace and Special-Purpose Communication

| Field | Value |
|---|---|
| Note id | `85e8e334-cbd1-4eab-8569-332af2743e06` |
| Plan / Section | 💬 Purposive Communication / Presentation and Workplace Writing |
| Why sampled | E: General Education Note touching technology or workplace writing |
| Subject / Domain Context / Authored Depth | Purposive Communication / GENERAL_EDUCATION / COLLEGE |
| Current Applicable Programs | Computer Science, Early Childhood Education, Elementary Education, Information Systems, Information Technology, Physical Education, Secondary Education, Special Needs Education, Technical-Vocational Teacher Education |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Workplace and Special-Purpose Communication

📘 Overview
Effective workplace communication aligns purpose, audience, message structure, and channel to achieve a clear professional outcome. Special-purpose messages address specific situations such as requests, complaints, refusals, proposals, and public announcements while preserving accuracy, respect, and accountability.

🧠 Key Idea
A professional message succeeds when its audience understands what matters, why it matters, and what response is expected. Its format and tone must support its purpose without concealing essential information or compromising ethical standards.

⚔️ Core Details:
- Audience analysis identifies readers’ knowledge, needs, authority, and likely response.
- Channel selection depends on urgency, complexity, confidentiality, and the need for a permanent record.
- Common formats include email for correspondence, memoranda for internal notices, business letters for formal external messages, and reports for organized findings.
- The direct approach places the main point first for routine or favorable messages.
- The indirect approach presents relevant context before sensitive or unfavorable news without obscuring the decision.
- Special-purpose messages pair a clear purpose with relevant support and an explicit requested action, decision, or next step.

🎯 Why It Matters:
- Clear responsibilities, deadlines, and requested actions reduce avoidable delays and misunderstandings.
- Appropriate formats and respectful tone strengthen professional credibility across organizational roles and cultural differences.
- Accurate claims, transparent reasoning, and protection of confidential information preserve trust and reduce ethical risks.

🧠 Quick Recall:
- Request message — States the action sought, necessary context, and a reasonable response deadline.
- Complaint and adjustment — A complaint documents a problem and seeks a remedy; an adjustment communicates the organization’s response.
- Proposal — Recommends a course of action supported by rationale, expected benefits, resource needs, and feasibility.
- Bad-news message — Communicates an unfavorable decision clearly, respectfully, and with a truthful explanation.
- Ethical professional writing — Uses accurate information, acknowledges sources, avoids discriminatory language, and protects confidential information.
```

### Study Pack

**Summary:** Effective workplace communication aligns purpose, audience, structure, and channel to achieve a clear professional outcome. Audience analysis considers readers’ knowledge, needs, authority, and likely response. Channel selection depends on urgency, complexity, confidentiality, and the need for a permanent record.

Email supports correspondence, memoranda convey internal notices, business letters serve formal external communication, and reports organize findings.

| Approach | Organization | Typical use |
|---|---|---|
| Direct | Main point before supporting details | Routine or favorable messages |
| Indirect | Relevant context before the decision | Sensitive or unfavorable messages |

Special-purpose messages establish a clear purpose, provide relevant support, and specify an action, decision, or next step. Requests identify the action sought, necessary context, and a reasonable response deadline. Complaints document problems and seek remedies; adjustments communicate the organization’s response. Proposals recommend action supported by rationale, expected benefits, resource needs, and feasibility. Bad-news messages convey unfavorable decisions respectfully and explain them truthfully.

Professional credibility depends on clear responsibilities, deadlines, respectful tone, and ethical writing. Ethical messages use accurate information, acknowledge sources, avoid discriminatory language, and protect confidentiality.

**Common Misconceptions**: An indirect approach does not justify hiding an unfavorable decision. A complaint seeks a remedy, while an adjustment communicates the organization’s response. Professional tone does not replace accurate information or explicit next steps.

**Key concepts:** Audience analysis; Channel selection; Communication formats; Direct approach; Indirect approach; Requests and complaints; Proposal development; Ethical professional writing

**Q1** (MCQ, CONCEPTUAL): Before drafting a workplace request, what should a writer examine through audience analysis?

- A. The message’s layout, spacing, and visual consistency
- B. The readers’ knowledge, needs, authority, and likely response ✅
- C. The proposal’s benefits, resource requirements, and feasibility
- D. The channel’s urgency, confidentiality, and recordkeeping requirements

_Explanation:_ Understanding the recipients helps the writer choose suitable detail, tone, and a request that they can reasonably act on.

**Q2** (MCQ, CONCEPTUAL): A workplace message contains sensitive information and must remain available for later reference. Which pair of considerations should guide channel selection?

- A. Expected benefits and resource needs
- B. Reader authority and likely response
- C. Confidentiality and the need for a permanent record ✅
- D. Requested remedy and organizational response

_Explanation:_ The chosen medium must protect the information while allowing the communication to be retained and consulted later.

**Q3** (MCQ, CONCEPTUAL): Which practice is NOT appropriate when using the indirect approach for an unfavorable decision?

- A. Presenting relevant context before the decision
- B. Maintaining a respectful tone
- C. Providing a truthful explanation
- D. Making the decision difficult to identify ✅

_Explanation:_ Context can prepare the reader for sensitive news, but the outcome must still be communicated clearly so the reader understands its implications.

**Q4** (MCQ, CONCEPTUAL): Which best describes the purpose and support expected in a proposal?

- A. Recommending action with reasons, benefits, resource needs, and feasibility ✅
- B. Documenting a problem and requesting a remedy
- C. Communicating an organization’s response to a complaint
- D. Announcing an unfavorable decision with a truthful explanation

_Explanation:_ A proposal helps readers evaluate whether a suggested course of action is worthwhile and practical before deciding whether to support it.

**Q5** (MCQ, CONCEPTUAL): Statement 1: Ethical professional writing acknowledges sources and avoids discriminatory language. Statement 2: A respectful tone makes it acceptable to omit essential information. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Ethical communication requires responsible attribution and fair treatment of readers. Courtesy cannot justify concealing information they need to understand the message or act appropriately.

---

## 8. Artificial Intelligence and Human Judgment

| Field | Value |
|---|---|
| Note id | `4325d971-d4ee-473e-bd7a-bd24b23f94e0` |
| Plan / Section | 💻 Introduction to Computing / Ethics, Society and Emerging Trends |
| Why sampled | D: Introduction to Computing Note written for a computing audience |
| Subject / Domain Context / Authored Depth | Computer Science / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Artificial Intelligence and Human Judgment

📘 Overview
AI systems produce predictions, recommendations, or generated outputs from computational models, but these outputs do not establish their own correctness or appropriateness. Human judgment remains necessary to define objectives, evaluate evidence, handle exceptions, and assign accountability.

🧠 Key Idea
Effective human–AI decision-making combines computational capabilities with informed human oversight. Oversight requires meaningful authority, relevant expertise, and enough information to challenge or override system outputs.

⚔️ Core Details:
- Machine learning models optimize specified objectives using training data rather than independently determining which objectives are appropriate.
- Decision support recommends options to a human decision-maker; automated decision-making executes choices without case-by-case human approval.
- Automation bias is the tendency to over-rely on automated recommendations despite contradictory evidence.
- Distribution shift occurs when deployment data differ from training data in ways that can reduce model reliability.
- Human-in-the-loop systems include human input or review within a computational workflow.
- Calibrated trust means reliance matches a system’s demonstrated capabilities and limitations.

🎯 Why It Matters:
- A poorly chosen optimization objective can produce technically successful outputs that violate application requirements or user expectations.
- Human approval alone does not ensure safety when reviewers lack time, expertise, or authority to reject an output.
- Combining model evaluation, audit logs, and override mechanisms supports investigation of failures and responsibility for deployed systems.
- Interface design affects whether users critically assess AI recommendations or accept them without verification.

🧠 Quick Recall:
- Human judgment — Context-sensitive evaluation of evidence, objectives, constraints, and consequences.
- Automation bias — Excessive reliance on automated outputs despite reasons to question them.
- Distribution shift — A deployment-time change in the data distribution relative to training.
- Human-in-the-loop — Human participation within an AI system’s operational workflow.
- Calibrated trust — Reliance proportional to demonstrated system reliability in the relevant context.
```

### Study Pack

**Summary:** AI systems generate predictions, recommendations, or other outputs from computational models; those outputs do not establish their own correctness or appropriateness. Machine learning optimizes specified objectives using training data, while humans must assess whether those objectives satisfy application requirements and user expectations.

| Decision approach | Human involvement |
|---|---|
| Decision support | Recommends options to a human decision-maker. |
| Automated decision-making | Executes choices without case-by-case human approval. |
| Human-in-the-loop | Incorporates human input or review within an operational workflow. |

Meaningful oversight requires relevant expertise, sufficient information and time, and authority to challenge or override outputs. Human participation alone does not guarantee effective oversight.

Automation bias involves excessive reliance on automated recommendations despite contradictory evidence. Distribution shift occurs when deployment data differ from training data in ways that can reduce reliability. Calibrated trust means matching reliance to demonstrated capabilities and limitations in the relevant context.

Model evaluation, audit logs, and override mechanisms support failure investigation and accountability. Interface design also influences whether users critically assess recommendations or accept them without verification.

**Common Misconceptions**: Optimizing an objective does not establish that the objective is appropriate. Human approval does not automatically ensure safety. Reliability demonstrated on training data may not persist under distribution shift.

**Key concepts:** Human judgment; Optimization objectives; Decision support; Automated decision-making; Automation bias; Distribution shift; Human-in-the-loop; Calibrated trust; Meaningful oversight; Audit logs

**Q1** (MULTI_SELECT, CONCEPTUAL): Select the three conditions that support meaningful human oversight of AI outputs.

- A. Relevant expertise to evaluate the output ✅
- B. Sufficient information to challenge the output ✅
- C. Authority to reject or override the output ✅
- D. Mandatory acceptance of the system's recommendation

_Explanation:_ Reviewers need the competence, evidence, and decision-making power to intervene effectively. Requiring approval regardless of their assessment makes oversight merely procedural.

**Q2** (MCQ, CONCEPTUAL): An AI system recommends an action, but a human decides whether to carry it out. What decision approach does this illustrate?

- A. Automated decision-making
- B. Decision support ✅
- C. Distribution shift
- D. Model training

_Explanation:_ The system supplies guidance while the person retains responsibility for selecting the action. It does not execute the choice independently of case-by-case approval.

**Q3** (MCQ, CONCEPTUAL): Which behavior does NOT illustrate automation bias?

- A. Accepting an AI recommendation despite conflicting evidence
- B. Treating an automated output as decisive despite reasons for doubt
- C. Approving an AI output while disregarding evidence that challenges it
- D. Investigating contradictory evidence before deciding whether to accept an AI output ✅

_Explanation:_ Critical examination preserves independent judgment. The bias arises when reliance on automation persists despite evidence that should prompt reconsideration.

**Q4** (MCQ, CONCEPTUAL): Statement 1: Deployment data can differ from training data in ways that reduce model reliability. Statement 2: Human approval alone guarantees safety when such differences occur. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ A change in the data distribution can undermine performance after deployment. A reviewer's approval is not a safety guarantee, especially without adequate expertise, time, information, or authority.

**Q5** (MCQ, CONCEPTUAL): Which best describes calibrated trust in an AI system?

- A. Relying on outputs whenever they are generated automatically
- B. Maintaining the same reliance regardless of deployment conditions
- C. Adjusting reliance to demonstrated reliability and limitations in context ✅
- D. Assuming human approval makes further evaluation unnecessary

_Explanation:_ Appropriate confidence depends on evidence about what the system can and cannot do in its actual setting. Reliance should change when that evidence or context changes.

---

## 9. Philippine Computing Laws: Data Privacy, Cybercrime and Intellectual Property

| Field | Value |
|---|---|
| Note id | `f4d030ea-33c4-4de0-be44-8160d1270e1c` |
| Plan / Section | 💻 Introduction to Computing / Ethics, Society and Emerging Trends |
| Why sampled | D: Introduction to Computing Note written for a computing audience |
| Subject / Domain Context / Authored Depth | Computer Science / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Philippine Computing Laws: Data Privacy, Cybercrime and Intellectual Property

📘 Overview
Philippine computing law regulates personal data processing, offenses involving computer systems, and ownership of software and digital works. The principal statutes are the Data Privacy Act, the Cybercrime Prevention Act, and the Intellectual Property Code.

🧠 Key Idea
Data privacy governs personal information processing; cybercrime law penalizes specified computer-related conduct; intellectual property law protects rights in software and other creations. Compliance with one does not establish compliance with the others.

⚔️ Core Details:
- The Data Privacy Act requires personal data processing to follow transparency, legitimate purpose, and proportionality.
- Data subjects have rights including information, access, objection, correction, and qualified erasure or blocking.
- The Cybercrime Prevention Act penalizes illegal access, illegal interception, data interference, system interference, misuse of devices, and cybersquatting.
- Computer-related forgery, fraud, and identity theft are offenses under the Cybercrime Prevention Act.
- Copyright protects original software expression, including source and object code, rather than ideas or algorithms as such.
- Software licenses define permissions and conditions for using, modifying, and distributing protected software.

🎯 Why It Matters:
- Applications need a lawful basis for personal data processing; consent is not the only basis, and sensitive personal information has stricter processing conditions.
- Security testing requires clear authorization and scope to avoid conduct that may constitute illegal access.
- Publicly accessible code is not automatically free to copy or redistribute; proprietary and open-source licenses impose different conditions.
- A single computing incident can create overlapping privacy, cybercrime, and copyright liabilities.

🧠 Quick Recall:
- Republic Act No. 10173 — Data Privacy Act of 2012; regulates personal information processing and protects data subject rights.
- Republic Act No. 10175 — Cybercrime Prevention Act of 2012; defines cybercrime offenses and provides investigation and enforcement mechanisms.
- Republic Act No. 8293 — Intellectual Property Code of the Philippines, enacted in 1997; includes copyright protection for computer programs.
- National Privacy Commission — Philippine authority responsible for administering and implementing the Data Privacy Act.
- Copyright — Protection arising automatically upon creation of an original work; registration is not required for protection.
```

### Study Pack

**Summary:** Philippine computing law addresses personal data processing, prohibited conduct involving computer systems, and rights in software and digital works. Compliance with one statute does not establish compliance with the others.

| Statute | Main computing concern |
|---|---|
| Republic Act No. 10173: Data Privacy Act of 2012 | Personal information processing and data subject rights |
| Republic Act No. 10175: Cybercrime Prevention Act of 2012 | Computer-related offenses, investigation, and enforcement |
| Republic Act No. 8293: Intellectual Property Code of 1997 | Copyright protection for computer programs and other original works |

Personal data processing must follow transparency, legitimate purpose, and proportionality. Data subjects have rights to information, access, objection, correction, and qualified erasure or blocking. The National Privacy Commission administers and implements the Data Privacy Act. Processing needs a lawful basis; sensitive personal information has stricter processing conditions.

Cybercrime offenses include illegal access, illegal interception, data interference, system interference, misuse of devices, cybersquatting, and computer-related forgery, fraud, and identity theft. Security testing requires clear authorization and scope.

Copyright protects original software expression, including source and object code, rather than ideas or algorithms as such. Protection arises automatically upon creation without requiring registration. Software licenses establish permissions and conditions for use, modification, and distribution.

**Common Misconceptions**: Consent is not the only lawful basis for processing personal data. Publicly accessible code is not automatically free to copy or redistribute. One computing incident can create overlapping privacy, cybercrime, and copyright liabilities.

**Key concepts:** Personal Data Processing; Data Subject Rights; National Privacy Commission; Lawful Processing Basis; Cybercrime Offenses; Authorized Security Testing; Software Copyright; Software License Conditions; Overlapping Legal Liabilities

**Q1** (MULTI_SELECT, CONCEPTUAL): Select the three principles that personal data processing must follow under the Data Privacy Act.

- A. Transparency ✅
- B. Legitimate purpose ✅
- C. Proportionality ✅
- D. Unrestricted retention

_Explanation:_ Processing must be understandable to data subjects, serve a valid purpose, and remain appropriately limited in relation to that purpose.

**Q2** (MCQ, CONCEPTUAL): Before testing an application's security, what should a tester establish to reduce the risk of conduct constituting illegal access?

- A. That the application is publicly reachable
- B. That the tester intends to improve security
- C. That authorization and testing scope are clearly defined ✅
- D. That testing will not modify source code

_Explanation:_ Permission must cover the systems and activities involved. A beneficial purpose alone does not establish permission to access a computer system.

**Q3** (MCQ, CONCEPTUAL): Which best describes the scope of copyright protection for computer programs?

- A. It protects every algorithm described by a program.
- B. It protects original expression embodied in source and object code. ✅
- C. It protects software only after copyright registration.
- D. It grants copying permission whenever code is publicly accessible.

_Explanation:_ Copyright distinguishes how a program is expressed from its underlying ideas or methods. Protection arises automatically when an original work is created.

**Q4** (MCQ, CONCEPTUAL): Which statement about software licensing is NOT correct?

- A. A license can establish conditions for modifying software.
- B. A license can establish conditions for distributing software.
- C. Proprietary and open-source licenses impose different conditions.
- D. Public access to code automatically permits copying and redistribution. ✅

_Explanation:_ Visibility does not itself grant legal permission to reuse a protected work. The applicable license determines what reuse is permitted and under what conditions.

**Q5** (MCQ, CONCEPTUAL): Statement 1: Consent is the only lawful basis for processing personal data. Statement 2: Sensitive personal information has stricter processing conditions. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct ✅
- D. Neither statement is correct

_Explanation:_ The notes recognize lawful grounds beyond consent. They also distinguish sensitive information as requiring more restrictive conditions for processing.

---

## 10. Foundations of Modern Computing: Turing and von Neumann

| Field | Value |
|---|---|
| Note id | `1753f534-5e87-4eea-a861-d0bf8a4c0aeb` |
| Plan / Section | 💻 Introduction to Computing / History and Evolution of Computing |
| Why sampled | D: Introduction to Computing Note written for a computing audience |
| Subject / Domain Context / Authored Depth | Computer Science / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Foundations of Modern Computing: Turing and von Neumann

📘 Overview
Turing’s model explains what can be computed algorithmically, while von Neumann architecture describes how a stored-program computer is organized. These foundations distinguish the theoretical limits of computation from the practical execution of programs.

🧠 Key Idea
A universal machine can execute encoded descriptions of other machines, enabling general-purpose computation. Stored-program architecture realizes programmable execution through instructions held in memory, but does not overcome the limits of computability.

⚔️ Core Details:
- A Turing machine consists of an unbounded tape, a read/write head, and finite control governed by transition rules.
- A universal Turing machine simulates another Turing machine from an encoded machine description and its input.
- The halting problem is undecidable: no algorithm correctly determines whether every possible program halts on a given input.
- In classical von Neumann architecture, instructions and data occupy a shared memory accessed by the processor.
- The processor executes instructions through the fetch–decode–execute cycle using a control unit, arithmetic logic unit, and registers.
- The von Neumann bottleneck is the performance constraint caused by limited transfer bandwidth between the processor and shared memory.

🎯 Why It Matters:
- General-purpose computers can perform different tasks by changing software rather than rebuilding hardware.
- Undecidability places fundamental limits on fully automatic program analysis, including universal termination checking.
- Memory-transfer costs motivate caches and other techniques that reduce processor waiting time.

🧠 Quick Recall:
- Alan Turing, 1936 — Introduced the machine model underlying modern computability theory.
- Church–Turing thesis — Every effectively calculable function can be computed by a Turing machine; this is a thesis, not a proved theorem.
- John von Neumann, 1945 — Authored the First Draft of a Report on the EDVAC, describing a stored-program design developed through collaborative work.
- Universal Turing machine — A single machine capable of simulating any encoded Turing machine on its input.
- Halting problem — The undecidable problem of determining whether an arbitrary program eventually stops on a specified input.
```

### Study Pack

**Summary:** Turing’s machine model establishes the scope and limits of algorithmic computation, while von Neumann architecture describes the organization of a stored-program computer.

| Foundation | Main concern | Central idea |
|---|---|---|
| Turing machine model | Computability | An unbounded tape, read/write head, and finite control execute transition rules. |
| von Neumann architecture | Computer organization | Instructions and data share memory accessed by the processor. |

A universal Turing machine simulates another Turing machine using its encoded description and input. This explains how one machine can support different computational tasks. The halting problem establishes that no algorithm can correctly decide termination for every possible program and input. The Church–Turing thesis states that every effectively calculable function is Turing-computable; it is a thesis rather than a proved theorem.

In a classical von Neumann computer, the processor performs the fetch–decode–execute cycle using a control unit, arithmetic logic unit, and registers. Limited transfer bandwidth between the processor and shared memory creates the von Neumann bottleneck. Caches and related techniques reduce processor waiting caused by memory transfers.

Turing introduced his machine model in 1936. In 1945, von Neumann authored the First Draft of a Report on the EDVAC, documenting a stored-program design developed collaboratively.

**Common Misconceptions**: Universality does not mean every computational problem is solvable. Stored-program architecture does not remove undecidability. The von Neumann bottleneck concerns memory-transfer performance, not computability.

**Key concepts:** Turing machine components; Universal Turing machine; Halting problem; Church–Turing thesis; Stored-program architecture; Shared memory; Fetch–decode–execute cycle; Processor components; von Neumann bottleneck

**Q1** (MCQ, CONCEPTUAL): Which best describes how a universal Turing machine supports general-purpose computation?

- A. It determines whether every simulated program will terminate.
- B. It simulates another machine using that machine’s encoded description and input. ✅
- C. It replaces transition rules with unlimited finite control.
- D. It requires a different physical machine for each computational task.

_Explanation:_ Encoding a machine’s behavior makes that behavior available as input to a simulator. Different descriptions therefore allow the same underlying machine to perform different tasks.

**Q2** (MCQ, CONCEPTUAL): Which claim about the halting problem is NOT correct?

- A. It concerns termination on a specified input.
- B. Its undecidability limits universal automatic termination checking.
- C. Its undecidability remains relevant to stored-program computers.
- D. A universal simulator guarantees a termination decision for every program. ✅

_Explanation:_ Running a simulation does not ensure a decision: if the simulated program continues indefinitely, observing its execution does not establish that it will never stop. No algorithm resolves every possible case.

**Q3** (MCQ, CONCEPTUAL): Statement 1: In classical von Neumann architecture, instructions and data occupy shared memory. Statement 2: Limited transfer bandwidth between the processor and that memory can constrain performance. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ The processor depends on memory transfers for program execution. When those transfers cannot keep pace with processing, waiting time creates the architectural bottleneck described in the notes.

**Q4** (MULTI_SELECT, CONCEPTUAL): Select the three processor components identified in the notes as supporting the fetch–decode–execute cycle.

- A. Control unit ✅
- B. Arithmetic logic unit ✅
- C. Registers ✅
- D. Unbounded tape

_Explanation:_ Instruction execution requires coordination, arithmetic or logical processing, and internal storage for values used during processing. These roles distinguish practical processor organization from the abstract Turing machine model.

**Q5** (MCQ, CONCEPTUAL): What does the Church–Turing thesis propose?

- A. Every program eventually terminates on every input.
- B. Shared instruction and data memory eliminates computation limits.
- C. Every effectively calculable function can be computed by a Turing machine. ✅
- D. Every computer must physically contain an unbounded tape.

_Explanation:_ The thesis connects the informal notion of an effective calculation with a precise mathematical model of computation. Because it makes that connection, it is not presented as a proved theorem.

---

## 11. Analyzing Organizational Computing Solutions

| Field | Value |
|---|---|
| Note id | `b144ae51-8e67-48bf-8788-55ae3191ae96` |
| Plan / Section | 💻 Introduction to Computing / The Computing Disciplines and Profession |
| Why sampled | D: Introduction to Computing Note written for a computing audience |
| Subject / Domain Context / Authored Depth | Computer Science / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Analyzing Organizational Computing Solutions

📘 Overview
Organizational computing solutions are evaluated by how well they satisfy business requirements within technical, financial, operational, and security constraints. Comparing custom development, commercial software, and cloud services requires evidence about organizational fit rather than feature lists alone.

🧠 Key Idea
The strongest computing solution is the best-supported fit between organizational needs, system capabilities, lifecycle costs, and acceptable risks. Mandatory requirements must be satisfied before weighted preferences determine the ranking.

⚔️ Core Details:
- Requirements analysis translates stakeholder needs and current workflow problems into measurable functional requirements and quality attributes.
- Fit-gap analysis identifies differences between required capabilities and each candidate solution's supported capabilities.
- Feasibility analysis assesses technical compatibility, economic affordability, operational readiness, legal compliance, and delivery schedules.
- Total cost of ownership includes acquisition, integration, data migration, training, operation, maintenance, and retirement costs over a defined evaluation period.
- Risk assessment examines security exposure, privacy obligations, service availability, migration failure, and vendor lock-in.
- Weighted scoring uses $S_j = \sum_{i=1}^{n} w_i r_{ij}$, where $S_j$ is candidate $j$'s score, $w_i$ is criterion $i$'s weight, $r_{ij}$ is its normalized rating, and $n$ is the number of criteria.

🎯 Why It Matters:
- Lifecycle analysis exposes recurring subscriptions, integration work, and support demands that a low purchase price can conceal.
- Explicit criteria make selection decisions traceable to stakeholder priorities rather than vendor claims or personal preferences.
- Pilot deployments and representative workload tests reduce uncertainty about performance, usability, and integration before full commitment.
- Sensitivity analysis reveals whether small changes in priorities or assumptions would reverse the preferred solution.

🧠 Quick Recall:
- Functional requirement — A capability the system must provide, such as enforcing role-based access to organizational records.
- Nonfunctional requirement — A measurable quality or constraint, such as response time, availability, scalability, or accessibility.
- Fit-gap analysis — Comparison of required capabilities against a candidate solution to identify unmet needs.
- Total cost of ownership — The complete cost of acquiring, using, supporting, and retiring a computing solution over a specified period.
- Mandatory requirement — A pass-or-fail condition that cannot be compensated for by high scores on preferred features.
```

### Study Pack

**Summary:** Selecting custom software, commercial software, or cloud services requires evidence of organizational fit, not feature lists alone. Evaluation connects stakeholder needs and workflows to measurable functional requirements and quality attributes while considering technical, financial, operational, and security constraints.

| Analysis | Main purpose |
|---|---|
| Requirements analysis | Translate stakeholder needs into measurable capabilities and quality attributes. |
| Fit-gap analysis | Identify differences between required and supported capabilities. |
| Feasibility analysis | Assess technical compatibility, affordability, operational readiness, legal compliance, and delivery schedules. |

Mandatory requirements are pass-or-fail conditions. Only candidates that satisfy them should be ranked using weighted preferences. Weighted scoring uses $S_j = \sum_{i=1}^{n} w_i r_{ij}$, where $S_j$ is a candidate's score, $w_i$ is a criterion's weight, and $r_{ij}$ is its normalized rating across $n$ criteria.

Total cost of ownership covers acquisition, integration, data migration, training, operation, maintenance, and retirement over a defined evaluation period. Risk assessment considers security exposure, privacy obligations, availability, migration failure, and vendor lock-in.

Pilot deployments and representative workload tests provide evidence about performance, usability, and integration. Sensitivity analysis checks whether changes in priorities or assumptions could reverse the preferred solution. Explicit criteria make decisions traceable to stakeholder priorities.

**Common Misconceptions** A high preference score cannot compensate for failing a mandatory requirement. A low purchase price does not necessarily imply a low total cost of ownership. An initial ranking may change when assumptions or priorities change.

**Key concepts:** Functional Requirements; Nonfunctional Requirements; Fit Gap Analysis; Feasibility Analysis; Total Cost of Ownership; Mandatory Requirements; Weighted Scoring; Risk Assessment; Pilot Deployments; Sensitivity Analysis

**Q1** (MCQ, CONCEPTUAL): Which best describes a nonfunctional requirement for an organizational computing solution?

- A. A capability to enforce role-based access to records
- B. A measurable response-time target for the system ✅
- C. A capability to migrate existing organizational records
- D. A capability to integrate with an existing application

_Explanation:_ Nonfunctional requirements specify measurable qualities or constraints on system behavior rather than the capabilities the system provides.

**Q2** (MCQ, CONCEPTUAL): A team compares required capabilities with each candidate system's supported capabilities to identify unmet needs. What analysis is it performing?

- A. Sensitivity analysis
- B. Total cost of ownership analysis
- C. Fit-gap analysis ✅
- D. Feasibility analysis

_Explanation:_ This comparison reveals where a candidate fails to meet organizational needs, making the missing capabilities explicit before selection.

**Q3** (MCQ, CONCEPTUAL): Which selection practice is NOT consistent with the role of mandatory requirements?

- A. Allowing strong preference scores to offset failure of a mandatory condition ✅
- B. Checking mandatory conditions before ranking eligible candidates
- C. Treating mandatory conditions as pass-or-fail checks
- D. Applying weighted preferences to candidates that meet every mandatory condition

_Explanation:_ Mandatory conditions determine eligibility. A candidate that fails one cannot become acceptable merely by earning more points on preferred features.

**Q4** (MCQ, CONCEPTUAL): Which cost estimate provides the most complete basis for comparing computing solutions over a defined evaluation period?

- A. Purchase and installation costs only
- B. Subscription and vendor support costs only
- C. Integration and data migration costs only
- D. Acquisition, integration, migration, training, operation, maintenance, and retirement costs ✅

_Explanation:_ A lifecycle comparison includes both initial spending and the continuing or eventual costs of using, supporting, and retiring the solution.

**Q5** (MCQ, CONCEPTUAL): Statement 1: Pilot deployments can reduce uncertainty about usability and integration before full commitment. Statement 2: Sensitivity analysis checks whether changes in priorities or assumptions could reverse the preferred solution. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Pilots provide practical evidence about a candidate in use, while sensitivity analysis tests how robust the selection remains when evaluation inputs change.

---

## 12. Computing in Other Fields: Biology, Sociology, Environment and Games

| Field | Value |
|---|---|
| Note id | `13d9e618-1921-401a-800b-c9c16add513d` |
| Plan / Section | 💻 Introduction to Computing / The Computing Disciplines and Profession |
| Why sampled | D: Introduction to Computing Note written for a computing audience |
| Subject / Domain Context / Authored Depth | Computer Science / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Computing in Other Fields: Biology, Sociology, Environment and Games

📘 Overview
Computing supports biological analysis, social research, environmental monitoring, and digital games through algorithms, data processing, and computational models. These applications share techniques such as simulation and machine learning but differ in their data, performance requirements, and validation methods.

🧠 Key Idea
Computing turns domain questions into representations that algorithms can process, such as biological sequences, social networks, environmental grids, and game states. Reliable results depend on choosing suitable representations and validating outputs against evidence or system requirements.

⚔️ Core Details:
- Computational modeling represents selected features of a system as data structures and rules.
- Bioinformatics uses sequence-alignment algorithms to identify similarities among DNA, RNA, or protein sequences.
- Computational social science uses graph algorithms to analyze relationships and information flow in social networks.
- Environmental computing combines sensor readings, satellite imagery, and spatial algorithms to monitor changes such as deforestation.
- Game engines coordinate rendering, physics simulation, input handling, and artificial intelligence within real-time performance limits.
- Model validation compares computational outputs with independent observations or specified system requirements.

🎯 Why It Matters:
- Automated analysis makes large sequence datasets, social networks, and satellite archives computationally tractable.
- Simulations support exploration of complex behavior without requiring every scenario to occur in the real world.
- Real-time games expose trade-offs between computational accuracy, visual quality, and response latency.
- Privacy protection, bias assessment, and uncertainty reporting help prevent misleading or harmful uses of computational results.

🧠 Quick Recall:
- Bioinformatics — computational storage, processing, and analysis of biological data.
- Computational social science — algorithmic analysis and modeling of social behavior and relationships.
- Environmental informatics — computational management and analysis of environmental data.
- Game artificial intelligence — algorithms that control non-player decisions, movement, and behavior.
- Agent-based simulation — a computational model in which interacting autonomous entities follow specified rules.
```

### Study Pack

**Summary:** Computing translates application questions into representations that algorithms can process, including biological sequences, social networks, environmental grids, and game states. Computational models encode selected system features using data structures and rules. Agent-based simulations represent interacting autonomous entities that follow specified rules.

| Application area | Main computational approach | Purpose |
|---|---|---|
| Bioinformatics | Sequence alignment | Identify similarities among DNA, RNA, or protein sequences |
| Computational social science | Graph algorithms | Analyze relationships and information flow |
| Environmental informatics | Sensor data, satellite imagery, and spatial algorithms | Monitor environmental changes |
| Digital games | Rendering, physics simulation, input handling, and artificial intelligence | Coordinate interactive behavior within real-time limits |

Automated analysis makes large datasets tractable, while simulation allows exploration of scenarios without requiring them to occur in the real world. Game artificial intelligence controls non-player decisions, movement, and behavior. Real-time game systems balance computational accuracy, visual quality, and response latency.

Reliable results require suitable representations and validation against independent observations or specified system requirements. Privacy protection, bias assessment, and uncertainty reporting help prevent misleading or harmful uses of computational results.

**Common Misconceptions**: A model represents selected features, not every aspect of a system. Producing computational output does not establish its validity. Real-time games cannot prioritize accuracy without considering responsiveness.

**Key concepts:** Computational representations; Sequence alignment; Graph algorithms; Spatial algorithms; Agent-based simulation; Game artificial intelligence; Real-time performance; Model validation; Responsible computing

**Q1** (MCQ, CONCEPTUAL): A program must identify similarities among protein sequences. Which computational technique is most directly suited to this task?

- A. Graph traversal of social relationships
- B. Sequence alignment ✅
- C. Spatial analysis of satellite imagery
- D. Real-time rendering

_Explanation:_ Comparing ordered biological symbols allows algorithms to detect corresponding regions and similarities across biological datasets.

**Q2** (MCQ, CONCEPTUAL): Which best describes the role of graph algorithms in computational social science?

- A. Comparing ordered symbols in biological datasets
- B. Coordinating rendering and player input
- C. Analyzing relationships and information flow in networks ✅
- D. Combining satellite images into environmental grids

_Explanation:_ A graph provides a computational representation of social connections, enabling algorithms to investigate how entities are linked and how information moves between them.

**Q3** (MULTI_SELECT, CONCEPTUAL): Select the three components explicitly associated with environmental monitoring in the notes.

- A. Sensor readings ✅
- B. Satellite imagery ✅
- C. Spatial algorithms ✅
- D. Non-player decision algorithms

_Explanation:_ Environmental monitoring combines ground-based measurements, remotely captured observations, and location-aware processing to detect changes such as deforestation.

**Q4** (MCQ, CONCEPTUAL): Which statement about game engines is NOT correct?

- A. They coordinate input handling with other engine functions.
- B. They use artificial intelligence to control non-player behavior.
- C. They coordinate rendering and physics simulation.
- D. They must maximize computational accuracy regardless of response latency. ✅

_Explanation:_ Interactive systems have real-time constraints, so designers must balance computational fidelity and visual quality against how quickly the system responds.

**Q5** (MCQ, CONCEPTUAL): Statement 1: Model validation compares computational outputs with independent observations or specified system requirements. Statement 2: A simulation removes the need to validate its outputs. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Running a model only produces results; it does not demonstrate that those results are reliable. External evidence or relevant requirements provide a basis for checking whether the model is suitable.

---

## 13. Professional Societies and Standards in Computing

| Field | Value |
|---|---|
| Note id | `12077fa7-d8ba-4a83-bf8b-578a33d3dee3` |
| Plan / Section | 💻 Introduction to Computing / The Computing Disciplines and Profession |
| Why sampled | D: Introduction to Computing Note written for a computing audience |
| Subject / Domain Context / Authored Depth | Computer Science / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Professional Societies and Standards in Computing

📘 Overview
Professional societies support computing practitioners, while standards establish shared technical requirements and specifications. Some organizations perform both roles. Standards are generally voluntary unless incorporated into laws, regulations, contracts, or procurement requirements.

🧠 Key Idea
Professional societies guide professional conduct and development; standards coordinate how computing technologies are designed and implemented. Ethical codes address professional responsibilities, whereas technical standards specify requirements for systems and processes.

⚔️ Core Details:
- ACM and the IEEE Computer Society support computing professionals through publications, conferences, education, and professional communities.
- Computing ethics codes address responsibilities such as avoiding harm, respecting privacy, maintaining competence, and acting honestly.
- ISO/IEC JTC 1 develops international information technology standards through collaboration between national standards bodies.
- IEEE develops technical standards such as the IEEE 802 family for local and metropolitan area networks.
- The IETF develops Internet specifications through open technical discussion and rough consensus.
- The W3C develops Web standards covering technologies such as CSS and accessibility specifications such as WCAG.

🎯 Why It Matters:
- Shared specifications enable independently developed software, devices, and services to interoperate.
- Established standards give developers and purchasers common criteria for evaluating compatibility, accessibility, and security.
- Professional ethics supports accountable decisions when technical capabilities conflict with privacy, safety, or the public interest.
- Participation in professional communities and standards development helps practitioners track changes and contribute technical expertise.

🧠 Quick Recall:
- ACM — Association for Computing Machinery; a professional society for computing.
- IEEE — Institute of Electrical and Electronics Engineers; a professional organization that also develops technical standards.
- ISO/IEC JTC 1 — Joint Technical Committee 1 of ISO and IEC, responsible for international information technology standardization.
- RFC — Request for Comments; a publication series containing Internet specifications and other documents, not all of which are standards.
- Normative versus informative — Normative content specifies requirements for conformance; informative content provides explanation or guidance.
```

### Study Pack

**Summary:** Professional societies support computing practitioners through publications, conferences, education, and professional communities. ACM and the IEEE Computer Society are key examples. Some organizations, including IEEE, also develop technical standards.

| Category | Main purpose | Examples |
|---|---|---|
| Professional societies | Support professional development and communities | ACM, IEEE Computer Society |
| Ethical codes | Guide professional responsibilities | Avoiding harm, respecting privacy, maintaining competence, honesty |
| Technical standards | Establish shared specifications for technologies and processes | IEEE 802, Web standards |

ISO/IEC JTC 1 develops international information technology standards through collaboration among national standards bodies. IEEE develops technical standards, including the IEEE 802 family for local and metropolitan area networks. The IETF develops Internet specifications through open technical discussion and rough consensus. W3C develops Web standards, including CSS and accessibility specifications such as WCAG.

Shared specifications enable independently developed software, devices, and services to interoperate. They also provide common criteria for evaluating compatibility, accessibility, and security. Normative content specifies conformance requirements; informative content supplies explanation or guidance. Participation in professional communities and standards development helps practitioners remain informed and contribute expertise.

**Common Misconceptions** Standards are generally voluntary, but may become mandatory through laws, regulations, contracts, or procurement requirements. Not every RFC is a standard. Ethical codes guide professional responsibilities rather than specify technical system requirements.

**Key concepts:** Professional development; Ethical responsibilities; Technical standardization; Interoperability; Normative content; Informative content; Rough consensus; RFC publication series; Standards conformance

**Q1** (MCQ, CONCEPTUAL): Which best describes the role of ACM and the IEEE Computer Society?

- A. They enforce all computing standards through legal penalties.
- B. They support practitioners through education, publications, conferences, and communities. ✅
- C. They jointly approve every Internet specification before publication.
- D. They replace professional ethical responsibilities with technical requirements.

_Explanation:_ These societies help computing practitioners develop expertise, exchange knowledge, and participate in professional networks.

**Q2** (MCQ, CONCEPTUAL): A developer needs to determine the requirements for conforming to a technical standard. What content should the developer consult?

- A. Informative background notes
- B. Professional conference announcements
- C. Normative provisions ✅
- D. Explanatory examples alone

_Explanation:_ Conformance depends on the portions of a standard that establish requirements, rather than material included only to explain or guide.

**Q3** (MCQ, CONCEPTUAL): Which statement about RFCs is NOT correct?

- A. RFC stands for Request for Comments.
- B. The RFC publication series includes Internet specifications.
- C. The RFC publication series contains documents other than standards.
- D. Publication as an RFC automatically makes a document a standard. ✅

_Explanation:_ The series includes several kinds of documents, so publication alone does not establish a document's standards status.

**Q4** (MULTI_SELECT, CONCEPTUAL): Select the TWO correct associations between organizations and their standards activities.

- A. W3C develops Web standards, including CSS and WCAG. ✅
- B. IETF develops Internet specifications through open discussion and rough consensus. ✅
- C. ISO/IEC JTC 1 is responsible for the IEEE 802 family.
- D. ACM is responsible for international IT standardization through national standards bodies.

_Explanation:_ Web technology and accessibility specifications belong to W3C's work, while the IETF uses an open, consensus-oriented process for Internet specifications.

**Q5** (MCQ, CONCEPTUAL): Statement 1: A generally voluntary technical standard can become mandatory when incorporated into a contract. Statement 2: Computing ethics codes address responsibilities such as respecting privacy and avoiding harm. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Contractual incorporation can create an obligation to follow a standard. Ethical codes separately guide accountable professional conduct when decisions affect people and the public interest.

---

## 14. The Computing Industry, Professions and Careers

| Field | Value |
|---|---|
| Note id | `55ad85d4-19b9-448f-b570-3d43307e044e` |
| Plan / Section | 💻 Introduction to Computing / The Computing Disciplines and Profession |
| Why sampled | D: Introduction to Computing Note written for a computing audience |
| Subject / Domain Context / Authored Depth | Computer Science / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
The Computing Industry, Professions and Careers

📘 Overview
The computing industry develops, operates, and secures digital systems through specialized professional roles. Computing careers differ in their technical responsibilities, entry requirements, and obligations to users and society.

🧠 Key Idea
Computing is a collection of interconnected professions rather than a single programming career. Career pathways depend on matching technical competencies and professional responsibilities to particular roles.

⚔️ Core Details:
- Industry segments include software products, hardware manufacturing, cloud services, telecommunications, cybersecurity, and IT consulting.
- Software roles span requirements analysis, system design, implementation, testing, deployment, and maintenance.
- Infrastructure roles include network administration, systems administration, cloud engineering, and site reliability engineering.
- Data roles include database administration, data engineering, data analysis, and machine learning engineering.
- Entry pathways include degree programs, internships, apprenticeships, portfolios, and role-specific certifications.
- Professional practice requires competence, ethical judgment, clear communication, collaboration, and continuing technical development.

🎯 Why It Matters:
- Distinguishing roles supports realistic career choices based on preferred tasks rather than job titles alone.
- Computing expertise is needed in both technology companies and organizations that maintain internal digital systems.
- Security, privacy, accessibility, and reliability decisions can affect users at scale and create professional accountability.
- Changing tools and platforms make transferable foundations and continuous learning essential for career mobility.

🧠 Quick Recall:
- Software engineer — applies systematic engineering practices to the development and maintenance of software.
- Systems analyst — translates organizational needs into requirements for information systems.
- Cybersecurity analyst — evaluates threats, monitors security events, and supports protection of systems and data.
- DevOps — practices that integrate software development and operations through collaboration, automation, and feedback.
- Professional bodies — ACM and IEEE Computer Society publish ethical guidance and support computing professional development.
```

### Study Pack

**Summary:** The computing industry develops, operates, and secures digital systems through interconnected professions. Industry segments include software products, hardware manufacturing, cloud services, telecommunications, cybersecurity, and IT consulting. Computing professionals work in both technology companies and organizations with internal digital systems.

| Role family | Responsibilities | Examples |
|---|---|---|
| Software | Requirements, design, implementation, testing, deployment, and maintenance | Software engineer, systems analyst |
| Infrastructure | Operating networks, systems, and cloud environments | Network administrator, cloud engineer |
| Data | Managing, preparing, analyzing, and learning from data | Database administrator, data engineer |

Systems analysts translate organizational needs into information-system requirements. Cybersecurity analysts evaluate threats, monitor security events, and help protect systems and data. DevOps integrates software development and operations through collaboration, automation, and feedback.

Entry pathways include degree programs, internships, apprenticeships, portfolios, and role-specific certifications. Career choices should reflect preferred tasks, technical competencies, and professional responsibilities rather than job titles alone. Transferable foundations and continuing learning support mobility as tools change.

Professional practice requires competence, ethical judgment, communication, and collaboration. Decisions about security, privacy, accessibility, and reliability create accountability to users and society. ACM and the IEEE Computer Society provide ethical guidance and support professional development.

**Common Misconceptions** Computing is not a single programming career. Computing expertise is not needed only in technology companies. DevOps describes integrated practices, not merely a particular tool.

**Key concepts:** Industry Segments; Software Lifecycle; Infrastructure Roles; Data Roles; Systems Analysis; Cybersecurity Analysis; DevOps Practices; Entry Pathways; Professional Accountability; Continuous Learning

**Q1** (MCQ, CONCEPTUAL): An organization needs someone to translate its operational needs into requirements for an information system. Which role most directly fits this task?

- A. Network administrator
- B. Systems analyst ✅
- C. Database administrator
- D. Cybersecurity analyst

_Explanation:_ This task connects organizational goals with what an information system must do, establishing requirements that guide its development.

**Q2** (MCQ, CONCEPTUAL): Which best describes DevOps?

- A. A data role focused on administering databases
- B. A certification pathway required for all computing careers
- C. A software phase limited to gathering requirements
- D. Practices integrating development and operations through collaboration, automation, and feedback ✅

_Explanation:_ The central idea is to connect the teams and activities that build software with those that run it, rather than treating them as isolated functions.

**Q3** (MCQ, CONCEPTUAL): Which role is NOT classified as an infrastructure role in the notes?

- A. Data analyst ✅
- B. Systems administrator
- C. Cloud engineer
- D. Site reliability engineer

_Explanation:_ The notes place work centered on interpreting data in the data-role family, separate from operating computing environments.

**Q4** (MULTI_SELECT, CONCEPTUAL): Select the three expectations of professional computing practice identified in the notes.

- A. Exercise ethical judgment when making technical decisions ✅
- B. Communicate clearly and collaborate with others ✅
- C. Treat certification as a replacement for professional accountability
- D. Continue developing technical competence as tools change ✅

_Explanation:_ Responsible practice combines sound judgment, effective teamwork, and ongoing learning. Credentials can support a career pathway, but responsibility for decisions and their effects remains with the professional.

**Q5** (MCQ, CONCEPTUAL): Statement 1: Portfolios and apprenticeships are among the entry pathways into computing careers. Statement 2: Computing expertise is needed only in technology companies. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ The notes recognize several educational and experience-based routes into computing. They also emphasize that organizations outside the technology sector need professionals to maintain their internal digital systems.

---

## 15. Coding Schemes: Bar Codes, QR Codes and Check Digits

| Field | Value |
|---|---|
| Note id | `5329c833-87d4-471f-97a8-58eb6fdb273f` |
| Plan / Section | 📐 Mathematics in the Modern World / Codes and Data Transmission |
| Why sampled | C: computing-flavoured Mathematics in the Modern World Note |
| Subject / Domain Context / Authored Depth | Mathematics / GENERAL_EDUCATION / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Coding Schemes: Bar Codes, QR Codes and Check Digits

📘 Overview
Bar codes and QR codes encode data in machine-readable patterns, while check digits help detect errors in recorded identifiers. Linear bar codes arrange data along one dimension; QR codes use a two-dimensional grid and include error correction.

🧠 Key Idea
Encoding represents data, check digits test data consistency, and error-correcting codes can recover some damaged data. A valid check digit does not prove that an identifier is genuine or correctly assigned.

⚔️ Core Details:
- Linear bar codes represent data through patterns of bars and spaces; UPC-A commonly identifies retail products using twelve decimal digits.
- QR codes encode data in a square grid of light and dark modules; their finder patterns help scanners locate and orient the symbol.
- QR codes use Reed–Solomon error correction to recover data from some damage; recovery capacity depends on the selected correction level and damage pattern.
- For UPC-A, $S = 3(d_1+d_3+d_5+d_7+d_9+d_{11})+(d_2+d_4+d_6+d_8+d_{10})$, where $d_i$ is the digit at position $i$ from the left among the first eleven digits.
- The UPC-A check digit is $c = (10-(S\bmod 10))\bmod 10$, where $S$ is the weighted sum and $c$ is the twelfth digit.
- UPC-A detects every single-digit substitution but misses some transpositions, including adjacent unequal digits differing by five.

🎯 Why It Matters:
- Machine-readable identifiers reduce manual entry during retail transactions, inventory tracking, and document processing.
- Check-digit validation can reject mistyped identifiers before they enter a database or transaction system.
- QR error correction supports scanning when a printed symbol is partly obscured or damaged.
- Error detection is not authentication: a fraudulent identifier or malicious QR link can still pass its coding checks.

🧠 Quick Recall:
- Check digit — A digit calculated from other digits in an identifier to detect specified classes of recording errors.
- UPC-A validation — An identifier passes when $S+c\equiv 0\pmod{10}$, where $S$ is its weighted sum and $c$ is its check digit.
- UPC-A example — For the first eleven digits 03600029145, the weighted sum is $S=58$ and the check digit is $c=2$.
- QR code — Quick Response code; a two-dimensional matrix symbol capable of encoding text, URLs, and other data.
- Reed–Solomon coding — Error-correcting coding used in QR symbols to reconstruct data within the symbol's correction capacity.
```

### Study Pack

**Summary:** Machine-readable codes represent data for scanning, reducing manual entry in retail transactions, inventory tracking, and document processing. Encoding represents information, check digits test consistency, and error-correcting codes can reconstruct some damaged data.

| Feature | UPC-A bar code | QR code |
|---|---|---|
| Structure | Linear bars and spaces | Two-dimensional grid of light and dark modules |
| Data | Twelve decimal digits identifying retail products | Text, URLs, and other data |
| Error handling | Check digit detects specified recording errors | Reed–Solomon coding recovers data within correction capacity |

QR finder patterns help scanners locate and orient the symbol. Recovery from damage depends on the selected correction level and damage pattern.

For UPC-A, number the first eleven digits from the left. Calculate $S = 3(d_1+d_3+d_5+d_7+d_9+d_{11})+(d_2+d_4+d_6+d_8+d_{10})$. The twelfth digit is $c = (10-(S\bmod 10))\bmod 10$. Validation requires $S+c\equiv 0\pmod{10}$. For the initial digit sequence $03600029145$, the weighted sum is $S=58$, giving $c=2$.

UPC-A detects every single-digit substitution but misses some transpositions, including swaps of adjacent unequal digits differing by five.

**Common Misconceptions**: Passing a check-digit test does not establish authenticity or correct assignment. QR error correction cannot repair unlimited damage. A QR code that passes coding checks may still contain a malicious link.

**Key concepts:** Data Encoding; Linear Bar Codes; UPC-A Check Digit; Weighted Sum; QR Finder Patterns; Reed–Solomon Coding; Transposition Errors; Validation Versus Authentication

**Q1** (MCQ, CONCEPTUAL): Which best describes the distinction between encoding and check-digit validation?

- A. Encoding verifies ownership, while check-digit validation identifies the issuing organization.
- B. Encoding repairs damaged symbols, while check-digit validation determines scanning orientation.
- C. Encoding represents data, while check-digit validation tests an identifier's consistency. ✅
- D. Encoding guarantees safe links, while check-digit validation guarantees correct assignment.

_Explanation:_ A machine-readable representation makes information scannable. A calculated check digit provides a separate test for specified recording errors, without proving that the identifier is genuine.

**Q2** (MCQ, CONCEPTUAL): What is the purpose of finder patterns in a QR code?

- A. Help a scanner locate and orient the symbol. ✅
- B. Calculate the final digit of a UPC-A identifier.
- C. Establish that an encoded URL is trustworthy.
- D. Determine whether a retail identifier is correctly assigned.

_Explanation:_ Before reading the modules, a scanner needs positional and directional reference points. Finder patterns supply those references.

**Q3** (MCQ, CONCEPTUAL): Which claim about UPC-A error detection is NOT correct?

- A. Every single-digit substitution is detected.
- B. Some transpositions can escape detection.
- C. The check digit is calculated from the preceding digits.
- D. Every swap of adjacent unequal digits is detected. ✅

_Explanation:_ Swapping adjacent unequal digits that differ by five can preserve the weighted sum modulo ten. Such a change can therefore pass validation.

**Q4** (MCQ, COMPUTATIONAL): A UPC-A identifier's first eleven digits produce a weighted sum of $S=58$. What is its check digit?

- A. $8$
- B. $2$ ✅
- C. $0$
- D. $5$

_Explanation:_ The check digit is $2$. First, $58\bmod 10=8$. Then subtract the remainder from ten and reduce again: $(10-8)\bmod 10=2$.

**Q5** (MCQ, CONCEPTUAL): Statement 1: QR codes use Reed–Solomon coding to reconstruct data within their correction capacity. Statement 2: Passing coding checks proves that a QR link is safe. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Redundant coding supports recovery from some damage, depending on correction level and damage pattern. Coding integrity does not authenticate the source or assess the destination's safety.

---

## 16. Cryptography and Data Security

| Field | Value |
|---|---|
| Note id | `52847921-5c3a-41ee-9be2-78ef941a686c` |
| Plan / Section | 📐 Mathematics in the Modern World / Codes and Data Transmission |
| Why sampled | C: computing-flavoured Mathematics in the Modern World Note |
| Subject / Domain Context / Authored Depth | Mathematics / GENERAL_EDUCATION / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Cryptography and Data Security

📘 Overview
Cryptography protects information through mathematical transformations controlled by keys. Encryption supports confidentiality, while authentication mechanisms and digital signatures help detect unauthorized changes and verify origin.

🧠 Key Idea
Data security requires more than hiding a message: confidentiality, integrity, and authenticity are distinct goals. Secure systems combine appropriate cryptographic methods with careful key management.

⚔️ Core Details:
- Symmetric encryption uses a shared secret key for encryption and decryption; AES is a widely used example.
- Public-key cryptography uses a public key and a mathematically related private key for operations such as encryption, digital signatures, or key establishment.
- Textbook RSA: $c \equiv m^e \pmod n$ and $m \equiv c^d \pmod n$, where $m$ is the message integer, $c$ ciphertext, $e$ the public exponent, $d$ the private exponent, and $n$ the modulus.
- A cryptographic hash maps an arbitrary-length input to a fixed-length digest without using a secret key.
- A digital signature links a message to a private signing key through verification with the corresponding public key.
- Authenticated encryption combines confidentiality with detection of unauthorized changes.

🎯 Why It Matters:
- Encryption limits exposure of intercepted or stolen data, but compromised keys can defeat that protection.
- An ordinary hash alone cannot prove authenticity because an attacker may replace both a message and its digest.
- Hybrid systems combine public-key key establishment with efficient symmetric encryption to protect large amounts of data.
- Security depends on correct implementation, secure randomness, and suitable protocols rather than secrecy of the algorithm.

🧠 Quick Recall:
- Confidentiality, integrity, availability — Protection against unauthorized disclosure, unauthorized alteration, and loss of timely access, respectively.
- Modular congruence — $a \equiv b \pmod n$ means $n$ divides $a-b$, where $a$ and $b$ are integers and $n$ is a positive integer.
- Collision resistance — The property that finding two distinct inputs with the same cryptographic hash is computationally infeasible.
- RSA security — Relies on the difficulty of recovering private-key information from a public modulus formed from large primes; practical encryption requires secure padding such as OAEP.
- Digital signature verification — Evidence of message integrity and possession of the signing key; a trusted binding between the public key and an identity is also necessary.
```

### Study Pack

**Summary:** Cryptography protects information using mathematical transformations and keys. Confidentiality prevents unauthorized disclosure, integrity concerns unauthorized alteration, authenticity concerns verified origin, and availability concerns timely access.

| Method | Key structure | Main role |
|---|---|---|
| Symmetric encryption | Shared secret key | Efficient confidentiality; AES is an example |
| Public-key cryptography | Related public and private keys | Encryption, signatures, or key establishment |
| Cryptographic hashing | No secret key | Fixed-length digest of arbitrary-length input |

Textbook RSA uses $c \equiv m^e \pmod n$ for encryption and $m \equiv c^d \pmod n$ for decryption. Modular congruence means that $a \equiv b \pmod n$ when $n$ divides $a-b$. Practical RSA encryption requires secure padding such as OAEP.

Digital signatures provide evidence of message integrity and possession of a private signing key. Connecting a signature to an identity also requires a trusted binding between that identity and the public key. Authenticated encryption combines confidentiality with detection of unauthorized changes. Hybrid systems combine public-key key establishment with efficient symmetric encryption.

Collision resistance makes finding distinct inputs with the same hash computationally infeasible. Overall security also depends on key management, correct implementation, secure randomness, and suitable protocols—not secrecy of the algorithm.

**Common Misconceptions**: Encryption alone does not guarantee integrity or authenticity. An ordinary hash alone cannot prove authenticity because an attacker may replace both message and digest. A valid signature does not establish a person's identity without a trusted public-key binding.

**Key concepts:** Symmetric encryption; Public key cryptography; Modular congruence; Cryptographic hashing; Collision resistance; Digital signatures; Authenticated encryption; Key management

**Q1** (MCQ, CONCEPTUAL): How are keys used in symmetric encryption?

- A. Encryption uses a public key, while decryption uses a private key.
- B. Encryption and decryption use the same secret key. ✅
- C. Encryption and decryption require no secret key.
- D. Each message is decrypted using its hash digest as the key.

_Explanation:_ Both parties must possess the shared secret to perform the corresponding operations. Protecting that secret is essential because its compromise can defeat confidentiality.

**Q2** (MCQ, CONCEPTUAL): Which claim about cryptographic hashes is NOT correct?

- A. A hash can accept an arbitrary-length input.
- B. A hash produces a fixed-length digest.
- C. Collision resistance makes finding distinct inputs with matching digests computationally infeasible.
- D. An ordinary hash alone proves that a message came from its claimed sender. ✅

_Explanation:_ An attacker may replace both a message and its digest. Without an authentication mechanism, checking the digest does not establish who supplied the message.

**Q3** (MCQ, CONCEPTUAL): Which best describes how a hybrid cryptographic system protects large amounts of data?

- A. It combines public-key key establishment with symmetric encryption of the data. ✅
- B. It replaces encryption with a public hash of the data.
- C. It uses digital signatures as the sole means of hiding the data.
- D. It relies on keeping the encryption algorithm secret.

_Explanation:_ The two approaches serve complementary roles: one enables key establishment, while the other handles bulk data efficiently.

**Q4** (MCQ, CONCEPTUAL): Statement 1: Digital signature verification provides evidence of message integrity and possession of the signing key. Statement 2: Associating that signature with a claimed identity also requires a trusted binding between the public key and the identity. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Cryptographic verification connects the message to a signing key. Trust in the signer's identity additionally depends on knowing whose public key is being used.

**Q5** (MCQ, COMPUTATIONAL): In a small textbook RSA illustration, let $m=2$, $e=3$, and $n=5$. What is the ciphertext representative $c$ satisfying $0 \leq c < 5$?

- A. $1$
- B. $2$
- C. $3$ ✅
- D. $4$

_Explanation:_ The ciphertext is $3$. Apply $c \equiv m^e \pmod n$, substitute to obtain $2^3=8$, and take the remainder when dividing by $5$. Since $8=1\cdot5+3$, the remainder is $3$.

---

## 17. Error-Detecting and Error-Correcting Codes

| Field | Value |
|---|---|
| Note id | `49ab447e-0936-4caa-84a4-7c12dde1e36f` |
| Plan / Section | 📐 Mathematics in the Modern World / Codes and Data Transmission |
| Why sampled | C: computing-flavoured Mathematics in the Modern World Note |
| Subject / Domain Context / Authored Depth | Mathematics / GENERAL_EDUCATION / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Error-Detecting and Error-Correcting Codes

📘 Overview
Error-control codes add redundant bits to a message so that changes during transmission or storage can be detected or corrected. For binary codes, the minimum Hamming distance between valid codewords determines the guaranteed protection against bit errors.

🧠 Key Idea
Detection identifies a received pattern as invalid; correction identifies the original codeword. Greater separation between valid codewords allows more errors to be detected or corrected reliably.

⚔️ Core Details:
- A binary block code maps each message of $k$ bits to a codeword of $n$ bits, adding $n-k$ redundant bits.
- Hamming distance $d_H(x,y)$ is the number of bit positions in which equal-length binary strings $x$ and $y$ differ.
- Minimum distance $d_{\min}$ is the smallest Hamming distance between any two distinct valid codewords.
- Guaranteed detection of up to $s$ bit errors requires $d_{\min}\geq s+1$, where $s$ is the maximum number of errors to detect.
- Guaranteed correction of up to $t$ bit errors requires $d_{\min}\geq 2t+1$, where $t$ is the maximum number of errors to correct.
- A single parity bit makes the total number of ones even or odd; this detects every odd number of bit errors but misses every positive even number.

🎯 Why It Matters:
- Redundancy makes transmitted and stored information more reliable without requiring the original message for comparison.
- Correction can recover information when requesting another transmission is slow, costly, or impossible.
- Code design balances error protection against the extra storage or transmission capacity required by redundant bits.

🧠 Quick Recall:
- Distance example — $d_H(10110,10001)=3$ because the strings differ in three positions.
- Detection versus correction — Detection flags an invalid received pattern; correction selects the original codeword within the guaranteed error limit.
- Single parity-check code — Minimum distance $d_{\min}=2$; guarantees detection of one bit error but cannot guarantee correction of one bit error.
- Binary Hamming code — The standard $(7,4)$ code uses four message bits and three redundant bits; $d_{\min}=3$ guarantees correction of one bit error.
- Code rate — $R=\frac{k}{n}$, where $k$ is the number of message bits and $n$ is the number of transmitted bits per codeword.
```

### Study Pack

**Summary:** Error-control codes add redundant bits to protect messages against changes during transmission or storage. A binary block code converts a message of $k$ bits into a codeword of $n$ bits, adding $n-k$ redundant bits. Its code rate is $R=\frac{k}{n}$, the fraction of transmitted bits that represent message bits.

Hamming distance counts the positions where two equal-length binary strings differ. Minimum distance $d_{\min}$ is the smallest Hamming distance between distinct valid codewords and determines guaranteed error protection.

| Capability | Purpose | Required minimum distance |
|---|---|---|
| Detection | Flag a received pattern as invalid | $d_{\min}\geq s+1$ to detect up to $s$ errors |
| Correction | Recover the original codeword | $d_{\min}\geq 2t+1$ to correct up to $t$ errors |

A single parity bit enforces an even or odd total number of ones. It detects every odd number of bit errors but misses every positive even number. A single parity-check code has $d_{\min}=2$, so it guarantees single-bit error detection but not single-bit correction. The standard binary Hamming $(7,4)$ code has four message bits, three redundant bits, and $d_{\min}=3$, guaranteeing single-bit correction.

Code design balances protection against the storage or transmission capacity required for redundancy.

**Common Misconceptions**: Detecting an error does not necessarily identify the original codeword. A parity check cannot detect every possible error pattern. Protection guarantees apply only within the stated error limit.

**Key concepts:** Binary block codes; Redundant bits; Hamming distance; Minimum distance; Error detection; Error correction; Parity checks; Hamming codes; Code rate

**Q1** (MCQ, CONCEPTUAL): Which best describes the distinction between error detection and error correction?

- A. Detection identifies the original message; correction only checks parity.
- B. Detection flags an invalid received pattern; correction identifies the original codeword within its guaranteed limit. ✅
- C. Detection requires the original message for comparison; correction requires no redundancy.
- D. Detection and correction both only report whether a received pattern is invalid.

_Explanation:_ Recognizing that a pattern is invalid does not establish what was sent. Recovery requires enough separation between valid codewords to resolve the original reliably.

**Q2** (MCQ, CONCEPTUAL): Why are redundant bits added to a binary block code?

- A. To remove the need for valid codewords
- B. To ensure transmission never changes any bit
- C. To reduce every codeword to its message bits
- D. To provide structure that supports detecting or correcting errors ✅

_Explanation:_ Extra bits impose constraints on valid encoded messages, allowing some changes to be recognized and, with sufficient minimum distance, reversed.

**Q3** (MCQ, CONCEPTUAL): Which claim about a single parity-check code is NOT correct?

- A. It detects every odd number of bit errors.
- B. It misses every positive even number of bit errors.
- C. It guarantees correction of a single bit error. ✅
- D. Its minimum Hamming distance is $2$.

_Explanation:_ A parity mismatch reveals that an error occurred but does not identify the affected position. Guaranteed single-bit recovery requires a minimum distance of at least $3$.

**Q4** (MCQ, COMPUTATIONAL): What is the smallest minimum Hamming distance required to guarantee correction of up to two bit errors?

- A. $3$
- B. $4$
- C. $5$ ✅
- D. $6$

_Explanation:_ Use the correction requirement $d_{\min}\geq 2t+1$. Substituting $t=2$ gives $d_{\min}\geq 2(2)+1=5$, so the smallest required distance is $5$.

**Q5** (MCQ, CONCEPTUAL): Statement 1: The standard binary Hamming $(7,4)$ code contains three redundant bits per codeword. Statement 2: This code guarantees correction of one bit error. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Four of the seven codeword bits carry the message, leaving three for redundancy. Its minimum distance of $3$ meets the requirement for guaranteed single-bit recovery.

---

## 18. Graphs and Networks: Paths, Circuits, Weighted Graphs and Coloring

| Field | Value |
|---|---|
| Note id | `56713e3c-ca99-4056-b069-9141f15de3c9` |
| Plan / Section | 📐 Mathematics in the Modern World / Graphs and Mathematical Systems |
| Why sampled | C: computing-flavoured Mathematics in the Modern World Note |
| Subject / Domain Context / Authored Depth | Mathematics / GENERAL_EDUCATION / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Graphs and Networks: Paths, Circuits, Weighted Graphs and Coloring

📘 Overview
A graph represents a network using vertices for objects and edges for connections. In an undirected graph, edges have no direction; edge weights can represent distance, cost, or travel time. Paths, circuits, and coloring describe different constraints on movement and relationships within a network.

🧠 Key Idea
Euler problems require visiting every edge, while Hamilton problems require visiting every vertex. Weighted problems optimize total cost, whereas coloring problems assign different labels to adjacent vertices.

⚔️ Core Details:
- A path is a sequence of adjacent vertices with no repeated vertices.
- A circuit is a closed trail: it starts and ends at the same vertex without repeating an edge.
- A connected undirected graph has an Euler circuit exactly when every vertex has even degree.
- A Hamilton cycle visits every vertex exactly once before returning to its starting vertex.
- The total weight of a path is $W(P)=\sum_{e\in E(P)}w(e)$, where $P$ is the path, $E(P)$ is its edge set, and $w(e)$ is the weight of edge $e$.
- A proper vertex coloring assigns different colors to every pair of adjacent vertices.

🎯 Why It Matters:
- Euler models help plan street inspection or collection routes that must cover every connection.
- Hamilton models describe tours that must visit every location rather than traverse every connection.
- Weighted graphs distinguish the cheapest or fastest route from a route with the fewest edges.
- Graph coloring models conflict-free scheduling by treating incompatible activities as adjacent vertices.

🧠 Quick Recall:
- Euler circuit — A closed traversal that uses every edge exactly once.
- Open Euler trail — A traversal using every edge exactly once with different endpoints; a connected undirected graph has one exactly when two vertices have odd degree.
- Cycle — A closed path with no repeated vertices except the shared starting and ending vertex.
- Chromatic number — $\chi(G)$ is the minimum number of colors needed for a proper vertex coloring of graph $G$.
- Shortest weighted path — A path with minimum total edge weight, not necessarily the fewest edges.
```

### Study Pack

**Summary:** Graphs represent objects as vertices and connections as edges. In an undirected graph, edges have no direction. Edge weights can represent distance, cost, or travel time.

A path is a sequence of adjacent vertices without repeated vertices. A circuit is a closed trail with no repeated edges. A cycle returns to its starting vertex without repeating any other vertex.

| Network problem | Required coverage | Endpoint condition |
|---|---|---|
| Euler circuit | Every edge exactly once | Same starting and ending vertex |
| Open Euler trail | Every edge exactly once | Different endpoints |
| Hamilton cycle | Every vertex exactly once before returning | Same starting and ending vertex |

For a connected undirected graph, an Euler circuit exists exactly when every vertex has even degree. An open Euler trail exists exactly when two vertices have odd degree.

The total weight of a path is $W(P)=\sum_{e\in E(P)}w(e)$. A shortest weighted path minimizes this total, which need not mean using the fewest edges.

A proper vertex coloring assigns different colors to adjacent vertices. The chromatic number $\chi(G)$ is the minimum number of colors required. Coloring can model conflict-free scheduling by representing incompatible activities as adjacent vertices.

**Common Misconceptions**: Euler problems concern edges, while Hamilton problems concern vertices. A circuit may repeat vertices even though it cannot repeat edges. The fewest-edge route is not necessarily the minimum-weight route.

**Key concepts:** Paths; Circuits; Cycles; Euler Circuits; Open Euler Trails; Hamilton Cycles; Weighted Paths; Chromatic Number

**Q1** (MCQ, CONCEPTUAL): Which best describes the difference between an Euler circuit and a Hamilton cycle?

- A. An Euler circuit covers every vertex exactly once; a Hamilton cycle covers every edge exactly once.
- B. An Euler circuit covers every edge exactly once; a Hamilton cycle visits every vertex exactly once before returning. ✅
- C. An Euler circuit must minimize total weight; a Hamilton cycle must minimize the number of edges.
- D. An Euler circuit has different endpoints; a Hamilton cycle has the same starting and ending vertex.

_Explanation:_ The defining coverage requirement concerns connections for Euler problems and locations for Hamilton problems. Both traversals named here return to their starting vertex.

**Q2** (MCQ, CONCEPTUAL): Which statement about paths, circuits, and cycles is NOT correct?

- A. A path has no repeated vertices.
- B. A circuit starts and ends at the same vertex.
- C. A circuit may repeat an edge if its endpoints are different. ✅
- D. A cycle has no repeated vertices except its shared starting and ending vertex.

_Explanation:_ A circuit is a closed trail, so each edge can be traversed only once. Revisiting a vertex is permitted, but traversing an edge again violates the definition.

**Q3** (MCQ, CONCEPTUAL): Consider a connected undirected graph. Statement 1: It has an Euler circuit exactly when every vertex has even degree. Statement 2: It has an open Euler trail exactly when two vertices have odd degree. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ In a closed traversal, arrivals and departures pair at every vertex. In an open traversal, the distinct starting and ending vertices each have one unpaired incident edge, accounting for the odd degrees.

**Q4** (MCQ, COMPUTATIONAL): A path contains three edges with weights $4$, $7$, and $3$. What is its total weight?

- A. $3$
- B. $7$
- C. $11$
- D. $14$ ✅

_Explanation:_ Add the weights of all edges in the path: $4+7+3=14$. The total weight is $14$.

**Q5** (MCQ, CONCEPTUAL): In a scheduling graph, adjacent vertices represent incompatible activities. What does the chromatic number tell you?

- A. The total number of incompatible activity pairs.
- B. The greatest number of activities connected to one activity.
- C. The minimum number of time slots needed to separate incompatible activities. ✅
- D. The minimum total edge weight of a route through the activities.

_Explanation:_ Each color can represent a time slot. Proper coloring prevents conflicting activities from sharing a slot, and the chromatic number gives the smallest feasible color count.

---

## 19. Mathematical Systems and Modular Arithmetic

| Field | Value |
|---|---|
| Note id | `1a952a58-64b6-4982-905a-5a8d77528a8d` |
| Plan / Section | 📐 Mathematics in the Modern World / Graphs and Mathematical Systems |
| Why sampled | C: computing-flavoured Mathematics in the Modern World Note |
| Subject / Domain Context / Authored Depth | Mathematics / GENERAL_EDUCATION / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Mathematical Systems and Modular Arithmetic

📘 Overview
A mathematical system consists of a set, operations, and specified rules governing those operations. Modular arithmetic forms a system in which integers with the same remainder are treated as equivalent, producing a finite set of residue classes.

🧠 Key Idea
Modular arithmetic preserves remainders rather than exact integer values. Addition and multiplication work consistently on residue classes, but division requires a multiplicative inverse that may not exist.

⚔️ Core Details:
- A mathematical system is described by its underlying set, its operations, and axioms such as closure, associativity, and identity laws.
- Congruence is defined by $a \equiv b \pmod n$ exactly when $n \mid (a-b)$, for integers $a,b$ and a positive integer modulus $n$.
- The system $\mathbb{Z}_n$ contains the residue classes represented by $0,1,\ldots,n-1$.
- Modular operations are compatible with congruence: if $a \equiv b \pmod n$ and $c \equiv d \pmod n$, then $a+c \equiv b+d \pmod n$ and $ac \equiv bd \pmod n$.
- An integer $a$ has a multiplicative inverse modulo $n$ exactly when $\gcd(a,n)=1$.
- Division can fail to give a unique result: $2x \equiv 4 \pmod 6$ has the distinct solutions $x \equiv 2 \pmod 6$ and $x \equiv 5 \pmod 6$.

🎯 Why It Matters:
- System properties identify which familiar algebraic steps remain valid when the underlying set or operation changes.
- Modular arithmetic models repeating patterns such as weekdays, clock times, and rotating schedules.
- The inverse condition explains why ordinary cancellation can produce incorrect conclusions in modular equations.

🧠 Quick Recall:
- Closure — An operation on a set $S$ is closed when $a*b \in S$ for every $a,b \in S$.
- Associativity — $(a*b)*c=a*(b*c)$ for all elements of the system.
- Commutativity — $a*b=b*a$ for all elements of the system.
- Identity — An element $e$ satisfying $e*a=a*e=a$ for every element $a$; modular addition has identity $0$, and modular multiplication has identity $1$.
- Multiplicative inverse — An integer $b$ satisfying $ab \equiv 1 \pmod n$; for example, $3 \cdot 5 \equiv 1 \pmod 7$.
```

### Study Pack

**Summary:** A mathematical system consists of an underlying set, operations, and axioms governing those operations. These properties determine which algebraic steps remain valid.

| Property | Meaning |
|---|---|
| Closure | Applying an operation to elements of the set produces another element of that set. |
| Associativity | Changing the grouping of an operation does not change its result. |
| Identity | An element leaves every element unchanged under the operation. |

Modular arithmetic groups integers into residue classes according to their remainders. For a positive integer modulus $n$, congruence $a \equiv b \pmod n$ means $n \mid (a-b)$. The system $\mathbb{Z}_n$ has residue classes represented by $0,1,\ldots,n-1$.

Addition and multiplication preserve congruence, so results do not depend on which representatives of the residue classes are used. Modular addition has identity $0$, and modular multiplication has identity $1$. Commutativity means that changing the order of operands does not change the result.

An integer $a$ has a multiplicative inverse modulo $n$ exactly when $\gcd(a,n)=1$. Division therefore requires care: $2x \equiv 4 \pmod 6$ has the distinct solutions $x \equiv 2 \pmod 6$ and $x \equiv 5 \pmod 6$. Modular arithmetic describes repeating patterns such as weekdays, clock times, and rotating schedules.

**Common Misconceptions**: Congruent integers need not be equal. Not every residue class has a multiplicative inverse. Ordinary cancellation is not automatically valid in modular equations.

**Key concepts:** Underlying Set; Closure; Associativity; Commutativity; Identity Elements; Residue Classes; Modular Congruence; Multiplicative Inverses

**Q1** (MCQ, CONCEPTUAL): Which best describes the meaning of $a \equiv b \pmod n$?

- A. The integers $a$ and $b$ must be equal.
- B. The modulus $n$ divides the difference $a-b$. ✅
- C. The modulus $n$ divides the sum $a+b$.
- D. The integers $a$ and $b$ must both be less than $n$.

_Explanation:_ Congruence groups integers with the same remainder. Their difference is therefore an integer multiple of the modulus, even when the integers themselves are different.

**Q2** (MCQ, CONCEPTUAL): An operation takes any two elements of a set and always produces an element of that same set. What property does this establish?

- A. Associativity
- B. Commutativity
- C. Closure ✅
- D. Identity

_Explanation:_ The defining requirement is that applying the operation never takes the result outside the underlying set.

**Q3** (MCQ, CONCEPTUAL): Which claim about modular division is NOT correct?

- A. Every nonzero residue class has a multiplicative inverse for every modulus. ✅
- B. An integer has an inverse modulo $n$ exactly when it is relatively prime to $n$.
- C. A modular equation can have more than one solution class.
- D. Ordinary cancellation can lead to incorrect conclusions in modular equations.

_Explanation:_ Invertibility depends on the greatest common divisor with the modulus being $1$. Being nonzero alone does not meet that condition.

**Q4** (MCQ, COMPUTATIONAL): What is the multiplicative inverse of $3$ modulo $7$, represented by an integer from $0$ through $6$?

- A. $2$
- B. $3$
- C. $4$
- D. $5$ ✅

_Explanation:_ The inverse is $5$. We require $3b \equiv 1 \pmod 7$. Since $3 \cdot 5 = 15 = 2 \cdot 7 + 1$, the product leaves remainder $1$.

**Q5** (MCQ, CONCEPTUAL): Suppose $a \equiv b \pmod n$ and $c \equiv d \pmod n$. Statement 1: $a+c \equiv b+d \pmod n$. Statement 2: $ac \equiv bd \pmod n$. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Addition and multiplication are compatible with congruence. Replacing operands by congruent representatives preserves the residue class of either result.

---

## 20. Linear Programming: Formulation and Graphical Solution

| Field | Value |
|---|---|
| Note id | `b3a2ab44-646c-4566-ab49-031cb5d7085c` |
| Plan / Section | 📐 Mathematics in the Modern World / Optimization |
| Why sampled | C: computing-flavoured Mathematics in the Modern World Note |
| Subject / Domain Context / Authored Depth | Mathematics / GENERAL_EDUCATION / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Linear Programming: Formulation and Graphical Solution

📘 Overview
Linear programming maximizes or minimizes a linear objective subject to linear constraints. For two decision variables, the graphical method identifies the feasible region and compares objective values at its vertices. A model may have a unique optimum, multiple optima, no feasible solution, or an unbounded objective.

🧠 Key Idea
Constraints determine which solutions are permitted; the objective function determines which permitted solution is best. For a feasible two-variable model with nonnegative variables and a finite optimum, at least one vertex is optimal.

⚔️ Core Details:
- Decision variables represent unknown quantities to be chosen, such as quantities of two products denoted by $x$ and $y$.
- The objective has the form $Z = px + qy$, where $Z$ is the quantity maximized or minimized and $p$ and $q$ are fixed contributions per unit.
- A resource constraint can take the form $ax + by \leq c$, where $a$ and $b$ are resource requirements per unit and $c$ is available capacity.
- Nonnegativity restrictions such as $x \geq 0$ and $y \geq 0$ exclude negative quantities when the variables represent physical amounts.
- The feasible region is the intersection of all permitted half-planes, identified by plotting constraint boundary lines and testing points on either side.
- The graphical solution compares objective values at feasible vertices found from boundary intersections, including intersections with coordinate axes.

🎯 Why It Matters:
- Linear programming makes trade-offs under limited resources explicit, such as choosing an activity mix within time and budget limits.
- A binding constraint identifies a fully used limit at a selected solution.
- Graphical analysis distinguishes a genuinely optimal solution from an attractive point that violates a constraint.
- Results depend on modeling assumptions: constant per-unit contributions, additive resource use, and divisible quantities unless integrality is explicitly required.

🧠 Quick Recall:
- Corner-point principle — A feasible model with nonnegative variables and a finite optimum has at least one optimal vertex.
- Binding constraint — A constraint satisfied as an equality at a particular feasible solution.
- Infeasible model — A model with no point satisfying every constraint simultaneously.
- Unbounded objective — An objective that can improve indefinitely within the feasible region; an unbounded region alone does not establish this.
- Multiple optima — Distinct feasible solutions share the best objective value; in the graphical method, an entire feasible edge can be optimal.
```

### Study Pack

**Summary:** Linear programming maximizes or minimizes a linear objective subject to linear constraints. Decision variables represent quantities to be chosen. An objective such as $Z = px + qy$ assigns fixed per-unit contributions, while a resource constraint such as $ax + by \leq c$ limits resource use. Nonnegativity restrictions exclude negative quantities when these are inappropriate.

For two variables, plot constraint boundaries and identify the intersection of all permitted half-planes. This intersection is the feasible region. Find its vertices, including relevant intersections with coordinate axes, and compare their objective values. For a feasible model with nonnegative variables and a finite optimum, at least one vertex is optimal.

| Outcome | Meaning |
|---|---|
| Unique optimum | Exactly one feasible solution gives the best objective value. |
| Multiple optima | Distinct feasible solutions share the best value; an entire feasible edge can be optimal. |
| Infeasibility | No point satisfies all constraints simultaneously. |
| Unbounded objective | The objective can improve indefinitely within the feasible region. |

A binding constraint holds as an equality at a selected feasible solution. Modeling assumptions include constant per-unit contributions, additive resource use, and divisible quantities unless integrality is explicitly required.

**Common Misconceptions**: An unbounded feasible region does not necessarily imply an unbounded objective. A favorable objective value does not make a constraint-violating point acceptable. An optimal solution need not be unique.

**Key concepts:** Decision Variables; Objective Function; Feasible Region; Nonnegativity Restrictions; Corner Point Principle; Binding Constraints; Multiple Optima; Unbounded Objective

**Q1** (MCQ, CONCEPTUAL): Which best describes the role of the objective function in a linear programming model?

- A. It specifies which quantities must be nonnegative.
- B. It determines which permitted solution is best. ✅
- C. It defines every resource limit.
- D. It guarantees that a feasible solution exists.

_Explanation:_ After the constraints establish what is allowed, the quantity being maximized or minimized provides the criterion for comparing those allowed solutions.

**Q2** (MCQ, CONCEPTUAL): A feasible two-variable model has nonnegative variables and a finite optimum. Where is at least one optimal solution guaranteed to occur?

- A. At every intersection of constraint boundary lines
- B. At the origin, regardless of feasibility
- C. At a vertex of the feasible region ✅
- D. Strictly inside the feasible region, away from its boundaries

_Explanation:_ The corner-point principle guarantees that evaluating feasible boundary intersections can identify an optimum under these conditions. Intersections that violate a constraint must first be excluded.

**Q3** (MCQ, CONCEPTUAL): Which is NOT a standard modeling assumption described in the notes?

- A. Resource use is additive.
- B. Per-unit contributions remain constant.
- C. Quantities are divisible unless integrality is required.
- D. Decision variables must always be integers. ✅

_Explanation:_ Whole-number restrictions must be imposed explicitly when needed. They do not automatically follow from a linear programming formulation.

**Q4** (MCQ, COMPUTATIONAL): A resource constraint is $2x + y \leq 10$. At the feasible solution $x = 2$ and $y = 3$, how much resource capacity remains unused?

- A. $3$ ✅
- B. $5$
- C. $7$
- D. $10$

_Explanation:_ First calculate resource use: $2(2) + 3 = 7$. Then subtract usage from capacity: $10 - 7 = 3$. The unused capacity is $3$, so this constraint is not binding at the selected solution.

**Q5** (MCQ, CONCEPTUAL): Statement 1: An unbounded feasible region always implies an unbounded objective. Statement 2: An infeasible model has no point satisfying every constraint simultaneously. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct ✅
- D. Neither statement is correct

_Explanation:_ The region's extent alone does not establish whether the objective can improve indefinitely; its direction also matters. Infeasibility occurs when the permitted half-planes have no common point.

---

## 21. Array Traversal and Common Operations in C++

| Field | Value |
|---|---|
| Note id | `c41a04aa-7972-4afd-86aa-fcb97b6579c0` |
| Plan / Section | 🧠 Computer Programming 2 / Arrays |
| Why sampled | A: uses asymptotic notation (same class as the Theta issue in DM Note 22) |
| Subject / Domain Context / Authored Depth | Programming Fundamentals / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Array Traversal and Common Operations in C++

📘 Overview
Array traversal visits elements to inspect or modify their values. Built-in C++ arrays have fixed capacity, while std::vector supports resizing; both provide contiguous storage and indexed access.

🧠 Key Idea
Traversal processes elements within valid bounds, usually with an indexed loop or a range-based for loop. Indexed access is constant time, but searching an unsorted array or shifting elements generally takes linear time.

⚔️ Core Details:
- Indexed traversal: for (std::size_t i = 0; i < n; ++i) visits the first $n$ elements of array a, provided $n$ does not exceed its capacity.
- Range-based traversal: for (auto& value : a) permits element modification through a reference.
- Indexed access and assignment, such as a[i] and a[i] = value, take $O(1)$ time for a valid index.
- Linear search compares elements sequentially until a match is found or the logical end is reached, taking $O(n)$ time in the worst case.
- Insertion at an occupied position shifts later elements right, taking $O(n)$ time in the worst case and requiring sufficient capacity.
- Order-preserving deletion shifts later elements left and decreases the logical size, taking $O(n)$ time in the worst case.

🎯 Why It Matters:
- Traversal underlies aggregation, counting, validation, and element-wise transformation.
- Separating logical size from capacity prevents processing unused slots or writing beyond allocated storage.
- Operation costs explain why contiguous arrays suit frequent indexed access better than frequent insertion near the front.
- A range-based loop over a built-in array visits its entire declared extent, not just an application-defined occupied portion.

🧠 Quick Recall:
- Logical size — Number of elements currently treated as occupied; capacity is the number of available storage slots.
- Valid index — For logical size $n$, an occupied element has an index $i$ satisfying $0 \leq i < n$.
- Bounds violation — Built-in array indexing and std::vector::operator[] do not check bounds; accessing outside the valid range causes undefined behavior.
- Reference versus copy — auto& permits modification of the original element; auto creates a separate value for ordinary element types.
- Traversal cost — Visiting each of $n$ elements once takes $O(n)$ time when each visit performs constant-time work.
```

### Study Pack

**Summary:** Array traversal visits elements to inspect or modify them. Built-in C++ arrays have fixed capacity, while std::vector supports resizing; both provide contiguous storage and indexed access.

Logical size counts occupied elements, whereas capacity counts available storage slots. For logical size $n$, occupied indices satisfy $0 \leq i < n$. Keeping these quantities distinct prevents processing unused slots or writing beyond allocated storage.

| Operation | Time cost | Key condition |
|---|---|---|
| Indexed access or assignment | $O(1)$ | The index must be valid |
| Full traversal or linear search | $O(n)$ worst case | Each visit or comparison takes constant time |
| Insertion or order-preserving deletion | $O(n)$ worst case | Later elements may need shifting |

An indexed loop can restrict traversal to the occupied portion. A range-based loop over a built-in array visits its entire declared extent. Using auto& allows modification of original elements; auto creates copies for ordinary element types. Insertion requires sufficient capacity, while order-preserving deletion shifts later elements left and reduces logical size.

**Common Misconceptions**: Capacity is not necessarily logical size. A range-based loop over a built-in array does not automatically stop at the occupied portion. Neither built-in array indexing nor std::vector::operator[] checks bounds; out-of-range access causes undefined behavior.

**Key concepts:** Logical size; Array capacity; Valid indices; Indexed traversal; Range-based traversal; Reference versus copy; Linear search; Element shifting; Undefined behavior; Operation complexity

**Q1** (MCQ, CONCEPTUAL): Which best describes an array's logical size?

- A. The total number of available storage slots
- B. The number of elements currently treated as occupied ✅
- C. The index of the last available storage slot
- D. The number of elements that can be accessed in constant time

_Explanation:_ An application may use only part of its allocated storage. Logical size tracks that used portion, so traversal can avoid unused slots.

**Q2** (MCQ, CONCEPTUAL): A range-based loop must modify the original elements of a built-in integer array a. Which loop header provides this behavior?

- A. for (auto value : a)
- B. for (const auto value : a)
- C. for (const auto& value : a)
- D. for (auto& value : a) ✅

_Explanation:_ A mutable reference binds directly to each stored element, so assignments through the loop variable update the array rather than a separate copy.

**Q3** (MCQ, CONCEPTUAL): Which statement about array bounds is NOT correct?

- A. std::vector::operator[] automatically checks whether an index is in range. ✅
- B. Accessing outside a built-in array's bounds causes undefined behavior.
- C. An occupied element's index must be less than the logical size.
- D. An indexed traversal must stay within allocated storage.

_Explanation:_ The subscript operator leaves bounds validation to the programmer. Using an out-of-range index therefore does not provide safe, checked access.

**Q4** (MULTI_SELECT, CONCEPTUAL): Select the three operations that take $O(n)$ time in the worst case for an array with logical size $n$.

- A. Reading an element at a valid index
- B. Searching an unsorted array sequentially ✅
- C. Inserting at an occupied position while preserving order ✅
- D. Deleting an element while preserving order ✅

_Explanation:_ Sequential comparisons may examine the entire occupied portion. Order-preserving updates may move many later elements, so their work can also grow linearly with logical size.

**Q5** (MCQ, CONCEPTUAL): Statement 1: A range-based loop over a built-in array visits its entire declared extent.
Statement 2: An indexed loop can restrict traversal to the first $n$ occupied elements when $n$ does not exceed capacity.
Which is correct?

- A. Only Statement 1 is correct
- B. Neither statement is correct
- C. Both statements are correct ✅
- D. Only Statement 2 is correct

_Explanation:_ Built-in array range iteration uses the array's declared extent rather than an application-maintained occupancy count. An explicit index condition lets the programmer stop at the logical end.

---

## 22. Growable Arrays with std::vector in C++

| Field | Value |
|---|---|
| Note id | `6ccb648d-2164-4914-9de4-e7d36e34ef46` |
| Plan / Section | 🧠 Computer Programming 2 / Arrays |
| Why sampled | A: uses asymptotic notation (same class as the Theta issue in DM Note 22) |
| Subject / Domain Context / Authored Depth | Programming Fundamentals / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Growable Arrays with std::vector in C++

📘 Overview
std::vector<T> manages a growable array and automatically releases its storage when destroyed. Its size is the number of elements currently stored, while its capacity is the number it can hold before reallocating.

🧠 Key Idea
A vector grows by acquiring larger storage when its capacity is exhausted. This makes appending efficient on average, but reallocation invalidates existing pointers, references, and iterators to its elements.

⚔️ Core Details:
- Declaration: include <vector> and write std::vector<int> values{10, 20, 30}; to create a vector containing three integers.
- Appending with push_back(value) takes amortized $O(1)$ time; an append requiring reallocation takes $O(n)$ time for $n$ existing elements.
- Insertion or erasure at an arbitrary position takes $O(n)$ time in the worst case because subsequent elements must shift.
- Element access: values[i] is unchecked; values.at(i) checks bounds and throws std::out_of_range for an invalid index.
- reserve(count) requests capacity for at least count elements without changing the vector's size.
- Reallocation invalidates all pointers, references, and iterators to elements; erasure invalidates those at or after the erased position.

🎯 Why It Matters:
- Automatic storage management avoids manual allocation and deallocation for dynamically sized sequences.
- Contiguous storage in ordinary vector specializations supports efficient indexed access and cache-friendly traversal.
- Reserving an expected element count can reduce repeated allocations during bulk insertion.
- Distinguishing size from capacity prevents out-of-bounds writes into reserved but unconstructed element storage.

🧠 Quick Recall:
- size() — Number of existing elements; valid indices satisfy $0 \le i < n$, where $i$ is an index and $n$ is the size.
- capacity() — Number of elements the current allocation can hold without reallocation.
- resize(count) — Changes the number of elements, destroying trailing elements or constructing additional ones.
- clear() — Destroys all elements and makes the vector empty without reducing its capacity.
- Random access — Retrieving an element by index takes $O(1)$ time.
```

### Study Pack

**Summary:** std::vector<T> provides a growable array with automatic storage management. Include <vector> to use it; std::vector<int> values{10, 20, 30}; creates three integer elements. Ordinary vector specializations store elements contiguously, supporting cache-friendly traversal and indexed access in $O(1)$ time.

| Member | Meaning or effect |
|---|---|
| size() | Number of existing elements |
| capacity() | Number of elements storage can hold before reallocation |
| reserve(count) | Requests capacity for at least count elements without changing size |
| resize(count) | Changes size by constructing or destroying elements |

push_back(value) takes amortized $O(1)$ time, but an append requiring reallocation takes $O(n)$ time for $n$ existing elements. Insertion or erasure at an arbitrary position takes $O(n)$ time in the worst case because subsequent elements must shift. Reserving an expected element count can reduce repeated allocations.

values[i] performs unchecked access; values.at(i) checks bounds and throws std::out_of_range for an invalid index. Valid indices satisfy $0 \le i < n$, where $n$ is the size.

Reallocation invalidates all pointers, references, and iterators to elements. Erasure invalidates those at or after the erased position. clear() destroys all elements without reducing capacity. Destruction of the vector automatically releases its storage.

**Common Misconceptions** Reserved capacity does not create elements or permit indexing beyond size. Amortized constant-time appending does not mean every append takes constant time. clear() does not reduce capacity.

**Key concepts:** Size versus capacity; Automatic storage management; Amortized append complexity; Contiguous storage; Bounds checking; Capacity reservation; Iterator invalidation; Resizing and clearing

**Q1** (MCQ, CONCEPTUAL): A vector contains three elements. After reserve(100) completes successfully, what is guaranteed?

- A. Its size becomes one hundred.
- B. Its size remains three and its capacity is at least one hundred. ✅
- C. Its size remains three and its capacity is exactly three.
- D. Its next append must reallocate storage.

_Explanation:_ reserve requests enough storage for the requested element count but does not construct additional elements. Existing elements remain the only valid targets for indexed access.

**Q2** (MCQ, CONCEPTUAL): Which statement about vector element access is NOT correct?

- A. Indexed access takes $O(1)$ time.
- B. at(i) checks whether the index is within bounds.
- C. operator[] throws std::out_of_range whenever an index is invalid. ✅
- D. Valid indices are determined by size rather than capacity.

_Explanation:_ operator[] performs no bounds checking, so an invalid index is not safely detected through this interface. Use at(i) when checked access is required.

**Q3** (MCQ, CONCEPTUAL): Which best describes the time complexity of push_back(value) for a vector?

- A. Every append takes $O(1)$ time, including reallocation.
- B. Every append takes $O(n)$ time, regardless of available capacity.
- C. Appending takes amortized $O(n)$ time and never reallocates.
- D. Appending takes amortized $O(1)$ time, with $O(n)$ time when reallocation is required. ✅

_Explanation:_ Most appends use available storage with constant work. When storage must grow, relocating the existing elements introduces work proportional to the current element count; spread across many appends, the average cost remains constant.

**Q4** (MULTI_SELECT, CONCEPTUAL): Select the TWO events that invalidate a previously stored iterator to a vector element.

- A. The vector reallocates its storage. ✅
- B. capacity() is called to inspect available storage.
- C. The element at the iterator's position is erased. ✅
- D. size() is called to inspect the element count.

_Explanation:_ Moving elements to a new allocation makes existing element iterators unusable. Erasure also invalidates iterators at or after the erased position because the affected element positions no longer retain their previous meaning.

**Q5** (MCQ, CONCEPTUAL): Statement 1: clear() destroys all vector elements without reducing capacity. Statement 2: resize(count) can construct additional elements when increasing size. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Element lifetime and allocated storage are separate concerns. Removing all elements can leave storage available for reuse, while increasing the element count requires constructing new elements.

---

## 23. Exceptions in C++

| Field | Value |
|---|---|
| Note id | `cb89ba7c-7236-4f98-bd82-d720015fbce5` |
| Plan / Section | 🧠 Computer Programming 2 / Exceptions and Robust Programming |
| Why sampled | B: C++-specific Note (reuse depends on Q1) |
| Subject / Domain Context / Authored Depth | Programming Fundamentals / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Exceptions in C++

📘 Overview
C++ exceptions transfer control from a throw expression to a matching exception handler. The try, throw, and catch constructs separate exceptional failure handling from normal program execution.

🧠 Key Idea
A thrown exception propagates through function calls until a compatible catch handler is found. During stack unwinding to that handler, destructors release resources owned by local objects.

⚔️ Core Details:
- A try block encloses operations whose exceptions may be handled by its associated catch handlers.
- A throw expression creates an exception object and transfers control toward a matching handler.
- Handlers for derived exception types must precede handlers for their base types because C++ selects the first matching handler.
- Catching class exceptions by const reference avoids copying and preserves access to the original exception's dynamic type.
- Stack unwinding destroys fully constructed automatic objects in the scopes exited during exception propagation.
- A bare throw; inside a handler rethrows the currently handled exception without replacing its type.

🎯 Why It Matters:
- Exceptions allow failures to cross multiple function calls without requiring every intermediate function to return and inspect error codes.
- RAII makes resource cleanup reliable during exception propagation, including memory ownership, file handles, and locks.
- Exception-safety guarantees clarify whether a failed operation preserves valid state or leaves program state unchanged.
- Throwing destructors are dangerous because an exception escaping a destructor during stack unwinding causes program termination.

🧠 Quick Recall:
- std::exception — Standard base class for many library exceptions; what() provides a diagnostic description.
- catch (const std::exception& e) — Handles std::exception and publicly derived exception objects by const reference.
- catch (...) — Catch-all handler for any C++ exception type; placed after more specific handlers.
- noexcept — Specifies that a function must not allow an exception to escape; violating this invokes std::terminate.
- Exception-safety guarantees — Basic: invariants remain valid and resources do not leak. Strong: failure leaves state unchanged. Nothrow: the operation does not emit exceptions.
```

### Study Pack

**Summary:** C++ exceptions separate exceptional failure handling from normal execution using try, throw, and catch. A thrown exception propagates through function calls until a compatible handler is found. Handlers are checked in order, so derived exception handlers must precede base exception handlers, with catch (...) last.

Catching class exceptions by const reference avoids copying and preserves access to their dynamic type. std::exception is a standard base class for many library exceptions; its what() member supplies a diagnostic description. A bare throw; inside a handler rethrows the current exception without replacing its type.

During stack unwinding, fully constructed automatic objects in exited scopes are destroyed. RAII uses this destruction to release owned resources such as memory, files, and locks. Exception-safety guarantees describe an operation's behavior when failure occurs:

| Guarantee | Failure behavior |
| --- | --- |
| Basic | Invariants remain valid and resources do not leak. |
| Strong | Program state remains unchanged. |
| Nothrow | The operation emits no exceptions. |

A noexcept function must not allow an exception to escape; violating this invokes std::terminate. An exception escaping a destructor during stack unwinding also causes termination.

**Common Misconceptions**: Handler order matters; a base handler can intercept derived exceptions. Stack unwinding destroys fully constructed automatic objects, not only objects directly involved in the failure. noexcept does not promise recovery if an exception escapes.

**Key concepts:** Exception propagation; Handler ordering; Const reference catching; Stack unwinding; RAII; Exception rethrowing; Exception safety guarantees; noexcept

**Q1** (MCQ, CONCEPTUAL): A try block has handlers for a derived exception class, its base class, and any remaining exception type. How should they be ordered?

- A. Base class, derived class, catch-all
- B. Catch-all, derived class, base class
- C. Derived class, base class, catch-all ✅
- D. Derived class, catch-all, base class

_Explanation:_ C++ selects the first compatible handler. More specific types must therefore be checked before broader handlers can intercept them.

**Q2** (MCQ, CONCEPTUAL): Which best describes how RAII supports cleanup during exception propagation?

- A. Each intermediate function must catch the exception and explicitly release every resource.
- B. Destruction of fully constructed automatic objects in exited scopes releases their owned resources. ✅
- C. The matching catch handler automatically releases every resource allocated anywhere in the program.
- D. The throw expression releases resources before exception propagation begins.

_Explanation:_ Stack unwinding invokes destructors as scopes are exited. When ownership is tied to object lifetime, those destructors perform cleanup without requiring a handler in every function.

**Q3** (MCQ, CONCEPTUAL): An operation fails but leaves program state exactly as it was before the operation began. Which exception-safety guarantee does this describe?

- A. Basic guarantee
- B. Nothrow guarantee
- C. No exception-safety guarantee
- D. Strong guarantee ✅

_Explanation:_ The defining requirement is rollback-like behavior: failure has no observable effect on the prior state. Merely maintaining valid invariants would provide a weaker assurance.

**Q4** (MCQ, CONCEPTUAL): Which statement about catching and rethrowing C++ exceptions is NOT correct?

- A. A bare throw; inside a handler replaces the exception's type with the handler's declared type. ✅
- B. Catching a class exception by const reference avoids copying it.
- C. std::exception provides what() for a diagnostic description.
- D. catch (...) can handle exceptions of any C++ exception type.

_Explanation:_ A bare rethrow continues propagation of the currently handled exception while preserving its original type. Catching through a base-class reference does not change that behavior.

**Q5** (MCQ, CONCEPTUAL): Statement 1: If an exception escapes a noexcept function, std::terminate is invoked. Statement 2: If an exception escapes a destructor during stack unwinding, the program terminates. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ The noexcept contract forbids exceptions from crossing the function boundary. Separately, allowing a destructor exception to escape while another exception is already unwinding the stack also triggers termination.

---

## 24. Text File Input and Output in C++

| Field | Value |
|---|---|
| Note id | `6bdfacf8-18b8-47a6-b2d2-bd84857c3105` |
| Plan / Section | 🧠 Computer Programming 2 / Files and Streams |
| Why sampled | B: C++-specific Note (reuse depends on Q1) |
| Subject / Domain Context / Authored Depth | Programming Fundamentals / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Text File Input and Output in C++

📘 Overview
C++ text file input and output uses file streams from the <fstream> header to transfer character data between programs and files. Correct file handling requires appropriate opening modes, operation-based loop conditions, and checks for stream errors.

🧠 Key Idea
A file stream maintains both a file connection and a state indicating whether operations succeed. Drive reading loops with the read operation itself, rather than checking for end-of-file before attempting a read.

⚔️ Core Details:
- The <fstream> header provides std::ifstream for input, std::ofstream for output, and std::fstream for both input and output.
- Construction or open() connects a stream to a filename; if (!file) immediately afterward detects an opening failure.
- while (std::getline(file, line)) processes each successfully read line without storing its terminating newline.
- The insertion operator writes formatted text: out << name << '\n'; sends the value of name and a newline to the output stream.
- Default std::ofstream opening replaces existing contents; std::ios::app appends each write, while std::ios::ate only positions the stream at the end initially.
- File stream destruction automatically closes the associated file through RAII; close() releases it earlier.

🎯 Why It Matters:
- Text files provide persistent storage for configuration settings, logs, and program results beyond a single execution.
- Choosing append rather than replacement mode prevents accidental loss of existing records.
- Checking stream state exposes missing files, permission problems, invalid input, and output failures instead of allowing silent data loss.
- RAII releases file resources automatically when execution leaves a scope, including during exception handling.

🧠 Quick Recall:
- Formatted extraction — file >> value parses a value of the destination type; string extraction stops at whitespace.
- std::getline — Reads through a delimiter, normally a newline, and discards that delimiter.
- eof() — Reports whether a previous input operation encountered end-of-file; it does not predict whether the next read will succeed.
- fail() and bad() — fail() detects formatting or operation failure; bad() indicates a serious stream error.
- Mixed input pitfall — After formatted extraction, a remaining newline can make the next std::getline return an empty line.
```

### Study Pack

**Summary:** C++ text file I/O uses streams from the <fstream> header: std::ifstream reads, std::ofstream writes, and std::fstream supports both. Construction or open() connects a stream to a file; checking if (!file) immediately afterward detects an opening failure.

A stream tracks both its file connection and operation state. Drive reading loops with the input operation itself, such as while (std::getline(file, line)). std::getline reads through a delimiter and discards it. Formatted extraction with >> parses the destination type; string extraction stops at whitespace. The insertion operator << writes formatted text.

| Opening mode | Effect |
| --- | --- |
| Default std::ofstream | Replaces existing contents |
| std::ios::app | Appends every write |
| std::ios::ate | Positions at the end only when opened |

Stream-state checks help detect missing files, permission problems, invalid input, and output failures. fail() detects formatting or operation failure, while bad() indicates a serious stream error. Stream destruction closes the associated file automatically through RAII, including during exception handling; close() releases it earlier.

**Common Misconceptions**: eof() reports a previous encounter with end-of-file rather than predicting the next read. std::ios::ate does not guarantee that every write appends. After formatted extraction, a leftover newline can cause the next std::getline to return an empty line.

**Key concepts:** File stream types; Opening failure checks; Operation driven loops; Formatted extraction; Line input; File opening modes; Stream error states; RAII resource management; Mixed input pitfalls

**Q1** (MCQ, CONCEPTUAL): Which stream class supports both text file input and output?

- A. std::ifstream
- B. std::ofstream
- C. std::fstream ✅
- D. std::ios

_Explanation:_ The <fstream> header provides a bidirectional file stream class for reading from and writing to the same file connection.

**Q2** (MCQ, CONCEPTUAL): Which best describes why while (std::getline(file, line)) is an appropriate line-reading loop?

- A. It predicts end-of-file before attempting input.
- B. It enters the loop body only after a successful read. ✅
- C. It preserves the terminating newline in line.
- D. It clears stream errors before each read.

_Explanation:_ The input operation also serves as the condition, so the program processes a line only when extraction succeeds. An end-of-file check alone cannot establish whether the next read will succeed.

**Q3** (MCQ, CONCEPTUAL): Which statement about file opening modes is NOT correct?

- A. Default std::ofstream opening replaces existing contents.
- B. std::ios::app appends each write.
- C. std::ios::ate initially positions the stream at the end.
- D. std::ios::ate guarantees that every write appends. ✅

_Explanation:_ An initial position does not impose a rule on all later writes. Use std::ios::app when every write must go to the file's end.

**Q4** (MCQ, CONCEPTUAL): After file >> value, the next std::getline(file, line) returns an empty line. What is the likely cause described in the notes?

- A. A newline remained in the input stream after formatted extraction. ✅
- B. The file stream was automatically closed after formatted extraction.
- C. String extraction always reads the entire remaining file.
- D. std::getline preserves the delimiter as the line's only character.

_Explanation:_ Formatted extraction can leave its following line delimiter unread. The next line-reading operation encounters that delimiter immediately, discards it, and returns with no preceding characters.

**Q5** (MCQ, CONCEPTUAL): Statement 1: File stream destruction automatically closes the associated file through RAII. Statement 2: Calling close() can release the file before the stream is destroyed. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ RAII ties file-resource cleanup to the stream object's lifetime, including scope exit during exception handling. Explicit closure is available when the program needs to release the resource sooner.

---

## 25. Linked List Operations in C++

| Field | Value |
|---|---|
| Note id | `e1828d0c-4bf7-4ed0-8f3e-8329d36fbc66` |
| Plan / Section | 🧠 Computer Programming 2 / Linked Structures |
| Why sampled | A: uses asymptotic notation (same class as the Theta issue in DM Note 22) |
| Subject / Domain Context / Authored Depth | Data Structures and Algorithms / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Linked List Operations in C++

📘 Overview
A singly linked list stores nodes connected by next pointers, with a head pointer identifying the first node. Operations depend on preserving links, handling empty lists, and managing dynamically allocated nodes correctly.

🧠 Key Idea
Linked list insertion and deletion change pointers rather than shifting elements. These updates take constant time when the required predecessor is already available; locating that predecessor may require linear traversal.

⚔️ Core Details:
- Node structure: A typical C++ node contains a data member and a Node* next member initialized to nullptr.
- Head insertion: For a newly allocated node, execute newNode->next = head; followed by head = newNode;.
- Insertion after a valid node p: Execute newNode->next = p->next; followed by p->next = newNode;.
- Deletion after p with a non-null successor: Execute Node* victim = p->next; then p->next = victim->next; then delete victim;.
- Head deletion for a nonempty list: Execute Node* victim = head; then head = head->next; then delete victim;.
- Traversal: Start a cursor at head and repeatedly follow next until nullptr, processing each visited node.

🎯 Why It Matters:
- Saving the successor before overwriting a link prevents accidental loss of access to the remaining nodes.
- Deleting a node invalidates every pointer to that node; later dereferencing such pointers causes undefined behavior.
- An owning C++ list must release its nodes and define or disable copying to prevent leaks, shallow-copy aliasing, and double deletion.
- Unlike arrays, linked lists do not provide constant-time indexed access and often have poorer cache locality.

🧠 Quick Recall:
- Empty-list invariant — head == nullptr; the last node of a nonempty list has next == nullptr.
- Head insertion and deletion — $O(1)$ time; deletion requires a nonempty list.
- Search and indexed access — $O(n)$ worst-case time for a list containing $n$ nodes.
- Tail insertion — $O(1)$ with a maintained tail pointer, otherwise $O(n)$; removing the only node must also reset tail to nullptr.
- Full cleanup — Repeatedly delete the head until head == nullptr; total time is $O(n)$ for $n$ nodes.
```

### Study Pack

**Summary:** A singly linked list consists of nodes containing data and a next pointer, with head identifying the first node. An empty list has head == nullptr; the final node of a nonempty list has next == nullptr.

Insertion and deletion modify links rather than shift elements. To insert at the head, execute newNode->next = head; before head = newNode;. To insert after a valid node p, execute newNode->next = p->next; before p->next = newNode;.

To delete after p, save its non-null successor as victim, connect p to victim->next, and then delete victim. Head deletion similarly saves the current head, advances head, and deletes the saved node. Traversal follows next pointers from head until nullptr.

| Operation | Time | Condition |
|---|---|---|
| Head insertion or deletion | $O(1)$ | Deletion requires a nonempty list |
| Search or indexed access | $O(n)$ worst case | List contains $n$ nodes |
| Tail insertion | $O(1)$ or $O(n)$ | Constant time requires a maintained tail pointer |

Pointer updates are constant time when the required predecessor is available; finding it may require traversal. If removing the only node, a maintained tail pointer must also become nullptr. Full cleanup repeatedly deletes the head and takes $O(n)$ time.

An owning C++ list must release allocated nodes and define or disable copying to avoid leaks, shallow-copy aliasing, and double deletion. Linked lists also often have poorer cache locality than arrays.

**Common Misconceptions**: Constant-time link updates do not imply constant-time searching. Deleting a node invalidates every pointer to it. Overwriting a link before preserving its successor can lose access to remaining nodes.

**Key concepts:** Node structure; Head insertion; Predecessor links; Node deletion; List traversal; Empty list invariant; Tail maintenance; Memory ownership; Copy control

**Q1** (MCQ, CONCEPTUAL): A newly allocated node must be inserted after a valid node p. Which sequence preserves access to the existing successor?

- A. p->next = newNode; newNode->next = p->next;
- B. newNode->next = p->next; p->next = newNode; ✅
- C. newNode->next = p; p->next = newNode;
- D. newNode->next = nullptr; p->next = newNode;

_Explanation:_ Preserve the original successor in the new node before replacing the predecessor's link. This connects the new node without losing the remaining chain.

**Q2** (MCQ, CONCEPTUAL): When deleting the non-null successor of a valid node p, which action is NOT safe?

- A. Save p->next in a pointer named victim.
- B. Read victim->next before deleting victim.
- C. Reconnect p to victim->next before deleting victim.
- D. Delete victim before reading victim->next to reconnect p. ✅

_Explanation:_ Once an object's lifetime ends, accessing its members through a pointer to that object causes undefined behavior. Preserve the needed link and reconnect the list while the node is still alive.

**Q3** (MCQ, CONCEPTUAL): Which best describes insertion after a node in a singly linked list?

- A. Link updates take constant time if the predecessor is available, but finding it may take linear time. ✅
- B. Both finding the predecessor and updating links always take constant time.
- C. Link updates require shifting every later node, even when the predecessor is available.
- D. Link updates require traversing the entire list regardless of the predecessor's availability.

_Explanation:_ Changing the necessary pointers involves a fixed amount of work. The separate task of locating the insertion position can require visiting nodes along the chain.

**Q4** (MCQ, CONCEPTUAL): Statement 1: Removing the only node must reset a maintained tail pointer to nullptr. Statement 2: A singly linked list provides constant-time indexed access. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ When the list becomes empty, its tail must not point to the deleted node. Reaching a particular index requires following links from the head rather than directly accessing an array position.

**Q5** (MULTI_SELECT, CONCEPTUAL): Select the TWO responsibilities of an owning C++ linked list.

- A. Release dynamically allocated nodes when they are no longer needed. ✅
- B. Rely on copying the head pointer alone to create independent node ownership.
- C. Define or disable copying to prevent unsafe ownership sharing. ✅
- D. Assume deleting the head node automatically deletes every successor.

_Explanation:_ Ownership requires managing node lifetimes and controlling how copying affects the owned chain. Without explicit cleanup and appropriate copy behavior, memory leaks, aliasing, or double deletion can occur.

---

## 26. Linked Lists: Nodes and Links

| Field | Value |
|---|---|
| Note id | `02e8d4af-54b4-4b0d-8efc-955af0ea95ab` |
| Plan / Section | 🧠 Computer Programming 2 / Linked Structures |
| Why sampled | A: uses asymptotic notation (same class as the Theta issue in DM Note 22) |
| Subject / Domain Context / Authored Depth | Data Structures and Algorithms / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Linked Lists: Nodes and Links

📘 Overview
A linked list stores elements in nodes connected by references rather than in contiguous array positions. Its structure supports efficient local insertion and deletion, but locating a node usually requires sequential traversal.

🧠 Key Idea
Each node combines stored data with links that define the list's order. Changing the list means updating those links while preserving access to every node that should remain.

⚔️ Core Details:
- A singly linked node contains a data field and a next reference to its successor.
- The head references the first node; an empty list has a null head.
- In a null-terminated list, the final node's next reference is null.
- A doubly linked node stores both next and previous references for bidirectional traversal.
- Insertion after a known node p sets new.next = p.next before setting p.next = new.
- Deleting the successor of node p sets p.next = p.next.next when that successor exists.

🎯 Why It Matters:
- Linked lists avoid shifting later elements during local insertions and deletions.
- Sequential access and pointer overhead often make linked lists less suitable than arrays for frequent indexed reads.
- Incorrect link updates can lose access to nodes or create unintended cycles.
- Manual memory management requires deallocating removed nodes without leaving dangling references.

🧠 Quick Recall:
- Node — a record containing data and one or more references to other nodes.
- Head and tail — references to the first and last nodes; a tail reference is optional.
- Traversal — following links from node to node; worst-case indexed access takes $O(n)$ time for $n$ nodes.
- Local insertion — $O(1)$ time after the insertion location is known.
- Singly linked deletion — $O(1)$ time with the predecessor available; finding that predecessor can take $O(n)$ time.
```

### Study Pack

**Summary:** A linked list stores data in nodes connected by references rather than contiguous array positions. Each node contains data and links that determine the list's order. The head references the first node; an empty list has a null head. An optional tail references the last node. In a null-terminated list, the last node's next reference is null.

| Structure | Node references | Traversal |
|---|---|---|
| Singly linked list | Next | Forward |
| Doubly linked list | Next and previous | Forward and backward |

To insert after a known node p, first set new.next = p.next, then set p.next = new. This preserves access to the original successor. To delete p's successor when it exists, set p.next = p.next.next. With manual memory management, the removed node must also be deallocated without leaving dangling references.

Local insertion takes $O(1)$ time once the location is known. Singly linked deletion takes $O(1)$ time when the predecessor is available, but finding it can take $O(n)$ time. Worst-case indexed access also takes $O(n)$ time because traversal follows links sequentially. Linked lists avoid shifting later elements during local updates, but sequential access and reference overhead can make arrays preferable for frequent indexed reads.

**Common Misconceptions**: Constant-time local updates do not imply constant-time search. A tail reference is optional. Incorrect link updates can disconnect nodes or create unintended cycles.

**Key concepts:** Node structure; Head and tail; Null termination; Singly linked lists; Doubly linked lists; Sequential traversal; Local insertion; Predecessor based deletion; Dangling references

**Q1** (MCQ, CONCEPTUAL): Which best describes the structural difference between singly and doubly linked nodes?

- A. Singly linked nodes contain only references, while doubly linked nodes also contain data.
- B. Singly linked nodes reference their successor, while doubly linked nodes reference both successor and predecessor. ✅
- C. Singly linked nodes require contiguous storage, while doubly linked nodes do not.
- D. Singly linked nodes require a tail reference, while doubly linked nodes require only a head.

_Explanation:_ An additional backward link allows traversal toward earlier nodes as well as later ones. Both node types store data.

**Q2** (MCQ, CONCEPTUAL): When inserting a new node after a known node p, which update sequence preserves access to the original successor?

- A. Set p.next = new, then set new.next = p.next.
- B. Set new.next = null, then set p.next = new.
- C. Set new.next = p, then set p.next = new.
- D. Set new.next = p.next, then set p.next = new. ✅

_Explanation:_ The new node must retain the original onward link before the predecessor's link changes. This keeps the remaining list reachable.

**Q3** (MCQ, CONCEPTUAL): Which statement about linked-list access is NOT correct?

- A. Knowing the head provides constant-time access to any indexed node. ✅
- B. Traversal follows references from one node to another.
- C. Frequent indexed reads can favor arrays over linked lists.
- D. Worst-case indexed access takes $O(n)$ time for $n$ nodes.

_Explanation:_ The head identifies only the starting node. Reaching an arbitrary position generally requires following the intervening links.

**Q4** (MCQ, CONCEPTUAL): Statement 1: Deleting a singly linked node takes $O(1)$ time when its predecessor is available. Statement 2: Finding that predecessor always takes $O(1)$ time. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Once the predecessor is known, deletion requires a local link update. Locating the predecessor may require traversal through the list, taking $O(n)$ time.

**Q5** (MCQ, CONCEPTUAL): After unlinking a node in a system with manual memory management, what additional responsibility remains?

- A. Shift every later node into a contiguous storage position.
- B. Make the removed node the new head regardless of its position.
- C. Deallocate the removed node without leaving dangling references. ✅
- D. Connect the final node back to the removed node.

_Explanation:_ Unlinking changes reachability but does not itself release allocated memory. Safe cleanup must also ensure that no retained reference points to freed storage.

---

## 27. Static versus Dynamic Data Structure Implementations

| Field | Value |
|---|---|
| Note id | `5240ceb2-02b1-49e2-8dae-f30bdef27863` |
| Plan / Section | 🧠 Computer Programming 2 / Linked Structures |
| Why sampled | A: uses asymptotic notation (same class as the Theta issue in DM Note 22) |
| Subject / Domain Context / Authored Depth | Data Structures and Algorithms / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Static versus Dynamic Data Structure Implementations

📘 Overview
Static implementations have fixed capacity, whereas dynamic implementations can change their capacity or node count during execution. This distinction concerns storage flexibility, not whether memory resides on the stack or heap.

🧠 Key Idea
Choose an implementation by balancing capacity predictability, operation costs, and memory overhead. Dynamic storage supports growth but introduces resizing costs or per-node allocation and linking overhead.

⚔️ Core Details:
- A static implementation fixes capacity when its storage is created.
- A dynamic implementation changes its allocated storage during execution through resizing or node allocation.
- Contiguous arrays provide indexed access in $O(1)$ time in both fixed-capacity and resizable implementations.
- Resizing a dynamic array may require allocating a larger block and copying $n$ elements, giving a single resize $O(n)$ time.
- Geometric capacity growth gives dynamic-array append amortized $O(1)$ time.
- A singly linked list supports insertion after a known node in $O(1)$ time through node allocation and link updates under the standard allocation-cost assumption.

🎯 Why It Matters:
- Fixed-capacity storage provides predictable memory demand and avoids growth-related allocation pauses.
- Resizable arrays combine fast indexing and cache-friendly traversal with flexible capacity, at the cost of spare storage and occasional copying.
- Linked structures avoid relocating existing elements during growth but require link storage and typically have weaker cache locality.
- Array-backed stacks and queues can use either fixed or resizable storage; the abstract data type does not determine the allocation strategy.

🧠 Quick Recall:
- Static implementation — Fixed capacity; insertion into a full structure requires rejection or replacement with another structure.
- Dynamic implementation — Storage can grow or shrink at runtime; dynamic does not mean linked.
- Size versus capacity — Size counts stored elements; capacity counts elements that fit before additional storage is required.
- Amortized cost — Average cost per operation over a worst-case sequence, not an average over random inputs.
- Linked-list indexed access — $O(n)$ worst-case time for an arbitrary position among $n$ nodes.
```

### Study Pack

**Summary:** Static and dynamic implementations differ in storage flexibility, not in whether memory resides on the stack or heap. Static implementations fix capacity when storage is created; dynamic implementations adjust storage during execution through resizing or node allocation. Size counts stored elements, while capacity counts how many elements fit before additional storage is needed.

| Implementation | Access and growth | Main trade-off |
|---|---|---|
| Fixed-capacity array | Indexed access is $O(1)$; capacity cannot grow | Predictable memory demand without growth-related allocation pauses |
| Resizable array | Indexed access is $O(1)$; a resize can take $O(n)$ | Flexible capacity and cache-friendly traversal, but spare storage and occasional copying |
| Singly linked list | Arbitrary indexed access is $O(n)$; insertion after a known node is $O(1)$ under the standard allocation-cost assumption | Growth without relocating existing elements, but link overhead and typically weaker cache locality |

Geometric capacity growth gives dynamic-array append amortized $O(1)$ time. Amortized analysis measures average cost per operation over a worst-case sequence, rather than averaging over random inputs.

Choose an implementation by balancing predictable capacity requirements, operation costs, and memory overhead. Array-backed stacks and queues may use either fixed or resizable storage; their abstract data types do not determine the storage strategy.

**Common Misconceptions**: Dynamic does not mean linked or heap-allocated. Size and capacity are different. Amortized constant-time append does not mean every individual append takes constant time.

**Key concepts:** Fixed Capacity; Dynamic Storage; Size versus Capacity; Indexed Access; Geometric Growth; Amortized Cost; Link Overhead; Cache Locality

**Q1** (MCQ, CONCEPTUAL): Which best describes the distinction between static and dynamic implementations?

- A. Whether memory is stored on the stack or heap
- B. Whether allocated storage can change during execution ✅
- C. Whether the structure supports indexed access
- D. Whether the structure represents a stack or queue

_Explanation:_ The distinction concerns storage flexibility: one approach fixes capacity at creation, while the other permits resizing or node allocation at runtime.

**Q2** (MCQ, CONCEPTUAL): A resizable array has unused allocated slots. What does its size measure?

- A. The number of elements it can hold before growing
- B. The number of unused slots currently allocated
- C. The number of elements currently stored ✅
- D. The number of elements copied during its last resize

_Explanation:_ Size tracks the array's current contents. Allocated slots that remain empty contribute to capacity, but not to size.

**Q3** (MCQ, CONCEPTUAL): Which statement about dynamic-array performance is NOT correct?

- A. Indexed access takes $O(1)$ time.
- B. A resize that copies $n$ elements takes $O(n)$ time.
- C. Geometric capacity growth supports amortized $O(1)$ append.
- D. Amortized analysis averages operation costs over random inputs. ✅

_Explanation:_ Amortized analysis bounds the total cost of a worst-case sequence and spreads that cost across its operations. It does not require a probability distribution over inputs.

**Q4** (MCQ, CONCEPTUAL): Under the standard allocation-cost assumption, why can insertion after a known node in a singly linked list take $O(1)$ time?

- A. Only a new node and a bounded number of link updates are needed. ✅
- B. Every existing node is copied into a larger contiguous block.
- C. The list is traversed from its head to locate the insertion point.
- D. Every node after the insertion point shifts one position.

_Explanation:_ The insertion location is already available, so no traversal is needed. The allocation and pointer changes do not increase with the number of existing nodes.

**Q5** (MCQ, CONCEPTUAL): Statement 1: An array-backed stack can use fixed-capacity storage. Statement 2: An array-backed queue can use resizable storage. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ An abstract data type specifies behavior rather than allocation strategy. Either structure can therefore be implemented with storage chosen to suit capacity and performance requirements.

---

## 28. Dynamic Memory Allocation in C++

| Field | Value |
|---|---|
| Note id | `f8d652a4-4c67-4d39-849a-62bb00989a9f` |
| Plan / Section | 🧠 Computer Programming 2 / Memory, Pointers and References |
| Why sampled | B: C++-specific Note (reuse depends on Q1) |
| Subject / Domain Context / Authored Depth | Programming Fundamentals / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Dynamic Memory Allocation in C++

📘 Overview
Dynamic memory allocation creates objects whose lifetimes are controlled independently of the scope that requested them. C++ provides new and delete for explicit management, but standard containers and smart pointers usually provide safer ownership.

🧠 Key Idea
Dynamically allocated objects remain alive until their storage is released by the responsible owner. Correct ownership prevents memory leaks, dangling pointers, and repeated deallocation.

⚔️ Core Details:
- A new expression obtains storage, initializes an object, and returns a pointer: int* p = new int{42};.
- A delete expression destroys an object and releases its storage: delete p;.
- Array allocations require matching array deallocation: int* values = new int[count]{}; pairs with delete[] values;.
- Ordinary throwing new reports allocation failure by throwing std::bad_alloc.
- Deleting an object leaves other pointers to it dangling; accessing the destroyed object through them causes undefined behavior.
- RAII ties resource ownership to an object's lifetime; std::unique_ptr, std::shared_ptr, and std::vector release owned resources automatically.

🎯 Why It Matters:
- Runtime-sized storage supports collections whose sizes are unknown at compilation.
- Explicit ownership allows objects to outlive the functions that create them without returning pointers to destroyed local objects.
- Automatic resource management reduces leaks and use-after-free defects across normal returns and exception paths.
- Unnecessary allocations add runtime overhead and can reduce cache locality compared with contiguous container storage.

🧠 Quick Recall:
- Allocation pairing — Match new with delete and new[] with delete[]; mismatches cause undefined behavior.
- Memory leak — Allocated storage remains unreleased after the program loses the means to free it.
- Double deletion — Deallocating the same allocation more than once causes undefined behavior.
- nullptr — The null pointer value; deleting a null pointer is safe, but nulling one pointer does not fix dangling aliases.
- Ownership tools — std::make_unique creates exclusive smart-pointer ownership; std::make_shared creates shared ownership; std::vector manages dynamic arrays.
```

### Study Pack

**Summary:** Dynamic memory allocation creates objects whose lifetimes are independent of the scope that requested them. A new expression obtains storage, initializes an object, and returns a pointer. A delete expression destroys the object and releases its storage. Ordinary throwing new reports allocation failure by throwing std::bad_alloc.

| Allocation approach | Ownership and release |
| --- | --- |
| Single object using new | The responsible owner must use delete. |
| Array using new[] | The responsible owner must use delete[]. |
| RAII ownership | Smart pointers and containers release owned resources automatically. |

RAII ties resource ownership to an object's lifetime, supporting cleanup during normal returns and exception paths. std::make_unique creates exclusive smart-pointer ownership, std::make_shared creates shared ownership, and std::vector manages dynamic arrays.

A memory leak occurs when allocated storage remains unreleased after the program loses the means to free it. Deleting the same allocation repeatedly causes undefined behavior. After an object is deleted, pointers that still refer to it are dangling; accessing the destroyed object through them also causes undefined behavior.

Dynamic storage supports runtime-sized collections and objects that outlive their creating functions. However, unnecessary allocations add runtime overhead and can reduce cache locality compared with contiguous container storage.

**Common Misconceptions**: Leaving the requesting scope does not automatically destroy a manually allocated object. Setting one pointer to nullptr does not repair dangling aliases. Deleting a null pointer is safe, but mismatched or repeated deallocation is not.

**Key concepts:** Dynamic object lifetime; Allocation pairing; Memory leaks; Dangling pointers; Double deletion; RAII; Exclusive ownership; Shared ownership; Allocation failure

**Q1** (MCQ, CONCEPTUAL): What does the expression new int{42} do?

- A. Creates a local integer that is destroyed when the requesting scope ends
- B. Obtains storage, initializes an integer, and returns a pointer to it ✅
- C. Returns a pointer to uninitialized storage without creating an object
- D. Creates an integer whose storage is automatically released after its first access

_Explanation:_ A new expression combines storage allocation with object initialization. The resulting object can outlive the requesting scope, so its owner must arrange eventual cleanup.

**Q2** (MCQ, CONCEPTUAL): Which action is NOT valid memory management in C++?

- A. Releasing an object created by new int{42} with delete
- B. Releasing an array created by new int[count]{} with delete[]
- C. Applying delete to a null pointer
- D. Releasing an array created by new int[count]{} with delete ✅

_Explanation:_ Array allocation requires the array form of deallocation. Mixing the single-object and array forms causes undefined behavior.

**Q3** (MCQ, CONCEPTUAL): Which best describes how RAII supports safe resource management?

- A. It ties resource ownership to an object's lifetime so cleanup occurs automatically ✅
- B. It makes dynamically allocated objects live until the program terminates
- C. It requires each function to manually release every resource before returning
- D. It prevents allocation failures by replacing exceptions with null pointers

_Explanation:_ When a resource-owning object is destroyed, its managed resources are released. This supports reliable cleanup during both normal execution and exception handling.

**Q4** (MCQ, CONCEPTUAL): Statement 1: Deleting an object leaves other pointers to that object dangling. Statement 2: Setting the deleting pointer to nullptr makes those other pointers safe to dereference. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Deallocation ends the object's lifetime without updating pointers that refer to it. Assigning nullptr changes only the assigned pointer; accessing the destroyed object through an alias still causes undefined behavior.

**Q5** (MCQ, CONCEPTUAL): How does ordinary throwing new report an allocation failure?

- A. It returns nullptr without throwing an exception
- B. It returns a dangling pointer to previously released storage
- C. It throws std::bad_alloc ✅
- D. It creates an uninitialized object and continues execution

_Explanation:_ When the requested storage cannot be obtained, the ordinary throwing form signals failure through an exception rather than returning a usable object pointer.

---

## 29. Pointers and References in C++

| Field | Value |
|---|---|
| Note id | `f79dc811-f532-453e-a68f-f0f96f3f209c` |
| Plan / Section | 🧠 Computer Programming 2 / Memory, Pointers and References |
| Why sampled | B: C++-specific Note (reuse depends on Q1) |
| Subject / Domain Context / Authored Depth | Programming Fundamentals / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Pointers and References in C++

OVERVIEW

Every variable in a running program lives somewhere in memory, and that location has an address. Pointers and references are the two C++ mechanisms that let code work with where a value is stored instead of only with the value itself. Using them is called indirection: reaching a value through something that refers to it.

This note introduces pointers and references as part of the computational and memory model, in standard C++17. It does not cover allocating memory dynamically or pointer arithmetic; those build on this foundation. Most everyday C++ is written with ordinary values and references, and raw pointers are one tool among several, used for specific purposes explained below.

VARIABLES AND ADDRESSES

When you write `int x = 10;`, the program reserves some memory to hold an `int` and associates the name `x` with it. That memory has an address. The address-of operator `&` gives the address of a variable:

    int x = 10;
    // &x is the address of x

Addresses are numbers determined when the program runs. You almost never care about the specific number; you care about the relationship: "this thing refers to that variable."

POINTERS

A pointer is a variable whose value is an address. Its type records what kind of value it points to.

    #include <iostream>

    int main() {
        int x = 10;
        int* p = &x;            // p holds the address of x: "p points to x"

        std::cout << x << '\n';   // 10
        std::cout << *p << '\n';  // 10: the value that p points to

        *p = 25;                  // write to x through p
        std::cout << x << '\n';   // 25
        return 0;
    }

Key points:

- `int* p` declares a pointer to `int`.
- `&x` produces the address that is stored in `p`.
- `*p` is dereferencing: it means "the object p points to". It can be read or assigned.
- Changing `*p` changes `x`, because they are the same object. `p` itself is a separate variable that happens to hold `x`'s address.

A pointer can be re-pointed to a different object:

    int y = 7;
    p = &y;      // p now points to y; x is unchanged

Null pointers

A pointer may refer to nothing. The value `nullptr` means "points to no object":

    int* q = nullptr;
    if (q != nullptr) {
        std::cout << *q << '\n';   // only reached if q points to something
    }

Dereferencing a null pointer has undefined behaviour: the language gives no guarantee about what happens, and in practice the program usually crashes. This is why pointers that might be null must be checked before use.

An uninitialized pointer is worse than a null one: it holds an arbitrary address. Always give a pointer a value when it is declared, `nullptr` if nothing else.

REFERENCES

A reference is another name for an existing object. It is written with `&` in a declaration:

    int x = 10;
    int& r = x;     // r is an alias for x
    r = 25;         // x is now 25
    std::cout << x << '\n';   // 25

Properties of a reference:

- It must be initialized when declared, because it must refer to something.
- It cannot be re-bound to a different object afterwards; `r = y` would assign y's value to x, not make r refer to y.
- It cannot be null.
- You use it exactly like the original variable, with no `*`.

POINTER OR REFERENCE?

- Can be null (Pointer: Yes (`nullptr`); Reference: No)
- Can be re-pointed to another object (Pointer: Yes; Reference: No)
- Must be initialized (Pointer: Should be, but not enforced; Reference: Yes, always)
- Syntax to reach the object (Pointer: `*p`; Reference: Just the name)
- Has its own address and storage (Pointer: Yes; Reference: Behaves as an alias)

A reference is usually simpler and safer when the thing always exists and always refers to the same object. A pointer is used when "no object" is a valid state, or when the target must change.

WHY INDIRECTION EXISTS

Indirection lets different parts of a program refer to the same object instead of copying it.

Letting a function change the caller's variable. By default, a function parameter receives a copy. A reference parameter or a pointer parameter lets the function change the original:

    #include <iostream>

    void addOneRef(int& n) { n = n + 1; }

    void addOnePtr(int* n) {
        if (n != nullptr) {
            *n = *n + 1;
        }
    }

    int main() {
        int a = 5;
        addOneRef(a);     // a is now 6
        addOnePtr(&a);    // a is now 7
        std::cout << a << '\n';   // 7
        return 0;
    }

Avoiding copies. Passing a large object by reference avoids copying it.

Optional relationships. A pointer can represent "there may or may not be an object here."

Linking data together. Structures that connect pieces of data, such as chains of linked elements, are built by storing in each piece a pointer to the next. Building those structures, including how the pieces are created, comes later and rests on the ideas in this note.

LIFETIME: WHEN A POINTER OR REFERENCE STOPS BEING VALID

A pointer or reference is only useful while the object it refers to still exists. A local variable exists only until the end of the block or function where it is declared. If something still refers to it after that, it dangles:

    int* makeValue() {
        int local = 42;
        return &local;   // WRONG: local ceases to exist when the function returns
    }

The returned pointer refers to memory that no longer holds `local`. Using it is undefined behaviour. Compilers with warnings enabled commonly flag this mistake, which is one reason to compile with `-Wall -Wextra`.

The rule to remember: the object must outlive every pointer or reference that refers to it.

USING THEM WISELY IN MODERN C++

Raw pointers are part of the language, but they are not the default solution to everyday problems.

- Prefer ordinary values and references for parameters and local data.
- Prefer library types such as `std::string` and `std::vector` over manually managed memory, because they handle their own storage.
- Use a raw pointer mainly when an object may be absent, when a reference will not do because the target must change, or to understand how linked structures work.
- Modern C++ also provides smart pointers, library types that manage the lifetime of objects for you. They are the usual choice when ownership of dynamically created objects is involved, and are introduced separately.

Learning pointers is valuable because they reveal how data is arranged and shared in memory. It does not mean they should appear in every piece of code.

COMMON MISCONCEPTIONS

- "A pointer holds the value." It holds an address; `*p` gives the value.
- "The `*` always means the same thing." In a declaration (`int* p`) it marks a pointer type; in an expression (`*p`) it dereferences; between two numbers (`a * b`) it multiplies.
- "`int* a, b;` makes two pointers." Only `a` is a pointer; `b` is an `int`. Declaring one variable per line avoids this.
- "A reference can be reassigned to refer to something else." Assigning to a reference changes the object it refers to.
- "Printing a pointer prints the value it points to." It prints the address (use `*p` for the value).
- "A pointer that was valid stays valid." Validity depends on the lifetime of the object it refers to.

KEY POINTS TO REMEMBER

- Every object has an address; `&x` gives it.
- A pointer stores an address, is dereferenced with `*`, can be `nullptr` and can be re-pointed.
- A reference is a permanent alias for an existing object, cannot be null and cannot be re-bound.
- Indirection lets code share and modify the same object instead of copying it, and is the basis for linked structures.
- Dereferencing a null or dangling pointer is undefined behaviour, so initialize pointers and make sure the object outlives them.
- In modern C++ prefer values, references and standard library types, and treat raw pointers as a specific tool.
```

### Study Pack

**Summary:** In C++, every variable occupies a memory location identified by an address. Pointers and references are mechanisms to access data indirectly through these addresses, a concept known as indirection. A pointer is a variable holding the address of another variable and is declared with an asterisk (e.g., `int* p`). Dereferencing a pointer using `*p` accesses the value stored at that address. Pointers can be null (`nullptr`), meaning they point to no object, and can be reassigned to different objects. In contrast, a reference is an alias for an existing object, declared with an ampersand (e.g., `int& r`), must be initialized upon declaration, cannot be null, and cannot be re-bound to refer to another object. Both pointers and references enable functions to modify caller variables and help avoid costly data copying. Pointers are also central to building dynamic data structures by linking elements via addresses. However, pointers must be used carefully: dereferencing null or dangling pointers (pointing to objects that no longer exist) causes undefined behavior. Modern C++ encourages using values and references where possible, reserving pointers for cases requiring optional or changeable references, while smart pointers manage dynamic memory more safely. Understanding these concepts is fundamental to grasping C++ memory and computational models.  

| Property               | Pointer               | Reference            |
|------------------------|-----------------------|----------------------|
| Can be null            | Yes (`nullptr`)       | No                   |
| Reassignment possible  | Yes                   | No                   |
| Initialization needed  | Should be             | Always                |
| Syntax to access value | `*p`                  | Variable name itself   |
| Owns storage           | Has own storage        | Acts as alias          |

**Common Misconceptions:**  
- A pointer holds the address, not the value directly.  
- The `*` symbol's meaning depends on context: declaration, dereference, or multiplication.  
- Declaring multiple variables in one line with `*` can lead to confusion about what is a pointer.  
- References cannot be reassigned to reference another object.  
- Printing a pointer shows its address, not the pointed-to value.  
- A pointer's validity requires the referenced object's lifetime to persist.

**Key concepts:** Pointer declaration; Dereferencing; Null pointer; Reference aliasing; Indirection; Pointer reassignment; Lifetime of objects; Undefined behavior; Modern C++ best practices

**Q1** (MCQ, CONCEPTUAL): What does the expression `*p` mean when `p` is a pointer in C++?

- A. Pointer declaration
- B. Address of p
- C. Dereferencing p ✅
- D. Multiplication operation

_Explanation:_ The expression `*p` accesses the value stored at the memory address contained in pointer `p`, which is called dereferencing.

**Q2** (MCQ, CONCEPTUAL): Which statement about references is correct?

- A. References can be null
- B. References can be reassigned
- C. References must be initialized upon declaration ✅
- D. References hold addresses like pointers

_Explanation:_ References must always be initialized when declared and act as aliases for existing objects. They cannot be null or reassigned to refer elsewhere.

**Q3** (MCQ, CONCEPTUAL): Why is dereferencing a null pointer dangerous in C++?

- A. It causes compilation error
- B. It leads to undefined behavior and possible crash ✅
- C. It returns zero
- D. It returns address null

_Explanation:_ Dereferencing a null pointer in C++ leads to undefined behavior, typically causing the program to crash at runtime.

**Q4** (MCQ, CONCEPTUAL): When should you prefer using a pointer over a reference?

- A. When referring to a mandatory object that always exists
- B. When you need an alias that can't be reassigned
- C. When the target object may be absent or needs to change ✅
- D. When you want simpler syntax

_Explanation:_ Pointers can hold `nullptr` to indicate no object and can be reassigned to point to different objects, suitable when the target may or may not exist or must change.

**Q5** (MCQ, CONCEPTUAL): Statement 1: A pointer's value is the address of the object it points to. Statement 2: Assigning to a reference changes which object it refers to. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ A pointer holds the address of the object it points to, so Statement 1 is correct. Assigning to a reference modifies the referred object’s value but does not change the reference to refer to another object, so Statement 2 is incorrect.

---

## 30. Unit Testing in C++

| Field | Value |
|---|---|
| Note id | `7141681d-ae42-40aa-b4d3-abefda584896` |
| Plan / Section | 🧠 Computer Programming 2 / Program Quality |
| Why sampled | B: C++-specific Note (reuse depends on Q1) |
| Subject / Domain Context / Authored Depth | Programming Fundamentals / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Unit Testing in C++

📘 Overview
Unit testing checks the behavior of small, isolated pieces of C++ code against explicit expectations. Automated tests expose regressions and verify normal cases, boundary conditions, and error handling.

🧠 Key Idea
A unit test supplies controlled inputs to a small unit of code and checks its observable behavior with assertions. Reliable tests are deterministic, independent, and focused on the unit's contract rather than its implementation.

⚔️ Core Details:
- A unit is a small testable component, typically a function or a class.
- Arrange–Act–Assert separates test setup, execution of the target operation, and verification of the result.
- GoogleTest provides TEST for test cases, EXPECT_EQ for nonfatal equality checks, and ASSERT_EQ for equality checks that stop the current test function on failure.
- Test cases should cover typical inputs, boundary values, and invalid inputs specified by the unit's contract.
- Test doubles replace dependencies such as file access or network clients to keep unit tests isolated and deterministic.
- C++ assert checks can disappear when NDEBUG is defined; dedicated test framework assertions remain available in release test builds.

🎯 Why It Matters:
- Regression tests reveal when a code change breaks previously verified behavior.
- Tests provide executable examples of a function or class contract.
- Isolating a component for testing can expose excessive coupling and encourage clearer interfaces.
- A passing suite checks only the cases it contains; high code coverage does not prove correctness.

🧠 Quick Recall:
- Unit test — an automated check of a small component's behavior in isolation.
- Arrange–Act–Assert — prepare inputs and dependencies, execute the operation, then verify the outcome.
- Fixture — shared setup and cleanup that gives related tests a consistent starting state.
- EXPECT_THROW — a GoogleTest assertion that checks whether a statement throws a specified exception type.
- Integration test — a test of interactions between components rather than one component in isolation.
```

### Study Pack

**Summary:** Unit testing verifies the observable behavior of a small C++ component, typically a function or class, against explicit expectations. Reliable tests are deterministic, independent, and focused on the component's contract rather than implementation details.

Arrange–Act–Assert organizes a test into setup, execution, and verification. Test cases should cover typical inputs, boundary values, and invalid inputs defined by the contract. Fixtures provide shared setup and cleanup, while test doubles replace dependencies such as files or network clients to preserve isolation.

GoogleTest provides TEST to define tests and assertions to verify outcomes.

| Assertion | Purpose | On failure |
|---|---|---|
| EXPECT_EQ | Check equality | Continue the current test |
| ASSERT_EQ | Check equality | Stop the current test function |
| EXPECT_THROW | Check for a specified exception type | Report an unmet exception expectation |

Unlike dedicated framework assertions, C++ assert checks can disappear when NDEBUG is defined. Regression tests detect changes that break previously verified behavior, and tests serve as executable examples of component contracts. Integration tests examine interactions between components rather than a single isolated unit.

**Common Misconceptions**: High code coverage does not prove correctness; passing tests verify only the cases included; a fatal GoogleTest assertion stops the current test function, not necessarily the entire suite.

**Key concepts:** Unit isolation; Arrange Act Assert; Test assertions; Test doubles; Test fixtures; Boundary testing; Regression detection; Code coverage; Exception testing; Integration testing

**Q1** (MCQ, CONCEPTUAL): In Arrange–Act–Assert, what should a test do immediately after executing the target operation?

- A. Create the controlled inputs
- B. Verify the observed outcome ✅
- C. Replace external dependencies
- D. Initialize the test fixture

_Explanation:_ The Act phase performs the operation. The Assert phase then compares what happened with the expectations defined by the unit's contract.

**Q2** (MCQ, CONCEPTUAL): Which GoogleTest assertion checks equality and prevents later statements in the current test function from running if the check fails?

- A. EXPECT_EQ
- B. EXPECT_THROW
- C. ASSERT_EQ ✅
- D. TEST

_Explanation:_ A fatal equality assertion returns from the current test function when the comparison fails. This is useful when later checks depend on that comparison succeeding.

**Q3** (MCQ, CONCEPTUAL): Which best describes why a unit test replaces a network client with a test double?

- A. To measure the availability of the real network service
- B. To exercise interactions with the production server
- C. To avoid checking the component's observable behavior
- D. To control dependency behavior and avoid external variability ✅

_Explanation:_ An unpredictable dependency can make a test fail for reasons unrelated to the unit. A controlled substitute helps keep results repeatable and focused on the component under test.

**Q4** (MCQ, CONCEPTUAL): Which claim about unit-test effectiveness is NOT correct?

- A. High code coverage proves that a component is correct. ✅
- B. Regression tests can reveal behavior broken by a code change.
- C. Passing tests verify only the cases included in the suite.
- D. Tests can provide executable examples of a component's contract.

_Explanation:_ Executing code during tests does not establish that every relevant input or behavior has been checked. Correctness still depends on meaningful expectations and adequate case selection.

**Q5** (MCQ, CONCEPTUAL): Statement 1: Defining NDEBUG can remove C++ assert checks. Statement 2: Dedicated test-framework assertions necessarily disappear in release test builds. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ The standard assert mechanism is affected by NDEBUG. Dedicated testing frameworks provide their own assertions that remain available for checking behavior in release test builds.

---

## 31. Recursion versus Iteration

| Field | Value |
|---|---|
| Note id | `43d19a0d-1d80-4a3b-952f-ab6b7ae855aa` |
| Plan / Section | 🧠 Computer Programming 2 / Recursion Revisited |
| Why sampled | A: uses asymptotic notation (same class as the Theta issue in DM Note 22) |
| Subject / Domain Context / Authored Depth | Programming Fundamentals / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Recursion versus Iteration

📘 Overview
Recursion repeats computation through function calls, while iteration repeats computation through loops. Both can solve the same problems, but their control flow, termination conditions, and memory requirements can differ.

🧠 Key Idea
Choose recursion when a problem naturally decomposes into smaller instances of itself; choose iteration when explicit state updates express the solution more directly. Compare actual operations and stored state rather than assuming either approach is always faster.

⚔️ Core Details:
- Recursion solves a problem through direct or indirect calls to the same function.
- Iteration repeats a block through a loop such as for or while.
- Recursive termination requires a reachable base case and progress toward it on every recursive path.
- Without tail-call optimization, recursive calls retain activation records containing parameters, local state, and return information.
- For factorial of a nonnegative integer $n$, straightforward recursive and iterative implementations both take $O(n)$ time under constant-time arithmetic.
- Without tail-call optimization, recursive factorial uses $O(n)$ auxiliary space versus $O(1)$ for iterative factorial.

🎯 Why It Matters:
- Recursion closely matches trees, nested structures, and divide-and-conquer algorithms, often making their logic easier to express.
- Deep recursion can exhaust the call stack even when the algorithm is otherwise correct.
- Replacing recursion with iteration may require an explicit stack, so removing function calls does not necessarily reduce asymptotic memory use.
- Performance depends on algorithm design and language implementation; repeated recursive subproblems may require memoization rather than a simple rewrite as a loop.

🧠 Quick Recall:
- Base case — A condition that returns a result without making another recursive call.
- Recursive case — A step that reduces the problem toward a base case.
- Loop termination — Execution ends when the loop condition becomes false or an explicit exit occurs.
- Tail-call optimization — Reuse of a call frame when a function call is the final operation; support depends on the language and implementation.
- Factorial recurrence — $0! = 1$ and $n! = n \times (n-1)!$ for integers $n \geq 1$.
```

### Study Pack

**Summary:** Recursion repeats computation through direct or indirect calls to the same function; iteration repeats a block using loops such as for or while. Choose recursion when a problem naturally decomposes into smaller instances of itself, and iteration when explicit state updates express the solution more directly.

| Aspect | Recursion | Iteration |
|---|---|---|
| Termination | Reachable base case with progress on every recursive path | Loop condition becomes false or an explicit exit occurs |
| Factorial time | $O(n)$ | $O(n)$ |
| Factorial auxiliary space | $O(n)$ without tail-call optimization | $O(1)$ |

These factorial bounds assume straightforward implementations and constant-time arithmetic. The recurrence is $0! = 1$ and $n! = n \times (n-1)!$ for integers $n \geq 1$.

Without tail-call optimization, recursive calls retain activation records containing parameters, local state, and return information. Deep recursion can exhaust the call stack. Tail-call optimization can reuse a call frame when a function call is the final operation, but support depends on the language and implementation.

Recursion often expresses trees, nested structures, and divide-and-conquer algorithms clearly. Converting recursion to iteration may require an explicit stack. Repeated recursive subproblems may benefit from memoization rather than merely replacing calls with loops.

**Common Misconceptions** Neither approach is always faster. Removing recursive calls does not necessarily reduce asymptotic memory use. A base case alone does not ensure termination unless every recursive path progresses toward it.

**Key concepts:** Base Case; Recursive Case; Loop Termination; Activation Records; Tail Call Optimization; Factorial Complexity; Explicit Stack; Memoization

**Q1** (MCQ, CONCEPTUAL): What must a recursive algorithm ensure to terminate?

- A. Every recursive call creates another recursive call.
- B. A reachable base case exists, and every recursive path progresses toward it. ✅
- C. A base case is declared, regardless of whether execution can reach it.
- D. Every recursive call receives the same unchanged problem.

_Explanation:_ Execution must eventually reach a condition that returns without another recursive call. Each recursive path must move toward that condition rather than continue indefinitely.

**Q2** (MCQ, CONCEPTUAL): For straightforward factorial implementations, assuming constant-time arithmetic and no tail-call optimization, how do their time and auxiliary space requirements compare?

- A. Both take $O(n)$ time; recursion uses $O(1)$ space and iteration uses $O(n)$ space.
- B. Recursion takes $O(n^2)$ time and iteration takes $O(n)$ time; both use $O(1)$ space.
- C. Both take $O(n)$ time; recursion uses $O(n)$ space and iteration uses $O(1)$ space. ✅
- D. Both take $O(n)$ time and both use $O(1)$ space.

_Explanation:_ Both implementations perform work proportional to the input. Recursive calls retain a chain of activation records, whereas the loop needs only a fixed amount of state.

**Q3** (MCQ, CONCEPTUAL): Which claim about replacing recursion with iteration is NOT correct?

- A. An iterative version may need an explicit stack.
- B. Stored state should be compared when evaluating memory requirements.
- C. Removing recursive calls always reduces asymptotic auxiliary space. ✅
- D. Deep recursion can exhaust the call stack.

_Explanation:_ An explicit stack can retain the same amount of pending work as recursive activation records. Changing the control mechanism therefore does not guarantee a better space complexity.

**Q4** (MCQ, CONCEPTUAL): Which best describes when recursion is a natural choice?

- A. The problem is most directly expressed through explicit loop-state updates.
- B. The solution must avoid all function calls.
- C. The implementation must guarantee constant auxiliary space for any input.
- D. The problem breaks into smaller instances of itself, as with nested structures. ✅

_Explanation:_ Recursive calls can mirror the structure of the problem, making the logic easier to express for trees, nesting, and divide-and-conquer algorithms.

**Q5** (MCQ, CONCEPTUAL): Statement 1: Tail-call optimization can reuse a call frame when a function call is the final operation. Statement 2: Every language implementation guarantees tail-call optimization. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ When no work remains after a call returns, frame reuse is possible. Whether that optimization actually occurs depends on the language and its implementation.

---

## 32. Recursive Problem Solving

| Field | Value |
|---|---|
| Note id | `08eca5d2-50e3-4bc0-aa4d-10c87207738b` |
| Plan / Section | 🧠 Computer Programming 2 / Recursion Revisited |
| Why sampled | A: uses asymptotic notation (same class as the Theta issue in DM Note 22) |
| Subject / Domain Context / Authored Depth | Programming Fundamentals / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Recursive Problem Solving

📘 Overview
Recursion solves a problem by combining solutions to smaller instances of the same problem. A correct recursive algorithm needs a base case, a recursive step, and progress toward termination.

🧠 Key Idea
A recursive call must reduce the problem toward a base case. Correctness depends on the base case being valid and the recursive step correctly using the smaller problem's result.

⚔️ Core Details:
- A base case produces a result without making another recursive call.
- A recursive step expresses the current solution using one or more smaller instances of the same problem.
- Termination requires a well-founded measure, such as remaining input length, to decrease toward a base case on every recursive path.
- Each active function call has a stack frame containing its parameters, local state, and return information.
- Recursive binary search examines the middle element and continues only in the half that could contain the target.
- Recursive binary search has recurrence $T(n)=T(\lfloor n/2\rfloor)+\Theta(1)$ and time complexity $\Theta(\log n)$; $T(n)$ is runtime for input size $n$.

🎯 Why It Matters:
- Recursion naturally expresses operations on nested computing structures such as directory trees, syntax trees, and linked lists.
- Recurrence analysis reveals performance costs that can be hidden by short recursive implementations.
- Excessive recursion depth can overflow the call stack even when the algorithm is logically correct.
- Memoization avoids repeated computation when recursive branches solve overlapping subproblems.

🧠 Quick Recall:
- Base case — A directly solvable instance that stops further recursive calls.
- Recursive step — A rule that constructs a solution from solutions to smaller instances of the same problem.
- Recursion depth — The maximum number of simultaneously active recursive calls.
- Tail recursion — Recursion in which the recursive call is the function's final operation; stack-frame reuse depends on language and runtime support.
- Inductive correctness — Establish the base case, then show that correct solutions to smaller instances produce a correct solution to the current instance.
```

### Study Pack

**Summary:** Recursion solves a problem by using solutions to smaller instances of the same problem. Correct recursive algorithms need valid stopping conditions, a correct recursive rule, and guaranteed progress toward termination.

| Component | Role |
| --- | --- |
| Base case | Returns a result without another recursive call. |
| Recursive step | Constructs the current solution from smaller instances. |
| Termination measure | Decreases toward a base case on every recursive path. |

Inductive correctness establishes that the base case is valid and that correct results for smaller instances produce a correct result for the current instance.

Each active function call has a stack frame containing parameters, local state, and return information. Recursion depth is the maximum number of simultaneously active recursive calls. Excessive depth can overflow the call stack even when an algorithm is logically correct.

Recursive binary search examines the middle element and continues only in the half that could contain the target. Its recurrence is $T(n)=T(\lfloor n/2\rfloor)+\Theta(1)$, giving time complexity $\Theta(\log n)$, where $n$ is the input size.

Recursion naturally represents operations on directory trees, syntax trees, and linked lists. Memoization avoids repeated computation when recursive branches encounter overlapping subproblems. Tail recursion places the recursive call as the function's final operation; stack-frame reuse depends on language and runtime support.

**Common Misconceptions** A base case alone does not guarantee termination; every recursive path must progress toward it. Tail recursion does not automatically guarantee stack-frame reuse. Logical correctness does not prevent stack overflow.

**Key concepts:** Base case; Recursive step; Well-founded measure; Inductive correctness; Stack frame; Recursion depth; Recursive binary search; Memoization; Tail recursion

**Q1** (MCQ, CONCEPTUAL): What must happen on every recursive path to establish termination?

- A. The number of local variables must decrease.
- B. A well-founded measure must decrease toward a base case. ✅
- C. Every call must generate multiple smaller subproblems.
- D. Each recursive call must immediately return a final result.

_Explanation:_ A decreasing measure, such as remaining input length, establishes progress toward a stopping condition. Merely defining a stopping condition is insufficient if some calls never approach it.

**Q2** (MCQ, CONCEPTUAL): Several recursive branches repeatedly solve the same subproblem. What technique directly addresses this repeated work?

- A. Increasing the maximum recursion depth
- B. Adding more parameters to every stack frame
- C. Memoization ✅
- D. Replacing the base case with another recursive call

_Explanation:_ Saving previously computed results allows later calls for the same subproblem to reuse them instead of performing the computation again.

**Q3** (MCQ, CONCEPTUAL): Which statement about recursive calls is NOT correct?

- A. An active call has a stack frame containing its parameters.
- B. Recursion depth counts the maximum simultaneously active recursive calls.
- C. A stack frame contains local state and return information.
- D. Logical correctness guarantees that recursion cannot overflow the call stack. ✅

_Explanation:_ Correctness concerns whether the algorithm produces the intended result. Available stack space is a separate resource constraint, so excessive depth can still cause execution to fail.

**Q4** (MCQ, CONCEPTUAL): Which best describes why recursive binary search has time complexity $\Theta(\log n)$?

- A. Each continuing call retains only about half the search range and performs constant additional work. ✅
- B. Each continuing call removes just one element and performs constant additional work.
- C. Each call recursively searches both halves before returning.
- D. Each call finishes the entire search without any further recursive calls.

_Explanation:_ The recurrence $T(n)=T(\lfloor n/2\rfloor)+\Theta(1)$ represents one shrinking recursive branch. Repeated halving reaches a base case after logarithmically many levels.

**Q5** (MCQ, CONCEPTUAL): Statement 1: In tail recursion, the recursive call is the function's final operation. Statement 2: Tail recursion guarantees stack-frame reuse in every language and runtime. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ The position of the recursive call determines whether recursion is tail-recursive. Eliminating additional stack growth requires implementation support and is not guaranteed by that structure alone.

---

## 33. Elementary Sorting: Bubble, Selection and Insertion

| Field | Value |
|---|---|
| Note id | `d6f2e0b4-c434-4b77-bc40-b76e69d60faa` |
| Plan / Section | 🧠 Computer Programming 2 / Searching and Sorting Basics |
| Why sampled | A: uses asymptotic notation (same class as the Theta issue in DM Note 22) |
| Subject / Domain Context / Authored Depth | Data Structures and Algorithms / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Elementary Sorting: Bubble, Selection and Insertion

📘 Overview
Bubble, selection, and insertion sort are comparison-based algorithms with quadratic average-case running time. Their standard array implementations use constant auxiliary space, but differ in how they move elements and exploit existing order.

🧠 Key Idea
Bubble sort exchanges adjacent out-of-order elements, selection sort places the smallest remaining element, and insertion sort inserts each new element into a sorted prefix. Algorithm choice depends on existing order, write costs, and whether equal-key records must retain their order.

⚔️ Core Details:
- Bubble sort repeatedly scans the unsorted region with adjacent swaps whenever the left key is larger than the right key.
- Selection sort swaps the minimum element of the unsorted suffix into its first position.
- Insertion sort extends a sorted prefix by shifting strictly larger elements right to make room for the next key.
- Bubble sort with a no-swap early exit has best-case time $\Theta(n)$ and average- and worst-case time $\Theta(n^2)$ for $n$ elements.
- Selection sort performs $\frac{n(n-1)}{2}$ key comparisons for $n$ elements, giving $\Theta(n^2)$ time in the best, average, and worst cases.
- Insertion sort has best-case time $\Theta(n)$ and average- and worst-case time $\Theta(n^2)$ for $n$ elements.

🎯 Why It Matters:
- Insertion sort is effective for small or nearly sorted arrays and often handles small subarrays within hybrid sorting algorithms.
- Selection sort performs at most $n-1$ swaps for $n$ elements, making it relevant when array writes are more expensive than comparisons.
- Stable sorting preserves earlier ordering among equal-key records, supporting successive sorts by different fields.
- Quadratic average-case growth makes these algorithms poor general-purpose choices for large, arbitrarily ordered arrays.

🧠 Quick Recall:
- Stable sort — preserves the original relative order of records with equal keys.
- In-place sorting — standard array versions of all three algorithms require $\Theta(1)$ auxiliary space.
- Stability by algorithm — bubble and insertion sort are stable with strict greater-than comparisons; standard swap-based selection sort is not stable.
- Bubble sort early exit — terminate after a complete pass with no swaps.
- Insertion sort adaptivity — time is $\Theta(n+I)$, where $n$ is the number of elements and $I$ counts index pairs $i<j$ with $A[i]>A[j]$.
```

### Study Pack

**Summary:** Bubble, selection, and insertion sort are comparison-based algorithms with average-case time $\Theta(n^2)$. Their standard array implementations use $\Theta(1)$ auxiliary space, but differ in element movement, adaptivity, and stability.

| Algorithm | Element movement | Best-case time |
|---|---|---|
| Bubble sort | Swaps adjacent out-of-order elements | $\Theta(n)$ with no-swap early exit |
| Selection sort | Swaps the smallest remaining element into position | $\Theta(n^2)$ |
| Insertion sort | Shifts larger elements to extend a sorted prefix | $\Theta(n)$ |

Selection sort always performs $\frac{n(n-1)}{2}$ key comparisons and at most $n-1$ swaps, making it relevant when writes are expensive. Insertion sort works well for small or nearly sorted arrays; its time is $\Theta(n+I)$, where $I$ counts inversions: index pairs $i<j$ with $A[i]>A[j]$. Bubble sort can stop after a complete pass without swaps.

Bubble and insertion sort are stable when they move elements only on strict greater-than comparisons. Standard swap-based selection sort is not stable. Stability preserves the original relative order of equal-key records, supporting successive sorts by different fields.

**Common Misconceptions**: In-place sorting does not imply stability. Selection sort does not become linear on sorted input. Bubble sort's linear best case requires the no-swap early exit.

**Key concepts:** Bubble sort; Selection sort; Insertion sort; Sorted prefix; Sort stability; In-place sorting; Early termination; Inversions; Write costs

**Q1** (MCQ, CONCEPTUAL): Which best describes how insertion sort processes the next element?

- A. It repeatedly exchanges neighboring elements across the entire unsorted region.
- B. It shifts larger elements right and places the next key into the sorted prefix. ✅
- C. It finds the minimum of the unsorted suffix and swaps it into its first position.
- D. It places the next key at the end without comparing it to the sorted prefix.

_Explanation:_ The processed portion remains ordered. Making space for each new key extends that ordered portion by one element.

**Q2** (MCQ, COMPUTATIONAL): How many key comparisons does standard selection sort perform on an array of $6$ elements?

- A. $6$
- B. $15$ ✅
- C. $30$
- D. $36$

_Explanation:_ Selection sort uses $\frac{n(n-1)}{2}$ comparisons regardless of initial order. Substituting $n=6$ gives $\frac{6(6-1)}{2}=\frac{30}{2}=15$, so the total is $15$.

**Q3** (MCQ, CONCEPTUAL): Which statement about sorting stability is NOT correct?

- A. Stability preserves the original relative order of equal-key records.
- B. Bubble sort is stable when it swaps only on strict greater-than comparisons.
- C. Insertion sort is stable when it shifts only strictly larger elements.
- D. Standard swap-based selection sort guarantees stability. ✅

_Explanation:_ Exchanging a minimum with a distant element can move equal-key records past one another, changing their original relative order.

**Q4** (MCQ, CONCEPTUAL): What allows bubble sort to terminate early?

- A. The first adjacent pair inspected is already ordered.
- B. The minimum element reaches the beginning of the array.
- C. A complete pass finishes without any swaps. ✅
- D. The current pass performs fewer swaps than the previous pass.

_Explanation:_ If no adjacent pair in the scanned region needs correction, that region is ordered. Together with the already settled portion, this means further passes are unnecessary.

**Q5** (MCQ, CONCEPTUAL): Statement 1: The standard array implementations of all three sorts use constant auxiliary space. Statement 2: Selection sort's limited number of swaps can make it useful when array writes are expensive. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ These implementations rearrange elements within the input array using only a fixed amount of extra storage. Selection sort also performs at most $n-1$ swaps, limiting element exchanges even though its comparison count remains quadratic.

---

## 34. Linear and Binary Search

| Field | Value |
|---|---|
| Note id | `2eefb012-b391-45b6-82b5-b895c621e455` |
| Plan / Section | 🧠 Computer Programming 2 / Searching and Sorting Basics |
| Why sampled | A: uses asymptotic notation (same class as the Theta issue in DM Note 22) |
| Subject / Domain Context / Authored Depth | Data Structures and Algorithms / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science, Information Systems, Information Technology |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Linear and Binary Search

📘 Overview
Linear search examines elements sequentially, whereas binary search repeatedly halves a sorted search interval. Their worst-case running times are $O(n)$ and $O(\log n)$ respectively, where $n$ is the number of elements.

🧠 Key Idea
Binary search gains efficiency by using sorted order to eliminate half the remaining candidates after each comparison. Linear search needs no sorted order and works directly on sequential collections.

⚔️ Core Details:
- Linear search checks elements from the beginning until it finds the target or exhausts the collection.
- Linear search has worst-case time $O(n)$ for a collection of $n$ elements.
- Standard binary search requires sorted data and constant-time indexed access to achieve logarithmic running time.
- Binary search compares the target with the midpoint element and retains only the half that can contain a match.
- An overflow-resistant midpoint is $m = l + \lfloor (r-l)/2 \rfloor$, where $l$ and $r$ are the inclusive lower and upper indices.
- Binary search has worst-case time $O(\log n)$ on a sorted array of $n$ elements.

🎯 Why It Matters:
- Linear search is suitable for unsorted data, small collections, and collections without efficient indexed access.
- Sorting solely for one lookup can cost more than a linear scan; repeated lookups can justify maintaining sorted data.
- A sorted linked list does not provide the constant-time midpoint access needed for standard binary search's logarithmic running time.
- Correct interval boundaries prevent skipped elements, out-of-range access, and nonterminating binary-search loops.

🧠 Quick Recall:
- Inclusive interval updates — For ascending order, set $l = m + 1$ when the target exceeds the midpoint value; set $r = m - 1$ when it is smaller.
- Unsuccessful binary search — With inclusive bounds, $l > r$ means no candidate remains.
- Best-case time — Both searches take $O(1)$ when the first examined element matches.
- Auxiliary space — Iterative linear and binary search use $O(1)$; standard recursive binary search uses $O(\log n)$ stack space.
- Duplicate values — Standard search returns one matching position, not necessarily the first or last occurrence.
```

### Study Pack

**Summary:** Linear search checks elements sequentially until it finds the target or exhausts the collection. Binary search compares the target with a midpoint element and uses sorted order to discard half the remaining candidates.

| Property | Linear search | Standard binary search |
|---|---|---|
| Requirements | No sorted order or efficient indexing needed | Sorted data and constant-time indexed access for logarithmic time |
| Worst-case time | $O(n)$ | $O(\log n)$ |
| Best-case time | $O(1)$ | $O(1)$ |

Here, $n$ is the number of elements. Linear search suits unsorted data, small collections, and collections without efficient indexed access. Sorting solely for one lookup may cost more than scanning; repeated lookups can justify maintaining sorted data.

For binary search with inclusive bounds, an overflow-resistant midpoint is $m = l + \lfloor (r-l)/2 \rfloor$. In ascending order, update $l = m + 1$ when the target exceeds the midpoint value, or $r = m - 1$ when it is smaller. If $l > r$, no candidates remain. Correct boundaries prevent skipped elements, out-of-range access, and nontermination.

Iterative versions of both searches use $O(1)$ auxiliary space. Standard recursive binary search uses $O(\log n)$ stack space.

**Common Misconceptions**: Sorted data alone does not guarantee logarithmic binary search on a linked list. Standard search with duplicates need not return the first or last occurrence. Binary search is not automatically the cheapest choice when sorting is needed for a single lookup.

**Key concepts:** Linear search; Binary search; Sorted order; Indexed access; Midpoint calculation; Inclusive bounds; Auxiliary space; Duplicate values

**Q1** (MCQ, CONCEPTUAL): An unsorted collection has no efficient indexed access, and only one lookup is needed. What is the most suitable approach described in the notes?

- A. Apply binary search directly
- B. Scan sequentially with linear search ✅
- C. Sort the collection before every comparison
- D. Repeatedly inspect midpoint positions without sorting

_Explanation:_ Checking elements in sequence requires neither a preprocessing sort nor efficient access to arbitrary positions, making it appropriate for this collection.

**Q2** (MCQ, CONCEPTUAL): Which statement about standard binary search is NOT correct?

- A. It may return any matching position when duplicates exist.
- B. With inclusive bounds, $l > r$ indicates an unsuccessful search.
- C. A sorted linked list guarantees $O(\log n)$ running time. ✅
- D. Its worst-case running time on a sorted array is $O(\log n)$.

_Explanation:_ Logarithmic running time requires constant-time indexed access as well as sorted order. Reaching midpoint elements through linked-list traversal does not satisfy that access requirement.

**Q3** (MCQ, CONCEPTUAL): Which best describes why binary search reduces the number of candidates so quickly?

- A. It compares the target with every remaining element simultaneously.
- B. It rearranges the collection after each failed comparison.
- C. It checks consecutive elements until the target is found.
- D. It uses a midpoint comparison and sorted order to eliminate half the remaining candidates. ✅

_Explanation:_ Ordering determines which side can still contain the target after a midpoint comparison, so the other side can be excluded without checking its elements individually.

**Q4** (MCQ, CONCEPTUAL): Statement 1: Iterative linear search and iterative binary search use $O(1)$ auxiliary space. Statement 2: Standard recursive binary search uses $O(1)$ stack space in the worst case. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ The iterative algorithms maintain only a fixed amount of extra state. Recursive binary search retains stack frames across successive calls, requiring $O(\log n)$ stack space in the worst case.

**Q5** (MCQ, COMPUTATIONAL): Binary search on an ascending array has inclusive bounds $l = 2$ and $r = 10$. Using $m = l + \lfloor (r-l)/2 \rfloor$, what is the new lower bound if the target exceeds the midpoint value?

- A. $5$
- B. $6$
- C. $7$ ✅
- D. $8$

_Explanation:_ First calculate the midpoint: $m = 2 + \lfloor (10-2)/2 \rfloor = 6$. Because the target is larger, exclude the midpoint and all lower indices: $l = m + 1$. The new lower bound is $7$.

---

## 35. The Physical, Sexual, Material, Digital, and Political Self

| Field | Value |
|---|---|
| Note id | `e0fbc576-5d95-4b9b-a3d3-3e9cc5456972` |
| Plan / Section | 🪞 Understanding the Self / Dimensions of the Self |
| Why sampled | E: General Education Note touching technology or workplace writing |
| Subject / Domain Context / Authored Depth | Understanding the Self / GENERAL_EDUCATION / COLLEGE |
| Current Applicable Programs | Computer Science, Early Childhood Education, Elementary Education, Information Systems, Information Technology, Physical Education, Secondary Education, Special Needs Education, Technical-Vocational Teacher Education |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
The Physical, Sexual, Material, Digital, and Political Self

📘 Overview
Personal identity includes embodied, sexual, possession-related, online, and civic dimensions. These dimensions develop through self-understanding and social interaction rather than existing as fixed, isolated parts of a person.

🧠 Key Idea
The physical, sexual, material, digital, and political selves are interconnected dimensions of identity. Self-awareness involves examining how personal choices, social expectations, and unequal opportunities shape each dimension.

⚔️ Core Details:
- The physical self concerns the body as experienced and perceived through appearance, bodily capacities, health, and body image.
- The sexual self encompasses sexual feelings, attraction, orientation, values, boundaries, and intimacy.
- The material self consists of possessions and places experienced as extensions of identity, such as clothing, a home, or personally meaningful objects.
- The digital self is the identity expressed and inferred through online profiles, posts, interactions, and data traces.
- The political self involves beliefs about power, rights, authority, collective belonging, and participation in public life.
- Identity dimensions intersect through experiences such as expressing body image online or connecting ownership with social status.

🎯 Why It Matters:
- Distinguishing bodily characteristics from judgments about appearance helps reveal how beauty standards affect self-worth.
- Respect for sexual autonomy supports relationships grounded in freely given, informed, specific, and reversible consent.
- Examining consumption and online self-presentation can expose pressures to equate personal worth with possessions or public approval.
- Reflection on political identity supports informed participation and critical evaluation of inherited beliefs, group loyalties, and competing claims.

🧠 Quick Recall:
- Body image — perceptions, thoughts, and feelings about one's body rather than the body's objective characteristics.
- Sexual orientation — a pattern of sexual or romantic attraction, distinct from gender identity and sexual behavior.
- Material self — possessions and places incorporated into a person's sense of identity.
- Digital footprint — data traces created through online activity, including information shared by others or collected by platforms.
- Political self — the dimension of identity concerning power, governance, rights, civic belonging, and public participation.
```

### Study Pack

**Summary:** Personal identity includes interconnected physical, sexual, material, digital, and political dimensions. These develop through self-understanding and social interaction, shaped by personal choices, social expectations, and unequal opportunities.

The physical self involves appearance, bodily capacities, health, and body image. The sexual self includes feelings, attraction, orientation, values, boundaries, and intimacy. Sexual autonomy supports consent that is freely given, informed, specific, and reversible.

The material self includes possessions and places experienced as extensions of identity. The digital self emerges through online profiles, posts, interactions, and data traces. The political self concerns power, rights, authority, civic belonging, and public participation.

Several important distinctions help clarify these dimensions:

| Concept | Meaning | Distinction |
|---|---|---|
| Body image | Perceptions, thoughts, and feelings about one's body | Not objective bodily characteristics |
| Sexual orientation | Pattern of sexual or romantic attraction | Distinct from gender identity and sexual behavior |
| Digital footprint | Data traces associated with online activity | Includes others' sharing and platform collection |

These dimensions intersect when, for example, body image is expressed online or possessions become associated with social status. Reflection helps people question beauty standards, consumption pressures, dependence on public approval, and inherited political beliefs.

**Common Misconceptions**: Identity dimensions are not fixed or isolated; personal worth is not determined by possessions or public approval; digital identity is not formed only through intentional self-presentation.

**Key concepts:** Physical Self; Body Image; Sexual Self; Sexual Orientation; Sexual Autonomy; Material Self; Digital Self; Digital Footprint; Political Self; Identity Intersections

**Q1** (MCQ, CONCEPTUAL): Which best describes body image?

- A. The body's objectively measurable characteristics
- B. A person's perceptions, thoughts, and feelings about their body ✅
- C. The possessions used to communicate social status
- D. A person's pattern of romantic attraction

_Explanation:_ Body image concerns how someone interprets and emotionally experiences their body. Distinguishing subjective experience from physical characteristics helps reveal the influence of beauty standards.

**Q2** (MCQ, CONCEPTUAL): What aspect of the sexual self refers to a pattern of sexual or romantic attraction?

- A. Sexual boundaries
- B. Sexual behavior
- C. Gender identity
- D. Sexual orientation ✅

_Explanation:_ The defining feature is the pattern of attraction a person experiences. The notes distinguish this from someone's identity, actions, and limits within intimate relationships.

**Q3** (MCQ, CONCEPTUAL): Which statement about the material self is NOT consistent with the notes?

- A. The value of a person's possessions determines their personal worth. ✅
- B. A home can become part of a person's sense of identity.
- C. Clothing can be experienced as an extension of identity.
- D. Ownership can intersect with perceptions of social status.

_Explanation:_ The notes encourage questioning consumption pressures rather than treating ownership as a measure of human value. Objects and places can carry personal meaning without establishing someone's worth.

**Q4** (MCQ, CONCEPTUAL): A platform collects information about a person's online interactions, and other users share information about that person. What do these traces contribute to?

- A. Only the person's deliberately edited profile
- B. Only the person's offline civic participation
- C. The person's digital footprint ✅
- D. The person's objective bodily characteristics

_Explanation:_ Online traces can arise without deliberate posting by the person concerned. Both platform collection and information supplied by other people help shape an individual's online presence.

**Q5** (MCQ, CONCEPTUAL): Statement 1: Political identity includes beliefs about power, rights, and civic belonging. Statement 2: Reflecting on political identity involves critically examining inherited beliefs and group loyalties. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ The political dimension connects a person's understanding of public life with reflective civic engagement. Examining the sources and assumptions of one's views supports informed participation.

---

_End of pack._
