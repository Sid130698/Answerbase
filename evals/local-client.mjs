import {readFileSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
export const base=process.env.API_URL||'http://localhost:8080/api/v1'
const local=url=>{if(!['localhost','127.0.0.1','[::1]'].includes(new URL(url).hostname))throw new Error('Local fixture runner only accepts loopback services')}
local(base)
const status=JSON.parse(execFileSync('npx',['--yes','supabase@3.3.0','status','-o','json'],{encoding:'utf8',stdio:['ignore','pipe','ignore']}))
local(status.API_URL)
const account=JSON.parse(readFileSync(process.env.TEST_ACCOUNT_FILE||'.cache/support-demo.json','utf8')).client
let token,expires=0
export async function api(path,init={}) {
 if(Date.now()>=expires){
  const response=await fetch(status.API_URL+'/auth/v1/token?grant_type=password',{method:'POST',redirect:'error',headers:{apikey:status.PUBLISHABLE_KEY||status.ANON_KEY,'Content-Type':'application/json'},body:JSON.stringify(account),signal:AbortSignal.timeout(15000)})
  if(!response.ok)throw new Error('Local sign-in failed: '+response.status)
  const session=await response.json();token=session.access_token;expires=Date.now()+(session.expires_in-60)*1000
 }
 const response=await fetch(base+path,{...init,redirect:'error',signal:AbortSignal.timeout(420000),headers:{Authorization:'Bearer '+token,...init.headers}})
 if(!response.ok)throw new Error(path+': HTTP '+response.status)
 const text=await response.text();return text?JSON.parse(text):null
}
export const json=body=>({method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
export const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms))
