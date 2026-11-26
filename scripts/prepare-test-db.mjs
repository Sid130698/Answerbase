import {execFileSync} from 'node:child_process'
const args=['compose','exec','-T','db','psql','-U','answerbase','-d','postgres','-At','-c',"SELECT 1 FROM pg_database WHERE datname='answerbase_test'"]
const exists=execFileSync('docker',args,{encoding:'utf8',stdio:['ignore','pipe','pipe']}).trim()==='1'
if(!exists)execFileSync('docker',['compose','exec','-T','db','createdb','-U','answerbase','answerbase_test'],{stdio:'inherit'})
console.log('Isolated answerbase_test database ready; application database is unchanged.')
