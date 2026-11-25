import {test} from 'node:test'
import assert from 'node:assert/strict'
import {createServer} from 'node:http'
import {mkdtempSync,mkdirSync,writeFileSync,rmSync,symlinkSync} from 'node:fs'
import {tmpdir} from 'node:os'
import {join} from 'node:path'
import {syncFolder} from './folder-sync.mjs'
test('scoped incremental sync, dry run, versions, removed files and symlink rejection',async()=>{
 const root=mkdtempSync(join(tmpdir(),'answerbase-sync-'));const folder=join(root,'export');mkdirSync(folder);writeFileSync(join(folder,'policy.md'),'# Policy\nRefunds within 30 days.');let calls=[]
 const workspace='11111111-1111-4111-8111-111111111111'
 const server=createServer(async(req,res)=>{assert.equal(req.headers.authorization,'Bearer test-token');let body='';for await(const chunk of req)body+=chunk;calls.push({method:req.method,url:req.url,body});res.setHeader('Content-Type','application/json');res.end(JSON.stringify(req.url.endsWith('/me')?{id:'user-a'}:req.method==='GET'?{id:workspace,role:'editor'}:{id:'version-a',documentId:'document-a'}))})
 await new Promise(r=>server.listen(0,'127.0.0.1',r));const options={folder,workspace,token:'test-token',base:`http://127.0.0.1:${server.address().port}/api/v1`,stateDirectory:join(root,'state'),log:()=>{}}
 try{
  assert.equal((await syncFolder(options)).created,1);assert.equal(calls.filter(c=>c.method==='POST').length,0)
  assert.equal((await syncFolder({...options,apply:true})).created,1)
  assert.equal((await syncFolder({...options,apply:true})).unchanged,1)
  writeFileSync(join(folder,'policy.md'),'# Policy\nRefunds within 60 days.')
  assert.equal((await syncFolder({...options,apply:true})).updated,1)
  assert.match(calls.filter(c=>c.method==='POST')[1].url,/document-a\/versions$/)
  rmSync(join(folder,'policy.md'));assert.equal((await syncFolder({...options,apply:true})).missing,1)
  symlinkSync(root,join(folder,'escape'));await assert.rejects(syncFolder(options),/Symbolic links/)
  await assert.rejects(syncFolder({...options,base:'http://example.com/api/v1'}),/Use HTTPS/)
 }finally{server.close();rmSync(root,{recursive:true,force:true})}
})
