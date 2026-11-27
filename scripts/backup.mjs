import {execFileSync} from 'node:child_process'
import {mkdirSync,openSync,closeSync,writeFileSync,readFileSync,chmodSync} from 'node:fs'
import {resolve,dirname,sep} from 'node:path'
import {createHash,randomUUID} from 'node:crypto'

// Restore verification always uses a new, disposable database, never the application database.
const production=process.argv.includes('--production')
const compose=['compose',...(production?['--env-file','.env.production','-f','compose.yml','-f','infra/deploy/compose.yml']:[])]
const run=(tail,options={})=>execFileSync('docker',[...compose,...tail],{stdio:['ignore','pipe','pipe'],...options})
const stamp=new Date().toISOString().replaceAll(':','-')
const folder=resolve('.cache','backups',stamp)
mkdirSync(folder,{recursive:true,mode:0o700});chmodSync(folder,0o700)
const dump=resolve(folder,'database.dump')
const fd=openSync(dump,'wx',0o600)
try{run(['exec','-T','db','sh','-c','exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-acl'],{stdio:['ignore',fd,'pipe']})}finally{closeSync(fd)}
const digest=createHash('sha256').update(readFileSync(dump)).digest('hex')
const database='restore_check_'+randomUUID().replaceAll('-','')
let created=false
try{
 run(['exec','-T','db','sh','-c','exec createdb -U "$POSTGRES_USER" "$1"','sh',database]);created=true
 run(['exec','-T','db','sh','-c','exec pg_restore -U "$POSTGRES_USER" -d "$1" --no-owner --no-acl --exit-on-error','sh',database],{input:readFileSync(dump),stdio:['pipe','pipe','pipe']})
 const query="SELECT json_build_object('workspaces',(SELECT count(*) FROM answerbase.workspaces),'documents',(SELECT count(*) FROM answerbase.documents),'questions',(SELECT count(*) FROM answerbase.questions))"
 const counts=run(['exec','-T','db','sh','-c','exec psql -XAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1" -c "$2"','sh',database,query],{encoding:'utf8'})
 let originals=0
 if(!process.argv.includes('--database-only')) {
  const originalsRoot=resolve(folder,'originals');mkdirSync(originalsRoot,{mode:0o700})
  const refsQuery="SELECT coalesce(json_agg(json_build_object('key',storage_key,'sha256',checksum,'bytes',byte_size)),'[]'::json) FROM answerbase.document_versions"
  const refs=JSON.parse(run(['exec','-T','db','sh','-c','exec psql -XAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1" -c "$2"','sh',database,refsQuery],{encoding:'utf8',maxBuffer:16*1024*1024}))
  let env={}
  if(production) env=Object.fromEntries(readFileSync('.env.production','utf8').split('\n').filter(line=>line&&!line.startsWith('#')).map(line=>{const i=line.indexOf('=');return [line.slice(0,i),line.slice(i+1)]}))
  if(production && env.DOCUMENT_STORAGE_URL) {
   const base=new URL(env.DOCUMENT_STORAGE_URL)
   if(base.protocol!=='https:')throw new Error('HTTPS required for backup storage')
   for(const ref of refs) {
    if(!/^[a-zA-Z0-9/_-]+$/.test(ref.key))throw new Error('Unsafe object key')
    const response=await fetch(base.origin+'/storage/v1/object/'+(env.DOCUMENT_STORAGE_BUCKET||'answerbase-documents')+'/'+ref.key,{headers:{apikey:env.DOCUMENT_STORAGE_SERVICE_KEY,Authorization:'Bearer '+env.DOCUMENT_STORAGE_SERVICE_KEY},signal:AbortSignal.timeout(30000)})
    if(!response.ok)throw new Error('Original backup failed: HTTP '+response.status)
    const chunks=[];let size=0
    for await(const chunk of response.body){size+=chunk.length;if(size>5242880)throw new Error('Original exceeds storage limit');chunks.push(chunk)}
    const path=resolve(originalsRoot,ref.key)
    if(!path.startsWith(originalsRoot+sep))throw new Error('Unsafe original path')
    mkdirSync(dirname(path),{recursive:true,mode:0o700});writeFileSync(path,Buffer.concat(chunks),{mode:0o600})
   }
  } else {
   const archive=resolve(folder,'originals.tar.gz'), out=openSync(archive,'wx',0o600)
   try{run(['exec','-T','worker','tar','-czf','-','-C','/app/documents','.'],{stdio:['ignore',out,'pipe']})}finally{closeSync(out)}
   execFileSync('tar',['-xzf',archive,'-C',originalsRoot],{stdio:'pipe'})
  }
  for(const ref of refs) {
   const path=resolve(originalsRoot,ref.key)
   if(!path.startsWith(originalsRoot+sep))throw new Error('Unsafe original path')
   const bytes=readFileSync(path)
   if(bytes.length!==ref.bytes || createHash('sha256').update(bytes).digest('hex')!==ref.sha256)throw new Error('Original backup integrity check failed')
   originals++
  }
 }
 writeFileSync(resolve(folder,'manifest.json'),JSON.stringify({createdAt:stamp,sha256:digest,restoreVerified:true,originalsVerified:originals,counts:JSON.parse(counts),scope:process.argv.includes('--database-only')?'Application PostgreSQL only':'Application PostgreSQL plus referenced original files. Managed Supabase Auth remains a separate recovery dependency.'},null,2)+'\n',{mode:0o600})
 console.log(JSON.stringify({backup:folder,restoreVerified:true,originalsVerified:originals}))
}finally{
 if(created)run(['exec','-T','db','sh','-c','exec dropdb -U "$POSTGRES_USER" "$1"','sh',database])
}
