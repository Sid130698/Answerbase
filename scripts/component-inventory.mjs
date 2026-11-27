import {execFileSync} from 'node:child_process'
import {mkdirSync,readFileSync,writeFileSync} from 'node:fs'
mkdirSync('.cache/sbom',{recursive:true})
const components=[]
for(const app of ['frontend','support-frontend']){
 const text=execFileSync('npm',['--prefix',app,'sbom','--sbom-format','cyclonedx'],{encoding:'utf8',maxBuffer:16*1024*1024})
 writeFileSync('.cache/sbom/'+app+'.json',text)
 const bom=JSON.parse(text)
 for(const c of bom.components)components.push({surface:app,name:c.name,version:c.version,purl:c.purl,licenses:c.licenses})
}
execFileSync('./backend/mvnw',['-f','backend/pom.xml','-B','-ntp','org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom'],{stdio:'inherit'})
for(const c of JSON.parse(readFileSync('backend/target/bom.json')).components)components.push({surface:'backend',name:c.name,version:c.version,purl:c.purl,licenses:c.licenses})
let python=[]
try{python=JSON.parse(readFileSync('.cache/sbom/reranker.json'))}catch(e){if(e.code!=='ENOENT')throw e}
const report={generatedAt:new Date().toISOString(),scope:'Installed npm dependency graphs and Maven aggregate dependencies; optional installed local reranker Python metadata. Model terms and OS/container packages are separate.',components,python,missingDeclaredLicense:components.filter(c=>!c.licenses?.length).map(c=>c.purl)}
writeFileSync('docs/dependency-inventory.json',JSON.stringify(report,null,2)+'\n')
console.log(JSON.stringify({components:components.length,python:python.length,missingDeclaredLicense:report.missingDeclaredLicense}))
