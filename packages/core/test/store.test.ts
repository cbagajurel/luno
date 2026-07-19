import { memoryStore } from '../src';
import { describeStoreConformance } from '../src/testing/store-conformance';

describeStoreConformance('memoryStore', memoryStore);
