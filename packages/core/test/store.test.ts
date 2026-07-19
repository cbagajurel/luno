import { memoryStore } from '../src';
import { describeStoreConformance } from './store-conformance';

describeStoreConformance('memoryStore', memoryStore);
