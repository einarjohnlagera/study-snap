## Overview

A **linked list** stores a sequence of values as a chain of separate pieces, called **nodes**. Each node holds a value and a link to the next node, so the order of the sequence is carried by the links rather than by position in memory. This note implements a singly linked list of integers in C++17 using pointers, and works through the basic operations: adding, searching, removing, traversing and releasing.

Writing a linked list by hand is a way to understand pointers, ownership and the memory model, and it is how many data structures are built inside libraries. In everyday programs you would normally use `std::vector`, or the standard library's own list types, rather than writing nodes yourself. The final section compares them.

This note assumes you know structures, pointers and `new`/`delete`.

## The node

```cpp
struct Node {
    int value;       // the data stored in this node
    Node* next;      // the next node, or nullptr if this is the last one
};
```

A list is described by a single pointer, the **head**, which points to the first node. An **empty list** is a head equal to `nullptr`. The last node's `next` is `nullptr`, which marks the end.

```
head -> [ 10 | * ] -> [ 20 | * ] -> [ 30 | nullptr ]
```

Each node is created separately on the heap, so the list can grow one node at a time without knowing its length in advance.

## Operations

All functions below take the head by pointer or return the new head, because adding or removing the first node *changes what the head is*.

### Adding at the front

```cpp
Node* pushFront(Node* head, int value) {
    return new Node{value, head};    // new node points to the old first node
}
```

The new node becomes the head. This takes the same amount of work however long the list is.

### Adding at the end

```cpp
Node* pushBack(Node* head, int value) {
    Node* node = new Node{value, nullptr};
    if (head == nullptr) {           // empty list: the new node is the whole list
        return node;
    }
    Node* current = head;
    while (current->next != nullptr) {   // walk to the last node
        current = current->next;
    }
    current->next = node;
    return head;
}
```

Reaching the end means walking the whole list, so the work grows with the list's length. The empty-list case must be handled separately, because there is no last node to attach to.

### Traversing and printing

```cpp
void printList(const Node* head) {
    for (const Node* current = head; current != nullptr; current = current->next) {
        std::cout << current->value << ' ';
    }
    std::cout << '\n';
}
```

Traversal starts at the head and follows `next` until it reaches `nullptr`. It never changes the list.

### Searching

```cpp
Node* findValue(Node* head, int target) {
    for (Node* current = head; current != nullptr; current = current->next) {
        if (current->value == target) {
            return current;           // found: a pointer to that node
        }
    }
    return nullptr;                   // not found
}
```

There is no way to jump to the middle; search is a walk, so it takes longer on longer lists.

### Removing the first node with a given value

```cpp
Node* removeValue(Node* head, int target) {
    Node* previous = nullptr;
    Node* current = head;
    while (current != nullptr && current->value != target) {
        previous = current;
        current = current->next;
    }
    if (current == nullptr) {         // not found: nothing to remove
        return head;
    }
    if (previous == nullptr) {        // the node to remove is the head
        head = current->next;
    } else {                          // bypass the node
        previous->next = current->next;
    }
    delete current;                   // release the removed node
    return head;
}
```

Removing means **re-linking** around the node and then releasing it. The order matters: if you released the node before reading `current->next`, you would be reading memory that is no longer yours. Removing the head is a separate case because there is no previous node to update.

### Releasing the whole list

```cpp
void clearList(Node* head) {
    while (head != nullptr) {
        Node* next = head->next;      // remember the rest BEFORE releasing this node
        delete head;
        head = next;
    }
}
```

Every node was allocated with `new`, so every node must be released. Forgetting this leaks every node.

## A complete program

```cpp
#include <iostream>

struct Node {
    int value;
    Node* next;
};

Node* pushFront(Node* head, int value) {
    return new Node{value, head};
}

Node* pushBack(Node* head, int value) {
    Node* node = new Node{value, nullptr};
    if (head == nullptr) {
        return node;
    }
    Node* current = head;
    while (current->next != nullptr) {
        current = current->next;
    }
    current->next = node;
    return head;
}

void printList(const Node* head) {
    for (const Node* current = head; current != nullptr; current = current->next) {
        std::cout << current->value << ' ';
    }
    std::cout << '\n';
}

Node* findValue(Node* head, int target) {
    for (Node* current = head; current != nullptr; current = current->next) {
        if (current->value == target) {
            return current;
        }
    }
    return nullptr;
}

Node* removeValue(Node* head, int target) {
    Node* previous = nullptr;
    Node* current = head;
    while (current != nullptr && current->value != target) {
        previous = current;
        current = current->next;
    }
    if (current == nullptr) {
        return head;
    }
    if (previous == nullptr) {
        head = current->next;
    } else {
        previous->next = current->next;
    }
    delete current;
    return head;
}

void clearList(Node* head) {
    while (head != nullptr) {
        Node* next = head->next;
        delete head;
        head = next;
    }
}

int main() {
    Node* head = nullptr;
    head = pushBack(head, 20);
    head = pushBack(head, 30);
    head = pushFront(head, 10);
    printList(head);                       // 10 20 30

    std::cout << (findValue(head, 20) != nullptr) << '\n';   // 1
    std::cout << (findValue(head, 99) != nullptr) << '\n';   // 0

    head = removeValue(head, 20);          // remove a middle node
    printList(head);                       // 10 30
    head = removeValue(head, 10);          // remove the head
    printList(head);                       // 30
    head = removeValue(head, 99);          // not present: no change
    printList(head);                       // 30

    clearList(head);
    head = nullptr;
    return 0;
}
```

Running the program prints:

```
10 20 30
1
0
10 30
30
30
```

## Edge cases to test

| Case | Why it matters |
|---|---|
| Empty list | Every function must handle a head of `nullptr` without dereferencing it |
| One-element list | The head is also the last node |
| Removing the head | There is no previous node |
| Removing the last node | The previous node's `next` becomes `nullptr` |
| Value not present | Search returns `nullptr`; removal changes nothing |

## Cost compared with an array or `std::vector`

| Operation | Linked list (this note) | `std::vector` |
|---|---|---|
| Add at the front | constant work | work grows with length (elements shift) |
| Add at the end | grows with length (walk to the end) | usually constant |
| Reach item *k* | grows with *k* (walk) | constant (direct index) |
| Remove a node you already hold | re-link, constant | elements shift |
| Memory | one allocation per node plus a pointer each | one contiguous block |

Neither is "better"; the right choice depends on the operations that dominate. In practice contiguous storage such as `std::vector` is often faster than expected because of how memory is accessed, so a hand-written list is rarely the best default.

## Using it wisely

- Treat this code as a model of how linked structures work. For real programs prefer `std::vector`, or the standard library's list containers, which do this work correctly for you.
- The hand-written version leaves ownership entirely to you: every `new` needs its `delete`, and a mistake is a leak or a dangling pointer. Modern C++ expresses node ownership with smart pointers (a separate topic); they are not used here so that the mechanism is visible.
- Run list code under the address sanitizer while learning. It reports use after release and, on supported systems, leaked nodes.

## Common misconceptions

- **"A linked list stores its items next to each other."** Nodes can be anywhere in memory; the links define the order.
- **"You can index a linked list like an array."** Reaching item *k* means following *k* links.
- **"Removing a node means just calling `delete` on it."** You must first re-link its neighbours, or the list is broken.
- **"The head is part of the list's data."** It is only a pointer to the first node, and it changes when the first node is added or removed.
- **"Forgetting `clearList` is harmless."** Every node would leak.

## Key points to remember

- A singly linked list is a chain of nodes, each holding a value and a pointer to the next; the head points to the first node and `nullptr` ends the chain.
- Adding or removing the first node changes the head, so those functions return the new head.
- Traversal and search follow `next` until `nullptr`; there is no direct indexing.
- Removal re-links the neighbours first, then releases the node, and the head case is separate.
- Every node was allocated with `new`, so the whole list must be released node by node.
- Test the empty list, one element, the head, the last node and a missing value.
- Prefer `std::vector` or standard containers in real programs; use hand-written lists to understand how they work.
