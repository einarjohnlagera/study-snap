package com.studysnap.backend.benchmark;

/**
 * Declared pilot authoring family; a value does not prove which model authored an item.
 * This format-breaking rename makes fixture, key and label files use these names
 * and the authoringFamily field;
 * pre-rename private files do not exist, and the strict loaders reject their format.
 */
enum ModelFamily { OPENAI, ANTHROPIC }
