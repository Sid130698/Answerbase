import {readFileSync} from 'node:fs'
const [before,after]=process.argv.slice(2).map(path=>JSON.parse(readFileSync(path)))
const identities=data=>data.models.map(m=>`${m.name}:${m.digest}`).sort()
if(JSON.stringify(identities(before))!==JSON.stringify(identities(after)))throw new Error('Model weights changed between runs; reports are not comparable')
console.log('Baseline and candidate used identical model weights.')
