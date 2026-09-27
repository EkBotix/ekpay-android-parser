import assert from 'node:assert/strict';
import {readFile, readdir} from 'node:fs/promises';
import {resolve} from 'node:path';
const root=resolve(import.meta.dirname,'..');
const banned=/android\.permission\.(?:READ_SMS|RECEIVE_SMS|SEND_SMS|READ_CONTACTS|READ_PHONE_STATE|READ_PHONE_NUMBERS)|android\.provider\.Telephony\.SMS/;
for(const variant of ['debug','release']) {
  const xml=await readFile(resolve(root,`app/build/intermediates/merged_manifests/${variant}/process${variant[0].toUpperCase()+variant.slice(1)}Manifest/AndroidManifest.xml`),'utf8');
  assert.ok(!banned.test(xml),'Forbidden permission/receiver');
  assert.match(xml,/android:allowBackup="false"/); assert.match(xml,/android:usesCleartextTraffic="false"/);
  for(const component of xml.matchAll(/<(?:activity|service|receiver|provider)\b[^>]*>/g)) {
    const tag=component[0]; if(!tag.includes('android:exported="true"')) continue;
    const name=/android:name="([^"]+)"/.exec(tag)?.[1];
    if(name==='com.ekbotix.ekpayparser.ui.MainActivity') continue;
    const required=new Map([
      ['androidx.work.impl.background.systemjob.SystemJobService','android.permission.BIND_JOB_SERVICE'],
      ['androidx.work.impl.diagnostics.DiagnosticsReceiver','android.permission.DUMP'],
      ['androidx.profileinstaller.ProfileInstallReceiver','android.permission.DUMP'],
    ]).get(name);
    assert.ok(required && tag.includes(`android:permission="${required}"`),`Unreviewed exported component ${name}`);
  }
}
const release=await readFile(resolve(root,'app/build/generated/source/buildConfig/release/com/ekbotix/ekpayparser/BuildConfig.java'),'utf8');
assert.match(release,/SANDBOX_NETWORKING = false/); assert.match(release,/TEST_BASE_URL = ""/); assert.match(release,/TEST_ONLY = true/);
async function inspect(dir) {
  for(const entry of await readdir(dir,{withFileTypes:true})) {
    const path=resolve(dir,entry.name);
    if(entry.isDirectory()) { await inspect(path); continue; }
    assert.ok(!/\.(?:pem|p12|pfx|jks|keystore)$/i.test(entry.name),'Private key material file in source');
    const text=await readFile(path,'utf8');
    assert.ok(!/-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/.test(text),'Private key in source');
    assert.ok(!/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/.test(text),'Hardcoded JWT in source');
  }
}
await inspect(resolve(root,'app/src')); await inspect(resolve(root,'core/src'));
console.log('PASS: debug/release merged permissions, guarded exports, backup/TLS policy, disabled release config and source key/JWT scan');
