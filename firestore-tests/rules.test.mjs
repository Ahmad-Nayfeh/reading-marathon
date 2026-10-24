import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, updateDoc, deleteDoc, deleteField, collection, collectionGroup, getDocs, query, where, writeBatch, arrayUnion } from 'firebase/firestore';
import { readFileSync } from 'fs';

// Each scenario follows the writes FirebaseBackend.kt makes, in the same batches.
const env = await initializeTestEnvironment({ projectId: 'demo-marathon', firestore: { rules: readFileSync('../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8085 } });
const as = (id, token = {}) => env.authenticatedContext(id, token).firestore();
const own = as('own', { email: 'own@gmail.com' });
const ali = as('ali');
const eve = as('eve');
const adm = as('adm', { email: 'readingmarathon.admin@gmail.com', email_verified: true });
const fake = as('fake', { email: 'readingmarathon.admin@gmail.com', email_verified: false });
let n = 0, bad = 0;
async function t(name, p) { n++; try { await p; console.log('ok  ', name); } catch (e) { bad++; console.log('FAIL', name, String(e.message).slice(0, 160)); } }

const G = 'groups/g1';

// --- profile and personal log
await t('own creates profile', assertSucceeds(setDoc(doc(own, 'users/own'), { email: 'own@gmail.com', guest: false, goal: 30, groups: [], createdAt: 1, lastSeen: 1 })));
await t('profile email must match the account', assertFails(setDoc(doc(eve, 'users/eve'), { email: 'x@gmail.com', guest: true })));
await t('guest profile with no email', assertSucceeds(setDoc(doc(ali, 'users/ali'), { guest: true, name: 'علي', goal: 20, groups: [] })));
await t('profile rejects unknown fields', assertFails(setDoc(doc(ali, 'users/ali'), { isAdmin: true }, { merge: true })));
await t('cannot write someone else\'s profile', assertFails(setDoc(doc(eve, 'users/ali'), { name: 'x' }, { merge: true })));
await t('cannot read someone else\'s profile', assertFails(getDoc(doc(eve, 'users/own'))));
await t('logs own day', assertSucceeds(setDoc(doc(ali, 'users/ali/days/2026-10-20'), { date: '2026-10-20', minutes: 30, updatedAt: 1 })));
await t('day id must match its date', assertFails(setDoc(doc(ali, 'users/ali/days/2026-10-21'), { date: '2026-10-20', minutes: 30, updatedAt: 1 })));
await t('day over 16 h rejected', assertFails(setDoc(doc(ali, 'users/ali/days/2026-10-22'), { date: '2026-10-22', minutes: 961, updatedAt: 1 })));
await t('others cannot read my log', assertFails(getDoc(doc(eve, 'users/ali/days/2026-10-20'))));

// --- createGroup: group + invite, then own member + profile
const b1 = writeBatch(own);
b1.set(doc(own, G), { name: 'نادي', about: '', visibility: 'private', inviteCode: 'ABC234', roles: { own: 'owner' }, rules: { minutesPerPoint: 10, finishBonus: 50 }, createdAt: 1, current: null });
b1.set(doc(own, 'invites/ABC234'), { groupId: 'g1', createdBy: 'own' });
await t('owner creates group with invite', assertSucceeds(b1.commit()));
const b2 = writeBatch(own);
b2.set(doc(own, G + '/members/own'), { name: 'أحمد', linkedUid: 'own', active: true, joinedAt: 1 });
b2.set(doc(own, 'users/own'), { email: 'own@gmail.com', groups: arrayUnion('g1') }, { merge: true });
await t('owner adds own member and profile', assertSucceeds(b2.commit()));
await t('cannot create a group owned by someone else', assertFails(setDoc(doc(eve, 'groups/x'), { name: 'x', visibility: 'private', roles: { own: 'owner' } })));
await t('cannot make an invite for a group you do not manage', assertFails(setDoc(doc(eve, 'invites/EVE234'), { groupId: 'g1', createdBy: 'eve' })));
await t('owner adds a book', assertSucceeds(setDoc(doc(own, G + '/challenges/c1'), { title: 'الأيام', author: '', start: '2026-10-01', end: '2026-10-28' })));

// --- outsiders
await t('outsider can get invite by code', assertSucceeds(getDoc(doc(eve, 'invites/ABC234'))));
await t('outsider cannot list invites', assertFails(getDocs(collection(eve, 'invites'))));
await t('outsider can read the group card', assertSucceeds(getDoc(doc(eve, G))));
await t('outsider cannot read members', assertFails(getDocs(collection(eve, G + '/members'))));
await t('outsider cannot read entries', assertFails(getDocs(collection(eve, G + '/entries'))));
await t('outsider cannot list private groups', assertFails(getDocs(collection(eve, 'groups'))));
await t('outsider cannot make self owner', assertFails(updateDoc(doc(eve, G), { 'roles.eve': 'owner' })));

const join = (db, id, code) => {
  const b = writeBatch(db);
  const m = { name: id, linkedUid: id, active: true, joinedAt: 1 };
  if (code !== undefined) m.code = code;
  b.set(doc(db, `${G}/members/${id}`), m);
  b.update(doc(db, G), { [`roles.${id}`]: 'member' });
  b.set(doc(db, `users/${id}`), { groups: arrayUnion('g1') }, { merge: true });
  return b.commit();
};
await t('private group: joining without the code fails', assertFails(join(eve, 'eve')));
await t('private group: joining with a wrong code fails', assertFails(join(eve, 'eve', 'ZZZ999')));
await t('roles alone (no member document) fails', assertFails(updateDoc(doc(eve, G), { 'roles.eve': 'member' })));
await t('cannot rename the group while joining', assertFails(updateDoc(doc(eve, G), { 'roles.eve': 'member', name: 'hack' })));
await t('ali joins with the code', assertSucceeds(join(ali, 'ali', 'ABC234')));
await t('ali reads members', assertSucceeds(getDocs(collection(ali, G + '/members'))));
await t('ali cannot promote self', assertFails(updateDoc(doc(ali, G), { 'roles.ali': 'admin' })));
await t('ali cannot change the group', assertFails(updateDoc(doc(ali, G), { name: 'x' })));
await t('ali cannot add a book', assertFails(setDoc(doc(ali, G + '/challenges/c2'), { title: 'x', start: '2026-11-01', end: '2026-11-02' })));
await t('ali renames himself', assertSucceeds(updateDoc(doc(ali, G + '/members/ali'), { name: 'علي' })));
await t('ali cannot rename others', assertFails(updateDoc(doc(ali, G + '/members/own'), { name: 'x' })));
await t('ali cannot add a reader', assertFails(setDoc(doc(ali, G + '/members/r1'), { name: 'x', linkedUid: null, active: true })));

// --- reading in the group
const e = (m, d, min = 30) => ({ memberId: m, date: d, minutes: min, updatedAt: 1 });
await t('ali logs his day in the group', assertSucceeds(setDoc(doc(ali, `${G}/entries/ali_2026-10-20`), e('ali', '2026-10-20'))));
await t('ali cannot log for the owner', assertFails(setDoc(doc(ali, `${G}/entries/own_2026-10-20`), e('own', '2026-10-20'))));
await t('entry id must be member_date', assertFails(setDoc(doc(ali, `${G}/entries/x`), e('ali', '2026-10-21'))));
await t('entry over 16 h rejected', assertFails(setDoc(doc(ali, `${G}/entries/ali_2026-10-22`), e('ali', '2026-10-22', 1000))));
await t('entry rejects extra fields', assertFails(setDoc(doc(ali, `${G}/entries/ali_2026-10-23`), { ...e('ali', '2026-10-23'), bonus: 99 })));
await t('ali deletes his day', assertSucceeds(deleteDoc(doc(ali, `${G}/entries/ali_2026-10-20`))));
await t('ali marks his finish', assertSucceeds(setDoc(doc(ali, `${G}/finishes/ali_c1`), { memberId: 'ali', challengeId: 'c1', date: '2026-10-20' })));
await t('ali cannot mark a finish for others', assertFails(setDoc(doc(ali, `${G}/finishes/own_c1`), { memberId: 'own', challengeId: 'c1', date: '2026-10-20' })));

// --- readers without the app (owner logs for them)
await t('owner adds a reader without the app', assertSucceeds(setDoc(doc(own, G + '/members/r_abu'), { name: 'أبو أحمد', linkedUid: null, active: true, joinedAt: 1 })));
await t('owner logs for the reader', assertSucceeds(setDoc(doc(own, `${G}/entries/r_abu_2026-10-20`), e('r_abu', '2026-10-20'))));

// --- roles
await t('owner makes ali admin', assertSucceeds(updateDoc(doc(own, G), { 'roles.ali': 'admin' })));
await t('admin edits the group', assertSucceeds(updateDoc(doc(ali, G), { about: 'نقرأ معاً' })));
await t('admin cannot promote anyone', assertFails(updateDoc(doc(ali, G), { 'roles.ali': 'owner' })));
await env.withSecurityRulesDisabled(async c => { await join(c.firestore(), 'sam', 'ABC234'); });
await t('admin removes a plain member', assertSucceeds(updateDoc(doc(ali, G), { 'roles.sam': deleteField() })));
await t('admin cannot remove the owner', assertFails(updateDoc(doc(ali, G), { 'roles.own': deleteField() })));
const sam = as('sam');
await t('removed member cannot log', assertFails(setDoc(doc(sam, `${G}/entries/sam_2026-10-20`), e('sam', '2026-10-20'))));
await t('removed member cannot read', assertFails(getDocs(collection(sam, G + '/entries'))));

// --- new invite code invalidates the old one
const b3 = writeBatch(own);
b3.set(doc(own, 'invites/NEW567'), { groupId: 'g1', createdBy: 'own' });
b3.update(doc(own, G), { inviteCode: 'NEW567' });
b3.delete(doc(own, 'invites/ABC234'));
await t('owner rotates the invite code', assertSucceeds(b3.commit()));
await t('old code no longer joins', assertFails(join(sam, 'sam', 'ABC234')));
await t('new code joins', assertSucceeds(join(sam, 'sam', 'NEW567')));

// --- leaving
const leave = (db, id) => {
  const b = writeBatch(db);
  b.update(doc(db, G), { [`roles.${id}`]: deleteField() });
  b.update(doc(db, `${G}/members/${id}`), { active: false });
  b.update(doc(db, `users/${id}`), { groups: [] });
  return b.commit();
};
await t('sam leaves', assertSucceeds(leave(sam, 'sam')));
await t('owner cannot leave without handing over', assertFails(updateDoc(doc(own, G), { 'roles.own': deleteField() })));

// --- public groups
await t('owner makes the group public', assertSucceeds(updateDoc(doc(own, G), { visibility: 'public' })));
await t('anyone lists public groups', assertSucceeds(getDocs(query(collection(eve, 'groups'), where('visibility', '==', 'public')))));
await t('public group: join without a code', assertSucceeds(join(eve, 'eve')));
await t('eve cannot rejoin as owner via member doc', assertFails(updateDoc(doc(eve, G), { 'roles.eve': 'owner' })));

// --- platform admin
await t('admin lists all users', assertSucceeds(getDocs(collection(adm, 'users'))));
await t('admin reads all entries', assertSucceeds(getDocs(collectionGroup(adm, 'entries'))));
await t('admin reads all personal days', assertSucceeds(getDocs(collectionGroup(adm, 'days'))));
await t('admin edits any group', assertSucceeds(updateDoc(doc(adm, G), { about: 'من الإدارة' })));
await t('unverified admin email gets nothing', assertFails(getDocs(collection(fake, 'users'))));
await t('a reader cannot read all entries', assertFails(getDocs(collectionGroup(ali, 'entries'))));

// --- handing over ownership (the old owner stays as an admin)
await env.withSecurityRulesDisabled(async c => { await updateDoc(doc(c.firestore(), G), { 'roles.ali': 'admin' }); });
await t('owner hands over to ali', assertSucceeds(updateDoc(doc(own, G), { 'roles.ali': 'owner', 'roles.own': 'admin' })));
await t('ali (new owner) hands it back', assertSucceeds(updateDoc(doc(ali, G), { 'roles.own': 'owner', 'roles.ali': 'admin' })));

// --- deleting
await t('admin cannot delete... (only the owner or platform admin)', assertFails(deleteDoc(doc(ali, G))));
await t('owner deletes the group', assertSucceeds(deleteDoc(doc(own, G))));

console.log(`\n${n - bad}/${n} passed`);
await env.cleanup();
process.exit(bad ? 1 : 0);
