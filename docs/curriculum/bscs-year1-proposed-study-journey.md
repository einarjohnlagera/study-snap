# BS Computer Science — 1st Year: Proposed Study Journey (revised 2026-09-30)

> **Implementation-ready shaping (2026-10-01).** D-27, D-35 and D-36 are owner-approved and applied; Workstream A is closed (`COMPUTING` on all 145 computing rows, the 136 ratified plus 9 new Programming notes; Precalculus `(unset)` per D-33). Execution still has curator and pre-publication checkpoints. No production writes, no notes generated, no collections created.

**Revision of the 2026-09-29 proposal** after the owner decisions of 2026-09-30 and 2026-10-01 (D-01 fresh root, D-04 Precalculus optional, D-05 language approach, PE and NSTP out of scope). The academic direction is unchanged; Precalculus and its 31 notes are preserved, moved out of the default Year.

## Year root

**Title:** BS Computer Science — 1st Year

**Description (proposed, goes in the root description):** A knowledge-focused reference journey for the first year of a Philippine BS Computer Science degree. It covers selected academic courses whose knowledge NoteLib can teach through Notes: computing, mathematics and General Education. It is not a replacement for any institution's complete degree prospectus. Courses such as Physical Education and NSTP are part of many BS Computer Science programs, but they are activity- or service-based and fall outside this Note-based journey. Course names, terms and sequence follow a NoteLib reference arrangement; your university's may differ.

**Shape:** 10 default Subject Plans (5 First Semester, 5 Second Semester) · 218 note placements · **50 existing notes reused** (40 need only the Computer Science tag; 5 are the R4 pilot Notes, recorded as Reuse on 2026-10-06) · **163 new notes** · 5 existing notes excluded · 0 note shared by two plans. Plus one **optional preparatory plan** (Precalculus, 31 new notes) delivered as a separate standalone root.

Legend: **[REUSE]** existing production note, unchanged · **[NEW]** to be authored · **[EXCLUDED]** existing note held out · `↳ closest existing` = candidate found in production, **not promoted** (verdict shown) · `class N/S` = Programming class (N neutral, S reference-language C++).

Hierarchy: `BS Computer Science — 1st Year` → Academic Term (metadata) → Subject Plan → Section (label) → canonical Note. Two persisted collection levels only (ADR-003). The old private "BS Computer Science" collection is preserved untouched; nothing is deleted or migrated automatically.

## What the default Year contains, and what it does not

| Category | Courses |
|---|---|
| **Default Year 1 curriculum** | Introduction to Computing · Computer Programming 1 · Mathematics in the Modern World · The Contemporary World · Art Appreciation · Computer Programming 2 · Discrete Structures I · Understanding the Self · Readings in Philippine History · Purposive Communication |
| **Optional / preparatory (separate standalone root)** | Precalculus (Algebra and Trigonometry) — see `bscs-year1-precalculus-preparatory-proposal.md` |
| **Institution-dependent (placement or inclusion varies by university)** | The programming language; Precalculus; Calculus; Probability and Statistics; Filipino; the semester of every general-education course; whether autonomous schools (Ateneo, UPLB) follow the CMO template at all |
| **Explicit scope exclusions** | Physical Education and NSTP (activity- or service-based, not knowledge Notes; part of many BSCS programs and simply outside this journey); Science, Technology and Society, Ethics, Life and Works of Rizal (later years by evidence); Calculus, Linear Algebra, Probability and Statistics (not Year 1) |

Semester placement is a **NoteLib reference arrangement**, not a claim of universal Philippine placement. CHED does not prescribe general-education terms.

## FIRST SEMESTER

### 1. Introduction to Computing

*An orientation to the computing disciplines and profession, the history and components of computer systems, how data is represented, how the Internet and the Web work, and the security, ethical and social issues of computing.*

- **Why Year 1 / delivery:** CMO 25 s.2015 lists it as a common course with no prerequisite; it sits in First Semester at all four CMO-25 schools examined (Antique, AICS, Cebu, Palawan).
- **Prerequisites:** None (CMO 25 curriculum map). Precedes Computer Programming 1 only administratively; no knowledge dependency.
- **Variability:** LOW for placement (Year 1 First Semester everywhere examined). MEDIUM for content: CMO description includes HTML and CSS and malware; some schools teach these in other courses.
- **Coverage:** 1 reuse · 38 new · 0 excluded

**Learning outcomes**

- Explain fundamental principles, concepts and the evolution of computing systems. — *CMO 25 LO1* → History and Evolution of Computing; Computer Systems: Organization and Architecture
- Discuss recent developments in the computing knowledge areas. — *CMO 25 LO2* → The Computing Disciplines and Profession; Ethics, Society and Emerging Trends
- Analyze solutions organizations use for computing issues. — *CMO 25 LO3* → The Computing Disciplines and Profession; Computer Security and Malware; Ethics, Society and Emerging Trends
- Describe the key components of a computer system (organization and architecture). — *CMO 25 description* → Computer Systems: Organization and Architecture
- Recognise malware and apply basic computer-security practices. — *CMO 25 description* → Computer Security and Malware
- Explain the Internet and Internet protocols and build a simple page in HTML and CSS. — *CMO 25 description* → Networks, the Internet and the Web
- Represent and convert data in binary, hexadecimal and standard character encodings. — *PROPOSAL* → Information Representation

**1. The Computing Disciplines and Profession**

- [REUSE] The Computing Disciplines: Computer Science, Information Technology, Information Systems, Software Engineering and Computer Engineering · *Computer Science*
- [NEW] The Computing Industry, Professions and Careers · *Computer Science*
- [NEW] Computing in Other Fields: Biology, Sociology, Environment and Games · *Computer Science*
- [NEW] Professional Societies and Standards in Computing · *Computer Science* — CMO 25 description names "an Understanding of ACM Requirements".
- [NEW] Analyzing Organizational Computing Solutions · *Computer Science* — Serves CMO 25 LO3 (analyse solutions organizations use for computing issues); case-analysis treatment.

**2. History and Evolution of Computing**

- [NEW] Early Computing Devices and Mechanical Calculation · *Computer Science*
- [NEW] Generations of Computers · *Computer Science*
- [NEW] Foundations of Modern Computing: Turing and von Neumann · *Computer Science*
  - ↳ closest existing: "Alan Turing and the Foundations of Artificial Intelligence" — **CONFIRMED NEW**
- [NEW] Evolution of Personal and Mobile Computing · *Computer Science*

**3. Information Representation**

- [NEW] Bits, Bytes and Units of Information · *Computer Science*
- [NEW] Number Systems: Decimal, Binary, Octal and Hexadecimal · *Computer Science*
- [NEW] Converting Between Number Systems · *Computer Science*
- [NEW] Binary Arithmetic and Signed Numbers · *Computer Science*
- [NEW] Character Encoding: ASCII and Unicode · *Computer Science*
- [NEW] Representing Images, Audio and Video · *Computer Science*

**4. Computer Systems: Organization and Architecture**

- [NEW] Components of a Computer System · *Computer Science*
- [NEW] The Central Processing Unit and the Fetch-Decode-Execute Cycle · *Computer Science*
- [NEW] Memory and the Storage Hierarchy · *Computer Science*
- [NEW] Input, Output Devices and Buses · *Computer Science*
- [NEW] System Software and Application Software · *Computer Science*
- [NEW] Functions of an Operating System · *Computer Science*
- [NEW] Programming Languages and Language Levels · *Computer Science*

**5. Networks, the Internet and the Web**

- [NEW] How the Internet Works · *Computer Networking*
  - ↳ closest existing: "TCP/IP Protocol Suite in Computer Networking" — **CONFIRMED NEW**
- [NEW] IP Addresses and the Domain Name System · *Computer Networking*
  - ↳ closest existing: "Domain Name System (DNS) in Computer Networking" — **CONFIRMED NEW**
- [NEW] Internet Protocols: TCP/IP and HTTP Basics · *Computer Networking*
  - ↳ closest existing: "Comprehensive Review of the OSI Model in Networking" — **CONFIRMED NEW**
- [NEW] The World Wide Web: Browsers, Servers and Web Pages · *Web Development*
- [NEW] HTML Document Structure and Elements · *Web Development*
- [NEW] HTML Links, Images, Lists, Tables and Forms · *Web Development*
- [NEW] CSS Fundamentals: Selectors, Properties and the Box Model · *Web Development*

**6. Computer Security and Malware**

- [NEW] Computer Security Fundamentals: Confidentiality, Integrity and Availability · *Information Assurance and Security*
- [NEW] Malware: Types, Infection and Defense · *Information Assurance and Security*
- [NEW] Authentication, Passwords and Access Control · *Information Assurance and Security*
- [NEW] Network Security Essentials: Firewalls and VPNs · *Information Assurance and Security*
  - ↳ closest existing: "Fundamentals of Network Security" — **REQUIRES CURATOR INSPECTION**
- [NEW] Privacy, Data Protection and Safe Computing Practices · *Information Assurance and Security*

**7. Ethics, Society and Emerging Trends**

- [NEW] Ethical, Legal and Social Issues in Computing · *Computer Science*
- [NEW] Philippine Computing Laws: Data Privacy, Cybercrime and Intellectual Property · *Computer Science*
- [NEW] Artificial Intelligence and Human Judgment · *Computer Science*
  - ↳ closest existing: "The Necessity of Human Judgment in Artificial Intelligence Systems" — **REQUIRES CURATOR INSPECTION**
- [REUSE] Emerging Technologies and Their Social Impacts · *Science and Technology* — Cross-plan reuse of a STRONG General Education note (Science, Technology and Society corpus).
- [NEW] Cloud Computing, the Internet of Things and Big Data · *Computer Science*

### 2. Computer Programming 1

*Fundamentals of programming: designing, implementing, testing and debugging programs with primitive data types, computation, input and output, selection, iteration, functions and recursion, and reviewing code for style and clarity. Concepts are taught language-neutrally where the knowledge is portable; C++ is the reference language for executable examples.*

- **Why Year 1 / delivery:** CMO 25 s.2015 common course (Fundamentals of Programming), no prerequisite; First Semester at all four CMO-25 schools examined.
- **Prerequisites:** None. Prerequisite of Computer Programming 2 (CMO 25 map).
- **Variability:** LOW for placement. HIGH for language: CMO 25 says "a general purpose programming language" and none of the four CMO-25 curricula examined names one for Year 1 (Cebu lists Java and Python only as electives). Owner decision 2026-09-30: language-neutral concepts plus ONE reference language; the language is NOT YET APPROVED (assessment recommends C++). Notes are classed neutral, reference-language or split-candidate; language-specific titles are held until approval.
- **Coverage:** 0 reuse · 42 new · 0 excluded
- **Programming review result:** 42 notes (N 20 · S 22). N titles are language-neutral; S titles name C++ (reference language, D-27). Memory and pointers are a teaching instrument, shown alongside safer standard-library abstractions (D-27 guardrail).

**Learning outcomes**

- Design, implement, test and debug a program using primitive data types, basic computation, simple I/O, conditional and iterative structures, functions with parameter passing, and recursion. — *CMO 25 LO1* → Program Structure, Variables and Data Types; Expressions and Operators; Input and Output; Selection; Iteration; Functions and Procedural Abstraction; Recursion; Input Validation
- Assess and recommend revisions to another programmer's code on documentation and style, appropriateness of constructs, and procedural abstraction. — *CMO 25 LO2* → Testing, Debugging and Code Quality
- Use a programming language to solve problems by first analysing the problem and designing an algorithm. — *CMO 25 description* → Problem Solving and Algorithms

**1. Problem Solving and Algorithms**

- [NEW] What Programming Is and How Programs Run · *Programming Fundamentals* · class N
- [NEW] Problem-Solving Steps in Programming · *Programming Fundamentals* · class N
- [REUSE] Algorithms and Their Properties · *Programming Fundamentals* · class N
- [NEW] Pseudocode and Flowcharts · *Programming Fundamentals* · class N
- [REUSE] Building and Running a C++ Program · *Programming Fundamentals* · class S

**2. Program Structure, Variables and Data Types**

- [NEW] Structure of a C++ Program · *Programming Fundamentals* · class S
- [NEW] Variables, Assignment and Program State · *Programming Fundamentals* · class N
- [NEW] Declaring Variables and Constants in C++ · *Programming Fundamentals* · class S
- [NEW] Tracing and Dry Runs · *Programming Fundamentals* · class N
- [NEW] Fundamental Data Types in C++ · *Programming Fundamentals* · class S
- [NEW] Type Conversion and Casting in C++ · *Programming Fundamentals* · class S

**3. Expressions and Operators**

- [NEW] Arithmetic Operators and Expressions in C++ · *Programming Fundamentals* · class S
- [NEW] Operator Precedence and Order of Evaluation in C++ · *Programming Fundamentals* · class S
- [NEW] Relational and Logical Operators in C++ · *Programming Fundamentals* · class S
- [NEW] Integer Division, Modulo and Numeric Limits in C++ · *Programming Fundamentals* · class S

**4. Input and Output**

- [NEW] Console Input and Output in C++ · *Programming Fundamentals* · class S
- [NEW] Formatted Output in C++ · *Programming Fundamentals* · class S

**5. Selection**

- [NEW] If and If-Else Statements in C++ · *Programming Fundamentals* · class S
- [NEW] Nested Selection and Switch Statements in C++ · *Programming Fundamentals* · class S
- [NEW] Boolean Conditions and Short-Circuit Evaluation · *Programming Fundamentals* · class N

**6. Iteration**

- [NEW] While and Do-While Loops in C++ · *Programming Fundamentals* · class S
- [NEW] For Loops in C++ · *Programming Fundamentals* · class S
- [NEW] Nested Loops · *Programming Fundamentals* · class N
- [NEW] Loop Control and Common Loop Patterns · *Programming Fundamentals* · class N

**7. Input Validation**

- [NEW] Input Validation: Checking Before Trusting Data · *Programming Fundamentals* · class N
- [NEW] Handling Invalid Console Input in C++ · *Programming Fundamentals* · class S

**8. Functions and Procedural Abstraction**

- [NEW] Functions and Procedural Abstraction · *Programming Fundamentals* · class N
- [NEW] Defining and Calling Functions in C++ · *Programming Fundamentals* · class S
- [NEW] Values, References and Aliasing · *Programming Fundamentals* · class N — Must make pass-by-value versus pass-by-reference explicit (CMO 25 LO1: functions with parameter passing).
- [NEW] Parameters, Argument Passing and Return Values in C++ · *Programming Fundamentals* · class S — Must make pass-by-value versus pass-by-reference explicit (CMO 25 LO1: functions with parameter passing).
- [NEW] Scope and Lifetime of Variables in C++ · *Programming Fundamentals* · class S
- [NEW] Modular Program Design · *Programming Fundamentals* · class N

**9. Recursion**

- [NEW] Recursion: Base Case and Recursive Case · *Programming Fundamentals* · class N
- [NEW] Tracing Recursive Calls · *Programming Fundamentals* · class N

**10. Testing, Debugging and Code Quality**

- [NEW] Syntax, Runtime and Logic Errors · *Programming Fundamentals* · class N
- [NEW] Reading C++ Compiler Errors and Warnings · *Programming Fundamentals* · class S
- [NEW] Debugging Techniques · *Programming Fundamentals* · class N
- [NEW] Debugging C++ Programs with a Debugger · *Programming Fundamentals* · class S
- [NEW] Designing Test Cases · *Programming Fundamentals* · class N
- [NEW] Testing a C++ Program with assert and Test Drivers · *Programming Fundamentals* · class S — Approved addition (D-36): Programming 1 outcome LO-2.1 says 'design, implement, test and debug'. Before this addition executable testing first appeared in Programming 2. Closes that gap with a small executable-testing note after 'Designing Test Cases'.
- [NEW] Documentation, Comments and Coding Style · *Programming Fundamentals* · class N
- [NEW] Reviewing Another Programmer's Code · *Programming Fundamentals* · class N

### 3. Mathematics in the Modern World

*The nature and language of mathematics and its use in data, geometric design, codes, optimisation, finance, social choice, graphs and mathematical systems; placement varies by university.*

- **Why Year 1 / delivery:** CHED core GE course (CMO 20 s.2013, 3 units); First Semester at Antique, Cebu and Palawan, Second Semester at AICS. Not a substitute for algebra, calculus or discrete mathematics.
- **Prerequisites:** None.
- **Variability:** MEDIUM: GE placement is not prescribed by CHED and varies (Year 1 at all four CMO-25 schools, term differs). UPLB (autonomous) does not require it.
- **Coverage:** 5 reuse · 16 new · 4 excluded

**Learning outcomes**

- Explain the nature of mathematics as an exploration of patterns and appreciate its practical, intellectual and aesthetic dimensions. — *CHED CMO 20 description (via syllabus quoting it)* → The Nature of Mathematics
- Use inductive and deductive reasoning and problem-solving strategies, and analyse statements with basic logic. — *CHED CMO 20 description* → The Nature of Mathematics; Logic
- Use statistical tools to organise, summarise and interpret data. — *CHED CMO 20 description* → Data Management
- Apply mathematics to personal finance. — *CHED CMO 20 description* → Mathematics of Finance
- Analyse voting and the fair division of limited resources. — *CHED CMO 20 description* → Social Choice and Fair Division
- Appreciate geometric designs and understand codes used in data transmission and security. — *CHED CMO 20 description* → Geometric Designs; Codes and Data Transmission
- Formulate simple linear programs; analyse graphs and mathematical systems. — *CHED CMO 20 outline (via syllabus)* → Optimization; Graphs and Mathematical Systems

**1. The Nature of Mathematics**

- [NEW] Mathematics in Our World: Practical, Intellectual and Aesthetic Dimensions · *Mathematics*
- [NEW] Patterns and Numbers in Nature and the Environment · *Mathematics*
- [REUSE] Functions, Patterns, and Sequences · *Mathematics*
- [NEW] Mathematical Language and Symbols · *Mathematics*
- [REUSE] Mathematical Problem Solving and Logical Reasoning · *Mathematics*

**2. Data Management**

- [REUSE] Data Interpretation and Graphical Reasoning · *Mathematics*
- [REUSE] Probability and Basic Statistics · *Mathematics*
- [NEW] Measures of Relative Position and the Normal Distribution · *Mathematics* — Replaces a descriptive-statistics note: the reused "Probability and Basic Statistics" already covers mean and variance.
- [NEW] Sampling and Data Collection · *Mathematics*
- [NEW] Correlation and Simple Linear Regression · *Mathematics*

**3. Logic**

- [NEW] Logic: Statements, Connectives and Truth Tables · *Mathematics* — GE-level introduction named in the CHED MMW outline (Topic: Logic). Discrete Structures I treats logic formally a semester later - overlap logged as D-17.

**4. Geometric Designs**

- [NEW] Symmetry, Patterns and Tessellations · *Mathematics*

**5. Codes and Data Transmission**

- [NEW] Coding Schemes: Bar Codes, QR Codes and Check Digits · *Mathematics*
- [NEW] Error-Detecting and Error-Correcting Codes · *Mathematics*
- [NEW] Cryptography and Data Security · *Mathematics*

**6. Optimization**

- [NEW] Linear Programming: Formulation and Graphical Solution · *Mathematics*

**7. Mathematics of Finance**

- [REUSE] Financial Mathematics in Everyday Life · *Mathematics* — D-08: kept as Reuse (conditional). Strategist full-content read passed 2026-09-30; curator confirmation pending. If the curator rejects it the row is held out (Excluded), not authored as a duplicate.

**8. Social Choice and Fair Division**

- [NEW] Voting Methods and Fairness Criteria · *Mathematics*
- [NEW] Apportionment Methods · *Mathematics*

**9. Graphs and Mathematical Systems**

- [NEW] Graphs and Networks: Paths, Circuits, Weighted Graphs and Coloring · *Mathematics*
- [NEW] Mathematical Systems and Modular Arithmetic · *Mathematics* — Discrete Structures II (Year 2) owns modular arithmetic per CMO 25; this GE-level note may be reused there.

**10. Held out of this journey (Excluded)**

- [EXCLUDED] Fundamental Arithmetic and Number Sense · *Mathematics* — Introductory numeracy (grade-school/junior-high treatment); not a college MMW topic.
- [EXCLUDED] Fractions, Decimals, Percentages, and Ratios · *Mathematics* — Introductory numeracy.
- [EXCLUDED] Algebraic Expressions and Equations · *Mathematics* — Foundational algebra at introductory depth; Precalculus covers this knowledge at the intended depth.
- [EXCLUDED] Geometry and Measurement · *Mathematics* — Introductory geometry/measurement; not the CHED "geometric designs" topic.

### 4. The Contemporary World

*Globalization and its economic, political, social and environmental dimensions, including global governance, inequality, migration, media and technology, climate change, and the place of the Philippines in the world; placement varies by university.*

- **Why Year 1 / delivery:** CHED core GE course (CMO 20 s.2013); Year 1 First Semester at Antique and Cebu (AICS and Palawan Second Semester).
- **Prerequisites:** None.
- **Variability:** MEDIUM: GE term not prescribed by CHED. Antique gives the course an ASEAN focus.
- **Coverage:** 8 reuse · 1 new · 0 excluded

**Learning outcomes**

- Explain globalization and its dimensions and debates. — *PROPOSAL (CHED syllabus outline not retrieved)* → Globalization
- Analyse global inequality, migration and climate change. — *PROPOSAL* → Global Challenges
- Evaluate the role of media and technology in the information society. — *PROPOSAL* → Technology and Society
- Situate the Philippines in a globalized world and in Asia. — *PROPOSAL* → The Philippines and the World

**1. Globalization**

- [REUSE] Globalization: Concepts, Dimensions, and Debates · *The Contemporary World*
- [REUSE] Global Economy and International Interdependence · *The Contemporary World*
- [REUSE] Global Governance and International Organizations · *The Contemporary World*

**2. Global Challenges**

- [REUSE] Global Inequality, Development, and Sustainability · *The Contemporary World*
- [REUSE] Global Migration and Cultural Change · *The Contemporary World*
- [REUSE] Climate Change and Global Environmental Challenges · *The Contemporary World*

**3. Technology and Society**

- [REUSE] Global Media, Technology, and Information Society · *The Contemporary World*

**4. The Philippines and the World**

- [REUSE] The Philippines in a Globalized World · *The Contemporary World*
- [NEW] Regional Integration and ASEAN · *The Contemporary World* — Recommended gap (INFERENCE): Antique gives the course an ASEAN focus.

### 5. Art Appreciation

*The elements, forms and meanings of art, approaches to interpretation and criticism, major art movements, and Philippine art and cultural heritage; placement varies by university.*

- **Why Year 1 / delivery:** CHED core GE course (CMO 20 s.2013); Year 1 First Semester at Antique, Cebu and Palawan (AICS Second Year).
- **Prerequisites:** None.
- **Variability:** MEDIUM: GE term not prescribed by CHED.
- **Coverage:** 7 reuse · 0 new · 0 excluded

**Learning outcomes**

- Explain the elements and principles of art and the role of art as expression and communication. — *PROPOSAL (CHED syllabus outline not retrieved)* → Foundations of Art
- Identify art forms, media and techniques. — *PROPOSAL* → Forms and Media
- Interpret and critique artworks using different approaches. — *PROPOSAL* → Interpretation and Criticism
- Situate art in major movements and in Philippine cultural heritage. — *PROPOSAL* → History and Heritage

**1. Foundations of Art**

- [REUSE] Aesthetics and Informed Art Appreciation · *Art Appreciation*
- [REUSE] Art as Expression, Communication, and Culture · *Art Appreciation*
- [REUSE] Elements and Principles of Art · *Art Appreciation*

**2. Forms and Media**

- [REUSE] Art Forms, Media, and Techniques · *Art Appreciation*

**3. Interpretation and Criticism**

- [REUSE] Approaches to Art Interpretation and Criticism · *Art Appreciation*

**4. History and Heritage**

- [REUSE] Major Movements and Traditions in Art · *Art Appreciation*
- [REUSE] Philippine Art and Cultural Heritage · *Art Appreciation*

## SECOND SEMESTER

### 6. Computer Programming 2

*Intermediate programming with arrays, strings, structures, pointers and references, linked lists, files and standard libraries, building on Computer Programming 1 with attention to static versus dynamic data structures and program quality. Memory, pointers and linked structures are taught as an instrument for understanding the computational model, with the safer standard-library alternatives shown alongside; C++ is the reference language.*

- **Why Year 1 / delivery:** CMO 25 s.2015 common course (Intermediate Programming) with prerequisite Programming 1; Second Semester at all four CMO-25 schools examined.
- **Prerequisites:** Computer Programming 1 (CMO 25 map). Prerequisite of Data Structures and Algorithms and Applications Development (later years).
- **Variability:** LOW for placement. HIGH for language: pointers, references, dynamic allocation, structures and linked-list nodes are language-dependent (C/C++ pointers versus Java/Python references). Reference language NOT YET APPROVED (assessment recommends C++). Searching and sorting here are an introduction; the CMO Data Structures and Algorithms course (Year 2) treats them thoroughly.
- **Coverage:** 0 reuse · 31 new · 0 excluded
- **Programming review result:** 31 notes (N 11 · S 20). N titles are language-neutral; S titles name C++ (reference language, D-27). Memory and pointers are a teaching instrument, shown alongside safer standard-library abstractions (D-27 guardrail).

**Learning outcomes**

- Use arrays, strings, structures, linked lists and files, with conditional, iterative and recursive constructs and standard libraries. — *CMO 25 LO1* → Arrays; Strings and Text Processing; Structures and Records; Linked Structures; Recursion Revisited; Files and Streams; Standard Libraries and Modules
- Assess and recommend revisions to code on documentation, style and appropriateness of constructs. — *CMO 25 LO2* → Program Quality
- Argue the costs and benefits of dynamic versus static data structure implementations. — *CMO 25 LO3* → Memory, Pointers and References; Linked Structures
- Write robust programs that handle errors and exceptions. — *PROPOSAL* → Exceptions and Robust Programming
- Search and sort small data sets and compare simple algorithms informally. — *PROPOSAL* → Searching and Sorting Basics

**1. Arrays**

- [NEW] One-Dimensional Arrays in C++ · *Programming Fundamentals* · class S
- [NEW] Array Traversal and Common Operations in C++ · *Programming Fundamentals* · class S
- [NEW] Multidimensional Arrays in C++ · *Programming Fundamentals* · class S
- [NEW] Growable Arrays with std::vector in C++ · *Programming Fundamentals* · class S — Approved addition (D-36): 'Static versus Dynamic Data Structure Implementations' (CMO 25 LO3) needs a concrete dynamic contrast before linked lists; currently std::vector appears only inside 'Using Standard Libraries in C++'.

**2. Searching and Sorting Basics**

- [NEW] Elementary Sorting: Bubble, Selection and Insertion · *Data Structures and Algorithms* · class N
- [NEW] Linear and Binary Search · *Data Structures and Algorithms* · class N
- [NEW] Comparing Algorithm Efficiency Informally · *Data Structures and Algorithms* · class N

**3. Strings and Text Processing**

- [NEW] Strings as Character Sequences in C++ · *Programming Fundamentals* · class S
- [NEW] String Operations and Library Functions in C++ · *Programming Fundamentals* · class S
- [NEW] Text Parsing and Simple Pattern Matching in C++ · *Programming Fundamentals* · class S

**4. Structures and Records**

- [NEW] Structures and Records in C++ · *Programming Fundamentals* · class S
- [NEW] Arrays of Structures in C++ · *Programming Fundamentals* · class S

**5. Memory, Pointers and References**

- [NEW] Memory Layout: Stack and Heap in C++ · *Programming Fundamentals* · class S
- [REUSE] Pointers and References in C++ · *Programming Fundamentals* · class S
- [NEW] Dynamic Memory Allocation in C++ · *Programming Fundamentals* · class S
- [NEW] Common Memory Errors in C++ · *Programming Fundamentals* · class S

**6. Linked Structures**

- [NEW] Linked Lists: Nodes and Links · *Data Structures and Algorithms* · class N
  - ↳ closest existing: "Fundamentals of Basic Data Structures" — **CONFIRMED NEW**
- [NEW] Linked List Operations in C++ · *Data Structures and Algorithms* · class S
- [NEW] Static versus Dynamic Data Structure Implementations · *Data Structures and Algorithms* · class N

**7. Recursion Revisited**

- [NEW] Recursive Problem Solving · *Programming Fundamentals* · class N
- [NEW] Recursion versus Iteration · *Programming Fundamentals* · class N

**8. Files and Streams**

- [NEW] Text File Input and Output in C++ · *Programming Fundamentals* · class S
- [NEW] Reading and Writing Structured Records in C++ · *Programming Fundamentals* · class S

**9. Exceptions and Robust Programming**

- [NEW] Error Handling Strategies: Return Codes, Exceptions and Failing Safely · *Programming Fundamentals* · class N
- [NEW] Exceptions in C++ · *Programming Fundamentals* · class S
- [NEW] Defensive Programming and Assertions · *Programming Fundamentals* · class N

**10. Standard Libraries and Modules**

- [NEW] Using Standard Libraries in C++ · *Programming Fundamentals* · class S
- [NEW] Building Reusable Modules in C++ · *Programming Fundamentals* · class S

**11. Program Quality**

- [NEW] Unit Testing in C++ · *Programming Fundamentals* · class S
- [NEW] Code Review and Refactoring · *Programming Fundamentals* · class N
- [NEW] Structured Problem Solving with Larger Programs · *Programming Fundamentals* · class N

### 7. Discrete Structures I

*The mathematical foundations of computing: propositional and predicate logic with rules of inference, sets, relations and functions, integers and divisibility, and proof techniques including induction and program correctness. Assumes competence in algebra and trigonometry (the CHED prerequisite); learners who need preparation can adopt the separate optional plan 'Precalculus (Algebra and Trigonometry)'.*

- **Why Year 1 / delivery:** CMO 25 s.2015 requires Discrete Structures 1 and 2 for BSCS; Discrete Structures 1 is Second Semester at all four CMO-25 schools examined.
- **Prerequisites:** Algebra and Trigonometry per the CMO 25 map. Not assumed to be met inside this root: learners who need it can adopt the optional standalone plan 'Precalculus (Algebra and Trigonometry)'; others meet it through senior-high preparation. The notes in this plan define their own terms and do not depend on Precalculus notes.
- **Variability:** MEDIUM. Prerequisite varies (CMO map: Algebra and Trigonometry; Cebu and Palawan: Introduction to Computing; Antique: a Precalculus course). The CMO prerequisite map alone would allow First Semester (its own sample chart is not reliably legible and may place a 3-unit professional course there), yet all four CMO-25 schools use Second Semester. Ateneo and UPLB (autonomous) differ. CMO 25 states outcomes but no course description; the DS1/DS2 split follows the CMO outcomes: DS2 = permutations, combinations, modular arithmetic; probability with counting and Bayes theorem; recurrences (so those are NOT here). Integers/divisibility and Boolean algebra are PROPOSALS supporting the proof outcomes.
- **Coverage:** 0 reuse · 34 new · 0 excluded

**Learning outcomes**

- Perform operations on sets, functions and relations and relate them to computer programming. — *CMO 25 LO1* → Sets; Relations; Functions and Sequences
- Construct sound arguments in propositional and predicate logic using rules of inference, applied to sample software. — *CMO 25 LO2* → Propositional Logic; Predicate Logic; Rules of Inference and Arguments
- Construct valid proofs by induction, direct proof and contradiction, to simplify programs and prove program correctness. — *CMO 25 LO3* → Proof Techniques
- Reason about divisibility, parity and the Euclidean algorithm as the standard material for proofs. — *PROPOSAL (supports LO3)* → Integers and Divisibility

**1. Propositional Logic**

- [REUSE] Propositions and Logical Connectives · *Discrete Mathematics*
- [NEW] Truth Tables and Compound Propositions · *Discrete Mathematics*
- [NEW] Logical Equivalence and the Laws of Logic · *Discrete Mathematics*
- [NEW] Conditional, Biconditional, Converse and Contrapositive · *Discrete Mathematics*
- [NEW] Boolean Algebra and Digital Logic · *Discrete Mathematics*
- [NEW] Logic in Programming and Software Specifications · *Discrete Mathematics*

**2. Predicate Logic**

- [NEW] Predicates and Quantifiers · *Discrete Mathematics*
- [NEW] Nested Quantifiers and Negation · *Discrete Mathematics*
- [NEW] Translating Between English and Logic · *Discrete Mathematics*

**3. Rules of Inference and Arguments**

- [NEW] Valid Arguments and Rules of Inference · *Discrete Mathematics*
- [NEW] Inference with Quantified Statements · *Discrete Mathematics*
- [NEW] Common Logical Fallacies · *Discrete Mathematics*

**4. Sets**

- [NEW] Sets and Set Notation · *Discrete Mathematics*
- [NEW] Set Operations and Venn Diagrams · *Discrete Mathematics*
- [NEW] Set Identities and Membership Proofs · *Discrete Mathematics*
- [NEW] Power Sets, Cartesian Products and Cardinality · *Discrete Mathematics*

**5. Relations**

- [NEW] Relations and Their Representations · *Discrete Mathematics*
- [NEW] Properties of Relations · *Discrete Mathematics*
- [NEW] Equivalence Relations and Partitions · *Discrete Mathematics*
- [NEW] Partial Orders · *Discrete Mathematics*

**6. Functions and Sequences**

- [NEW] Functions: Definition and Notation · *Discrete Mathematics*
- [NEW] One-to-One, Onto and Bijective Functions · *Discrete Mathematics*
- [NEW] Composition and Inverse of Functions · *Discrete Mathematics*
- [NEW] Sequences and Summations · *Discrete Mathematics*

**7. Integers and Divisibility**

- [NEW] Divisibility, Parity and the Division Algorithm · *Discrete Mathematics*
- [NEW] Greatest Common Divisors and the Euclidean Algorithm · *Discrete Mathematics*

**8. Proof Techniques**

- [NEW] Introduction to Mathematical Proof · *Discrete Mathematics*
- [NEW] Direct Proof · *Discrete Mathematics*
- [NEW] Proof by Contraposition · *Discrete Mathematics*
- [NEW] Proof by Contradiction · *Discrete Mathematics*
- [NEW] Proof by Cases and Counterexamples · *Discrete Mathematics*
- [NEW] Mathematical Induction · *Discrete Mathematics*
- [NEW] Strong Induction · *Discrete Mathematics*
- [NEW] Proving Program Correctness: Assertions and Loop Invariants · *Discrete Mathematics* — Serves CMO 25 DS1 LO3 ("prove program correctness").

### 8. Understanding the Self

*How the self is understood from philosophical, psychological, biological, social and cultural perspectives, its physical, material, digital and political dimensions, and how to manage and care for oneself; placement varies by university.*

- **Why Year 1 / delivery:** CHED core GE course (CMO 20 s.2013); Year 1 at all four CMO-25 schools examined (Second Semester at Antique, Cebu and Palawan; First at AICS).
- **Prerequisites:** None.
- **Variability:** MEDIUM: GE term not prescribed by CHED.
- **Coverage:** 6 reuse · 2 new · 1 excluded

**Learning outcomes**

- Explain the self from philosophical, psychological, biological, social and cultural perspectives. — *PROPOSAL (CHED syllabus outline not retrieved)* → The Self from Various Perspectives
- Describe the physical, material, digital and political dimensions of the self. — *PROPOSAL* → Dimensions of the Self
- Manage emotions, stress and personal goals and practise self-regulation. — *PROPOSAL* → Managing and Caring for the Self

**1. The Self from Various Perspectives**

- [REUSE] The Self from Philosophical and Psychological Perspectives · *Understanding the Self*
- [REUSE] Biological, Social, and Cultural Influences on the Self · *Understanding the Self*
- [REUSE] Self-Concept, Self-Esteem, and Identity Development · *Understanding the Self*

**2. Dimensions of the Self**

- [REUSE] The Physical, Sexual, Material, Digital, and Political Self · *Understanding the Self*
- [NEW] The Spiritual Self and the Search for Meaning · *Understanding the Self* — UNVERIFIED gap: CHED syllabus outline not retrieved; recommended only if the outline lists it.

**3. Managing and Caring for the Self**

- [REUSE] Emotions, Stress, and Coping · *Understanding the Self*
- [REUSE] Goal Setting, Self-Regulation, and Personal Growth · *Understanding the Self*
- [NEW] Learning to Learn: Academic Self-Management · *Understanding the Self* — UNVERIFIED gap: optional; a study-skills note that BSCS first-years would use.

**4. Held out of this journey (Excluded)**

- [EXCLUDED] Self-Understanding and the Development of the Professional Teacher · *Understanding the Self* — Teacher-framed treatment (10 teaching/classroom terms); belongs to the Education program family.

### 9. Readings in Philippine History

*Philippine history read through primary sources and historical interpretation, from precolonial society through Spanish and American rule, the Revolution, the Japanese occupation and the postwar republic to Martial Law and contemporary legacies; placement varies by university.*

- **Why Year 1 / delivery:** CHED core GE course (CMO 20 s.2013); Year 1 at all four CMO-25 schools examined (Second Semester at Antique, Cebu; First at AICS, Palawan).
- **Prerequisites:** None.
- **Variability:** MEDIUM: GE term not prescribed by CHED. Second Semester follows the Antique/Cebu agreement.
- **Coverage:** 10 reuse · 0 new · 0 excluded

**Learning outcomes**

- Analyse primary sources and historical interpretations. — *PROPOSAL (CHED syllabus outline not retrieved)* → Sources and Interpretation
- Explain precolonial society and the Spanish colonial transformation of the Philippines. — *PROPOSAL* → Precolonial and Spanish Periods
- Trace the Reform Movement, Revolution and the First Republic. — *PROPOSAL* → Reform, Revolution and the First Republic
- Evaluate American rule, the Japanese occupation and the postwar republic through Martial Law to the present. — *PROPOSAL* → American, Japanese and Postwar Periods; Contemporary Philippines

**1. Sources and Interpretation**

- [REUSE] Primary Sources and Historical Interpretation · *Philippine History*

**2. Precolonial and Spanish Periods**

- [REUSE] Precolonial Philippine Society and Culture · *Philippine History*
- [REUSE] Spanish Colonization and Transformation of Philippine Society · *Philippine History*

**3. Reform, Revolution and the First Republic**

- [REUSE] Philippine Reform Movement and the Propaganda Movement · *Philippine History*
- [REUSE] Philippine Revolution and the First Philippine Republic · *Philippine History*

**4. American, Japanese and Postwar Periods**

- [REUSE] American Colonial Rule and Philippine Society · *Philippine History*
- [REUSE] Japanese Occupation and the Second World War in the Philippines · *Philippine History*
- [REUSE] Postwar Philippine Republic and Nation-Building · *Philippine History*

**5. Contemporary Philippines**

- [REUSE] Martial Law and Democratic Restoration · *Philippine History*
- [REUSE] Contemporary Philippine Society and Historical Legacies · *Philippine History*

### 10. Purposive Communication

*Effective, audience-aware communication in academic, professional, global and digital settings, with critical reading, academic and workplace writing, and oral presentation; placement varies by university.*

- **Why Year 1 / delivery:** CHED core GE course (CMO 20 s.2013); Year 1 Second Semester at Antique, Cebu and Palawan (Ateneo First; AICS Second Year).
- **Prerequisites:** None.
- **Variability:** MEDIUM: GE term not prescribed by CHED. Some schools split it (Purposive Communication 1 and 2). CMO 25 program outcomes name communication in both English and Filipino; the Filipino course is treated separately.
- **Coverage:** 8 reuse · 4 new · 0 excluded

**Learning outcomes**

- Apply the principles, process and contexts of effective communication, including verbal and nonverbal communication and barriers. — *PROPOSAL (CHED syllabus outline not retrieved)* → Foundations of Communication
- Read critically, evaluate sources and write academic and professional texts. — *PROPOSAL* → Academic and Professional Communication
- Communicate across cultures and through multimodal and digital media. — *PROPOSAL* → Communication in a Global and Digital World
- Prepare oral presentations and workplace and technical documents. — *PROPOSAL* → Presentation and Workplace Writing

**1. Foundations of Communication**

- [REUSE] Effective Communication: Principles, Process, and Contexts · *Purposive Communication*
- [REUSE] Verbal and Nonverbal Communication · *Purposive Communication*
- [REUSE] Communication Barriers and Strategies · *Purposive Communication*
- [REUSE] Audience, Purpose, Tone, and Register · *Purposive Communication*

**2. Academic and Professional Communication**

- [REUSE] Academic and Professional Communication · *Purposive Communication*
- [REUSE] Critical Reading and Evaluating Sources · *Purposive Communication*
- [REUSE] Academic Writing: Organization, Coherence, and Cohesion · *Purposive Communication*

**3. Communication in a Global and Digital World**

- [NEW] Intercultural and Global Communication · *Purposive Communication* — Recommended gap (INFERENCE: not verified against the CHED outline).
- [NEW] Multimodal and Digital Communication · *Purposive Communication* — Recommended gap (INFERENCE).

**4. Presentation and Workplace Writing**

- [NEW] Oral Presentation and Public Speaking · *Purposive Communication* — Recommended gap (INFERENCE).
- [REUSE] Workplace and Special-Purpose Communication · *Purposive Communication*
- [NEW] Technical Writing and Documentation · *Purposive Communication* — Recommended gap (INFERENCE): Cebu lists a technical-writing course in Year 1.

## OPTIONAL PREPARATORY — separate standalone root (not part of the default Year)

**Precalculus (Algebra and Trigonometry)** · 31 notes · 5 sections · 0 reuse. Full note-by-note listing, delivery reasoning and the 12 candidate verdicts are in `bscs-year1-precalculus-preparatory-proposal.md`; the shaping file is `bscs-precalculus-preparatory-shaping.tsv`.

*Optional preparatory course in algebra, functions, trigonometry, analytic geometry and sequences and series, covering the algebra and trigonometry preparation that CHED lists as the prerequisite of Discrete Structures 1. It is a separate, standalone plan for learners who need this preparation before BS Computer Science Year 1; many programs do not teach it as a course.*

- **1. Algebraic Foundations** — 8 notes
- **2. Functions and Graphs** — 7 notes
- **3. Trigonometry** — 8 notes
- **4. Analytic Geometry** — 5 notes
- **5. Sequences and Series** — 3 notes

Discrete Structures I (default Year, Second Semester) names Algebra and Trigonometry as its prerequisite and points to this plan, but stands alone: none of its notes depends on Precalculus notes.

