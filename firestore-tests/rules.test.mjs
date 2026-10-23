import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, updateDoc, deleteDoc, collection, getDocs, writeBatch } from 'firebase/firestore';
import { readFileSync } from 'fs';

const env = await initializeTestEnvironment({ projectId: 'demo-marathon', firestore: { rules: readFileSync('../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8085 } });
const org = env.authenticatedContext('org').firestore();
const ali = env.authenticatedContext('ali').firestore();
const eve = env.authenticatedContext('eve').firestore();
let n = 0, bad = 0;
async function t(name, p) { n++; try { await p; console.log('ok  ', name); } catch (e) { bad++; console.log('FAIL', name, e.message.slice(0, 140)); } }

const M = 'marathons/mr1';
// --- organiser creates (same sequence as SharedSetup.create)
await t('org creates marathon', assertSucceeds(setDoc(doc(org, M), { name: 'نادي', inviteCode: 'ABC234', defaultRules: {}, createdAt: 1, roles: { org: 'organizer' } })));
await t('cannot create marathon naming someone else organiser', assertFails(setDoc(doc(eve, 'marathons/x'), { name: 'x', roles: { org: 'organizer' } })));
const b = writeBatch(org);
b.set(doc(org, 'invites/ABC234'), { marathonId: 'mr1', createdBy: 'org' });
b.set(doc(org, M + '/members/m_org'), { name: 'أحمد', active: true, linkedUid: 'org' });
await t('org batch: invite + own member', assertSucceeds(b.commit()));
await t('org adds member', assertSucceeds(setDoc(doc(org, M + '/members/m_ali'), { name: 'علي', active: true, linkedUid: null })));
await t('org adds challenge', assertSucceeds(setDoc(doc(org, M + '/challenges/c1'), { title: 'كتاب', start: '2026-10-01', end: '2026-10-30' })));

// --- outsider
await t('outsider cannot read marathon', assertFails(getDoc(doc(eve, M))));
await t('outsider cannot list invites', assertFails(getDocs(collection(eve, 'invites'))));
await t('outsider can get invite by code', assertSucceeds(getDoc(doc(eve, 'invites/ABC234'))));
await t('outsider cannot make self organiser', assertFails(updateDoc(doc(eve, M), { 'roles.eve': 'organizer' })));
await t('outsider cannot rename while joining', assertFails(updateDoc(doc(eve, M), { 'roles.eve': 'member', name: 'hack' })));

// --- ali joins (SharedSetup.join) and claims his name (linkMember)
await t('ali joins as member', assertSucceeds(updateDoc(doc(ali, M), { 'roles.ali': 'member' })));
await t('ali reads marathon', assertSucceeds(getDoc(doc(ali, M))));
await t('ali reads members', assertSucceeds(getDocs(collection(ali, M + '/members'))));
await t('ali cannot promote self', assertFails(updateDoc(doc(ali, M), { 'roles.ali': 'organizer' })));
await t('ali cannot claim organiser member', assertFails(setDoc(doc(ali, M + '/members/m_org'), { linkedUid: 'ali' }, { merge: true })));
await t('ali claims his member', assertSucceeds(setDoc(doc(ali, M + '/members/m_ali'), { linkedUid: 'ali' }, { merge: true })));
await t('ali cannot rename member', assertFails(updateDoc(doc(ali, M + '/members/m_ali'), { name: 'x' })));
await t('ali cannot write challenge', assertFails(setDoc(doc(ali, M + '/challenges/c2'), { title: 'x' })));

// --- entries
const e = (m, d, c = 30) => ({ memberId: m, date: d, commonMinutes: c, otherMinutes: 0, quoteCommon: false, quoteOther: false, updatedAt: 1 });
await t('ali logs own entry', assertSucceeds(setDoc(doc(ali, M + '/entries/m_ali_2026-10-20'), e('m_ali', '2026-10-20'))));
await t('ali cannot log for organiser member', assertFails(setDoc(doc(ali, M + '/entries/m_org_2026-10-20'), e('m_org', '2026-10-20'))));
await t('entry id must match member+date', assertFails(setDoc(doc(ali, M + '/entries/other'), e('m_ali', '2026-10-21'))));
await t('more than 16h rejected', assertFails(setDoc(doc(ali, M + '/entries/m_ali_2026-10-22'), e('m_ali', '2026-10-22', 1000))));
await t('org logs for ali', assertSucceeds(setDoc(doc(org, M + '/entries/m_ali_2026-10-19'), e('m_ali', '2026-10-19'))));
await t('ali deletes own entry', assertSucceeds(deleteDoc(doc(ali, M + '/entries/m_ali_2026-10-19'))));
await t('org entry for org member', assertSucceeds(setDoc(doc(org, M + '/entries/m_org_2026-10-20'), e('m_org', '2026-10-20'))));
await t('ali cannot delete org entry', assertFails(deleteDoc(doc(ali, M + '/entries/m_org_2026-10-20'))));

// --- achievements
const a = (m) => ({ memberId: m, challengeId: 'c1', type: 'FINISHED_COMMON_BOOK', date: '2026-10-20', bookTitle: null });
await t('ali adds own achievement', assertSucceeds(setDoc(doc(ali, M + '/achievements/a1'), a('m_ali'))));
await t('ali cannot add for others', assertFails(setDoc(doc(ali, M + '/achievements/a2'), a('m_org'))));
await t('ali deletes own achievement', assertSucceeds(deleteDoc(doc(ali, M + '/achievements/a1'))));

// --- self-add by name (joinAsNewMember: addMember then linkMember)
await env.withSecurityRulesDisabled(async c => { await updateDoc(doc(c.firestore(), M), { 'roles.sam': 'member' }); });
const sam = env.authenticatedContext('sam').firestore();
await t('new member adds self (unlinked)', assertSucceeds(setDoc(doc(sam, M + '/members/m_sam'), { name: 'سام', active: true, linkedUid: null })));
await t('new member links self', assertSucceeds(setDoc(doc(sam, M + '/members/m_sam'), { linkedUid: 'sam' }, { merge: true })));
await t('member cannot add archived/forged fields', assertFails(setDoc(doc(sam, M + '/members/m_x'), { name: 'x', active: true, linkedUid: 'ali' })));
await t('org archives member', assertSucceeds(updateDoc(doc(org, M + '/members/m_sam'), { active: false })));
await t('org renames marathon', assertSucceeds(updateDoc(doc(org, M), { name: 'جديد' })));

console.log(`\n${n - bad}/${n} passed`);
await env.cleanup();
process.exit(bad ? 1 : 0);
