# BSCS Year 1 — Phase 1 Run Sheet: Introduction to Computing and Computer Programming 1 (2026-10-06)

> **For the owner to execute in the production UI. Claude creates, generates and publishes nothing** (`CLAUDE.md`: production writes are owner-only). Everything below comes from `bscs-year1-shaping.tsv`. Claude can read results back with `SELECT` after each batch.

## 1. What Phase 1 is

Two plans, **81 placements**: Introduction to Computing (39) and Computer Programming 1 (42). By how each note gets made:

| How | Notes | Meaning |
|---|---|---|
| **Already in production** | 3 | The three pilot Notes (Computing Disciplines, Algorithms, Building and Running). **Do not recreate them**; just add them to their plans. |
| **Reuse** | 1 | An existing note to add to the plan: "Emerging Technologies and Their Social Impacts". |
| **Authored body, create manually** | 2 | Testing with assert and Debugging with a Debugger. Bodies are drafted (`authored-bodies/`); Bulk Generate cannot carry a body. |
| **Bulk Generate** | 75 | In 18 batches below. 19 are neutral (N) notes, 19 are C++ (S) notes that need a curator edit, 37 are overview notes. |

## 2. The decisions you need to make

1. **Create the collection first, then Bulk Generate into it (recommended), or generate first and add notes later?** You usually generate with a collection and section selected, so this is just your normal flow: it needs the private Year root and its 10 Subject Plans created first (checklist sections 1 to 3).
2. **Year root course/program:** `Computer Science` (recommended; puts the Year in the BSCS recommendation slot).
3. **For the 19 Phase 1 C++ notes, bulk generate and then edit (recommended), or author each one?** Authoring is slower; bulk then edit means adding the C++17 specifics at review (section 6).

Settled, so nothing to decide: quota (see section 3), batch size, Subject shelves, programs.

## 3. Limits that apply (FACT from the code, 2026-10-06)

- **Your account is `ADMIN`, and the backend does not enforce the monthly generation quota for admins** (`NoteController.bulkGenerate`: `enforceLimits = role != ADMIN`). If the Bulk Generate page still shows a lower cap for you, tell me and I will check the UI.
- **At most 50 topics per request** (`note.bulk-generation.max-topics`, default 50; the production value was not read). The largest Phase 1 batch has far fewer.
- **Titles up to 160 characters**; the longest Phase 1 title is 135.
- **Each generated note also gets a Study Pack automatically**, so each batch produces packs to review.

## 4. Order of work

1. Create the private Year root, the 10 Subject Plans and their terms (checklist sections 1 to 3). Note each Subject Plan's identity: Bulk Generate asks for the collection.
2. Run the Bulk Generate batches in section 5, in order.
3. Create the two authored notes manually (section 7).
4. Add the three existing pilot Notes and the one reuse note to their plans with the right section label (section 8).
5. Fix section order (section 8) and run the review gate (section 9).

## 5. Bulk Generate batches

Per batch, enter exactly these values. **Overwrite the Section field**: the form pre-fills it from the Subject. Field labels in the page may differ slightly from these names.

| Field | Value for every Phase 1 batch |
|---|---|
| Authored Depth (learner level) | College |
| Make public | **Off** (private until publication; notes must be public before the collection can publish) |
| Topics | one title per line, copied exactly from the block |

### Batch 1: Introduction to Computing / The Computing Disciplines and Profession

- **Subject:** Computer Science
- **Domain Context:** Computing
- **Applicable Programs:** Computer Science, Information Technology, Information Systems
- **Collection (Subject Plan):** Introduction to Computing
- **Section:** The Computing Disciplines and Profession
- **Notes:** 4 (4 BULK)

Topics:

```
The Computing Industry, Professions and Careers
Computing in Other Fields: Biology, Sociology, Environment and Games
Professional Societies and Standards in Computing
Analyzing Organizational Computing Solutions
```

### Batch 2: Introduction to Computing / History and Evolution of Computing

- **Subject:** Computer Science
- **Domain Context:** Computing
- **Applicable Programs:** Computer Science, Information Technology, Information Systems
- **Collection (Subject Plan):** Introduction to Computing
- **Section:** History and Evolution of Computing
- **Notes:** 4 (4 BULK)

Topics:

```
Early Computing Devices and Mechanical Calculation
Generations of Computers
Foundations of Modern Computing: Turing and von Neumann
Evolution of Personal and Mobile Computing
```

### Batch 3: Introduction to Computing / Information Representation

- **Subject:** Computer Science
- **Domain Context:** Computing
- **Applicable Programs:** Computer Science, Information Technology, Information Systems
- **Collection (Subject Plan):** Introduction to Computing
- **Section:** Information Representation
- **Notes:** 6 (6 BULK)

Topics:

```
Bits, Bytes and Units of Information
Number Systems: Decimal, Binary, Octal and Hexadecimal
Converting Between Number Systems
Binary Arithmetic and Signed Numbers
Character Encoding: ASCII and Unicode
Representing Images, Audio and Video
```

### Batch 4: Introduction to Computing / Computer Systems: Organization and Architecture

- **Subject:** Computer Science
- **Domain Context:** Computing
- **Applicable Programs:** Computer Science, Information Technology, Information Systems
- **Collection (Subject Plan):** Introduction to Computing
- **Section:** Computer Systems: Organization and Architecture
- **Notes:** 7 (7 BULK)

Topics:

```
Components of a Computer System
The Central Processing Unit and the Fetch-Decode-Execute Cycle
Memory and the Storage Hierarchy
Input, Output Devices and Buses
System Software and Application Software
Functions of an Operating System
Programming Languages and Language Levels
```

### Batch 5: Introduction to Computing / Networks, the Internet and the Web

- **Subject:** Computer Networking
- **Domain Context:** Computing
- **Applicable Programs:** Computer Science, Information Technology, Information Systems
- **Collection (Subject Plan):** Introduction to Computing
- **Section:** Networks, the Internet and the Web
- **Notes:** 3 (3 BULK)

Topics:

```
How the Internet Works
IP Addresses and the Domain Name System
Internet Protocols: TCP/IP and HTTP Basics
```

### Batch 6: Introduction to Computing / Networks, the Internet and the Web

- **Subject:** Web Development
- **Domain Context:** Computing
- **Applicable Programs:** Computer Science, Information Technology, Information Systems
- **Collection (Subject Plan):** Introduction to Computing
- **Section:** Networks, the Internet and the Web
- **Notes:** 4 (4 BULK)

Topics:

```
The World Wide Web: Browsers, Servers and Web Pages
HTML Document Structure and Elements
HTML Links, Images, Lists, Tables and Forms
CSS Fundamentals: Selectors, Properties and the Box Model
```

### Batch 7: Introduction to Computing / Computer Security and Malware

- **Subject:** Information Assurance and Security
- **Domain Context:** Computing
- **Applicable Programs:** Computer Science, Information Technology, Information Systems
- **Collection (Subject Plan):** Introduction to Computing
- **Section:** Computer Security and Malware
- **Notes:** 5 (5 BULK)

Topics:

```
Computer Security Fundamentals: Confidentiality, Integrity and Availability
Malware: Types, Infection and Defense
Authentication, Passwords and Access Control
Network Security Essentials: Firewalls and VPNs
Privacy, Data Protection and Safe Computing Practices
```

### Batch 8: Introduction to Computing / Ethics, Society and Emerging Trends

- **Subject:** Computer Science
- **Domain Context:** Computing
- **Applicable Programs:** Computer Science, Information Technology, Information Systems
- **Collection (Subject Plan):** Introduction to Computing
- **Section:** Ethics, Society and Emerging Trends
- **Notes:** 4 (4 BULK)

Topics:

```
Ethical, Legal and Social Issues in Computing
Philippine Computing Laws: Data Privacy, Cybercrime and Intellectual Property
Artificial Intelligence and Human Judgment
Cloud Computing, the Internet of Things and Big Data
```

### Batch 9: Computer Programming 1 / Problem Solving and Algorithms

- **Subject:** Programming Fundamentals
- **Domain Context:** Computing
- **Applicable Programs:** Computer Science, Information Technology, Information Systems
- **Collection (Subject Plan):** Computer Programming 1
- **Section:** Problem Solving and Algorithms
- **Notes:** 3 (3 BULK-N)

Topics:

```
What Programming Is and How Programs Run
Problem-Solving Steps in Programming
Pseudocode and Flowcharts
```

### Batch 10: Computer Programming 1 / Program Structure, Variables and Data Types

- **Subject:** Programming Fundamentals
- **Domain Context:** Computing
- **Applicable Programs:** Computer Science, Information Technology, Information Systems
- **Collection (Subject Plan):** Computer Programming 1
- **Section:** Program Structure, Variables and Data Types
- **Notes:** 6 (2 BULK-N, 4 BULK-S)

Topics:

```
Structure of a C++ Program
Variables, Assignment and Program State
Declaring Variables and Constants in C++
Tracing and Dry Runs
Fundamental Data Types in C++
Type Conversion and Casting in C++
```

### Batch 11: Computer Programming 1 / Expressions and Operators

- **Subject:** Programming Fundamentals
- **Domain Context:** Computing
- **Applicable Programs:** Computer Science, Information Technology, Information Systems
- **Collection (Subject Plan):** Computer Programming 1
- **Section:** Expressions and Operators
- **Notes:** 4 (4 BULK-S)

Topics:

```
Arithmetic Operators and Expressions in C++
Operator Precedence and Order of Evaluation in C++
Relational and Logical Operators in C++
Integer Division, Modulo and Numeric Limits in C++
```

### Batch 12: Computer Programming 1 / Input and Output

- **Subject:** Programming Fundamentals
- **Domain Context:** Computing
- **Applicable Programs:** Computer Science, Information Technology, Information Systems
- **Collection (Subject Plan):** Computer Programming 1
- **Section:** Input and Output
- **Notes:** 2 (2 BULK-S)

Topics:

```
Console Input and Output in C++
Formatted Output in C++
```

### Batch 13: Computer Programming 1 / Selection

- **Subject:** Programming Fundamentals
- **Domain Context:** Computing
- **Applicable Programs:** Computer Science, Information Technology, Information Systems
- **Collection (Subject Plan):** Computer Programming 1
- **Section:** Selection
- **Notes:** 3 (1 BULK-N, 2 BULK-S)

Topics:

```
If and If-Else Statements in C++
Nested Selection and Switch Statements in C++
Boolean Conditions and Short-Circuit Evaluation
```

### Batch 14: Computer Programming 1 / Iteration

- **Subject:** Programming Fundamentals
- **Domain Context:** Computing
- **Applicable Programs:** Computer Science, Information Technology, Information Systems
- **Collection (Subject Plan):** Computer Programming 1
- **Section:** Iteration
- **Notes:** 4 (2 BULK-N, 2 BULK-S)

Topics:

```
While and Do-While Loops in C++
For Loops in C++
Nested Loops
Loop Control and Common Loop Patterns
```

### Batch 15: Computer Programming 1 / Input Validation

- **Subject:** Programming Fundamentals
- **Domain Context:** Computing
- **Applicable Programs:** Computer Science, Information Technology, Information Systems
- **Collection (Subject Plan):** Computer Programming 1
- **Section:** Input Validation
- **Notes:** 2 (1 BULK-N, 1 BULK-S)

Topics:

```
Input Validation: Checking Before Trusting Data
Handling Invalid Console Input in C++
```

### Batch 16: Computer Programming 1 / Functions and Procedural Abstraction

- **Subject:** Programming Fundamentals
- **Domain Context:** Computing
- **Applicable Programs:** Computer Science, Information Technology, Information Systems
- **Collection (Subject Plan):** Computer Programming 1
- **Section:** Functions and Procedural Abstraction
- **Notes:** 6 (3 BULK-N, 3 BULK-S)

Topics:

```
Functions and Procedural Abstraction
Defining and Calling Functions in C++
Values, References and Aliasing
Parameters, Argument Passing and Return Values in C++
Scope and Lifetime of Variables in C++
Modular Program Design
```

### Batch 17: Computer Programming 1 / Recursion

- **Subject:** Programming Fundamentals
- **Domain Context:** Computing
- **Applicable Programs:** Computer Science, Information Technology, Information Systems
- **Collection (Subject Plan):** Computer Programming 1
- **Section:** Recursion
- **Notes:** 2 (2 BULK-N)

Topics:

```
Recursion: Base Case and Recursive Case
Tracing Recursive Calls
```

### Batch 18: Computer Programming 1 / Testing, Debugging and Code Quality

- **Subject:** Programming Fundamentals
- **Domain Context:** Computing
- **Applicable Programs:** Computer Science, Information Technology, Information Systems
- **Collection (Subject Plan):** Computer Programming 1
- **Section:** Testing, Debugging and Code Quality
- **Notes:** 6 (5 BULK-N, 1 BULK-S)

Topics:

```
Syntax, Runtime and Logic Errors
Reading C++ Compiler Errors and Warnings
Debugging Techniques
Designing Test Cases
Documentation, Comments and Coding Style
Reviewing Another Programmer's Code
```

## 6. Curator edit for the C++ (S) notes, after generation

The pilot showed the generator writes correct but generic C++ from a title. For each BULK-S note, open the Note and confirm or add:

- the language standard is stated (C++17);
- any command shown uses `-std=c++17 -Wall -Wextra`;
- there is a small example that compiles;
- nothing outside the approved subset (no classes, templates, smart-pointer teaching, build systems, STL beyond `vector`, `string`, `sort`, `find`);
- the pack title has no Subject suffix.

Editing a Note body in the app is blocked once a Study Pack exists (you found this), so the edit is a SQL update, as before. Claude can prepare that script per batch after you review the generated notes.

## 7. Create manually (authored bodies)

| Note | Plan / section | Subject | Programs | Body file |
|---|---|---|---|---|
| Debugging C++ Programs with a Debugger | Computer Programming 1 / Testing, Debugging and Code Quality | Programming Fundamentals | Computer Science, Information Technology, Information Systems | `authored-bodies/pc-11-debugging-cpp-programs-with-a-debugger.md` |
| Testing a C++ Program with assert and Test Drivers | Computer Programming 1 / Testing, Debugging and Code Quality | Programming Fundamentals | Computer Science, Information Technology, Information Systems | `authored-bodies/pc-08-testing-a-cpp-program-with-assert-and-test-drivers.md` |

Domain Context Computing, Authored Depth College. Paste the **plain-text** version of the body (the Full Notes view is plain text); the SQL generator I used for the pilot produces it. Tell me which you want created this way and I will prepare the script.

## 8. Add existing notes, and the order in each section

**Add these** (they already exist; do not generate them again):

- Introduction to Computing / The Computing Disciplines and Profession: **The Computing Disciplines: Computer Science, Information Technology, Information Systems, Software Engineering and Computer Engineering** (`9b81844a-209c-426a-89e9-0f110620b9f8`) (pilot Note A)
- Introduction to Computing / Ethics, Society and Emerging Trends: **Emerging Technologies and Their Social Impacts** (`5e2a6baa-791f-4bfb-9b1b-b4317623cd0b`) (reuse; needs the Computer Science tag before publication)
- Computer Programming 1 / Problem Solving and Algorithms: **Algorithms and Their Properties** (`583c82dc-fc5b-4088-ae21-dd474a787be3`) (pilot Note B, authored body applied)
- Computer Programming 1 / Problem Solving and Algorithms: **Building and Running a C++ Program** (`bb34133d-7427-46ca-a8de-b0364cc2236a`) (pilot Note C, authored body applied)

**Teaching order per section.** Bulk Generate adds notes in the order you typed them, but existing, reused and manually created notes arrive separately, so check each section matches this order and drag in the Builder where it does not. A section whose non-bulk notes are all at the start or all at the end needs no fix if you add them first or last respectively. Marks: B = bulk, X = existing, R = reuse, A = authored.

### Introduction to Computing

1. **The Computing Disciplines and Profession** `XBBBB`
   - The Computing Disciplines: Computer Science, Information Technology, Information Systems, Software Engineering and Computer Engineering
   - The Computing Industry, Professions and Careers
   - Computing in Other Fields: Biology, Sociology, Environment and Games
   - Professional Societies and Standards in Computing
   - Analyzing Organizational Computing Solutions
2. **History and Evolution of Computing** `BBBB`
   - Early Computing Devices and Mechanical Calculation
   - Generations of Computers
   - Foundations of Modern Computing: Turing and von Neumann
   - Evolution of Personal and Mobile Computing
3. **Information Representation** `BBBBBB`
   - Bits, Bytes and Units of Information
   - Number Systems: Decimal, Binary, Octal and Hexadecimal
   - Converting Between Number Systems
   - Binary Arithmetic and Signed Numbers
   - Character Encoding: ASCII and Unicode
   - Representing Images, Audio and Video
4. **Computer Systems: Organization and Architecture** `BBBBBBB`
   - Components of a Computer System
   - The Central Processing Unit and the Fetch-Decode-Execute Cycle
   - Memory and the Storage Hierarchy
   - Input, Output Devices and Buses
   - System Software and Application Software
   - Functions of an Operating System
   - Programming Languages and Language Levels
5. **Networks, the Internet and the Web** `BBBBBBB`
   - How the Internet Works
   - IP Addresses and the Domain Name System
   - Internet Protocols: TCP/IP and HTTP Basics
   - The World Wide Web: Browsers, Servers and Web Pages
   - HTML Document Structure and Elements
   - HTML Links, Images, Lists, Tables and Forms
   - CSS Fundamentals: Selectors, Properties and the Box Model
6. **Computer Security and Malware** `BBBBB`
   - Computer Security Fundamentals: Confidentiality, Integrity and Availability
   - Malware: Types, Infection and Defense
   - Authentication, Passwords and Access Control
   - Network Security Essentials: Firewalls and VPNs
   - Privacy, Data Protection and Safe Computing Practices
7. **Ethics, Society and Emerging Trends** `BBBRB` **(order fix needed: a non-bulk note sits in the middle)**
   - Ethical, Legal and Social Issues in Computing
   - Philippine Computing Laws: Data Privacy, Cybercrime and Intellectual Property
   - Artificial Intelligence and Human Judgment
   - Emerging Technologies and Their Social Impacts
   - Cloud Computing, the Internet of Things and Big Data

### Computer Programming 1

1. **Problem Solving and Algorithms** `BBXBX` **(order fix needed: a non-bulk note sits in the middle)**
   - What Programming Is and How Programs Run
   - Problem-Solving Steps in Programming
   - Algorithms and Their Properties
   - Pseudocode and Flowcharts
   - Building and Running a C++ Program
2. **Program Structure, Variables and Data Types** `BBBBBB`
   - Structure of a C++ Program
   - Variables, Assignment and Program State
   - Declaring Variables and Constants in C++
   - Tracing and Dry Runs
   - Fundamental Data Types in C++
   - Type Conversion and Casting in C++
3. **Expressions and Operators** `BBBB`
   - Arithmetic Operators and Expressions in C++
   - Operator Precedence and Order of Evaluation in C++
   - Relational and Logical Operators in C++
   - Integer Division, Modulo and Numeric Limits in C++
4. **Input and Output** `BB`
   - Console Input and Output in C++
   - Formatted Output in C++
5. **Selection** `BBB`
   - If and If-Else Statements in C++
   - Nested Selection and Switch Statements in C++
   - Boolean Conditions and Short-Circuit Evaluation
6. **Iteration** `BBBB`
   - While and Do-While Loops in C++
   - For Loops in C++
   - Nested Loops
   - Loop Control and Common Loop Patterns
7. **Input Validation** `BB`
   - Input Validation: Checking Before Trusting Data
   - Handling Invalid Console Input in C++
8. **Functions and Procedural Abstraction** `BBBBBB`
   - Functions and Procedural Abstraction
   - Defining and Calling Functions in C++
   - Values, References and Aliasing
   - Parameters, Argument Passing and Return Values in C++
   - Scope and Lifetime of Variables in C++
   - Modular Program Design
9. **Recursion** `BB`
   - Recursion: Base Case and Recursive Case
   - Tracing Recursive Calls
10. **Testing, Debugging and Code Quality** `BBBABABB` **(order fix needed: a non-bulk note sits in the middle)**
   - Syntax, Runtime and Logic Errors
   - Reading C++ Compiler Errors and Warnings
   - Debugging Techniques
   - Debugging C++ Programs with a Debugger
   - Designing Test Cases
   - Testing a C++ Program with assert and Test Drivers
   - Documentation, Comments and Coding Style
   - Reviewing Another Programmer's Code

## 9. Review gate before Phase 2

After the batches and the manual notes, tell me "Phase 1 generated". I read everything back (`SELECT` only) and check, per Note: exact title, one Note per title, Subject, Domain Context, Applicable Programs, Authored Depth, visibility, and the Study Pack. Then I score a **sample of five**: 

- **N, neutral (2):** "What Programming Is and How Programs Run", "Problem-Solving Steps in Programming" (language drift check);
- **S, C++ (2):** "Structure of a C++ Program", "Declaring Variables and Constants in C++" (C++17, subset, correct code);
- **Overview (1):** "Computer Security Fundamentals: Confidentiality, Integrity and Availability".

I will also scan **every** pack for a Subject suffix in the title and for quiz items with more than one defensible answer, since both occurred in the pilot.

## 10. What is not in Phase 1

Discrete Structures I, Computer Programming 2, the general-education additions and Precalculus (Phases 2 to 4). The five pilot Notes A and E, and the unauthored eleven Path C bodies, are not part of this batch.
