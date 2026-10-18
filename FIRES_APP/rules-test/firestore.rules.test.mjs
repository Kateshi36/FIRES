// W14: checks of ../firestore.rules, run on the local Firestore emulator.
//
// Each test has an ID (R-01 ...) that matches the W14 test record, and says what the rules must do.
// Nothing here touches the live project: "demo-" project ids never leave your computer.
//
// Run:  npm install   (once)   then   npm test      (needs Node 20+ and Java, see RUN.md)

import { readFileSync } from 'node:fs';
import { describe, it, before, after, beforeEach } from 'node:test';
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails
} from '@firebase/rules-unit-testing';
import {
  collection, doc, getDoc, getDocs, query, serverTimestamp, setDoc, setLogLevel, updateDoc, where
} from 'firebase/firestore';

setLogLevel('error'); // the emulator logs every refusal as a warning, which is just noise here

const PROJECT_ID = 'demo-fires';
const RULES = readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8');

let env;

// People used below. The role is read from users/{uid}.role, exactly as the real rules do.
const USERS = {
  admin1: { fullName: 'Admin One', email: 'admin1@example.ph', role: 'admin' },
  admin2: { fullName: 'Admin Two', email: 'admin2@example.ph', role: 'admin' },
  resp1: { fullName: 'Ana Responder', email: 'resp1@example.ph', role: 'responder' },
  resp2: { fullName: 'Ben Responder', email: 'resp2@example.ph', role: 'responder' },
  cit1: { fullName: 'Cora Citizen', email: 'cit1@example.ph', role: 'citizen' },
  cit2: { fullName: 'Dan Citizen', email: 'cit2@example.ph', role: 'citizen' }
};

// A reported incident owned by cit1 and one owned by cit2.
const incidentOf = (reporterId) => ({
  reporterId,
  reporterName: 'Reporter',
  fireType: 'structural',
  description: 'Smoke from a house',
  latitude: 14.6,
  longitude: 121.0,
  fireSize: 'small',
  status: 'reported',
  verification: 'pending',
  severity: 'medium',
  severitySource: 'auto'
});

const assignmentFor = (responderId, extra = {}) => ({
  responderId,
  responderName: 'Someone',
  unit: 'B-FLARE',
  assignedResources: ['Fire truck'],
  assignedAt: serverTimestamp(),
  ...extra
});

const dbOf = (uid) => env.authenticatedContext(uid).firestore();
const signedOutDb = () => env.unauthenticatedContext().firestore();

before(async () => {
  env = await initializeTestEnvironment({ projectId: PROJECT_ID, firestore: { rules: RULES } });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const [uid, data] of Object.entries(USERS)) {
      await setDoc(doc(db, 'users', uid), data);
    }
    await setDoc(doc(db, 'incidents', 'inc1'), incidentOf('cit1'));
    await setDoc(doc(db, 'incidents', 'inc2'), incidentOf('cit2'));
  });
});

describe('Users', () => {
  it('R-01 an admin can list users', async () => {
    await assertSucceeds(getDocs(collection(dbOf('admin1'), 'users')));
  });

  it('R-02 a responder cannot list users', async () => {
    await assertFails(getDocs(collection(dbOf('resp1'), 'users')));
  });

  it('R-03 a citizen cannot list users', async () => {
    await assertFails(getDocs(collection(dbOf('cit1'), 'users')));
  });

  it('R-03b a citizen reads their own user document but not someone else\'s', async () => {
    await assertSucceeds(getDoc(doc(dbOf('cit1'), 'users', 'cit1')));
    await assertFails(getDoc(doc(dbOf('cit1'), 'users', 'cit2')));
  });

  it('R-11 a citizen cannot make themselves an admin or a responder', async () => {
    await assertFails(updateDoc(doc(dbOf('cit1'), 'users', 'cit1'), { role: 'admin' }));
    // A brand-new account (no users document yet) creating its own document as a responder.
    await assertFails(setDoc(doc(dbOf('newUid'), 'users', 'newUid'), { fullName: 'X', role: 'responder' }));
    await assertSucceeds(setDoc(doc(dbOf('newUid'), 'users', 'newUid'), { fullName: 'X', role: 'citizen' }));
  });
});

describe('Incidents', () => {
  it('R-04 a citizen cannot read another citizen\'s incident', async () => {
    await assertFails(getDoc(doc(dbOf('cit1'), 'incidents', 'inc2')));
  });

  it('R-04b a citizen can read their own incident', async () => {
    await assertSucceeds(getDoc(doc(dbOf('cit1'), 'incidents', 'inc1')));
  });

  it('R-05 a citizen cannot list all incidents, only their own', async () => {
    await assertFails(getDocs(collection(dbOf('cit1'), 'incidents')));
    await assertSucceeds(getDocs(query(collection(dbOf('cit1'), 'incidents'), where('reporterId', '==', 'cit1'))));
  });

  it('R-05b a responder and an admin can list every incident', async () => {
    await assertSucceeds(getDocs(collection(dbOf('resp1'), 'incidents')));
    await assertSucceeds(getDocs(collection(dbOf('admin1'), 'incidents')));
  });

  it('R-12 an admin can verify a reported incident, a citizen cannot change one', async () => {
    const verify = { verification: 'verified', status: 'verified', updatedAt: serverTimestamp() };
    await assertSucceeds(updateDoc(doc(dbOf('admin1'), 'incidents', 'inc1'), verify));
    await assertFails(updateDoc(doc(dbOf('cit1'), 'incidents', 'inc1'), verify));
  });
});

describe('Assignments', () => {
  const assignmentDoc = (uid, id = 'a1') => doc(dbOf(uid), 'incidents', 'inc1', 'assignments', id);

  it('R-06 an admin can create an assignment for another responder', async () => {
    await assertSucceeds(setDoc(assignmentDoc('admin1'), assignmentFor('resp2')));
  });

  it('R-07 a responder cannot create an assignment for someone else', async () => {
    await assertFails(setDoc(assignmentDoc('resp1'), assignmentFor('resp2')));
  });

  it('R-08 a responder can still assign themselves', async () => {
    await assertSucceeds(setDoc(assignmentDoc('resp1'), assignmentFor('resp1')));
  });

  it('R-09 an admin cannot assign a citizen, another admin, or an account that does not exist', async () => {
    await assertFails(setDoc(assignmentDoc('admin1', 'a-cit'), assignmentFor('cit1')));
    await assertFails(setDoc(assignmentDoc('admin1', 'a-adm'), assignmentFor('admin2')));
    await assertFails(setDoc(assignmentDoc('admin1', 'a-none'), assignmentFor('ghost-uid')));
  });

  it('R-09b a citizen cannot create an assignment, not even for themselves', async () => {
    await assertFails(setDoc(assignmentDoc('cit1'), assignmentFor('cit1')));
  });

  it('R-09c an assignment cannot carry extra fields', async () => {
    await assertFails(setDoc(assignmentDoc('admin1'), assignmentFor('resp2', { note: 'extra' })));
  });

  it('R-09d staff can read assignments, a citizen cannot', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'incidents', 'inc1', 'assignments', 'a1'), {
        responderId: 'resp2', responderName: 'Ben', unit: 'BFP', assignedResources: []
      });
    });
    const col = (uid) => collection(dbOf(uid), 'incidents', 'inc1', 'assignments');
    await assertSucceeds(getDocs(col('resp1')));
    await assertFails(getDocs(col('cit1')));
  });

  it('R-09e a responder can save their own assignment again (a retry), but an admin cannot overwrite someone else\'s', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, 'incidents', 'inc1', 'assignments', 'own'), {
        responderId: 'resp1', responderName: 'Ana', unit: 'BFP', assignedResources: []
      });
      await setDoc(doc(db, 'incidents', 'inc1', 'assignments', 'bens'), {
        responderId: 'resp2', responderName: 'Ben', unit: 'BFP', assignedResources: []
      });
    });
    await assertSucceeds(setDoc(assignmentDoc('resp1', 'own'), assignmentFor('resp1')));
    // The rules give admins no update right, so the website must check "does it exist?" before retrying.
    await assertFails(setDoc(assignmentDoc('admin1', 'bens'), assignmentFor('resp2')));
  });
});

describe('Nothing is left open', () => {
  it('R-10 a signed-out visitor can read nothing', async () => {
    await assertFails(getDocs(collection(signedOutDb(), 'users')));
    await assertFails(getDocs(collection(signedOutDb(), 'incidents')));
    await assertFails(getDoc(doc(signedOutDb(), 'incidents', 'inc1')));
  });

  it('R-13 a collection the rules do not name is refused, even for an admin', async () => {
    await assertFails(setDoc(doc(dbOf('admin1'), 'somethingElse', 'x'), { a: 1 }));
    await assertFails(getDoc(doc(dbOf('admin1'), 'somethingElse', 'x')));
  });
});
