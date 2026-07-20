import { memoryStore } from '@luno-oss/core';
import { describeStoreConformance } from '../src/store';

// Proves the re-exported conformance suite runs, and that its own reference target
// (memoryStore) passes it — the baseline a real store package is measured against.
describeStoreConformance('memoryStore (via @luno-oss/testing)', memoryStore);
