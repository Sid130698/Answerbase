import {readFileSync,mkdirSync,writeFileSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import {syncFolder} from '../connectors/folder-sync.mjs'
const status=JSON.parse(execFileSync('npx',['--yes','supabase@3.3.0','status','-o','json'],{encoding:'utf8',stdio:['ignore','pipe','ignore']}))
const account=JSON.parse(readFileSync('.cache/support-demo.json','utf8')).client
const response=await fetch(`${status.API_URL}/auth/v1/token?grant_type=password`,{method:'POST',headers:{apikey:status.PUBLISHABLE_KEY||status.ANON_KEY,'Content-Type':'application/json'},body:JSON.stringify({email:account.email,password:account.password})})
if(!response.ok)throw new Error('Local test login failed')
const token=(await response.json()).access_token
const workspaces=await(await fetch('http://localhost:8080/api/v1/workspaces',{headers:{Authorization:`Bearer ${token}`}})).json()
const workspace=workspaces.find(w=>w.role==='admin').id
const folder='.cache/notion-export-smoke';mkdirSync(folder,{recursive:true});writeFileSync(`${folder}/team-guide.md`,'# Team guide\n\nSynthetic connector fixture. Office hours are Monday 10:00 UTC.\n')
const options={folder,workspace,token,apply:true}
await syncFolder(options)
const repeat=await syncFolder(options)
if(repeat.unchanged!==1||repeat.created||repeat.updated)throw new Error('Repeat sync was not idempotent')
writeFileSync(`${folder}/team-guide.md`,'# Team guide\n\nSynthetic connector fixture. Office hours are Tuesday 10:00 UTC.\n')
const changed=await syncFolder(options)
if(changed.updated!==1)throw new Error('Changed file did not create a version')
console.log('Local connector smoke passed: repeated sync skipped content; changed file created a version.')
