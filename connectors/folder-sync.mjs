import {createHash,randomUUID} from 'node:crypto'
import {constants,existsSync,lstatSync,mkdirSync,openSync,closeSync,fstatSync,readSync,readFileSync,readdirSync,realpathSync,renameSync,writeFileSync} from 'node:fs'
import {basename,dirname,extname,join,relative,resolve} from 'node:path'
import {fileURLToPath} from 'node:url'
const supported=new Set(['.md','.txt','.html','.htm','.pdf','.docx'])
const digest=value=>createHash('sha256').update(value).digest('hex')
function files(root,current=root,result=[]) {
 for(const entry of readdirSync(current,{withFileTypes:true}).sort((a,b)=>a.name.localeCompare(b.name))) {
  if(entry.name.startsWith('.'))continue
  const path=join(current,entry.name)
  if(entry.isSymbolicLink())throw new Error(`Symbolic links are not supported: ${relative(root,path)}`)
  if(entry.isDirectory())files(root,path,result)
  else if(entry.isFile()&&supported.has(extname(entry.name).toLowerCase())){result.push(path);if(result.length>200)throw new Error('This preview supports up to 200 source files per sync folder.')}
 }
 return result
}
function bytes(path){const fd=openSync(path,constants.O_RDONLY|constants.O_NOFOLLOW);try{if(!fstatSync(fd).isFile())throw new Error('Only regular files are supported');const buffer=Buffer.alloc(5*1024*1024+1);let size=0;while(size<buffer.length){const n=readSync(fd,buffer,size,buffer.length-size,null);if(!n)break;size+=n}if(size===0||size>5*1024*1024)throw new Error(`File must contain 1 byte–5 MB: ${basename(path)}`);return buffer.subarray(0,size)}finally{closeSync(fd)}}
export async function syncFolder({folder,workspace,token,base='http://localhost:8080/api/v1',stateDirectory='.cache/folder-sync',apply=false,log=console.log}) {
 const endpoint=new URL(base)
 if(endpoint.username||endpoint.password||endpoint.search||endpoint.hash)throw new Error('API URL must not include credentials, a query or fragment')
 if(endpoint.protocol!=='https:'&&!(endpoint.protocol==='http:'&&['localhost','127.0.0.1','[::1]'].includes(endpoint.hostname)))throw new Error('Use HTTPS except for a loopback local API')
 if(!token)throw new Error('Set ANSWERBASE_TOKEN to a current customer access token; never put it in source control')
 if(!/^[0-9a-f-]{36}$/i.test(workspace||''))throw new Error('Provide a workspace UUID')
 const root=realpathSync(folder)
 if(!lstatSync(root).isDirectory())throw new Error('Choose an extracted export folder')
 base=base.replace(/\/$/,'')
 async function api(path,options={}){const response=await fetch(base+path,{...options,redirect:'error',signal:AbortSignal.timeout(60000),headers:{...options.headers,Authorization:`Bearer ${token}`}});if(!response.ok)throw new Error(`Answerbase returned HTTP ${response.status}; sync state is preserved for retry`);return response.json()}
 const identity=await api('/me');const access=await api(`/workspaces/${workspace}`)
 if(!['admin','editor'].includes(access.role))throw new Error('Folder sync requires editor or admin workspace access')
 const statePath=join(stateDirectory,digest(`${base}\n${identity.id}\n${workspace}\n${root}`)+'.json')
 let state=existsSync(statePath)?JSON.parse(readFileSync(statePath,'utf8')):{root,workspace,user:identity.id,base,files:{}}
 if(state.root!==root||state.workspace!==workspace||state.user!==identity.id||state.base!==base)throw new Error('Sync manifest scope mismatch')
 const save=()=>{mkdirSync(dirname(statePath),{recursive:true,mode:0o700});const temporary=statePath+'.tmp';writeFileSync(temporary,JSON.stringify(state,null,2)+'\n',{mode:0o600});renameSync(temporary,statePath)}
 // No two processes may publish updates to the same manifest simultaneously.
 mkdirSync(dirname(statePath),{recursive:true,mode:0o700});let lock
 try{lock=openSync(statePath+'.lock','wx',0o600)}catch{throw new Error('Another sync owns this manifest. If it crashed, remove its .lock file only after checking that no sync process is running.')}
 const result={created:0,updated:0,unchanged:0,missing:0,dryRun:!apply}
 try {
  const seen=new Set()
  for(const path of files(root)) {
   const name=relative(root,path);seen.add(name);const content=bytes(path);const hash=digest(content);const previous=state.files[name]
   if(previous?.pending&&previous.pending.hash!==hash)throw new Error(`Restore the content of ${name} from its interrupted upload before retrying; its server result must be reconciled first`)
   if(previous?.hash===hash&&!previous.pending){result.unchanged++;continue}
   const action=previous?.documentId?'updated':'created';result[action]++;log(`${apply?'Sync':'Would sync'} ${name} (${action})`)
   if(!apply)continue
   const requestKey=previous?.pending?.requestKey||randomUUID()
   state.files[name]={...previous,pending:{hash,requestKey}};save()
   const body=new FormData();body.set('file',new Blob([content]),basename(path))
   const suffix=previous?.documentId?`/${previous.documentId}/versions`:''
   const version=await api(`/workspaces/${workspace}/documents${suffix}`,{method:'POST',headers:{'Idempotency-Key':requestKey},body})
   state.files[name]={hash,documentId:version.documentId,versionId:version.id,lastSyncedAt:new Date().toISOString()};save()
  }
  for(const name of Object.keys(state.files))if(!seen.has(name)){result.missing++;log(`Missing locally, retained in Answerbase: ${name}`)}
  return result
 } finally {closeSync(lock);const {unlinkSync}=await import('node:fs');unlinkSync(statePath+'.lock')}
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
 const args=process.argv.slice(2);const option=name=>args[args.indexOf(name)+1]
 if(!args.includes('--folder')||!args.includes('--workspace')){console.error('Usage: ANSWERBASE_TOKEN=<token> node connectors/folder-sync.mjs --folder <export-directory> --workspace <uuid> [--apply]');process.exitCode=1}
 else syncFolder({folder:option('--folder'),workspace:option('--workspace'),token:process.env.ANSWERBASE_TOKEN,base:process.env.ANSWERBASE_API_URL,apply:args.includes('--apply')}).then(result=>console.log(JSON.stringify(result))).catch(error=>{console.error(error.message);process.exitCode=1})
}
