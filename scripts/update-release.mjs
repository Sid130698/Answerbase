// Pull deployment avoids exposing SSH to changing GitHub-hosted runner IP ranges.
// Enable the host timer only after main has PR review and required-check protection.
import {execFileSync} from 'node:child_process'
import {readFileSync} from 'node:fs'
const git=(...args)=>execFileSync('git',args,{encoding:'utf8',stdio:['ignore','pipe','pipe']}).trim()
const remote=git('remote','get-url','origin')
const match=remote.match(/^(?:https:\/\/github.com\/|git@github.com:)([\w.-]+\/[\w.-]+?)(?:\.git)?$/)
if(!match)throw new Error('Expected a GitHub origin')
git('fetch','--no-tags','origin','main')
const revision=git('rev-parse','origin/main')
let deployed=''
try{deployed=readFileSync('.cache/deployed-revision','utf8').trim()}catch(error){if(error.code!=='ENOENT')throw error}
if(deployed===revision){console.log('Already running the current release.');process.exit(0)}
const response=await fetch(`https://api.github.com/repos/${match[1]}/commits/${revision}/check-runs?per_page=100`,{headers:{Accept:'application/vnd.github+json'},signal:AbortSignal.timeout(15000)})
if(!response.ok)throw new Error('Could not verify release checks: HTTP '+response.status)
const result=await response.json()
if(result.total_count>100)throw new Error('Check pagination needs operator review')
const required=['backend','frontend','support-frontend','connectors','evaluation-contracts','deployment-config']
const eligible=required.every(name=>result.check_runs.some(check=>check.name===name&&check.head_sha===revision&&check.app?.slug==='github-actions'&&check.status==='completed'&&check.conclusion==='success'))
if(!eligible){console.log('Main is not deployable yet: required CI checks have not all passed.');process.exit(0)}
if(git('status','--porcelain'))throw new Error('Host checkout has local changes; refusing to overwrite them')
git('checkout','--detach',revision)
execFileSync(process.execPath,['scripts/deploy.mjs'],{stdio:'inherit'})
